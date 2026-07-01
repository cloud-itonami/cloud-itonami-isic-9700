# Business Model: Community Domestic Employment

## Classification
- Repository: `cloud-itonami-9700`
- ISIC Rev.5: `9700` — activities of households as employers of domestic personnel
- Social impact: worker protection, fair pay, care access

## Customer
- households employing nannies, caregivers, housekeepers, gardeners
- cooperatives pooling domestic-worker services
- elder-care and child-care support programs
- worker collectives leaving closed agency SaaS

## Offer
- employment contracts (hourly/monthly) with worker and household identity
- service-robot mission scheduling and safety gating
- timesheet and wage calculation
- payroll with documented deductions
- role-based access and immutable audit ledger
- worker-portal and household-portal separation

## Revenue
- self-host setup fee
- managed hosting subscription per household
- support retainer with SLA
- payroll and tax integration

## Trust Controls
- a robot action the governor refuses is never dispatched to hardware
- safety-critical actions (near a child/elder, agents, private rooms) require human sign-off
- payroll must balance gross minus documented deductions
- worker disclosures require governor approval
- worker personal data stays outside Git
