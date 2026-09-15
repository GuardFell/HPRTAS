# Hospital Patient Referral, Treatment and Administration System (HPRTAS)

> Group project repository for the hospital patient administration system. The content is filled
> in by the group as the project runs.

## Group members

| Name | Student ID | Primary role (role slot used in all owner columns) | Second-owner coverage for | Contribution evidence |
|---|---|---|---|---|
|  |  | M1 - Coordinator; operational and strategic BPMN modelling; deployment configuration | Worker integration; requirements | `models/`, `docs/agile/contribution-matrix.md` |
|  |  | M2 - Requirements and business rules; variable contract; i* SD and SR models | Financial and correspondence modelling; acceptance criteria | `docs/requirements/`, `models/socio-technical/`, `docs/agile/contribution-matrix.md` |
|  |  | M3 - External workers; simulated services; failure handling; configuration management | BPMN integration; forms variable exchange | `workers/`, `README.md`, `docs/agile/contribution-matrix.md` |
|  |  | M4 - Camunda Forms and task bindings; accessibility; demonstration | Testing; requirements | `forms/`, `docs/agile/contribution-matrix.md` |
|  |  | M5 - Test plan and execution; acceptance criteria; planned versus actual | Requirements; forms | `tests/`, `docs/planning/`, `docs/agile/contribution-matrix.md` |

Replace M1-M5 with the real names. Every activity has a **first owner** (leads delivery) and a
**second owner** (sufficiently up to date to continue if the first owner cannot).

## Key dates

| Date | Milestone | Version tag |
|---|---|---|
|  |  |  |
|  |  |  |
|  |  |  |
|  |  |  |
|  |  |  |
|  |  |  |
|  |  |  |

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

## Running the project

| Item | Value |
|---|---|
|  |  |
|  |  |
|  |  |
|  |  |
|  |  |
|  |  |
|  |  |

Start a process instance from Tasklist (Tasklist -> Processes), then complete the user tasks in
order: referral submission, clinical review, appointment contact recording, treatment
authorisation, funding and payment, clinic letter approval.

## How we work

1. Every task has a first owner and a second owner (`docs/backlog/task-breakdown.md`).
2. Work is done only when it meets the definition of done (`docs/agile/definition-of-done.md`).
3. Integrate continuously. Commit messages name the item, for example `PB-006 add rejected referral path (TB-010)`.
4. Releases are tagged `release-1.0` and `release-2.0`.
