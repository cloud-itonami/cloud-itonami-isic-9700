(ns domesticops.store
  "SSoT for the community-domestic-employment actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/domesticops/store_contract_test.clj), which is the whole
  point: the actor, the Domestic Employment Governor and the audit
  ledger never know which SSoT they run on.

  Like `libraryops`/9101's own `item`, the primary entity here is an
  `assignment` -- mission-dispatch and payroll-posting actuation
  events apply SEQUENTIALLY to the SAME assignment record (dispatch
  first, pay later), matching the freight/quarry/agronomy/
  hospitality/practice/employment/administration/library cluster's
  own sequential entity shape. Dedicated double-actuation-guard
  booleans (`:dispatched?`/`:paid?`, never a `:status` value).

  The ledger stays append-only on every backend: 'which assignment was
  screened for an unregistered household employer or a missing
  vulnerable-person safeguarding check, which mission was dispatched,
  which payroll was posted, on what jurisdictional basis, approved by
  whom' is always a query over an immutable log -- the audit trail a
  household-employer or worker-collective trusting an operator needs,
  and the evidence an operator needs if a dispatch or a payroll
  posting is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [domesticops.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (assignment [s id])
  (all-assignments [s])
  (assessment-of [s assignment-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only mission-dispatch history (domesticops.registry drafts)")
  (payroll-history [s] "the append-only payroll-posting history (domesticops.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-payroll-sequence [s jurisdiction] "next payroll-number sequence for a jurisdiction")
  (assignment-already-dispatched? [s assignment-id] "has this assignment already been dispatched?")
  (assignment-already-paid? [s assignment-id] "has this assignment already been paid?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-assignments [s assignments] "replace/seed the assignment directory (map id->assignment)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained assignment set covering both actuation
  lifecycles (dispatch, pay) plus the governor's own new checks, so
  the actor + tests run offline."
  []
  {:assignments
   {"assignment-1" {:id "assignment-1" :worker "Kita Sato" :household "Tanaka Household"
                     :role "housekeeper" :wage-type :hourly :rate 1500 :hours-worked 8
                     :claimed-gross 12000
                     :household-employment-registered? true
                     :involves-vulnerable-person? false :safeguarding-check-verified? false
                     :dispatched? false :paid? false
                     :jurisdiction "JPN" :status :intake}
    "assignment-2" {:id "assignment-2" :worker "Atlantis Ann" :household "Atlantis Household"
                     :role "housekeeper" :wage-type :hourly :rate 1500 :hours-worked 6
                     :claimed-gross 9000
                     :household-employment-registered? true
                     :involves-vulnerable-person? false :safeguarding-check-verified? false
                     :dispatched? false :paid? false
                     :jurisdiction "ATL" :status :intake}
    "assignment-3" {:id "assignment-3" :worker "Minami Hana" :household "Yamada Household"
                     :role "gardener" :wage-type :hourly :rate 1600 :hours-worked 5
                     :claimed-gross 10000
                     :household-employment-registered? true
                     :involves-vulnerable-person? false :safeguarding-check-verified? false
                     :dispatched? false :paid? false
                     :jurisdiction "JPN" :status :intake}
    "assignment-4" {:id "assignment-4" :worker "Higashi Ichiro" :household "Suzuki Household"
                     :role "housekeeper" :wage-type :hourly :rate 1500 :hours-worked 7
                     :claimed-gross 10500
                     :household-employment-registered? false
                     :involves-vulnerable-person? false :safeguarding-check-verified? false
                     :dispatched? false :paid? false
                     :jurisdiction "JPN" :status :intake}
    "assignment-5" {:id "assignment-5" :worker "Nishi Kenji" :household "Ito Household"
                     :role "nanny" :wage-type :hourly :rate 1800 :hours-worked 8
                     :claimed-gross 14400
                     :household-employment-registered? true
                     :involves-vulnerable-person? true :safeguarding-check-verified? false
                     :dispatched? false :paid? false
                     :jurisdiction "JPN" :status :intake}
    "assignment-6" {:id "assignment-6" :worker "Chuo Yuki" :household "Watanabe Household"
                     :role "nanny" :wage-type :hourly :rate 1800 :hours-worked 8
                     :claimed-gross 14400
                     :household-employment-registered? true
                     :involves-vulnerable-person? true :safeguarding-check-verified? true
                     :dispatched? false :paid? false
                     :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-mission!
  "Backend-agnostic `:assignment/mark-dispatched` -- looks up the
  assignment via the protocol and drafts the dispatch record, and
  returns {:result .. :assignment-patch ..} for the caller to
  persist."
  [s assignment-id]
  (let [a (assignment s assignment-id)
        seq-n (next-dispatch-sequence s (:jurisdiction a))
        result (registry/register-mission-dispatch assignment-id (:jurisdiction a) seq-n)]
    {:result result
     :assignment-patch {:dispatched? true
                        :dispatch-number (get result "dispatch_number")}}))

(defn- post-payroll!
  "Backend-agnostic `:assignment/mark-paid` -- looks up the assignment
  via the protocol and drafts the payroll-posting record, and returns
  {:result .. :assignment-patch ..} for the caller to persist."
  [s assignment-id]
  (let [a (assignment s assignment-id)
        seq-n (next-payroll-sequence s (:jurisdiction a))
        result (registry/register-payroll-posting assignment-id (:jurisdiction a) seq-n)]
    {:result result
     :assignment-patch {:paid? true
                        :payroll-number (get result "payroll_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (assignment [_ id] (get-in @a [:assignments id]))
  (all-assignments [_] (sort-by :id (vals (:assignments @a))))
  (assessment-of [_ assignment-id] (get-in @a [:assessments assignment-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatch-records @a))
  (payroll-history [_] (:payroll-records @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-payroll-sequence [_ jurisdiction] (get-in @a [:payroll-sequences jurisdiction] 0))
  (assignment-already-dispatched? [_ assignment-id] (boolean (get-in @a [:assignments assignment-id :dispatched?])))
  (assignment-already-paid? [_ assignment-id] (boolean (get-in @a [:assignments assignment-id :paid?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :assignment/upsert
      (swap! a update-in [:assignments (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :assignment/mark-dispatched
      (let [assignment-id (first path)
            {:keys [result assignment-patch]} (dispatch-mission! s assignment-id)
            jurisdiction (:jurisdiction (assignment s assignment-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:assignments assignment-id] merge assignment-patch)
                       (update :dispatch-records registry/append result))))
        result)

      :assignment/mark-paid
      (let [assignment-id (first path)
            {:keys [result assignment-patch]} (post-payroll! s assignment-id)
            jurisdiction (:jurisdiction (assignment s assignment-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:payroll-sequences jurisdiction] (fnil inc 0))
                       (update-in [:assignments assignment-id] merge assignment-patch)
                       (update :payroll-records registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-assignments [s assignments] (when (seq assignments) (swap! a assoc :assignments assignments)) s))

(defn seed-db
  "A MemStore seeded with the demo assignment set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :dispatch-sequences {} :dispatch-records []
                           :payroll-sequences {} :payroll-records []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, dispatch/
  payroll records) are stored as EDN strings so `langchain.db` doesn't
  expand them into sub-entities -- the same convention every sibling
  actor's store uses."
  {:assignment/id                  {:db/unique :db.unique/identity}
   :assessment/assignment-id       {:db/unique :db.unique/identity}
   :ledger/seq                     {:db/unique :db.unique/identity}
   :dispatch-record/seq            {:db/unique :db.unique/identity}
   :payroll-record/seq             {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction     {:db/unique :db.unique/identity}
   :payroll-sequence/jurisdiction      {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- assignment->tx [{:keys [id worker household role wage-type rate hours-worked claimed-gross
                               household-employment-registered?
                               involves-vulnerable-person? safeguarding-check-verified?
                               dispatched? paid?
                               jurisdiction status dispatch-number payroll-number]}]
  (cond-> {:assignment/id id}
    worker                                       (assoc :assignment/worker worker)
    household                                       (assoc :assignment/household household)
    role                                                (assoc :assignment/role role)
    wage-type                                             (assoc :assignment/wage-type wage-type)
    rate                                                     (assoc :assignment/rate rate)
    hours-worked                                                (assoc :assignment/hours-worked hours-worked)
    claimed-gross                                                  (assoc :assignment/claimed-gross claimed-gross)
    (some? household-employment-registered?)                         (assoc :assignment/household-employment-registered? household-employment-registered?)
    (some? involves-vulnerable-person?)                                 (assoc :assignment/involves-vulnerable-person? involves-vulnerable-person?)
    (some? safeguarding-check-verified?)                                   (assoc :assignment/safeguarding-check-verified? safeguarding-check-verified?)
    (some? dispatched?)                                                       (assoc :assignment/dispatched? dispatched?)
    (some? paid?)                                                                (assoc :assignment/paid? paid?)
    jurisdiction                                                                    (assoc :assignment/jurisdiction jurisdiction)
    status                                                                             (assoc :assignment/status status)
    dispatch-number                                                                       (assoc :assignment/dispatch-number dispatch-number)
    payroll-number                                                                            (assoc :assignment/payroll-number payroll-number)))

(def ^:private assignment-pull
  [:assignment/id :assignment/worker :assignment/household :assignment/role :assignment/wage-type
   :assignment/rate :assignment/hours-worked :assignment/claimed-gross
   :assignment/household-employment-registered? :assignment/involves-vulnerable-person? :assignment/safeguarding-check-verified?
   :assignment/dispatched? :assignment/paid?
   :assignment/jurisdiction :assignment/status :assignment/dispatch-number :assignment/payroll-number])

(defn- pull->assignment [m]
  (when (:assignment/id m)
    {:id (:assignment/id m) :worker (:assignment/worker m) :household (:assignment/household m)
     :role (:assignment/role m) :wage-type (:assignment/wage-type m)
     :rate (:assignment/rate m) :hours-worked (:assignment/hours-worked m) :claimed-gross (:assignment/claimed-gross m)
     :household-employment-registered? (boolean (:assignment/household-employment-registered? m))
     :involves-vulnerable-person? (boolean (:assignment/involves-vulnerable-person? m))
     :safeguarding-check-verified? (boolean (:assignment/safeguarding-check-verified? m))
     :dispatched? (boolean (:assignment/dispatched? m)) :paid? (boolean (:assignment/paid? m))
     :jurisdiction (:assignment/jurisdiction m) :status (:assignment/status m)
     :dispatch-number (:assignment/dispatch-number m) :payroll-number (:assignment/payroll-number m)}))

(defrecord DatomicStore [conn]
  Store
  (assignment [_ id]
    (pull->assignment (d/pull (d/db conn) assignment-pull [:assignment/id id])))
  (all-assignments [_]
    (->> (d/q '[:find [?id ...] :where [?e :assignment/id ?id]] (d/db conn))
         (map #(pull->assignment (d/pull (d/db conn) assignment-pull [:assignment/id %])))
         (sort-by :id)))
  (assessment-of [_ assignment-id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?a :assessment/assignment-id ?aid] [?a :assessment/payload ?p]]
              (d/db conn) assignment-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch-record/seq ?s] [?e :dispatch-record/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (payroll-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :payroll-record/seq ?s] [?e :payroll-record/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-payroll-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :payroll-sequence/jurisdiction ?j] [?e :payroll-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (assignment-already-dispatched? [s assignment-id]
    (boolean (:dispatched? (assignment s assignment-id))))
  (assignment-already-paid? [s assignment-id]
    (boolean (:paid? (assignment s assignment-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :assignment/upsert
      (d/transact! conn [(assignment->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/assignment-id (first path) :assessment/payload (enc payload)}])

      :assignment/mark-dispatched
      (let [assignment-id (first path)
            {:keys [result assignment-patch]} (dispatch-mission! s assignment-id)
            jurisdiction (:jurisdiction (assignment s assignment-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(assignment->tx (assoc assignment-patch :id assignment-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch-record/seq (count (dispatch-history s)) :dispatch-record/record (enc (get result "record"))}])
        result)

      :assignment/mark-paid
      (let [assignment-id (first path)
            {:keys [result assignment-patch]} (post-payroll! s assignment-id)
            jurisdiction (:jurisdiction (assignment s assignment-id))
            next-n (inc (next-payroll-sequence s jurisdiction))]
        (d/transact! conn
                     [(assignment->tx (assoc assignment-patch :id assignment-id))
                      {:payroll-sequence/jurisdiction jurisdiction :payroll-sequence/next next-n}
                      {:payroll-record/seq (count (payroll-history s)) :payroll-record/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-assignments [s assignments]
    (when (seq assignments) (d/transact! conn (mapv assignment->tx (vals assignments)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:assignments ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [assignments]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-assignments s assignments))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo assignment set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
