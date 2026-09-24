# Operational Models

The executable BPMN processes: the models Camunda deploys and runs, and the ones the external
workers are bound to.

Each process covers one part of the patient pathway, and between them they carry the whole pathway
from the referral arriving to the refund of a cancelled paid appointment.

`core-1-referral-and-new-patient-appointment.bpmn` covers the referral being received, the
supporting documents being checked, the consultant's decision to accept, reject, query or redirect
it, and the booking of the new patient appointment through the external scheduling service,
including the rule that a patient whose appointment falls inside two weeks is telephoned as well as
written to, and the case where no suitable slot exists: a routine referral is recorded and
reviewed with the delay, and an urgent one is escalated at once rather than re-checked, because
the scheduling service answers the same request with the same result.

`core-2-treatment-authorisation-funding-and-payment.bpmn` covers consent and the authorisation of
treatment, the determination of the funding route, the payment request to the external payment
service provider with its declined, duplicated and unconfirmed outcomes, the treatment booking
itself with the external treatment service, and the clinical review that happens between treatment
cycles.

`core-3-clinic-letter-and-pathway-escalation.bpmn` covers the clinic letter: its preparation and
clinical approval, its administrative check and distribution through the external correspondence
service, the target of completing it within seven days of the appointment, and the escalation of a
letter that is late, first as a reminder to the consultant and then to higher management as the
delay grows.

`core-4-follow-up-cancellation-enquiry-and-refund.bpmn` covers what happens after treatment: a
follow-up appointment being requested and booked, the cancellation, decline or non-attendance of an
appointment and the decision that follows it, the handling and routing of patient enquiries, and
the financial decision on a paid appointment that has been cancelled or changed, ending in a refund
where one is due.

Each file holds one executable process. The processes are deployed and started separately, and
`core-4` has three start events: one ordinary start event for the follow-up being requested, and
two message start events for the cancellation and for the enquiry, which arrive unannounced and
cannot share the single ordinary start event that Camunda allows a process.

## What the models require

- **Participants and lanes.** Every process has more than one pool where independent participants
  collaborate, with message flows between them, and its lanes keep clinical, administrative and
  financial responsibilities visibly separate.
- **Camunda user tasks.** Every user task carries the Camunda user task marker so it appears in
  Tasklist, and an assignment to the candidate group of its lane.
- **Camunda Forms.** User tasks that need structured input bind a form by its key, and the matching
  `.form` file in `../../forms/` has to be deployed with the model.
- **Service tasks and job types.** Every automated step is a service task with a job type, and each
  job type has a worker in `../../workers/` registered against it.
- **Error catch events.** A service task that can raise a business error has a boundary error event
  to catch it, and that error path returns the process to the task that owns the request, so the
  request is corrected and the step retried rather than the process stalling.
- **A default flow on every gateway.** An exclusive gateway falls back only to the flow named in
  its `default` attribute. Without one, a gateway whose conditions all evaluate false raises an
  incident instead of continuing, so every gateway in these models declares a fallback, and the
  flow it names carries no condition of its own.
