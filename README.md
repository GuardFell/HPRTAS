# Hospital Patient Referral, Treatment and Administration System (HPRTAS)

> Group project repository for the hospital patient administration system. The content is filled
> in by the group as the project runs.

## Group members

| Name | Student ID | Primary role (role slot used in all owner columns) | Second-owner coverage for | Contribution evidence |
|---|---|---|---|---|
| | | M1 - Coordinator; operational and strategic BPMN modelling; deployment configuration | Worker integration; requirements | `models/`, `docs/agile/contribution-matrix.md` |
| | | M2 - Requirements and business rules; variable contract; i* SD and SR models | Financial and correspondence modelling; acceptance criteria | `docs/requirements/`, `models/socio-technical/`, `docs/agile/contribution-matrix.md` |
| | | M3 - External workers; simulated services; failure handling; configuration management | BPMN integration; forms variable exchange | `workers/`, `README.md`, `docs/agile/contribution-matrix.md` |
| | | M4 - Camunda Forms and task bindings; accessibility; demonstration | Testing; requirements | `forms/`, `docs/agile/contribution-matrix.md` |
| | | M5 - Test plan and execution; acceptance criteria; planned versus actual | Requirements; forms | `tests/`, `docs/planning/`, `docs/agile/contribution-matrix.md` |

Replace M1-M5 with the real names. Every activity has a **first owner** (leads delivery) and a
**second owner** (sufficiently up to date to continue if the first owner cannot).

## Key dates

| Date | Milestone | Version tag |
|---|---|---|
| 15 Sep 2026 | Sprint 1 starts | |
| 15 - 20 Sep 2026 | Sprint 1 | |
| 21 - 27 Sep 2026 | Sprint 2 | |
| 28 Sep 2026 | Initial release | `release-1.0` |
| 29 Sep - 15 Oct 2026 | Sprint 3 | |
| 16 - 21 Oct 2026 | Sprint 4 | |
| 22 Oct 2026 | Second release | `release-2.0` |

Sprint goals and sprint dates are recorded in `docs/backlog/sprint-backlogs.md`.

## Repository structure

| Path | What goes here |
|---|---|
| `docs/case-study-summary.md` | Shared understanding of the case: participants, process, rules, exceptions, assumptions |
| `docs/requirements/` | Functional and non-functional requirements, business rules, traceability |
| `docs/deliverables.md` | What the group must produce, with owners and dates |
| `docs/backlog/` | Product backlog, task breakdown, dependencies, sprint backlogs |
| `docs/planning/` | Scope, estimates, allocation, risks, timeline, planned vs actual |
| `docs/agile/` | Definition of done, contribution matrix |
| `models/strategic/` | High-level business process models |
| `models/socio-technical/` | i\* Strategic Dependency (SD) and Strategic Rationale (SR) models |
| `models/operational/` | Executable operational BPMN models |
| `forms/` | Camunda Forms and their task bindings |
| `workers/` | External worker code, dependencies, configuration |
| `tests/` | Test plan and test evidence |
| `Enterprise Architecture/` | Portfolio deliverable: description of the enterprise and its information systems, and the Zachman Framework of Information Systems Architecture |

## Running the project

| Item | Value |
|---|---|
| Camunda version | Camunda 8 Run 8.9.19 (Orchestration Cluster API, `/v2/`) |
| JDK version | Java 21 (Temurin 21.0.12.1 LTS, in `camunda-runtime\jdk-21`); JDK 24 or newer is not supported |
| Start the engine | `camunda-runtime\start-camunda.bat` (first start takes about 60 seconds); stop with `camunda-runtime\stop-camunda.bat` |
| Modeler | `camunda-runtime\camunda-modeler\Camunda Modeler.exe` (launch the engine first so the connection is detected) |
| Deploy a model | `curl -X POST "http://localhost:8080/v2/deployments" -F "resources=@models/operational/referral-to-appointment.bpmn;type=application/xml"` - repeat for `treatment-authorisation-and-booking.bpmn` and `clinic-letter-and-pathway-monitoring.bpmn` |
| Start the workers | `cd workers && npm install && npm start` (Node.js 22 with the Camunda 8 Node SDK); copy `workers/.env.example` to `workers/.env` first |
| Check or test the workers | `cd workers && npm run check` (configuration and wiring, no engine needed), `npm test` (unit tests, no engine needed), `npm run test:smoke` (both scenarios against the running engine). See `workers/README.md` for the job types, variables, error codes and the simulated services |
| Open the engine UI | http://localhost:8080/operate and http://localhost:8080/tasklist (login `demo` / `demo`) |

The three operational models are separate processes, so they are deployed and started separately
from Tasklist (Tasklist -> Processes): `referral-to-appointment`,
`treatment-authorisation-and-booking` and `clinic-letter-and-pathway-monitoring`. Complete the user
tasks on each instance as it reaches them; the tasks are assigned to candidate groups that match the
lanes. The strategic model (`models/strategic/referral-to-treatment-pathway.bpmn`) is a
non-executable view: do not deploy it, Camunda rejects a deployment that contains no executable
process (`INVALID_ARGUMENT: Must contain at least one executable process`).

## How we work

1. Every task has a first owner and a second owner (`docs/backlog/task-breakdown.md`).
2. Work is done only when it meets the definition of done (`docs/agile/definition-of-done.md`).
3. Integrate continuously. Commit messages name the item, for example `PB-006 add rejected referral path (TB-010)`.
4. Releases are tagged `release-1.0` and `release-2.0`.
