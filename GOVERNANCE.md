# Governance

`cloud-itonami-9700` is an OSS open-business blueprint for community domestic
employment, robotics-premised.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a robot action the governor refuses is never dispatched to hardware.
- the Domestic Employment Governor remains independent of the advisor.
- hard policy violations (force-dispatch, unbalanced payroll, unauthorized disclosure) cannot be overridden by human approval.
- every dispatch, sign-off, payroll and disclose path is auditable.
- worker personal data and credentials stay outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage contract, public business model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a separate trust mark and should require security, robot-safety, audit and data-flow review.

Certified operators can lose certification for:
- bypassing robot-safety or payroll policy checks
- mishandling worker data
- misrepresenting certification status
- failing to respond to security or safety incidents
