# The forms, by operational model

Every user task in `../models/operational/` binds a Camunda Form, and each of the 41 `.form` files
in this directory is one a model points at. `README.md` is the reference for the set: how a form is
bound to a task, the four schema rules that fail silently, and every binding as a single list. This
file is the same set of bindings **arranged by the model that uses them**, which is the arrangement
a demonstration needs - deploy a model, and the forms it resolves are the ones listed under it.

A form is bound by its `id`, which is also the file name: `referral-check.form` declares
`"id": "referral-check"`, and a user task carrying `<zeebe:formDefinition formId="referral-check" />`
is what resolves it. So the table below is also the list of form keys each model refers to.

## Summary

| Model | File | Lanes | User tasks | Forms bound |
|---|---|---|---|---|
| `core-1` | `core-1-referral-and-new-patient-appointment.bpmn` | 4 | 13 | 10 |
| `core-2` | `core-2-treatment-authorisation-funding-and-payment.bpmn` | 4 | 18 | 16 |
| `core-3` | `core-3-clinic-letter-and-pathway-escalation.bpmn` | 4 | 10 | 8 |
| `core-4` | `core-4-follow-up-cancellation-enquiry-and-refund.bpmn` | 5 | 15 | 12 |
| `core-5` | `core-5-missing-information-message-exchange.bpmn` | 1 | 2 | 2 |
| **all five** | | | **58** | **41 distinct** |

Five of the 41 forms are bound in more than one model. They are listed under each model that binds
them, because the same form is the variable contract for every task that binds it and those
contracts differ from model to model:

- **`appointment-confirmation.form`** - bound in core-2 and core-4.
  - `N_TB_ConfirmAppointment` in `core-2` - Confirm treatment appointment
  - `N_OB_ConfirmFollowUp` in `core-4` - Confirm the follow-up appointment
- **`booking-request.form`** - bound in core-1 and core-4.
  - `N_OB_PrepareRequest` in `core-1` - Prepare booking request
  - `N_OB_CorrectRequest` in `core-1` - Correct booking request
  - `N_OB_ArrangeFollowUp` in `core-4` - Arrange follow-up appointment
  - `N_OB_CorrectRequest` in `core-4` - Correct the follow-up request
- **`dispatch-failure.form`** - bound in core-1, core-2, core-3 and core-4.
  - `N_OB_RecordDispatchFailure` in `core-1` - Record dispatch failure
  - `N_TB_RecordNotificationFailure` in `core-2` - Record notification failure
  - `N_MS_HandleDispatchFailure` in `core-3` - Handle dispatch failure
  - `N_OB_RecordDispatchFailure` in `core-4` - Record dispatch failure
- **`missing-information-request.form`** - bound in core-1 and core-5.
  - `N_MS_RequestMissing` in `core-1` - Request missing information
  - `N_MS_RecordMissingItems` in `core-5` - Record the missing information
- **`referral-check.form`** - bound in core-1 and core-5.
  - `N_MS_CheckReferral` in `core-1` - Check referral and documents
  - `N_MS_CorrectReferralInput` in `core-1` - Correct referral input
  - `N_MS_CheckSuppliedDocuments` in `core-5` - Check the supplied documents

## core-1 - Referral receipt, clinical decision and new patient appointment

`core-1-referral-and-new-patient-appointment.bpmn`

13 user tasks in 4 lanes, binding 10 forms.

| Form | Task | Task name | Lane | Candidate group |
|---|---|---|---|---|
| `booking-request.form` | `N_OB_PrepareRequest` | Prepare booking request | Outpatient Bookings Team | `outpatient-bookings` |
|  | `N_OB_CorrectRequest` | Correct booking request | Outpatient Bookings Team | `outpatient-bookings` |
| `delay-review.form` | `N_PC_ReviewDelay` | Review the delay | Patient Pathway Coordinators | `pathway-coordinators` |
| `dispatch-failure.form` | `N_OB_RecordDispatchFailure` | Record dispatch failure | Outpatient Bookings Team | `outpatient-bookings` |
| `missing-information-request.form` | `N_MS_RequestMissing` | Request missing information | Medical Secretaries | `medical-secretaries` |
| `no-suitable-slot.form` | `N_OB_RecordNoSlot` | Record no suitable slot | Outpatient Bookings Team | `outpatient-bookings` |
| `patient-contact.form` | `N_OB_TelephonePatient` | Telephone the patient | Outpatient Bookings Team | `outpatient-bookings` |
|  | `N_OB_RecordAttempt` | Record and retry contact | Outpatient Bookings Team | `outpatient-bookings` |
| `referral-check.form` | `N_MS_CheckReferral` | Check referral and documents | Medical Secretaries | `medical-secretaries` |
|  | `N_MS_CorrectReferralInput` | Correct referral input | Medical Secretaries | `medical-secretaries` |
| `referral-exception-review.form` | `N_MS_ReviewException` | Review referral exception | Medical Secretaries | `medical-secretaries` |
| `referral-review.form` | `N_C_ClinicalReview` | Review the referral clinically | Consultants | `consultants` |
| `urgent-referral-escalation.form` | `N_PC_EscalateUrgent` | Escalate the urgent referral | Patient Pathway Coordinators | `pathway-coordinators` |

