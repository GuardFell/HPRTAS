# Camunda Forms

The Camunda Forms the staff fill in, one `.form` file per form, and the user tasks in the
operational models they are bound to. Every one of the 55 user tasks in the four operational models
binds a form.

## How a form is bound to a task

Every `.form` file carries a top-level `id`, which is the form's key. A user task refers to that key
from its extension elements:

```xml
<bpmn:userTask id="N_C_ClinicalReview" name="Clinical review of referral">
  <bpmn:extensionElements>
    <zeebe:userTask />
    <zeebe:formDefinition formId="referral-review" />
    <zeebe:assignmentDefinition candidateGroups="consultants" />
  </bpmn:extensionElements>
</bpmn:userTask>
```

Two things have to hold for Tasklist to render the form. The attribute is **`formId`**, and it sits
on **`<zeebe:formDefinition>`** inside `<bpmn:extensionElements>`. An attribute named
`camunda:formKey` on the task itself is Camunda 7 syntax: Camunda 8 ignores it, and because the
`camunda:` prefix was never declared the models stopped being well-formed XML and would not deploy
at all. The `.form` file must also declare the matching `id` and be deployed as a resource together
with the model, or Tasklist has no form to resolve the key against.

A form is resolved when the user task is **created**, and the task then holds that form version. A
corrected form does not reach a task that is already open; the task has to be created again.
Assigning the task to yourself is a separate step: an unassigned task renders its form read-only and
`Complete Task` stays disabled.

## The four schema rules that are easy to get wrong

These are not style preferences. Each one was got wrong in the first edition of these forms, and
each one fails silently — the form still deploys and the task still resolves its key.

**A field's `key` is the process variable.** There is no separate mapping. A field named
`documentsComplete` writes the variable `documentsComplete`, which is the variable the model's
gateway and the `validate-referral` worker read. A form and a model that disagree are reconciled by
changing the form.

**A group must not carry `path`.** The `group` component is declared `pathed: true`, and
`PathRegistry.getValuePath` prepends a pathed ancestor's path to the field's own key. A group with
`"path": "referralInfo"` around a field with `"key": "referralId"` writes
`referralInfo.referralId`, not `referralId` — so no gateway and no worker ever sees it, and the
form cannot even read back what it wrote. The groups here are visual sections only; the fields
inside them stay at the top level. The same applies to a key written as a dot path: a key of
`a.b` would nest, so keys here are single words.

**A container's children live under `components`.** `Importer.js` reads `fieldAttrs.components` and
`SimpleChildrenRenderer` renders `field.components`. A group whose children sit under any other key
— `elements`, which is what these forms used — imports as an **empty** container: the group renders
its label and nothing else, and no field exists to read or write. This is the fault that made every
form in the first edition render as a set of empty headings.

**A date field is the `datetime` field with a subtype.** `Datetime.config.type` is `datetime`, and
`date` selects the date half of it:

```json
{ "type": "datetime", "subtype": "date", "dateLabel": "Date", "key": "requestDate", "label": "Date Requested" }
```

`"type": "date"` names a field type that is not registered, and the importer rejects the **whole
form**, not just that field — one wrong date field leaves a task with no form at all.

### `required` on a checkbox means "must be ticked"

The validator rejects an unchanged checkbox when it is required:

```js
const isUncheckedCheckbox = field.type === 'checkbox' && value === false;
if (isUncheckedCheckbox || isUnsetValue || isEmptyMultiselect) errors.push('Field is required.');
```

A checkbox is the only control that writes a real boolean, and its empty value is already `false`.
So a boolean whose `false` value is a legitimate answer — `documentsComplete`, `consentGiven`,
`fitToContinue`, `suspectedClinicalError`, `paidAppointment`, `urgentClinicalConcern`,
`affectsCharge`, `letterWithinSevenDays`, `pathwayClosed` — **cannot be marked required**: marking
it so makes the `false` branch unreachable from the form, and a task that can only ever answer
"yes" is worse than one that answers nothing. Those fields carry a description instead, which states
what leaving them unticked means.

