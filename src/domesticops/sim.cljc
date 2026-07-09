(ns domesticops.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean assignment
  through intake -> jurisdiction assessment -> mission dispatch
  (escalate/approve/commit) -> payroll posting (escalate/approve/
  commit), then a SEPARATE clean vulnerable-person assignment through
  the same lifecycle (demonstrating the conditional safeguarding-
  check check passing cleanly), then shows HARD-hold scenarios: a
  jurisdiction with no spec-basis, a payroll mismatch (verified
  first), an unregistered household employer, and a missing
  safeguarding check on a vulnerable-person assignment, a double
  dispatch, and a double payroll posting.

  Like `retailops`/4711's, `freightops`/4920's, `quarryops`/0810's,
  `agronomyops`/0162's, `hospitalityops`/5510's, `practiceops`/7110's,
  `employmentops`/7810's, `adminops`/8411's and `libraryops`/9101's
  own new checks, this actor's new checks (`household-employment-
  unregistered?`, `vulnerable-person-safeguarding-check-missing?`) are
  evaluated directly at `:assignment/dispatch`/`:assignment/pay` time
  rather than via a separate screening op -- a real dispatch/payroll
  decision validates household-employer registration and safeguarding
  clearance at the point of the act itself. Each check is still
  exercised directly and independently below, one assignment per
  HARD-hold scenario, following the SAME 'exercise the failure mode
  directly, never only via a happy-path actuation' discipline
  `parksafety`'s ADR-2607071922 Decision 5 and every sibling since
  establish."
  (:require [langgraph.graph :as g]
            [domesticops.store :as store]
            [domesticops.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :household-employer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== assignment/intake assignment-1 (JPN, clean, no vulnerable person) ==")
    (println (exec-op actor "t1" {:op :assignment/intake :subject "assignment-1"
                                  :patch {:id "assignment-1" :worker "Kita Sato"}} operator))

    (println "== jurisdiction/assess assignment-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :jurisdiction/assess :subject "assignment-1"} operator))
    (println (approve! actor "t2"))

    (println "== assignment/dispatch assignment-1 (always escalates -- actuation/dispatch-mission) ==")
    (let [r (exec-op actor "t3" {:op :assignment/dispatch :subject "assignment-1"} operator)]
      (println r)
      (println "-- human household-employer approves --")
      (println (approve! actor "t3")))

    (println "== assignment/pay assignment-1 (always escalates -- actuation/post-payroll) ==")
    (let [r (exec-op actor "t4" {:op :assignment/pay :subject "assignment-1"} operator)]
      (println r)
      (println "-- human household-employer approves --")
      (println (approve! actor "t4")))

    (println "== assignment/intake assignment-6 (JPN, clean, vulnerable person, safeguarding verified) ==")
    (println (exec-op actor "t5" {:op :assignment/intake :subject "assignment-6"
                                  :patch {:id "assignment-6" :worker "Chuo Yuki"}} operator))

    (println "== jurisdiction/assess assignment-6 (escalates -- human approves) ==")
    (println (exec-op actor "t6" {:op :jurisdiction/assess :subject "assignment-6"} operator))
    (println (approve! actor "t6"))

    (println "== assignment/dispatch assignment-6 (vulnerable person, safeguarding verified -- escalates -- human approves) ==")
    (println (exec-op actor "t7" {:op :assignment/dispatch :subject "assignment-6"} operator))
    (println (approve! actor "t7"))

    (println "== assignment/pay assignment-6 (always escalates -- human approves) ==")
    (println (exec-op actor "t7b" {:op :assignment/pay :subject "assignment-6"} operator))
    (println (approve! actor "t7b"))

    (println "== jurisdiction/assess assignment-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :jurisdiction/assess :subject "assignment-2" :no-spec? true} operator))

    (println "== jurisdiction/assess assignment-3 (escalates -- human approves; sets up the payroll-mismatch test) ==")
    (println (exec-op actor "t9" {:op :jurisdiction/assess :subject "assignment-3"} operator))
    (println (approve! actor "t9"))

    (println "== assignment/dispatch assignment-3 (always escalates -- human approves) ==")
    (println (exec-op actor "t9b" {:op :assignment/dispatch :subject "assignment-3"} operator))
    (println (approve! actor "t9b"))

    (println "== assignment/pay assignment-3 (claimed 10000 vs kotoba.labor/wages-for recompute 8000 -> HARD hold) ==")
    (println (exec-op actor "t10" {:op :assignment/pay :subject "assignment-3"} operator))

    (println "== jurisdiction/assess assignment-4 (escalates -- human approves; sets up the unregistered-household test) ==")
    (println (exec-op actor "t11" {:op :jurisdiction/assess :subject "assignment-4"} operator))
    (println (approve! actor "t11"))

    (println "== assignment/dispatch assignment-4 (household employment unregistered -> HARD hold) ==")
    (println (exec-op actor "t12" {:op :assignment/dispatch :subject "assignment-4"} operator))

    (println "== jurisdiction/assess assignment-5 (escalates -- human approves; sets up the safeguarding-check test) ==")
    (println (exec-op actor "t13" {:op :jurisdiction/assess :subject "assignment-5"} operator))
    (println (approve! actor "t13"))

    (println "== assignment/dispatch assignment-5 (vulnerable person, safeguarding check missing -> HARD hold) ==")
    (println (exec-op actor "t14" {:op :assignment/dispatch :subject "assignment-5"} operator))

    (println "== assignment/dispatch assignment-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec-op actor "t15" {:op :assignment/dispatch :subject "assignment-1"} operator))

    (println "== assignment/pay assignment-1 AGAIN (double-payroll-posting -> HARD hold) ==")
    (println (exec-op actor "t16" {:op :assignment/pay :subject "assignment-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft payroll records ==")
    (doseq [r (store/payroll-history db)] (println r))))
