(ns domesticops.governor-contract-test
  "The governor contract as executable tests -- this vertical's own
  Trust Controls ('payroll must balance gross minus documented
  deductions; safety-critical actions... require human sign-off')
  implemented faithfully. The single invariant under test:

    DomesticOps-LLM never dispatches a mission or posts payroll the
    Domestic Employment Governor would reject, `:assignment/
    dispatch`/`:assignment/pay` NEVER auto-commit at any phase,
    `:assignment/intake` (no direct household-facing risk) MAY auto-
    commit when clean, and every decision (commit OR hold) leaves
    exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [domesticops.store :as store]
            [domesticops.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :household-employer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- dispatch!
  "Walks `subject` through dispatch -> approve, leaving :dispatched?
  true. Assumes `assess!` already ran for this subject."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-dispatch") {:op :assignment/dispatch :subject subject} operator)
  (approve! actor (str tid-prefix "-dispatch")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :assignment/intake :subject "assignment-1"
                   :patch {:id "assignment-1" :worker "Kita Sato"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Kita Sato" (:worker (store/assignment db "assignment-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "assignment-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "assignment-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "assignment-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "assignment-1")) "no assessment written"))))

(deftest dispatch-without-assessment-is-held
  (testing "assignment/dispatch before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :assignment/dispatch :subject "assignment-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest household-employment-unregistered-is-held-and-unoverridable
  (testing "an unregistered household employer -> HOLD, and never reaches request-approval -- the FLAGSHIP genuinely new check this vertical adds, the 90th unconditional-evaluation-discipline grounding overall, grounded in Japan's own 家事使用人特例 registration obligations, the US's IRS Schedule H, the UK's HMRC PAYE-for-Employers and Germany's Minijob-Zentrale Haushaltsscheckverfahren"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "assignment-4")
          res (exec-op actor "t5" {:op :assignment/dispatch :subject "assignment-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:household-employment-unregistered} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest payroll-mismatch-is-held
  (testing "a claimed gross that doesn't equal kotoba.labor/wages-for's own recompute -> HOLD (the ground-truth-recompute discipline every sibling's cost/total-matching check establishes, this time delegating to the REAL kotoba.labor capability library)"
    (let [[db actor] (fresh)
          _ (assess! actor "t6pre" "assignment-3")
          _ (dispatch! actor "t6pre" "assignment-3")
          res (exec-op actor "t6" {:op :assignment/pay :subject "assignment-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:payroll-mismatch} (-> (store/ledger db) last :basis)))
      (is (empty? (store/payroll-history db))))))

(deftest vulnerable-person-safeguarding-check-missing-is-held-and-unoverridable
  (testing "a missing safeguarding check on a vulnerable-person assignment -> HOLD, and never reaches request-approval -- a genuinely new check, the 91st unconditional-evaluation-discipline grounding overall, the FIFTEENTH conditional variant (see this actor's governor ns docstring / the full accumulated ADR-0001 chain: parksafety's ADR-2607071922 Decision 5 through leathergoods's, ictrepair's, retailops's, freightops's, quarryops's, agronomyops's, hospitalityops's, practiceops's, employmentops's, adminops's and libraryops's own)"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "assignment-5")
          res (exec-op actor "t7" {:op :assignment/dispatch :subject "assignment-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:vulnerable-person-safeguarding-check-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest dispatch-is-a-noop-when-no-vulnerable-person-involved
  (testing "the safeguarding-check check is CONDITIONAL: an assignment with no vulnerable-person involvement has no such requirement at all"
    (let [[_db actor] (fresh)
          _ (assess! actor "t7bpre" "assignment-1")
          res (exec-op actor "t7b" {:op :assignment/dispatch :subject "assignment-1"} operator)]
      (is (= :interrupted (:status res)) "clean dispatch still escalates for human sign-off, but is NOT a HARD hold"))))

(deftest pay-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, matching-gross payroll posting still ALWAYS interrupts for human approval -- actuation/post-payroll is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "assignment-1")
          _ (dispatch! actor "t8pre" "assignment-1")
          r1 (exec-op actor "t8" {:op :assignment/pay :subject "assignment-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, payroll record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:paid? (store/assignment db "assignment-1"))))
          (is (= 1 (count (store/payroll-history db))) "one draft payroll record"))))))

(deftest dispatch-always-escalates-then-human-decides
  (testing "a clean, fully-assessed dispatch still ALWAYS interrupts for human approval -- actuation/dispatch-mission is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "assignment-1")
          r1 (exec-op actor "t9" {:op :assignment/dispatch :subject "assignment-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:dispatched? (store/assignment db "assignment-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest assignment-double-dispatch-is-held
  (testing "dispatching the same assignment record twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "assignment-1")
          _ (dispatch! actor "t10pre" "assignment-1")
          res (exec-op actor "t10" {:op :assignment/dispatch :subject "assignment-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest assignment-double-payroll-posting-is-held
  (testing "posting payroll for the same assignment twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t11pre" "assignment-1")
          _ (dispatch! actor "t11pre" "assignment-1")
          _ (exec-op actor "t11a" {:op :assignment/pay :subject "assignment-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :assignment/pay :subject "assignment-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-paid} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/payroll-history db))) "still only the one earlier payroll posting"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :assignment/intake :subject "assignment-1"
                          :patch {:id "assignment-1" :worker "Kita Sato"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "assignment-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