Some of those defaults are the permissive direction and the form cannot enforce them: an unticked
`paymentRequiredFromPatient` confirms an appointment without taking payment, and an unticked
`suspectedClinicalError` distributes a letter without it having been checked. Making those two safer
needs a model change — a gateway on `fundingRoute`, or an explicit acknowledgement field — not a
form change, and that decision is not taken here.

Where the answer really is only ever "yes" — `patientIdentificationConfirmed`, `letterCorrected`,
`referrerNotified`, `appointmentConfirmed` — the field is required. A boolean whose false answer is
meaningful and which nothing reads uses a Yes/No `select` instead, so the answer is always explicit.

## The forms and what they cover

`referral-check` is bound to checking a referral's documents and to correcting the input when the
validation refuses it, in `core-1`. It records the referral and patient identifiers, the referring
organisation, the identification check (BR-46) and the completeness decision. It supplies
`documentsComplete`, `missingItems` and `referringOrganisation` to `validate-referral`; a referral
ticked as complete must not also list missing items, or the check cannot be decided.

`missing-information-request` and `referral-exception-review` cover the incomplete referral. The
request records what was asked for and from whom; the exception review is for the case where the
referral was marked incomplete but named nothing, and it records the items so the re-run validation
has something to request.

`referral-review` is bound to the consultant's clinical review of a referral in `core-1`. It records
the patient and referral identifiers, the referring organisation, the clinical summary and the
decision with its reason. It writes the decision as `decision`, with the values the gateway tests:
`accepted`, `rejected`, `further_information` and `redirected`.

`booking-request` is bound to preparing and correcting a booking request in `core-1`, and to
arranging and correcting a follow-up request in `core-4`. It records the speciality, the priority,
the time frame the clinician asked for, any requirements the patient has, and the recipients and
document type the correspondence service is called with.

`patient-contact` is bound to the telephone contact and the retry in `core-1`. It records the contact
method, the attempt number, the outcome and any notes. Its outcome values are the ones the model
branches on: `contacted` reaches the appointment being arranged, `alternative_requested` returns to
preparing the request, and `unanswered` and `wrong_number` record another attempt.

`cancellation-record` is bound to recording a cancellation, a decline or a non-attendance in
`core-4`. It records the event and its date, and then the three decisions the model routes on:
whether a paid appointment is affected, whether another appointment is to be offered, and whether
the pathway needs a clinical review.

`no-suitable-slot`, `delay-review`, `unbooked-case-review` and `referrer-notification` cover what
happens when the appointment the clinician asked for cannot be found: the scheduling outcome and the
action taken are recorded, the pathway team reviews the case, and the referring organisation is
told.

`dispatch-failure` is bound to all four tasks that handle a correspondence failure — one in each of
`core-1` to `core-4`. It records the corrected recipients and document type, so the dispatch that is
retried succeeds, with the reason for the failure and the alternative channel the patient was
reached on.

`appointment-confirmation` is bound to confirming a treatment appointment in `core-2` and a
follow-up appointment in `core-4`. It records the basis for confirming — funding in place, payment
settled, payment not required or an exemption — and the recipients and document type for the
notification that follows.

`patient-assessment` is bound to assessing the patient and explaining the treatment options in
`core-2`. It records the diagnosis and that it was discussed, and takes the two clinical answers the
later gateways read: `consentGiven`, and `delayingCareIsClinicalRisk`, which decides what happens if
the funding or payment gate fails weeks later.

`treatment-booking` is bound to recording consent and authorising a request, and to correcting a
booking input, in `core-2`. It records the proposed treatment, the start date, the number of cycles,
any special resources, and the clinical authorisation with the clinician who gave it.

`treatment-modification` is bound to authorising a treatment modification in `core-2`. It has its
own form rather than sharing `treatment-booking` because the modification is the task that answers
`affectsCharge`, and asking that question at the initial authorisation — before there is a charge to
affect — would be meaningless.

`authorisation-verification` is bound to the administrative check that a treatment request is
complete and clinically authorised. It records the verification and who made it, and deliberately
does **not** write `clinicalAuthorised`: that variable belongs to the clinician, written by
`treatment-booking`, and an administrative task that could set it would defeat the check it exists
to make. `authorisation-return` records a request sent back for that authorisation.

