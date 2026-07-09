(ns domesticops.registry
  "Pure-function mission-dispatch + payroll-posting record
  construction -- an append-only household-employer book-of-record
  draft.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a mission-dispatch or payroll-posting
  record -- every household/jurisdiction assigns its own reference
  format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `domesticops.facts` uses.

  `payroll-matches-contract?` is DIFFERENT from every prior sibling
  actor's own cost/total-matching check: instead of reimplementing a
  flat quantity x unit-rate calculation, it delegates DIRECTLY to
  `kotoba.labor/contract`, `kotoba.labor/timesheet` and `kotoba.labor/
  wages-for` -- this vertical's own bespoke domain capability library
  (contracts, timesheets, wages, payroll), the THIRD capability-
  library-wrapping vertical in this fleet after `retailops`/4711
  (`kotoba-lang/retail`'s own `ean13-valid?`) and `freightops`/4920
  (`kotoba-lang/logistics`'s own `tracking-valid?`). Reusing the
  library's own wage-computation logic is MORE honest than
  reimplementing wage arithmetic a second time -- a household-employer
  operator must never pay a worker a gross that doesn't actually
  equal what the REAL capability library computes from the contract
  and timesheet entries.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real payroll system. It builds the RECORD an operator
  would keep, not the act of dispatching a mission or posting payroll
  itself (that is `domesticops.operation`'s `:assignment/dispatch`/
  `:assignment/pay`, always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]
            [kotoba.labor :as labor]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the household-employer operator's act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn compute-gross-wages
  "The ground-truth gross wages for `assignment`, computed by
  delegating DIRECTLY to `kotoba.labor/contract`, `kotoba.labor/
  timesheet` and `kotoba.labor/wages-for` -- this vertical's own real
  capability library, not reimplemented arithmetic. Returns nil if
  `kotoba.labor/contract` itself rejects the assignment's own
  `:wage-type` (an unknown wage type, e.g. neither `:hourly` nor
  `:monthly`)."
  [{:keys [assignment-id worker household role wage-type rate hours-worked]}]
  (when-let [c (labor/contract assignment-id worker household role wage-type rate)]
    (labor/wages-for c [(labor/timesheet worker "period" hours-worked)])))

(defn payroll-matches-contract?
  "Does `assignment`'s own `:claimed-gross` equal the independently
  recomputed `compute-gross-wages` (delegating to `kotoba.labor/
  wages-for`)? A pure ground-truth check against the assignment's own
  permanent fields plus the REAL capability library's own wage-
  computation logic -- see ns docstring for why this reuses
  `kotoba.labor` directly rather than reimplementing wage arithmetic."
  [{:keys [claimed-gross] :as assignment}]
  (when-let [computed (compute-gross-wages assignment)]
    (== (double claimed-gross) (double computed))))

(defn register-mission-dispatch
  "Validate + construct the MISSION-DISPATCH registration DRAFT --
  the household-employer operator's own act of dispatching a real
  service robot / domestic worker for a real household task. Pure
  function -- does not touch any real household-management system; it
  builds the RECORD an operator would keep. `domesticops.governor`
  independently re-verifies the assignment's own household-employer-
  registration and vulnerable-person-safeguarding ground truth, and
  blocks a double-dispatch of the same record, before this is ever
  allowed to commit."
  [assignment-id jurisdiction sequence]
  (when-not (and assignment-id (not= assignment-id ""))
    (throw (ex-info "mission-dispatch: assignment_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "mission-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "mission-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-DSP-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "mission-dispatch-draft"
                "assignment_id" assignment-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "MissionDispatch" dispatch-number dispatch-number)}))

(defn register-payroll-posting
  "Validate + construct the PAYROLL-POSTING registration DRAFT -- the
  household-employer operator's own act of posting a real payroll to
  a real worker (triggering wage payment). Pure function -- does not
  touch any real payroll system; it builds the RECORD an operator
  would keep. `domesticops.governor` independently re-verifies the
  assignment's own gross-wages ground truth (via `kotoba.labor/
  wages-for`), and blocks a double-payroll-posting of the same
  record, before this is ever allowed to commit."
  [assignment-id jurisdiction sequence]
  (when-not (and assignment-id (not= assignment-id ""))
    (throw (ex-info "payroll-posting: assignment_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "payroll-posting: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "payroll-posting: sequence must be >= 0" {})))
  (let [payroll-number (str (str/upper-case jurisdiction) "-PAY-" (zero-pad sequence 6))
        record {"record_id" payroll-number
                "kind" "payroll-posting-draft"
                "assignment_id" assignment-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "payroll_number" payroll-number
     "certificate" (unsigned-certificate "PayrollPosting" payroll-number payroll-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
