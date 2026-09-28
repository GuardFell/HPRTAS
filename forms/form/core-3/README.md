# core-3 - the forms this model binds

| | |
|---|---|
| Model | `models/operational/core-3-clinic-letter-and-pathway-escalation.bpmn` |
| Process | `Core 3 - Clinic letter approval, distribution and escalation of delays` |
| User tasks | 10 |
| Forms in this folder | **8** |

Every form here is a copy of the file of the same name in the `forms/` directory this folder sits in; the copies exist so that one model's forms can be deployed as a bundle. **The files in `forms/` itself are the originals** - edit those, and the copies are regenerated from them, never the other way round.

| Form | Bound to | Lane | Candidate group |
|---|---|---|---|
| `clinic-letter.form` | Prepare the clinic letter (`N_C_PrepareLetter`) | Consultants | `consultants` |
|  | Approve the clinical content (`N_C_ApproveLetter`) | Consultants | `consultants` |
|  | Confirm the recipients (`N_MS_ConfirmRecipients`) | Medical Secretaries | `medical-secretaries` |
| `clinical-error-review.form` | Review suspected clinical error (`N_C_ReviewClinicalError`) | Consultants | `consultants` |
| `consultant-contact.form` | Contact the responsible consultant (`N_AM_ContactConsultant`) | Administrative Management Team | `administrative-management` |
| `consultant-reminder.form` | Issue weekly reminder to consultant (`N_PC_IssueReminder`) | Patient Pathway Coordinators | `pathway-coordinators` |
| `correspondence-monitoring.form` | Monitor outstanding letters (`N_PC_MonitorCorrespondence`) | Patient Pathway Coordinators | `pathway-coordinators` |
| `dispatch-failure.form` | Handle dispatch failure (`N_MS_HandleDispatchFailure`) | Medical Secretaries | `medical-secretaries` |
| `letter-processing.form` | Process and check the letter (`N_MS_ProcessLetter`) | Medical Secretaries | `medical-secretaries` |
| `management-escalation.form` | Escalate to higher management (`N_AM_EscalateHigher`) | Administrative Management Team | `administrative-management` |

## Also bound by another model

- **`dispatch-failure.form`** - also bound in core-1, core-2, core-4. The variable contract is the same form; what differs is the tasks that read it.

## Deploying this bundle

From the repository root - the model, then the forms it binds:

```bash
cd models/operational
curl.exe -X POST "http://localhost:8080/v2/deployments" -F "resources=@core-3-clinic-letter-and-pathway-escalation.bpmn;type=application/xml"
cd forms/form/core-3
for %f in (*.form) do curl.exe -X POST "http://localhost:8080/v2/deployments" -F "resources=@%f;type=application/json"
```

The forms are deployed by key, so they have to be deployed before the model reaches a task that resolves one; deploying them afterwards does not reach a task that already exists.
