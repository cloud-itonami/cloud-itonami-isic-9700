# ADR-0001: DomesticOps-LLM ⊣ Domestic Employment Governor architecture

## Status

Accepted. `cloud-itonami-isic-9700` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-9700` publishes an OSS business blueprint for
community domestic employment (households employing nannies,
caregivers, housekeepers, gardeners). Like every prior actor in this
fleet, the blueprint alone is not an implementation: this ADR records
the governed-actor architecture that promotes it to real, tested
code, following the same langgraph StateGraph + independent Governor
+ Phase 0→3 rollout pattern established by `cloud-itonami-isic-6511`
(life insurance) and applied across 95 prior siblings, most recently
`cloud-itonami-isic-9101` (community library and archive).

Unlike the majority of this fleet's actors, this blueprint's own
README explicitly names a bespoke domain capability library:
`kotoba-lang/labor` (contracts, timesheets, wages, payroll). This is
the THIRD capability-library-wrapping vertical in this fleet, after
`retailops`/4711 (`kotoba-lang/retail`) and `freightops`/4920
(`kotoba-lang/logistics`). `kotoba-lang/robotics` is ALSO named, but
that is the same generic cross-cutting robotics contract every
cloud-itonami vertical already resolves via `kotoba.technology`, not
a domain-specific library unique to this vertical.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:domestic-employment-governor`, is grep-verified UNIQUE fleet-wide --
no naming-collision precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:domestic-employment-governor` is grep-verified unique across every
blueprint.edn in this fleet. This build follows the SAME governed-
actor architecture as every prior actor, but with its own distinct
governor identity.

### Decision 2: dual-actuation shape, SEQUENTIAL on the SAME `assignment` entity

This blueprint's own Core Contract ("Household Advisor -> Domestic
Employment Governor -> schedule, work, pay, or human approval") and
its own Trust Controls ("payroll must balance...; safety-critical
actions... require human sign-off") name two real-world acts:
dispatching a mission and posting payroll. These apply SEQUENTIALLY
to the SAME `assignment` entity -- dispatch first, pay later --
matching `libraryops`/9101's, `adminops`/8411's, `employmentops`/
7810's, `practiceops`/7110's, `hospitalityops`/5510's, `freightops`/
4920's, `quarryops`/0810's and `agronomyops`/0162's own sequential
shape rather than `retailops`/4711's own alternative-kind shape.
`high-stakes` is `#{:actuation/dispatch-mission :actuation/
post-payroll}`.

### Decision 3: `payroll-matches-contract?` -- reuses `kotoba.labor` directly, not reimplemented arithmetic

`domesticops.registry/payroll-matches-contract?` (assignment's own
claimed gross vs. what `kotoba.labor/wages-for` independently
computes from `kotoba.labor/contract` + `kotoba.labor/timesheet`)
applies the SAME ground-truth-recompute DISCIPLINE every sibling
actor's own cost/total-matching check establishes, but this time by
delegating DIRECTLY to this vertical's own bespoke `kotoba.labor`
capability library rather than reimplementing wage arithmetic a
second time -- more honest than reinventing wage math the library
already provides correctly. This matches `retailops`/4711's own
`ean13-valid?` delegation and `freightops`/4920's own `tracking-
valid?` delegation, both counted as "capability-library-reuse," not
claimed as a genuinely new invention.

### Decision 4: entity and op shape

The primary entity is an `assignment`. Four ops: `:assignment/intake`
(directory upsert, no household-facing risk), `:jurisdiction/assess`
(per-jurisdiction household-employer-registration/safeguarding
evidence checklist, never auto), `:assignment/dispatch` (POSITIVE,
high-stakes), and `:assignment/pay` (POSITIVE, high-stakes).

### Decision 5: `household-employment-unregistered?` -- the 90th unconditional-evaluation grounding, the FLAGSHIP genuinely new check

Grep-verified absent fleet-wide (zero hits for `household-
employment-unregistered` as a governor check name). Grounded in real
household-employer-registration law: Japan's own 労働保険/健康保険/厚生年金保険
家事使用人特例 registration obligations (enforced by MHLW/日本年金機構), the
US's IRS Schedule H household-employment-tax registration (26 U.S.C.
§3510), the UK's HMRC PAYE-for-Employers household-employer
registration, and Germany's Minijob-Zentrale Haushaltsscheckverfahren
(§28a SGB IV) -- directly grounded in this blueprint's own text
("household-employer operator... employment records"). Evaluated
UNCONDITIONALLY on every `:assignment/dispatch` (every mission
dispatch needs the household's own employer registration checked).

### Decision 6: `vulnerable-person-safeguarding-check-missing?` -- the 91st unconditional-evaluation grounding, the FIFTEENTH conditional variant

Before writing this check, every prior sibling's governor namespace
was grepped for any check function named `vulnerable-person-
safeguarding` or `safeguarding-check-missing` -- zero hits, confirming
this is a genuinely new concept. This is the FIFTEENTH conditional
variant (after `socialresearch`/7220's, `bizassoc`/9411's, `training`/
8549's, `furniture`/9524's, `specialtyrepair`/9529's, `leathergoods`/
9523's, `ictrepair`/9511's, `quarryops`/0810's, `agronomyops`/0162's,
`hospitalityops`/5510's, `practiceops`/7110's, `employmentops`/7810's,
`adminops`/8411's and `libraryops`/9101's own, at 63rd, 64th, 66th,
67th, 68th, 69th, 71st, 77th, 79th, 81st, 83rd, 85th, 87th and 89th)
-- CONDITIONAL on the assignment's own `:involves-vulnerable-person?`
ground truth: not every domestic-worker assignment cares for a child
or elder (a gardener assignment does not). Grounded in real
vulnerable-person-safeguarding law: Japan's own 児童福祉法/日本版DBS制度
(Child Welfare Act / Japan's DBS-equivalent, enforced by the Children
and Families Agency), the US's state in-home child/elder-care
background-check statutes (National Background Check Program), the
UK's Safeguarding Vulnerable Groups Act 2006 (DBS check for
"regulated activity," enforced by the Disclosure and Barring
Service), and Germany's §72a SGB VIII (erweitertes Führungszeugnis,
enforced by Jugendämter). ALL FOUR seeded jurisdictions actually have
a real regime here, reported honestly -- a full-coverage sub-
citation, matching `quarryops`/0810's own blast-safety, `agronomyops`/
0162's own water-buffer, `practiceops`/7110's own professional-seal,
`employmentops`/7810's own work-authorization, `adminops`/8411's own
appeal-rights and `libraryops`/9101's own conservation-standards full
coverage rather than `hospitalityops`/5510's own honest single-
jurisdiction gap.

### Decision 7: dedicated double-actuation-guard booleans

`:dispatched?`/`:paid?` are dedicated booleans on the `assignment`
record, never a single `:status` value -- the same discipline every
prior governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 8: Store protocol, MemStore + DatomicStore parity

`domesticops.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.
db`-backed), proven to satisfy the same contract in
`test/domesticops/store_contract_test.clj`.

### Decision 9: real capability library, no `blueprint.edn` field-sync fixes needed

`kotoba-lang/labor` (contracts, timesheets, wages, payroll) is a
genuine, real domain capability library for this vertical -- verified
by reading its own README and source (`kotoba.labor/contract`,
`kotoba.labor/timesheet`, `kotoba.labor/wages-for`, `kotoba.labor/
payroll`, `kotoba.labor/validate-contract`). This repo's `blueprint.
edn` already had the correct `:required-technologies` (including
`:labor`) and `:optional-technologies [:optimization]` matching the
`kotoba-lang/industry` registry's own entry for `"9700"` exactly --
only the `:maturity` field itself needed adding, a clean fix unlike
several recent siblings' own genuine field-sync gaps.

### Decision 10: mock + LLM advisor pair

`domesticops.domesticopsllm` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-
dispatching a mission or auto-posting payroll).

## Alternatives considered

- **An unconditional vulnerable-person-safeguarding check** (applying
  to every dispatch regardless of whether the assignment actually
  involves a child or elder). Rejected: gardener/general-housekeeping
  assignments do not carry the same safeguarding requirement --
  forcing the check onto every dispatch would fabricate a
  requirement.
- **Reimplementing wage arithmetic instead of delegating to
  `kotoba.labor`.** Rejected: the library already provides correct,
  tested wage-computation logic (`wages-for`); reimplementing it would
  be LESS honest, not more, and would risk silent drift between the
  actor's own math and the library's own math.
- **Fabricating a jurisdiction gap** to match `hospitalityops`/5510's
  own single-jurisdiction honesty gap. Rejected: all four seeded
  jurisdictions genuinely have a real vulnerable-person-safeguarding
  regime here.

## Consequences

- 97th actor in this fleet (96 implemented before this build).
- Establishes two genuinely NEW unconditional-evaluation-discipline
  checks: `household-employment-unregistered?` (FLAGSHIP, 90th
  distinct application overall) and `vulnerable-person-safeguarding-
  check-missing?` (91st distinct application overall, the FIFTEENTH
  conditional variant).
- `MemStore` ‖ `DatomicStore` parity is proven by
  `test/domesticops/store_contract_test.clj`.
- 40 tests / 177 assertions pass (one extra test covering the
  capability-library-wrap's own unknown-wage-type edge case); lint is
  clean; the demo (`clojure -M:dev:run`) walks two clean dispatch+pay
  lifecycles (no vulnerable person, vulnerable person with
  safeguarding verified), plus four HARD-hold scenarios, end-to-end.
- `blueprint.edn` needed no field-sync fix this time -- only the
  `:maturity` flip.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of
  the general governed-actor architecture pattern)
- `cloud-itonami-isic-9101/docs/adr/0001-architecture.md` (most recent
  prior sibling, template for this ADR's structure)
- `cloud-itonami-isic-4711/docs/adr/0001-architecture.md` and
  `cloud-itonami-isic-4920/docs/adr/0001-architecture.md` (the two
  prior capability-library-wrapping verticals)
- 労働保険の保険料の徴収等に関する法律 (家事使用人特例); 健康保険法/厚生年金保険法; 児童福祉法
  (Child Welfare Act) (Japan)
- IRS Schedule H, 26 U.S.C. §3510; National Background Check Program
  (US)
- Income Tax (Earnings and Pensions) Act 2003; Safeguarding
  Vulnerable Groups Act 2006 (UK)
- §28a SGB IV (Haushaltsscheckverfahren); §72a SGB VIII (erweitertes
  Führungszeugnis) (Germany)
