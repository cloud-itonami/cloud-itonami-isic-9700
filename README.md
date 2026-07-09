# cloud-itonami-isic-9700

Open Business Blueprint for **ISIC Rev.5 9700**: activities of
households as employers of domestic personnel (nannies, caregivers,
housekeepers, gardeners).

This repository publishes a community-domestic-employment actor --
assignment intake, per-jurisdiction household-employer-registration/
safeguarding regulatory assessment, mission dispatch and payroll
posting -- as an OSS business that any qualified operator can fork,
deploy, run, improve and sell, so a household-employer never
surrenders employment and payroll data to a closed agency SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet (96 prior actors) -- here it is
**DomesticOps-LLM ⊣ Domestic Employment Governor**. This blueprint's
own `:itonami.blueprint/governor` keyword,
`:domestic-employment-governor`, is a UNIQUE keyword fleet-wide
(grep-verified: no other blueprint declares it) -- a fresh,
independent build.

> **Why an actor layer at all?** An LLM is great at drafting an
> assignment summary, normalizing records, and checking whether a
> claimed gross wage actually equals what
> [`kotoba-lang/labor`](https://github.com/kotoba-lang/labor)'s own
> wage-computation logic would independently derive from the
> assignment's own contract and timesheet -- but it has **no notion
> of which jurisdiction's household-employer-registration/
> safeguarding law is official, no license to dispatch a real mission
> into a private household or post a real payroll, and no way to know
> on its own whether a household employing a domestic worker has
> actually registered as an employer or whether a worker assigned to
> care for a child or elder has actually passed a required
> safeguarding check**. Letting it dispatch or post payroll directly
> invites fabricated regulatory citations, a payroll mismatch being
> paid to a worker, an unregistered household's mission being
> dispatched, and an unchecked worker being sent to care for a
> vulnerable person -- exposing the household to real regulatory
> liability and vulnerable people to real, serious harm. This project
> seals the DomesticOps-LLM into a single node and wraps it with an
> independent **Domestic Employment Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers assignment intake through household-employer-
registration/safeguarding regulatory assessment, mission dispatch and
payroll posting. It does **not**, by itself, hold any operating
authority required to employ domestic personnel in a given
jurisdiction, and it does not claim to. It also does not perform the
actual physical household work itself, or judge worker fit --
`domesticops.registry/payroll-matches-contract?` is a pure ground-
truth recompute (delegating to `kotoba.labor/wages-for`) against the
assignment's own recorded fields, not a fit judgment. Whoever deploys
and operates a live instance (a qualified household-employer/agency
coordinator) supplies any jurisdiction-specific registration, the
real service-robot integration and the real payroll/tax integrations,
and bears that jurisdiction's liability -- the software supplies the
governed, spec-cited, audited execution scaffold so that operator
does not have to build the compliance layer from scratch.

### Actuation

**Dispatching a real mission into a private household and posting a
real payroll are never autonomous, at any phase, by construction.**
Two independent layers enforce this (`domesticops.governor`'s
`:actuation/dispatch-mission`/`:actuation/post-payroll` high-stakes
gate and `domesticops.phase`'s phase table, which never puts either
op in any phase's `:auto` set) -- see `domesticops.phase`'s docstring
and `test/domesticops/phase_test.clj`'s `assignment-dispatch-never-
auto-at-any-phase`/`assignment-pay-never-auto-at-any-phase`. The
actor may draft, check and recommend; a human household-employer/
agency coordinator is always the one who actually dispatches a
mission or posts a payroll. Grounded directly in this blueprint's own
`docs/business-model.md` Trust Controls text ("payroll must balance
gross minus documented deductions; safety-critical actions (near a
child/elder, agents, private rooms) require human sign-off") -- a
genuine DUAL-actuation shape, applied SEQUENTIALLY to the SAME
assignment record (dispatch first, pay later), matching
`libraryops`/9101's, `adminops`/8411's, `employmentops`/7810's,
`practiceops`/7110's, `hospitalityops`/5510's, `freightops`/4920's,
`quarryops`/0810's and `agronomyops`/0162's own sequential shape
rather than `retailops`/4711's own alternative-kind shape.

## The core contract

```
assignment intake + jurisdiction facts (domesticops.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ DomesticOps-LLM       │ ─────────────▶ │ Domestic Employment Governor  │  (independent system)
   │ (sealed)              │  + citations    │ spec-basis · evidence-       │
   └───────────────────────┘                 │ incomplete · household-       │
          │                 commit ◀┼ employment-unregistered (FLAGSHIP     │
          │                         │ NEW) · payroll-mismatch (delegates    │
    record + ledger        escalate ┼ to kotoba.labor/wages-for) ·              │
          │              (ALWAYS for│ vulnerable-person-safeguarding-check-     │
          │       :actuation/       │ missing (conditional, NEW) · already-     │
          │       dispatch-mission/ │ dispatched · already-paid                  │
          │       :actuation/post-  │                                            │
          │       payroll}           │                                            │
          ▼                          └───────────────────────┘
      human approval
```

**The DomesticOps-LLM never dispatches a mission or posts payroll the
Domestic Employment Governor would reject, and never does so without
a human sign-off.** Hard violations (fabricated regulatory
requirements; unsupported evidence; an unregistered household
employer; a payroll mismatch; a missing safeguarding check on a
vulnerable-person assignment; a double dispatch/payroll-posting)
force **hold** and *cannot* be approved past; a clean dispatch/
payroll proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk two clean dispatch+pay lifecycles (no vulnerable person, vulnerable person with safeguarding verified), plus four HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a service robot (cleaning,
assistive-care, garden) performs the household task, under the actor,
gated by the independent **Domestic Employment Governor**. The
governor never dispatches hardware itself; `:high`/`:safety-critical`
actions (operate near a child or elder, handle cleaning agents, enter
a private room) require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Domestic Employment Governor, dispatch/payroll draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9700`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) --
  missions, actions, safety-stops, telemetry proofs (the same generic
  cross-cutting robotics contract every cloud-itonami vertical uses)
- [`kotoba-lang/labor`](https://github.com/kotoba-lang/labor) --
  contracts, timesheets, wages, payroll (this vertical's own bespoke
  domain capability library, wrapped directly by `domesticops.
  registry`'s own `payroll-matches-contract?` -- the THIRD capability-
  library-wrapping vertical in this fleet, after `retailops`/4711's
  own `kotoba-lang/retail` and `freightops`/4920's own `kotoba-lang/
  logistics` integrations)

## Layout

| File | Role |
|---|---|
| `src/domesticops/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + dispatch AND payroll history (dual history). The double-actuation guard checks dedicated `:dispatched?`/`:paid?` booleans rather than a `:status` value |
| `src/domesticops/registry.cljc` | Dispatch/payroll draft records, plus `payroll-matches-contract?` -- delegates DIRECTLY to `kotoba.labor/wages-for` rather than reimplementing wage arithmetic |
| `src/domesticops/facts.cljc` | Per-jurisdiction household-employer-registration AND vulnerable-person-safeguarding catalog with an official spec-basis citation per entry, honest coverage reporting -- ALL FOUR seeded jurisdictions have a safeguarding sub-citation here |
| `src/domesticops/domesticopsllm.cljc` | **DomesticOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/jurisdiction-assessment/dispatch/payroll proposals |
| `src/domesticops/governor.cljc` | **Domestic Employment Governor** -- 5 HARD checks (spec-basis · evidence-incomplete · household-employment-unregistered, FLAGSHIP NEW, the 90th unconditional-evaluation-discipline grounding · payroll-mismatch, delegates to `kotoba.labor` · vulnerable-person-safeguarding-check-missing, CONDITIONAL, the 91st grounding) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/domesticops/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (dispatch/pay always human; assignment intake is the ONLY auto-eligible op, no direct household-facing risk) |
| `src/domesticops/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/domesticops/sim.cljc` | demo driver |
| `test/domesticops/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers assignment intake through household-employer-
registration/safeguarding regulatory assessment, mission dispatch and
payroll posting -- the core governed lifecycle this blueprint's own
`docs/business-model.md` names in its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Assignment intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:assignment/intake`/`:jurisdiction/assess`) | Real service-robot integration, real worker-fit judgment (see `domesticops.facts`'s docstring) |
| Mission dispatch, HARD-gated on full evidence and household-employer registration, plus a double-dispatch guard (`:actuation/dispatch-mission`) | |
| Payroll posting, HARD-gated on full evidence, a matching gross-wage claim (via `kotoba.labor/wages-for`) and (when applicable) a safeguarding check, plus a double-payroll-posting guard (`:actuation/post-payroll`) | |
| Immutable audit ledger for every intake/assessment/dispatch/payroll decision | |

Extending coverage is additive: add the next gate (e.g. a payroll-
deduction-documentation-verification check) as its own governed op
with its own HARD checks and tests, following the SAME "an
independent governor re-verifies against the actor's own records
before any real-world act" pattern this repo's flagship ops already
establish.

## Jurisdiction coverage (honest)

`domesticops.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `domesticops.facts/catalog`
-- currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `domesticops.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger. Note that the vulnerable-person-
safeguarding sub-citation is FULL coverage rather than a gap: ALL FOUR
seeded jurisdictions (JPN, USA, GBR, DEU) actually have a real
vulnerable-person-safeguarding enforcement regime, reported honestly.

## Maturity

`:implemented` -- `DomesticOps-LLM` + `Domestic Employment Governor`
run as real, tested code (see `Run` above), promoted from the
originally-published `:blueprint`-tier scaffold, following the SAME
governed-actor architecture as the 96 other prior actors across this
fleet, with its own distinct, independently-named governor. See
`docs/adr/0001-architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