`booking-pending`, `funding-route`, `funding-approval`, `payment`, `payment-investigation` and
`payment-notification` carry the rest of the money path: a booking left pending while the service is
unavailable, the funding route, the approval with its reference and amount, the charge and payment
reference, a transaction taken without a confirmation, and the notification that answers
`retryPayment` — the variable that sends the case back to the charge.

`urgent-authorisation` records a clinical decision to start treatment before the funding or payment
question is settled, and `financial-impact-review` records Finance's review of a modification that
touches an existing charge, approval or payment.

`clinic-letter` is bound to preparing and approving a clinic letter and to confirming its recipients
in `core-3`. It records the consultation it follows, the authoring clinician, the letter content,
the diagnosis, the treatment decisions, the follow-up arrangements, the recipients, and the document
type the correspondence service is called with.

`letter-processing` is bound to the administrative check and the correspondence timeline. It
records the appointment date, the approval date and the processing date, and answers
`suspectedClinicalError`, which sends the letter back to the consultant rather than letting an
administrative task change clinical meaning. `clinical-error-review` records that review and the
correction. `correspondence-monitoring`, `consultant-reminder`, `consultant-contact` and
`management-escalation` carry the seven-day target, the reminders with their responses and reasons
for delay, and the escalation past one month and past three months.

`enquiry-classification` records an enquiry with when it was received, who took it, the team
responsible and the priority, and answers `enquiryType`, which routes it. `enquiry-response` records
the answer, who gave it and whether the enquiry is resolved, and is bound both to answering an
administrative enquiry and to answering a funding or payment one. `clinical-advice` and
`urgent-escalation` cover the clinical half: the advice given, whether an urgent clinical concern is
present — which decides whether the enquiry is closed or escalated immediately — and the escalation
itself.

`pathway-review` records the clinical decision on a patient's pathway: whether treatment continues,
is delayed, or the patient is discharged, with the reason. It answers both `continueTreatment` and
`pathwayClosed`, which are separate variables the model routes on, so the form states which
combinations of the two are the three real outcomes.

`refund` is bound to the Finance Team's decision on a paid appointment and to correcting a refund
request in `core-4`. It records the payment reference being refunded, the decision (`full`,
`partial` or `none`), the amount where the refund is partial, the reason, and any notes. The
decision is written as `refundDecision` because that is the variable the gateway tests and the
refund worker validates.

`pre-cycle-review` records the review before each later chemotherapy cycle: the blood test and its
result, and `fitToContinue`, which is the clinical decision the model branches on.

## Every binding

A form shared by several tasks is listed once per task, because the same form is the variable
contract for each of them. Every user task in the four operational models appears exactly once.

