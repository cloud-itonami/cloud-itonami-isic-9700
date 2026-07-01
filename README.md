# cloud-itonami-9700

Open Business Blueprint for **ISIC Rev.5 9700**: activities of households as
employers of domestic personnel (nannies, caregivers, housekeepers, gardeners).

This repository designs a forkable OSS business for community domestic
employment: robotics-assisted household work, employment contracts, timesheet
and wage management, and payroll — run by a qualified operator so a
household-employer keeps its own employment records instead of renting a
closed agency SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a service robot (cleaning, assistive-care,
garden) performs the household task under an actor that proposes actions and
an independent **Domestic Employment Governor** that gates them. The governor
never dispatches hardware itself; `:high`/`:safety-critical` actions (operate
near a child or elder, handle cleaning agents, enter a private room) require
human sign-off.

## Core Contract

```text
intake + identity + employment contract + robot mission
        |
        v
Household Advisor -> Domestic Employment Governor -> schedule, work, pay, or human approval
        |
        v
robot actions (gated) + timesheet + payroll + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, post a
payroll that doesn't balance against the contract, or disclose worker data
without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `9700`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/labor`](https://github.com/kotoba-lang/labor) — contracts, timesheets, wages, payroll

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
