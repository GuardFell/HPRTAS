# Models

The BPMN models of the business process, as `.bpmn` files. Build them from a **Camunda 8** template
in Camunda Modeler; a model built from the Camunda 7 template will not deploy.

The models are arranged by the level they describe:

```
models/
|-- strategic/        the high-level business process across the organisation
|-- socio-technical/  i* Strategic Dependency (SD) and Strategic Rationale (SR) models
`-- operational/      the executable processes Camunda runs
```

Everything in `operational/` is meant to be deployed and executed. The strategic and
socio-technical models are views for analysis and are not deployed.

## Rules for a model

- Name elements meaningfully. `Task_1`, `Gateway_2` and `Process_3` say nothing to a reader.
- An operational process has to be executable and deployable, with its diagram information for
  every element it contains.
- Use a **Camunda user task**, not a *User task (legacy)*. Only a Camunda user task appears in
  Tasklist; a legacy task needs a job worker to move the process on and is invisible to staff.
- Write gateway conditions in **FEEL** (`= accepted`), not in the Camunda 7 style (`${...}`), which
  Camunda 8 does not evaluate.
- One process per file, and the file name matches the process.
- A process is tagged before a release, for example `release-1.0` or `release-2.0`.

## Deploying a model

```bash
curl -X POST "http://localhost:8080/v2/deployments" \
     -F "resources=@models/operational/<model>.bpmn;type=application/xml"
```

A model that binds Camunda Forms also needs those forms deployed, because the user tasks refer to
them by key.
