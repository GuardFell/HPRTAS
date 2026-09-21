# Camunda Forms

Camunda Forms (`.form`) connected to the relevant user tasks.

## Requirements

- Capture the required information for each task
- Use clear labels and appropriate controls
- Validate inputs where the process depends on the value
- Exchange variables correctly with the process
- Consider the intended users, accessibility and consistency across the workflow

## Form-to-task bindings

| Form file | Bound user task | Model | Variables written | Variables read | Status |
|---|---|---|---|---|---|
| `referral-review.form` | `UserTask_CheckReferralDocuments` | `referral-to-appointment.bpmn` | referralId, patientName, referringOrganisation, clinicalSummary, reviewDecision, decisionReason, speciality, priority, requestedWindow, patientRequirements | - | Done |
| `referral-review.form` | `UserTask_ClinicalReviewReferral` | `referral-to-appointment.bpmn` | referralId, patientName, referringOrganisation, clinicalSummary, reviewDecision, decisionReason, speciality, priority, requestedWindow, patientRequirements | - | Done |
| `booking-request.form` | `UserTask_PrepareBookingRequest` | `referral-to-appointment.bpmn` | referralId, patientId, speciality, priority, requestedWindow, patientRequirements | - | Done |
| `booking-request.form` | `UserTask_CorrectBookingRequest` | `referral-to-appointment.bpmn` | referralId, patientId, speciality, priority, requestedWindow, patientRequirements | - | Done |
| `patient-contact.form` | `UserTask_TelephoneContactPatient` | `referral-to-appointment.bpmn` | patientId, contactMethod, contactOutcome, contactNotes | - | Done |
| `patient-contact.form` | `UserTask_RecordUnsuccessfulAttempt` | `referral-to-appointment.bpmn` | patientId, contactMethod, contactOutcome, contactNotes | - | Done |
| `patient-contact.form` | `UserTask_RecordCancellationOrNonAttendance` | `clinic-letter-and-pathway-monitoring.bpmn` | patientId, contactMethod, contactOutcome, contactNotes | - | Done |
| `treatment-booking.form` | `UserTask_AssessAndAuthoriseTreatment` | `treatment-authorisation-and-booking.bpmn` | treatmentRequestId, proposedTreatment, treatmentStartDate, numberOfCycles, specialResources, clinicalAuthorised, authorisingClinician | - | Done |
| `treatment-booking.form` | `UserTask_PrepareTreatmentBooking` | `treatment-authorisation-and-booking.bpmn` | treatmentRequestId, proposedTreatment, treatmentStartDate, numberOfCycles, specialResources, clinicalAuthorised, authorisingClinician | - | Done |
| `funding-route.form` | `UserTask_DetermineFundingRoute` | `treatment-authorisation-and-booking.bpmn` | treatmentRequestId, fundingRoute, fundingNotes | - | Done |
| `payment.form` | `UserTask_CalculateCharge` | `treatment-authorisation-and-booking.bpmn` | treatmentRequestId, chargeAmount, fundingRoute, paymentReference | - | Done |
| `refund.form` | `UserTask_DetermineRefund` | `treatment-authorisation-and-booking.bpmn` | appointmentId, refundReason, refundType, refundAmount, refundNotes | - | Done |
| `refund.form` | `UserTask_DetermineRefund` | `clinic-letter-and-pathway-monitoring.bpmn` | appointmentId, refundReason, refundType, refundAmount, refundNotes | - | Done |
| `clinic-letter.form` | `UserTask_PrepareAndApproveClinicLetter` | `clinic-letter-and-pathway-monitoring.bpmn` | consultationId, letterContent, diagnosis, treatmentDecisions, followUpArrangements, recipients | - | Done |

## Design decisions

| Decision | Rationale | Accessibility / usability consideration |
|---|---|---|
| Used `camunda:form:` prefix for form keys | Required by Camunda 8 Tasklist to load embedded forms | Forms render in Tasklist UI with standard HTML controls |
| Grouped related fields with `<group>` components | Reduces visual clutter and helps users focus on sections | Clear section headings improve screen reader navigation |
| Added `validate.required` on critical fields | Prevents incomplete submissions that would cause worker errors | Inline validation messages shown immediately |
| Used `select` dropdowns for enumerated values | Enforces valid options and matches worker `oneOf` constraints | Keyboard navigable; visible option labels instead of codes |
| Kept `textarea` for free-text fields | Allows detailed clinical notes and decision reasons | Generous rows for comfortable editing |

## Testing

Forms are tested as part of the scenarios in `tests/test-plan.md` (TC-01 to TC-10, TC-13 to
TC-16, TC-20); evidence is stored in `tests/evidence/`.
