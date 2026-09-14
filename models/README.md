# Models

BPMN models (`.bpmn`). Create them from a **Camunda 8** template in Camunda Modeler.

```
models/
|-- strategic/        high-level business process models
|-- socio-technical/  i* Strategic Dependency (SD) and Strategic Rationale (SR) models
`-- operational/      executable operational models
```

## Rules

- Name elements meaningfully - no `Task_1`, `Gateway_2`, `Process_3`
- Operational processes must be executable and deployable
- Use **Camunda user task** (not *User task (legacy)*) so tasks appear in Tasklist
- Use **FEEL** gateway conditions (`= approved`), not Camunda 7 style `${...}`
- One model per file; the file name should match the process

## Deploying

```bash
curl -X POST "http://localhost:8080/v2/deployments" \
     -F "resources=@models/operational/<model>.bpmn;type=application/xml"
```
