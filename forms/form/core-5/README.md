# core-5 - the forms this model binds

| | |
|---|---|
| Model | `models/operational/core-5-missing-information-message-exchange.bpmn` |
| Process | `Core 5 - Missing information requested from, and supplied by, the referring organisation` |
| User tasks | 2 |
| Forms in this folder | **2** |

Every form here is a copy of the file of the same name in the `forms/` directory this folder sits in; the copies exist so that one model's forms can be deployed as a bundle. **The files in `forms/` itself are the originals** - edit those, and the copies are regenerated from them, never the other way round.

| Form | Bound to | Lane | Candidate group |
|---|---|---|---|
| `missing-information-request.form` | Record the missing information (`N_MS_RecordMissingItems`) | Medical Secretaries | `medical-secretaries` |
| `referral-check.form` | Check the supplied documents (`N_MS_CheckSuppliedDocuments`) | Medical Secretaries | `medical-secretaries` |

## Also bound by another model

- **`missing-information-request.form`** - also bound in core-1. The variable contract is the same form; what differs is the tasks that read it.
- **`referral-check.form`** - also bound in core-1. The variable contract is the same form; what differs is the tasks that read it.

## Deploying this bundle

From the repository root - the model, then the forms it binds:

```bash
cd models/operational
curl.exe -X POST "http://localhost:8080/v2/deployments" -F "resources=@core-5-missing-information-message-exchange.bpmn;type=application/xml"
cd forms/form/core-5
for %f in (*.form) do curl.exe -X POST "http://localhost:8080/v2/deployments" -F "resources=@%f;type=application/json"
```

The forms are deployed by key, so they have to be deployed before the model reaches a task that resolves one; deploying them afterwards does not reach a task that already exists.
