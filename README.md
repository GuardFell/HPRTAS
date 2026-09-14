# Hospital Patient Referral, Treatment and Administration System (HPRTAS)

> **Table frameworks only - the group fills in the content.**
> Do not submit anything the group cannot explain. Fill the tables as the project runs rather
> than at the deadline: review marks assess the increment available at that review.

The work comes from `Agile Workshop.docx` (Parts 1-8), the case study, and the assessment
specifications.

## Group members

| Name | Student ID | Primary role | Second-owner coverage for | Contribution evidence |
|---|---|---|---|---|
|  |  |  |  |  |
|  |  |  |  |  |
|  |  |  |  |  |
|  |  |  |  |  |
|  |  |  |  |  |

Every activity has a **first owner** (leads delivery) and a **second owner** (keeps sufficiently
up to date to continue if the first owner cannot).

## Key dates and assessed focus

| Date | Activity | Assessed focus | Evidence location |
|---|---|---|---|
| | Standup 1 (SU1) | Individual progress, next actions, blockers, ownership | `docs/agile/standups.md` |
| before 28 Sep 2026 | Sprint Review 1 (SR1) | Initial executable BPMN increment, configuration management evidence, project plan | `reviews/SR1/` |
| | Standup 2 (SU2) | Individual progress, follow-up actions, handover, initial workers and forms | `docs/agile/standups.md` |
| by 28 Sep 2026 | Sprint Review 2 (SR2) | Integrated workflow increment: operational model, external workers, forms | `reviews/SR2/` |
| 28 Sep 2026 | Initial release submission | Portfolio / coursework artefacts | identified repository version |
| | Standup 3 (SU3) | Individual progress, next actions, blockers, ownership | `docs/agile/standups.md` |
| before 22 Oct 2026 | Sprint Review 3 (SR3) | Prioritised feedback actions, implemented improvements, revised sprint plan | `reviews/SR3/` |
| | Standup 4 (SU4) | Individual progress, follow-up actions, handover | `docs/agile/standups.md` |
| by 22 Oct 2026 | Sprint Review 4 (SR4) | Validated improvements, feedback responses, planned vs actual progress | `reviews/SR4/` |
| 22 Oct 2026 | Presentation submission | Slides, updated solution, feedback record, planning evidence | `presentation/` |

## Repository structure

| Path | What goes here |
|---|---|
| `docs/case-study-summary.md` | Workshop Part 1: shared understanding of the case |
| `docs/requirements/` | Functional and non-functional requirements, business rules, traceability |
| `docs/deliverables.md` | Workshop Part 2: what the group must produce |
| `docs/backlog/` | Workshop Parts 3-6: product backlog, task breakdown, dependencies, sprint backlogs |
| `docs/planning/` | Scope, estimates, allocation, risks, timeline, planned vs actual |
| `docs/agile/` | Definition of done, standups, sprint reviews, retrospectives, contribution matrix |
| `docs/feedback/` | Feedback response record |
| `models/strategic/` | High-level business process models |
| `models/socio-technical/` | i\* Strategic Dependency (SD) and Strategic Rationale (SR) models |
| `models/operational/` | Executable operational BPMN models |
| `forms/` | Camunda Forms and their task bindings |
| `workers/` | External worker code, dependencies, configuration |
| `tests/` | Test plan and test evidence |
| `reviews/` | Snapshot of the increment at each sprint review |
| `presentation/` | Slides and supporting material |

## Running the project

| Item | Value |
|---|---|
| Camunda version | |
| JDK version | |
| Start the engine | |
| Deploy a model | |
| Start the workers | |
| Open the engine UI | |

## How we work

1. Every task has a first owner and a second owner.
2. Work is done only when it meets the definition of done (`docs/agile/definition-of-done.md`).
3. Integrate continuously. Commit messages name the item, e.g. `PB-012 add rejected referral path (TB-010)`.