| Form | Tasks bound to it |
|---|---|
| `appointment-confirmation.form` | `N_TB_ConfirmAppointment`, `N_OB_ConfirmFollowUp` |
| `authorisation-return.form` | `N_TB_ReturnRequest` |
| `authorisation-verification.form` | `N_TB_VerifyAuthorisation` |
| `booking-pending.form` | `N_TB_KeepPending` |
| `booking-request.form` | `N_OB_PrepareRequest`, `N_OB_CorrectRequest` (core-1), `N_OB_ArrangeFollowUp`, `N_OB_CorrectRequest` (core-4) |
| `cancellation-record.form` | `N_OB_RecordCancellation` |
| `clinic-letter.form` | `N_C_PrepareLetter`, `N_C_ApproveLetter`, `N_MS_ConfirmRecipients` |
| `clinical-advice.form` | `N_CNS_ClinicalAdvice` |
| `clinical-error-review.form` | `N_C_ReviewClinicalError` |
| `consultant-contact.form` | `N_AM_ContactConsultant` |
| `consultant-reminder.form` | `N_PC_IssueReminder` |
| `correspondence-monitoring.form` | `N_PC_MonitorCorrespondence` |
| `delay-review.form` | `N_PC_ReviewDelay` |
| `dispatch-failure.form` | `N_OB_RecordDispatchFailure` (core-1), `N_TB_RecordNotificationFailure`, `N_MS_HandleDispatchFailure`, `N_OB_RecordDispatchFailure` (core-4) |
| `enquiry-classification.form` | `N_CH_ClassifyEnquiry` |
| `enquiry-response.form` | `N_CH_AnswerAdmin`, `N_F_AnswerFinance` |
| `financial-impact-review.form` | `N_F_ReviewFinancialImpact` |
| `funding-approval.form` | `N_F_RecordApproval` |
| `funding-route.form` | `N_F_DetermineFunding` |
| `letter-processing.form` | `N_MS_ProcessLetter` |
| `management-escalation.form` | `N_AM_EscalateHigher` |
| `missing-information-request.form` | `N_MS_RequestMissing` |
| `no-suitable-slot.form` | `N_OB_RecordNoSlot` |
| `pathway-review.form` | `N_CNS_PathwayReview` |
| `patient-assessment.form` | `N_CL_AssessPatient` |
| `patient-contact.form` | `N_OB_TelephonePatient`, `N_OB_RecordAttempt` |
| `payment-investigation.form` | `N_F_InvestigatePayment` |
| `payment-notification.form` | `N_F_NotifyPatient` |
| `payment.form` | `N_F_CalculateCharge`, `N_F_CorrectPaymentRequest` |
| `pre-cycle-review.form` | `N_OC_PreCycleReview` |
| `referral-check.form` | `N_MS_CheckReferral`, `N_MS_CorrectReferralInput` |
| `referral-exception-review.form` | `N_MS_ReviewException` |
| `referral-review.form` | `N_C_ClinicalReview` |
| `referrer-notification.form` | `N_OB_NotifyReferrer` |
| `refund.form` | `N_F_RetentionDecision`, `N_F_CorrectRefundRequest` |
| `treatment-booking.form` | `N_CL_AuthoriseTreatment`, `N_TB_CorrectBookingInput` |
| `treatment-modification.form` | `N_CL_ModifyTreatment` |
| `unbooked-case-review.form` | `N_PC_ReviewUnbooked` |
| `urgent-authorisation.form` | `N_CL_UrgentAuthorise` |
| `urgent-escalation.form` | `N_CNS_HighlightUrgent` |

## How the forms are built

Related fields are grouped into sections, so a long form is read in parts rather than as one list of
fields. Fields the process cannot proceed without are marked required, which stops an incomplete
submission that would only fail later in a worker. Enumerated values use dropdowns, so the value a
form writes is one the gateway condition or the worker's validation accepts, and the person filling
it in sees a label rather than a code. Free-text clinical notes and decision reasons use text areas
with room to write in. Fields that carry a list — `recipients`, `missingItems`, `specialResources` —
say to separate the entries with commas, because the worker reads a comma-separated string as a
list; one entry per line would arrive as a single entry. Each form has a short kebab-case `id`,
because that id is the key the model refers to and it has to stay stable.

## Testing the forms

The forms are exercised as part of the scenarios in `../tests/test-plan.md`, and the end-to-end run
described in `../tests/evidence/README.md` deploys them alongside the models so the bindings are
proved to resolve. The runs in `../tests/evidence/` that predate the forms passed their variables
with the task completion call instead, so they do not exercise the form bindings.

What has been checked about the forms themselves:

- All 40 deploy to the engine, and every user task in the four operational models resolves a form
  key.
- All 40 import in `@bpmn-io/form-js`, the library Tasklist renders them with, and each one's
  submission carries exactly its own field keys and nothing else, at the top level — 287 fields,
  287 variables. That is the check that a field's output name is its `key`.
- The form bound to `N_MS_CheckReferral` renders in Tasklist with all ten of its fields, becomes
  editable once the task is assigned, and enables `Complete Task` only when the required fields are
  filled.
- `mvn test -Pengine -Dtest=OperationalModelsTest` runs all four models with these bindings in
  place.

Not checked: a task completed from a form by a signed-in user, end to end. The variables a form
produces have been shown to be the variables the model reads, but the round trip through Tasklist
has not been driven. That is the same gap as `DEF-08` in `../tests/test-plan.md`: the candidate
groups on the tasks are not enforced by anything yet, so neither the tasks nor the demonstration
depend on a signed-in user.
