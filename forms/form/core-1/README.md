# core-1 - the forms this model binds

| | |
|---|---|
| Model | `models/operational/core-1-referral-and-new-patient-appointment.bpmn` |
| Process | `Core 1 - Referral receipt, clinical decision and new patient appointment` |
| User tasks | 13 |
| Forms in this folder | **10** |

Every form here is a copy of the file of the same name in the `forms/` directory this folder sits in; the copies exist so that one model's forms can be deployed as a bundle. **The files in `forms/` itself are the originals** - edit those, and the copies are regenerated from them, never the other way round.

| Form | Bound to | Lane | Candidate group |
|---|---|---|---|
| `booking-request.form` | Prepare booking request (`N_OB_PrepareRequest`) | Outpatient Bookings Team | `outpatient-bookings` |
|  | Correct booking request (`N_OB_CorrectRequest`) | Outpatient Bookings Team | `outpatient-bookings` |
| `delay-review.form` | Review the delay (`N_PC_ReviewDelay`) | Patient Pathway Coordinators | `pathway-coordinators` |
| `dispatch-failure.form` | Record dispatch failure (`N_OB_RecordDispatchFailure`) | Outpatient Bookings Team | `outpatient-bookings` |
| `missing-information-request.form` | Request missing information (`N_MS_RequestMissing`) | Medical Secretaries | `medical-secretaries` |
| `no-suitable-slot.form` | Record no suitable slot (`N_OB_RecordNoSlot`) | Outpatient Bookings Team | `outpatient-bookings` |
| `patient-contact.form` | Telephone the patient (`N_OB_TelephonePatient`) | Outpatient Bookings Team | `outpatient-bookings` |
|  | Record and retry contact (`N_OB_RecordAttempt`) | Outpatient Bookings Team | `outpatient-bookings` |
| `referral-check.form` | Check referral and documents (`N_MS_CheckReferral`) | Medical Secretaries | `medical-secretaries` |
|  | Correct referral input (`N_MS_CorrectReferralInput`) | Medical Secretaries | `medical-secretaries` |
| `referral-exception-review.form` | Review referral exception (`N_MS_ReviewException`) | Medical Secretaries | `medical-secretaries` |
| `referral-review.form` | Review the referral clinically (`N_C_ClinicalReview`) | Consultants | `consultants` |
| `urgent-referral-escalation.form` | Escalate the urgent referral (`N_PC_EscalateUrgent`) | Patient Pathway Coordinators | `pathway-coordinators` |

## Also bound by another model

- **`booking-request.form`** - also bound in core-4. The variable contract is the same form; what differs is the tasks that read it.
- **`dispatch-failure.form`** - also bound in core-2, core-3, core-4. The variable contract is the same form; what differs is the tasks that read it.
- **`missing-information-request.form`** - also bound in core-5. The variable contract is the same form; what differs is the tasks that read it.
- **`referral-check.form`** - also bound in core-5. The variable contract is the same form; what differs is the tasks that read it.

## Deploying this bundle

From the repository root - the model, then the forms it binds:

```bash
cd models/operational
curl.exe -X POST "http://localhost:8080/v2/deployments" -F "resources=@core-1-referral-and-new-patient-appointment.bpmn;type=application/xml"
cd forms/form/core-1
for %f in (*.form) do curl.exe -X POST "http://localhost:8080/v2/deployments" -F "resources=@%f;type=application/json"
```

The forms are deployed by key, so they have to be deployed before the model reaches a task that resolves one; deploying them afterwards does not reach a task that already exists.
