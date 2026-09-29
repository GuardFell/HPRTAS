# core-4 - the forms this model binds

| | |
|---|---|
| Model | `models/operational/core-4-follow-up-cancellation-enquiry-and-refund.bpmn` |
| Process | `Core 4 - Follow-up, cancellation and non-attendance, enquiry handling and refund` |
| User tasks | 15 |
| Forms in this folder | **12** |

Every form here is a copy of the file of the same name in the `forms/` directory this folder sits in; the copies exist so that one model's forms can be deployed as a bundle. **The files in `forms/` itself are the originals** - edit those, and the copies are regenerated from them, never the other way round.

| Form | Bound to | Lane | Candidate group |
|---|---|---|---|
| `appointment-confirmation.form` | Confirm the follow-up appointment (`N_OB_ConfirmFollowUp`) | Outpatient Bookings Team | `outpatient-bookings` |
| `booking-request.form` | Arrange follow-up appointment (`N_OB_ArrangeFollowUp`) | Outpatient Bookings Team | `outpatient-bookings` |
|  | Correct the follow-up request (`N_OB_CorrectRequest`) | Outpatient Bookings Team | `outpatient-bookings` |
| `cancellation-record.form` | Record cancellation or non-attendance (`N_OB_RecordCancellation`) | Outpatient Bookings Team | `outpatient-bookings` |
| `clinical-advice.form` | Provide clinical advice or support (`N_CNS_ClinicalAdvice`) | Clinical Nurse Specialist Team | `clinical-nurse-specialists` |
| `dispatch-failure.form` | Record dispatch failure (`N_OB_RecordDispatchFailure`) | Outpatient Bookings Team | `outpatient-bookings` |
| `enquiry-classification.form` | Record and classify the enquiry (`N_CH_ClassifyEnquiry`) | Call Handling Team | `call-handling` |
| `enquiry-response.form` | Answer administrative enquiry (`N_CH_AnswerAdmin`) | Call Handling Team | `call-handling` |
|  | Answer funding or payment enquiry (`N_F_AnswerFinance`) | Finance Team | `finance` |
| `pathway-review.form` | Review the patient pathway (`N_CNS_PathwayReview`) | Clinical Nurse Specialist Team | `clinical-nurse-specialists` |
| `referrer-notification.form` | Inform the referring organisation (`N_OB_NotifyReferrer`) | Outpatient Bookings Team | `outpatient-bookings` |
| `refund.form` | Decide retention, transfer or refund (`N_F_RetentionDecision`) | Finance Team | `finance` |
|  | Correct the refund request (`N_F_CorrectRefundRequest`) | Finance Team | `finance` |
| `unbooked-case-review.form` | Highlight and review the case (`N_PC_ReviewUnbooked`) | Patient Pathway Coordinators | `pathway-coordinators` |
| `urgent-escalation.form` | Highlight and escalate urgently (`N_CNS_HighlightUrgent`) | Clinical Nurse Specialist Team | `clinical-nurse-specialists` |

## Also bound by another model

- **`appointment-confirmation.form`** - also bound in core-2. The variable contract is the same form; what differs is the tasks that read it.
- **`booking-request.form`** - also bound in core-1. The variable contract is the same form; what differs is the tasks that read it.
- **`dispatch-failure.form`** - also bound in core-1, core-2, core-3. The variable contract is the same form; what differs is the tasks that read it.

## Deploying this bundle

From the repository root - the model, then the forms it binds:

```bash
cd models/operational
curl.exe -X POST "http://localhost:8080/v2/deployments" -F "resources=@core-4-follow-up-cancellation-enquiry-and-refund.bpmn;type=application/xml"
cd forms/form/core-4
for %f in (*.form) do curl.exe -X POST "http://localhost:8080/v2/deployments" -F "resources=@%f;type=application/json"
```

The forms are deployed by key, so they have to be deployed before the model reaches a task that resolves one; deploying them afterwards does not reach a task that already exists.