Deploy these with it:

```
forms/booking-request.form
forms/delay-review.form
forms/dispatch-failure.form
forms/missing-information-request.form
forms/no-suitable-slot.form
forms/patient-contact.form
forms/referral-check.form
forms/referral-exception-review.form
forms/referral-review.form
forms/urgent-referral-escalation.form
```

## core-2 - Treatment authorisation, funding determination and payment

`core-2-treatment-authorisation-funding-and-payment.bpmn`

18 user tasks in 4 lanes, binding 16 forms.

| Form | Task | Task name | Lane | Candidate group |
|---|---|---|---|---|
| `appointment-confirmation.form` | `N_TB_ConfirmAppointment` | Confirm treatment appointment | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `authorisation-return.form` | `N_TB_ReturnRequest` | Return request for authorisation | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `authorisation-verification.form` | `N_TB_VerifyAuthorisation` | Verify treatment authorisation | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `booking-pending.form` | `N_TB_KeepPending` | Keep booking pending | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `dispatch-failure.form` | `N_TB_RecordNotificationFailure` | Record notification failure | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `financial-impact-review.form` | `N_F_ReviewFinancialImpact` | Review the financial impact | Finance Team | `finance` |
| `funding-approval.form` | `N_F_RecordApproval` | Record funding approval | Finance Team | `finance` |
| `funding-route.form` | `N_F_DetermineFunding` | Determine funding route | Finance Team | `finance` |
| `patient-assessment.form` | `N_CL_AssessPatient` | Assess patient and treatment options | Consultant and clinical team | `consultants` |
| `payment.form` | `N_F_CalculateCharge` | Calculate or obtain the charge | Finance Team | `finance` |
|  | `N_F_CorrectPaymentRequest` | Correct the payment request | Finance Team | `finance` |
| `payment-investigation.form` | `N_F_InvestigatePayment` | Mark transaction for investigation | Finance Team | `finance` |
| `payment-notification.form` | `N_F_NotifyPatient` | Notify patient and booking team | Finance Team | `finance` |
| `pre-cycle-review.form` | `N_OC_PreCycleReview` | Review the patient and the blood test | Other Clinical Professionals | `clinical-professionals` |
| `treatment-booking.form` | `N_CL_AuthoriseTreatment` | Record consent and authorise request | Consultant and clinical team | `consultants` |
|  | `N_TB_CorrectBookingInput` | Correct booking input | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `treatment-modification.form` | `N_CL_ModifyTreatment` | Authorise treatment modification | Consultant and clinical team | `consultants` |
| `urgent-authorisation.form` | `N_CL_UrgentAuthorise` | Authorise urgent treatment | Consultant and clinical team | `consultants` |

Deploy these with it:

```
forms/appointment-confirmation.form
forms/authorisation-return.form
forms/authorisation-verification.form
forms/booking-pending.form
forms/dispatch-failure.form
forms/financial-impact-review.form
forms/funding-approval.form
forms/funding-route.form
forms/patient-assessment.form
forms/payment.form
forms/payment-investigation.form
forms/payment-notification.form
forms/pre-cycle-review.form
forms/treatment-booking.form
forms/treatment-modification.form
forms/urgent-authorisation.form
```

## core-3 - Clinic letter approval, distribution and escalation of delays

`core-3-clinic-letter-and-pathway-escalation.bpmn`

10 user tasks in 4 lanes, binding 8 forms.

