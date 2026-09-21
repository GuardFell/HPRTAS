# Camunda Forms

Camunda Forms (`.form`) connected to the relevant user tasks.

## Requirements

- Capture the required information for each task
- Use clear labels and appropriate controls
- Validate inputs where the process depends on the value
- Exchange variables correctly with the process
- Consider the intended users, accessibility and consistency across the workflow

## How a form is bound to a task

Every `.form` file carries a top-level `id`, which is the form's key. The user task points at it
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

Two things have to hold for Tasklist to render the form, and both were got wrong in the first
edition of this directory:

- the attribute is **`formId`** and it lives on **`<zeebe:formDefinition>`** inside
  `<bpmn:extensionElements>`. An attribute named `camunda:formKey` on the task is Camunda 7 syntax:
  Camunda 8 ignores it, and because the `camunda:` prefix was never declared the models stopped
  being well-formed XML and would not deploy at all.
- the `.form` file must declare the matching `id`, and must be deployed as a resource together with
  the model, or Tasklist has no form to resolve the `formId` against.

## Form-to-task bindings

| Form file | Bound user task | Model | Variables written | Status |
|---|---|---|---|---|
| `referral-review.form` | `N_C_ClinicalReview` | `core-1-referral-and-new-patient-appointment.bpmn` | referralId, patientName, referringOrganisation, clinicalSummary, decision, decisionReason, speciality, priority, requestedWindow, patientRequirements | Done |
| `booking-request.form` | `N_OB_PrepareRequest` | `core-1-referral-and-new-patient-appointment.bpmn` | referralId, patientId, speciality, priority, requestedWindow, patientRequirements | Done |
| `booking-request.form` | `N_OB_CorrectRequest` | `core-1-referral-and-new-patient-appointment.bpmn` | referralId, patientId, speciality, priority, requestedWindow, patientRequirements | Done |
| `patient-contact.form` | `N_OB_TelephonePatient` | `core-1-referral-and-new-patient-appointment.bpmn` | patientId, contactMethod, contactOutcome, contactNotes | Done |
| `patient-contact.form` | `N_OB_RecordAttempt` | `core-1-referral-and-new-patient-appointment.bpmn` | patientId, contactMethod, contactOutcome, contactNotes | Done |
| `treatment-booking.form` | `N_CL_AuthoriseTreatment` | `core-2-treatment-authorisation-funding-and-payment.bpmn` | treatmentRequestId, proposedTreatment, treatmentStartDate, numberOfCycles, specialResources, clinicalAuthorised, authorisingClinician | Done |
| `treatment-booking.form` | `N_CL_ModifyTreatment` | `core-2-treatment-authorisation-funding-and-payment.bpmn` | treatmentRequestId, proposedTreatment, treatmentStartDate, numberOfCycles, specialResources, clinicalAuthorised, authorisingClinician | Done |
| `treatment-booking.form` | `N_TB_CorrectBookingInput` | `core-2-treatment-authorisation-funding-and-payment.bpmn` | treatmentRequestId, proposedTreatment, treatmentStartDate, numberOfCycles, specialResources, clinicalAuthorised, authorisingClinician | Done |
| `funding-route.form` | `N_F_DetermineFunding` | `core-2-treatment-authorisation-funding-and-payment.bpmn` | treatmentRequestId, fundingRoute, fundingNotes | Done |
| `payment.form` | `N_F_CalculateCharge` | `core-2-treatment-authorisation-funding-and-payment.bpmn` | treatmentRequestId, chargeAmount, fundingRoute, paymentReference | Done |
| `payment.form` | `N_F_CorrectPaymentRequest` | `core-2-treatment-authorisation-funding-and-payment.bpmn` | treatmentRequestId, chargeAmount, fundingRoute, paymentReference | Done |
| `clinic-letter.form` | `N_C_PrepareLetter` | `core-3-clinic-letter-and-pathway-escalation.bpmn` | consultationId, letterContent, diagnosis, treatmentDecisions, followUpArrangements, recipients | Done |
| `clinic-letter.form` | `N_C_ApproveLetter` | `core-3-clinic-letter-and-pathway-escalation.bpmn` | consultationId, letterContent, diagnosis, treatmentDecisions, followUpArrangements, recipients | Done |
| `clinic-letter.form` | `N_MS_ConfirmRecipients` | `core-3-clinic-letter-and-pathway-escalation.bpmn` | consultationId, letterContent, diagnosis, treatmentDecisions, followUpArrangements, recipients | Done |
| `booking-request.form` | `N_OB_ArrangeFollowUp` | `core-4-follow-up-cancellation-enquiry-and-refund.bpmn` | referralId, patientId, speciality, priority, requestedWindow, patientRequirements | Done |
| `booking-request.form` | `N_OB_CorrectRequest` | `core-4-follow-up-cancellation-enquiry-and-refund.bpmn` | referralId, patientId, speciality, priority, requestedWindow, patientRequirements | Done |
| `patient-contact.form` | `N_OB_RecordCancellation` | `core-4-follow-up-cancellation-enquiry-and-refund.bpmn` | patientId, contactMethod, contactOutcome, contactNotes | Done |
| `refund.form` | `N_F_RetentionDecision` | `core-4-follow-up-cancellation-enquiry-and-refund.bpmn` | paymentReference, refundDecision, refundAmount, refundReason, refundNotes | Done |
| `refund.form` | `N_F_CorrectRefundRequest` | `core-4-follow-up-cancellation-enquiry-and-refund.bpmn` | paymentReference, refundDecision, refundAmount, refundReason, refundNotes | Done |

A form's variable names are the process variables its task writes, so a field name and the name a
gateway or a worker reads have to be the same word. `referral-review.form` writes `decision`
because `N_C_ReferralDecision` reads `decision`; `refund.form` writes `refundDecision` because
`N_F_RefundDecision` reads it and `refund-processing` validates it.

## Design decisions

| Decision | Rationale | Accessibility / usability consideration |
|---|---|---|
| Linked deployed forms through `zeebe:formDefinition formId` | The mechanism Camunda 8.9 resolves against a deployed `.form` resource | Forms render in Tasklist UI with standard HTML controls |
| Grouped related fields with `<group>` components | Reduces visual clutter and helps users focus on sections | Clear section headings improve screen reader navigation |
| Added `validate.required` on critical fields | Prevents incomplete submissions that would cause worker errors | Inline validation messages shown immediately |
| Used `select` dropdowns for enumerated values | Enforces valid options and matches the gateway conditions and the worker `oneOf` constraints | Keyboard navigable; visible option labels instead of codes |
| Kept `textarea` for free-text fields | Allows detailed clinical notes and decision reasons | Generous rows for comfortable editing |
| Gave every form a short kebab-case `id` | The `id` is the key the model refers to, so it has to be stable and readable | Nothing user-facing; it keeps the model readable for a reviewer |

## Testing

Forms are tested as part of the scenarios in `tests/test-plan.md` (TC-01 to TC-10, TC-13 to
TC-16, TC-20); evidence is stored in `tests/evidence/`. The end-to-end runs in `tests/evidence/`
that predate the forms passed the variables with the task completion call instead.
