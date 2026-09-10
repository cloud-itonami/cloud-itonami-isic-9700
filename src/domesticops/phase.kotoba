(ns domesticops.phase
  "Phase 0->3 staged rollout for the community-domestic-employment
  actor.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- assignment intake allowed, every
                                 write needs human approval.
    Phase 2  assisted-assess  -- adds jurisdiction assessment writes,
                                 still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:assignment/intake` (no household-
                                 facing risk yet) may auto-commit.
                                 `:assignment/dispatch`/`:assignment/
                                 pay` NEVER auto-commit, at any phase.

  `:assignment/dispatch`/`:assignment/pay` are deliberately ABSENT
  from every phase's `:auto` set, including phase 3 -- a permanent
  structural fact, not a rollout milestone still to come. Dispatching
  a real mission into a private household and posting a real payroll
  are the two real-world acts this actor performs; both are always a
  human household-employer/agency coordinator's call.
  `domesticops.governor`'s `:actuation/dispatch-mission`/`:actuation/
  post-payroll` high-stakes gate enforces the same invariant
  independently -- two layers, not one, agree on this. Like every
  prior sibling's phase 3 `:auto` set, this domain has only ONE member
  (`:assignment/intake`) -- no separate no-household-facing-risk
  'file' lifecycle distinct from the assignment itself.")

(def read-ops  #{})
(def write-ops #{:assignment/intake :jurisdiction/assess :assignment/dispatch :assignment/pay})

;; NOTE the invariant: `:assignment/dispatch`/`:assignment/pay` are
;; members of `write-ops` (governor-gated like any write) but are
;; NEVER members of any phase's `:auto` set below. Do not add them
;; there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"       :writes #{}                                                              :auto #{}}
   1 {:label "assisted-intake" :writes #{:assignment/intake}                                             :auto #{}}
   2 {:label "assisted-assess" :writes #{:assignment/intake :jurisdiction/assess}                         :auto #{}}
   3 {:label "supervised-auto" :writes write-ops
      :auto #{:assignment/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:assignment/dispatch`/`:assignment/pay` are never auto-eligible
    at any phase, so they always escalate once the governor clears
    them (or hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Domestic Employment Governor verdict to a base disposition
  before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
