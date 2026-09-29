# core-2 - the forms this model binds

| | |
|---|---|
| Model | `models/operational/core-2-treatment-authorisation-funding-and-payment.bpmn` |
| Process | `Core 2 - Treatment authorisation, funding determination and payment` |
| User tasks | 18 |
| Forms in this folder | **16** |

Every form here is a copy of the file of the same name in the `forms/` directory this folder sits in; the copies exist so that one model's forms can be deployed as a bundle. **The files in `forms/` itself are the originals** - edit those, and the copies are regenerated from them, never the other way round.

| Form | Bound to | Lane | Candidate group |
|---|---|---|---|
| `appointment-confirmation.form` | Confirm treatment appointment (`N_TB_ConfirmAppointment`) | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `authorisation-return.form` | Return request for authorisation (`N_TB_ReturnRequest`) | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `authorisation-verification.form` | Verify treatment authorisation (`N_TB_VerifyAuthorisation`) | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `booking-pending.form` | Keep booking pending (`N_TB_KeepPending`) | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `dispatch-failure.form` | Record notification failure (`N_TB_RecordNotificationFailure`) | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `financial-impact-review.form` | Review the financial impact (`N_F_ReviewFinancialImpact`) | Finance Team | `finance` |
| `funding-approval.form` | Record funding approval (`N_F_RecordApproval`) | Finance Team | `finance` |
| `funding-route.form` | Determine funding route (`N_F_DetermineFunding`) | Finance Team | `finance` |
| `patient-assessment.form` | Assess patient and treatment options (`N_CL_AssessPatient`) | Consultant and clinical team | `consultants` |
| `payment.form` | Calculate or obtain the charge (`N_F_CalculateCharge`) | Finance Team | `finance` |
|  | Correct the payment request (`N_F_CorrectPaymentRequest`) | Finance Team | `finance` |
| `payment-investigation.form` | Mark transaction for investigation (`N_F_InvestigatePayment`) | Finance Team | `finance` |
| `payment-notification.form` | Notify patient and booking team (`N_F_NotifyPatient`) | Finance Team | `finance` |
| `pre-cycle-review.form` | Review the patient and the blood test (`N_OC_PreCycleReview`) | Other Clinical Professionals | `clinical-professionals` |
| `treatment-booking.form` | Record consent and authorise request (`N_CL_AuthoriseTreatment`) | Consultant and clinical team | `consultants` |
|  | Correct booking input (`N_TB_CorrectBookingInput`) | Treatment and Chemotherapy Bookings Team | `treatment-bookings` |
| `treatment-modification.form` | Authorise treatment modification (`N_CL_ModifyTreatment`) | Consultant and clinical team | `consultants` |
| `urgent-authorisation.form` | Authorise urgent treatment (`N_CL_UrgentAuthorise`) | Consultant and clinical team | `consultants` |

## Also bound by another model

- **`appointment-confirmation.form`** - also bound in core-4. The variable contract is the same form; what differs is the tasks that read it.
- **`dispatch-failure.form`** - also bound in core-1, core-3, core-4. The variable contract is the same form; what differs is the tasks that read it.

## Deploying this bundle

From the repository root - the model, then the forms it binds:

```bash
cd models/operational
curl.exe -X POST "http://localhost:8080/v2/deployments" -F "resources=@core-2-treatment-authorisation-funding-and-payment.bpmn;type=application/xml"
cd forms/form/core-2
for %f in (*.form) do curl.exe -X POST "http://localhost:8080/v2/deployments" -F "resources=@%f;type=application/json"
```

The forms are deployed by key, so they have to be deployed before the model reaches a task that resolves one; deploying them afterwards does not reach a task that already exists.