| Form | Task | Task name | Lane | Candidate group |
|---|---|---|---|---|
| `clinic-letter.form` | `N_C_PrepareLetter` | Prepare the clinic letter | Consultants | `consultants` |
|  | `N_C_ApproveLetter` | Approve the clinical content | Consultants | `consultants` |
|  | `N_MS_ConfirmRecipients` | Confirm the recipients | Medical Secretaries | `medical-secretaries` |
| `clinical-error-review.form` | `N_C_ReviewClinicalError` | Review suspected clinical error | Consultants | `consultants` |
| `consultant-contact.form` | `N_AM_ContactConsultant` | Contact the responsible consultant | Administrative Management Team | `administrative-management` |
| `consultant-reminder.form` | `N_PC_IssueReminder` | Issue weekly reminder to consultant | Patient Pathway Coordinators | `pathway-coordinators` |
| `correspondence-monitoring.form` | `N_PC_MonitorCorrespondence` | Monitor outstanding letters | Patient Pathway Coordinators | `pathway-coordinators` |
| `dispatch-failure.form` | `N_MS_HandleDispatchFailure` | Handle dispatch failure | Medical Secretaries | `medical-secretaries` |
| `letter-processing.form` | `N_MS_ProcessLetter` | Process and check the letter | Medical Secretaries | `medical-secretaries` |
| `management-escalation.form` | `N_AM_EscalateHigher` | Escalate to higher management | Administrative Management Team | `administrative-management` |

Deploy these with it:

```
forms/clinic-letter.form
forms/clinical-error-review.form
forms/consultant-contact.form
forms/consultant-reminder.form
forms/correspondence-monitoring.form
forms/dispatch-failure.form
forms/letter-processing.form
forms/management-escalation.form
```

## core-4 - Follow-up, cancellation and non-attendance, enquiry handling and refund

`core-4-follow-up-cancellation-enquiry-and-refund.bpmn`

15 user tasks in 5 lanes, binding 12 forms.

| Form | Task | Task name | Lane | Candidate group |
|---|---|---|---|---|
| `appointment-confirmation.form` | `N_OB_ConfirmFollowUp` | Confirm the follow-up appointment | Outpatient Bookings Team | `outpatient-bookings` |
| `booking-request.form` | `N_OB_ArrangeFollowUp` | Arrange follow-up appointment | Outpatient Bookings Team | `outpatient-bookings` |
|  | `N_OB_CorrectRequest` | Correct the follow-up request | Outpatient Bookings Team | `outpatient-bookings` |
| `cancellation-record.form` | `N_OB_RecordCancellation` | Record cancellation or non-attendance | Outpatient Bookings Team | `outpatient-bookings` |
| `clinical-advice.form` | `N_CNS_ClinicalAdvice` | Provide clinical advice or support | Clinical Nurse Specialist Team | `clinical-nurse-specialists` |
| `dispatch-failure.form` | `N_OB_RecordDispatchFailure` | Record dispatch failure | Outpatient Bookings Team | `outpatient-bookings` |
| `enquiry-classification.form` | `N_CH_ClassifyEnquiry` | Record and classify the enquiry | Call Handling Team | `call-handling` |
| `enquiry-response.form` | `N_CH_AnswerAdmin` | Answer administrative enquiry | Call Handling Team | `call-handling` |
|  | `N_F_AnswerFinance` | Answer funding or payment enquiry | Finance Team | `finance` |
| `pathway-review.form` | `N_CNS_PathwayReview` | Review the patient pathway | Clinical Nurse Specialist Team | `clinical-nurse-specialists` |
| `referrer-notification.form` | `N_OB_NotifyReferrer` | Inform the referring organisation | Outpatient Bookings Team | `outpatient-bookings` |
| `refund.form` | `N_F_RetentionDecision` | Decide retention, transfer or refund | Finance Team | `finance` |
|  | `N_F_CorrectRefundRequest` | Correct the refund request | Finance Team | `finance` |
| `unbooked-case-review.form` | `N_PC_ReviewUnbooked` | Highlight and review the case | Patient Pathway Coordinators | `pathway-coordinators` |
| `urgent-escalation.form` | `N_CNS_HighlightUrgent` | Highlight and escalate urgently | Clinical Nurse Specialist Team | `clinical-nurse-specialists` |

Deploy these with it:

```
forms/appointment-confirmation.form
forms/booking-request.form
forms/cancellation-record.form
forms/clinical-advice.form
forms/dispatch-failure.form
forms/enquiry-classification.form
forms/enquiry-response.form
forms/pathway-review.form
forms/referrer-notification.form
forms/refund.form
forms/unbooked-case-review.form
forms/urgent-escalation.form
```

## core-5 - Missing information requested from, and supplied by, the referring organisation

`core-5-missing-information-message-exchange.bpmn`

2 user tasks in 1 lane, binding 2 forms.

| Form | Task | Task name | Lane | Candidate group |
|---|---|---|---|---|
| `missing-information-request.form` | `N_MS_RecordMissingItems` | Record the missing information | Medical Secretaries | `medical-secretaries` |
| `referral-check.form` | `N_MS_CheckSuppliedDocuments` | Check the supplied documents | Medical Secretaries | `medical-secretaries` |

Deploy these with it:

```
forms/missing-information-request.form
forms/referral-check.form
```
