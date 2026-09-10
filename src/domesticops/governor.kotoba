(ns domesticops.governor
  "Domestic Employment Governor -- the independent compliance layer
  that earns the DomesticOps-LLM the right to commit. The LLM has no
  notion of jurisdictional household-employer-registration or
  vulnerable-person-safeguarding law, whether an assignment's own
  claimed gross wages actually equal what the REAL `kotoba.labor`
  capability library would independently compute from the contract
  and timesheet, whether a household employing a domestic worker has
  actually registered as an employer, whether a worker assigned to
  care for a child or elder has actually passed a required
  safeguarding check, or when an act stops being a draft and becomes a
  real-world mission dispatch or payroll posting, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD.

  `:itonami.blueprint/governor` is `:domestic-employment-governor`,
  grep-verified UNIQUE fleet-wide -- no naming-collision precedent
  question, a fresh independent build following the SAME governed-
  actor architecture (langgraph StateGraph + independent Governor +
  Phase 0->3 rollout) established by `cloud-itonami-isic-6511`.

  This blueprint's own text (docs/business-model.md's own Trust
  Controls: 'payroll must balance gross minus documented deductions;
  safety-critical actions (near a child/elder, agents, private rooms)
  require human sign-off') and its own README ('robot actions (gated)
  + timesheet + payroll + audit ledger') name exactly the checks
  below.

  Seven checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them. The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `domesticops.phase`: for `:stake
  :actuation/dispatch-mission`/`:actuation/post-payroll` (a real
  mission dispatch or payroll posting) NO phase ever allows auto-
  commit either. Two independent layers agree that actuation is
  always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`domesticops.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:assignment/dispatch`/
                                       `:assignment/pay`, has the
                                       jurisdiction actually been
                                       assessed with a full evidence
                                       checklist on file?
    3. Household employment
       unregistered                   -- for `:assignment/dispatch`,
                                       INDEPENDENTLY verify the
                                       assignment's own `:household-
                                       employment-registered?` is true
                                       -- the FLAGSHIP genuinely new
                                       check this vertical adds (grep-
                                       verified absent fleet-wide --
                                       zero hits for 'household-
                                       employment-unregistered' as a
                                       governor check function name),
                                       the 90th distinct application
                                       of the unconditional-evaluation
                                       discipline overall (most
                                       recently `libraryops.governor/
                                       conservator-sign-off-missing-
                                       violations` at 89th). Grounded
                                       in real household-employer-
                                       registration law: Japan's own
                                       労働保険/健康保険/厚生年金保険 家事使用人特例
                                       registration obligations
                                       (enforced by MHLW/日本年金機構), the
                                       US's IRS Schedule H household-
                                       employment-tax registration (26
                                       U.S.C. §3510), the UK's HMRC
                                       PAYE-for-Employers household-
                                       employer registration, and
                                       Germany's Minijob-Zentrale
                                       Haushaltsscheckverfahren (§28a
                                       SGB IV) -- directly grounded in
                                       this blueprint's own text
                                       ('household-employer operator...
                                       employment records'). Evaluated
                                       UNCONDITIONALLY (every mission
                                       dispatch needs the household's
                                       own employer registration
                                       checked).
    4. Payroll mismatch            -- for `:assignment/pay`,
                                       INDEPENDENTLY recompute whether
                                       the assignment's own `:claimed-
                                       gross` equals what
                                       `kotoba.labor/wages-for`
                                       computes from the assignment's
                                       own contract + timesheet fields
                                       (`domesticops.registry/
                                       payroll-matches-contract?`) --
                                       an HONEST reapplication of the
                                       SAME ground-truth-recompute
                                       DISCIPLINE every sibling
                                       actor's own cost/total-matching
                                       check establishes, but this
                                       time by delegating DIRECTLY to
                                       this vertical's own bespoke
                                       `kotoba.labor` capability
                                       library (the THIRD capability-
                                       library-wrapping vertical in
                                       this fleet, after `retailops`/
                                       4711's own `kotoba-lang/retail`
                                       and `freightops`/4920's own
                                       `kotoba-lang/logistics`) rather
                                       than reimplementing wage
                                       arithmetic a second time.
    5. Vulnerable person
       safeguarding check missing     -- for `:assignment/dispatch`,
                                       for an assignment whose own
                                       record declares `:involves-
                                       vulnerable-person? true` (i.e.
                                       this domestic worker is
                                       actually assigned to care for a
                                       child or elder -- not every
                                       assignment is, e.g. a gardener
                                       assignment is not),
                                       INDEPENDENTLY check whether
                                       `:safeguarding-check-verified?`
                                       is true. A GENUINELY NEW
                                       concept (grep-verified absent
                                       fleet-wide -- zero hits for
                                       'vulnerable-person-
                                       safeguarding'/'safeguarding-
                                       check-missing' as a governor
                                       check function name), the 91st
                                       distinct application overall,
                                       the FIFTEENTH conditional
                                       variant (after
                                       `socialresearch`/7220's,
                                       `bizassoc`/9411's, `training`/
                                       8549's, `furniture`/9524's,
                                       `specialtyrepair`/9529's,
                                       `leathergoods`/9523's,
                                       `ictrepair`/9511's, `quarryops`/
                                       0810's, `agronomyops`/0162's,
                                       `hospitalityops`/5510's,
                                       `practiceops`/7110's,
                                       `employmentops`/7810's,
                                       `adminops`/8411's and
                                       `libraryops`/9101's own, at
                                       63rd, 64th, 66th, 67th, 68th,
                                       69th, 71st, 77th, 79th, 81st,
                                       83rd, 85th, 87th and 89th).
                                       CONDITIONAL on the assignment's
                                       own `:involves-vulnerable-
                                       person?` ground truth. Grounded
                                       in real vulnerable-person-
                                       safeguarding law: Japan's own
                                       児童福祉法/日本版DBS制度
                                       (Child Welfare Act / Japan's
                                       DBS-equivalent, enforced by the
                                       Children and Families Agency),
                                       the US's state in-home child/
                                       elder-care background-check
                                       statutes (National Background
                                       Check Program), the UK's
                                       Safeguarding Vulnerable Groups
                                       Act 2006 (DBS check for
                                       'regulated activity', enforced
                                       by the Disclosure and Barring
                                       Service), and Germany's §72a
                                       SGB VIII (erweitertes
                                       Führungszeugnis, enforced by
                                       Jugendämter) -- ALL FOUR seeded
                                       jurisdictions actually have a
                                       real regime here, reported
                                       honestly (a full-coverage sub-
                                       citation, matching `quarryops`/
                                       0810's own blast-safety,
                                       `agronomyops`/0162's own
                                       water-buffer, `practiceops`/
                                       7110's own professional-seal,
                                       `employmentops`/7810's own
                                       work-authorization, `adminops`/
                                       8411's own appeal-rights and
                                       `libraryops`/9101's own
                                       conservation-standards full
                                       coverage rather than
                                       `hospitalityops`/5510's own
                                       honest single-jurisdiction
                                       gap).
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:assignment/
                                       dispatch`/`:assignment/pay`
                                       (REAL acts) -> escalate.

  Two more guards, double-dispatch/double-payroll-posting prevention,
  are enforced but NOT listed as numbered HARD checks above because
  they need no upstream comparison at all -- `already-dispatched-
  violations`/`already-paid-violations` refuse to dispatch/pay the
  SAME assignment twice, off dedicated `:dispatched?`/`:paid?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean,
  not status' discipline every prior governor's guards establish,
  informed by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [domesticops.facts :as facts]
            [domesticops.registry :as registry]
            [domesticops.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real mission and posting a real payroll are the two
  real-world actuation events this actor performs -- a two-member
  set, matching every sibling's own dual-actuation shape."
  #{:actuation/dispatch-mission :actuation/post-payroll})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:assignment/dispatch`/`:assignment/
  pay`) proposal with no spec-basis citation is a HARD violation --
  never invent a jurisdiction's household-employer-registration/
  safeguarding requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :assignment/dispatch :assignment/pay} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:assignment/dispatch`/`:assignment/pay`, the jurisdiction's
  required intake/contract/timesheet evidence must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone."
  [{:keys [op subject]} st]
  (when (contains? #{:assignment/dispatch :assignment/pay} op)
    (let [a (store/assignment st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction a) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(受入記録/雇用契約記録/勤務記録/身元確認記録等)が充足していない状態での提案"}]))))

(defn- household-employment-unregistered-violations
  "For `:assignment/dispatch`, INDEPENDENTLY verify the assignment's
  own `:household-employment-registered?` is true -- the flagship
  genuinely new check this vertical adds. Evaluated UNCONDITIONALLY
  (every mission dispatch needs the household's own employer
  registration checked)."
  [{:keys [op subject]} st]
  (when (= op :assignment/dispatch)
    (let [a (store/assignment st subject)]
      (when-not (true? (:household-employment-registered? a))
        [{:rule :household-employment-unregistered
          :detail (str subject " の世帯主(雇用主)登録が未完了")}]))))

(defn- payroll-mismatch-violations
  "For `:assignment/pay`, INDEPENDENTLY recompute whether the
  assignment's own claimed gross equals `kotoba.labor/wages-for`'s
  own computation via `domesticops.registry/payroll-matches-
  contract?` -- needs no proposal inspection or stored-verdict lookup
  at all, an honest reapplication of the same discipline every
  sibling actor's own cost/total-matching check establishes, this
  time by delegating to the REAL `kotoba.labor` capability library."
  [{:keys [op subject]} st]
  (when (= op :assignment/pay)
    (let [a (store/assignment st subject)]
      (when-not (registry/payroll-matches-contract? a)
        [{:rule :payroll-mismatch
          :detail (str subject " の申告総支給額(" (:claimed-gross a)
                      ")がkotoba.labor/wages-forの独立再計算値(" (registry/compute-gross-wages a) ")と一致しない")}]))))

(defn- vulnerable-person-safeguarding-check-missing-violations
  "For `:assignment/dispatch`, for an assignment whose own record
  declares `:involves-vulnerable-person? true`, INDEPENDENTLY check
  whether `:safeguarding-check-verified?` is true -- a genuinely new
  concept, CONDITIONAL on the assignment's own `:involves-vulnerable-
  person?` ground truth (not every assignment cares for a child or
  elder)."
  [{:keys [op subject]} st]
  (when (= op :assignment/dispatch)
    (let [a (store/assignment st subject)]
      (when (and (true? (:involves-vulnerable-person? a))
                 (not (true? (:safeguarding-check-verified? a))))
        [{:rule :vulnerable-person-safeguarding-check-missing
          :detail (str subject " は児童/高齢者の世話を伴うが身元確認が未完了 -- 派遣提案は進められない")}]))))

(defn- already-dispatched-violations
  "For `:assignment/dispatch`, refuses to dispatch the SAME
  assignment record twice, off a dedicated `:dispatched?` fact (never
  a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :assignment/dispatch)
    (when (store/assignment-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に派遣済み")}])))

(defn- already-paid-violations
  "For `:assignment/pay`, refuses to pay the SAME assignment twice,
  off a dedicated `:paid?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :assignment/pay)
    (when (store/assignment-already-paid? st subject)
      [{:rule :already-paid
        :detail (str subject " は既に支払済み")}])))

(defn check
  "Censors a DomesticOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (household-employment-unregistered-violations request st)
                           (payroll-mismatch-violations request st)
                           (vulnerable-person-safeguarding-check-missing-violations request st)
                           (already-dispatched-violations request st)
                           (already-paid-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
