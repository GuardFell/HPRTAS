# Operational Models

Executable BPMN models of the required business process.

## Requirements

- Represent participants, responsibilities, user tasks, service tasks, events, gateways and interactions
- Use **more than one pool** where independent participants collaborate, with message flows between them
- Configure the model for deployment
- Demonstrate normal, alternative and exception paths
- Keep clinical, administrative and financial responsibilities visibly separate

## Files

The first edition of this directory held three models (`referral-to-appointment`,
`treatment-authorisation-and-booking`, `clinic-letter-and-pathway-monitoring`). They were replaced
by the four `core-N` models below, which cover the same pathway plus the follow-up, enquiry and
refund requirements. The superseded models are in the history only; they were removed because the
form binding added to them used an undeclared `camunda:` namespace, which made them unparseable.

| File | Description | Deployable | Status |
|---|---|---|---|
| `core-1-referral-and-new-patient-appointment.bpmn` | Referral receipt and document check, clinical decision, and the new patient appointment with the two-week telephone rule | Yes | Deployed, version 1 |
| `core-2-treatment-authorisation-funding-and-payment.bpmn` | Consent and treatment authorisation, funding route, payment through the external provider, treatment booking and the between-cycle review | Yes | Deployed, version 1 |
| `core-3-clinic-letter-and-pathway-escalation.bpmn` | Clinic letter preparation, approval and distribution, the seven-day target and escalation of delays | Yes | Deployed, version 2 |
| `core-4-follow-up-cancellation-enquiry-and-refund.bpmn` | Follow-up booking, cancellation and non-attendance, enquiry handling, and the financial decision and refund | Yes | Deployed, version 1 |

Each file holds one executable process. `core-4` has three start events: one none start event plus
two message start events for the cancellation and the enquiry, which arrive unannounced and cannot
share the single none start event Camunda allows.

## Design decisions

| Decision | Rationale | Rule it satisfies |
|---|---|---|
|  |  |  |
|  |  |  |
|  |  |  |
|  |  |  |
