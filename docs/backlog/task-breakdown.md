# Task Breakdown

> **Agile Workshop, Part 4.** Each backlog item broken into manageable tasks. A task is small
> enough to complete within one sprint. Every task has a first and a second owner.
>
> **How this table is filled in.** The tasks below are the ones selected into Sprints 1 and 2 in the
> group's project plan and product backlog document, which is the plan of record: the descriptions,
> the owners, the estimates and the acceptance conditions are the group's. The **Backlog ID** column
> is the item each task serves, read from the evidence the task produces and the backlog item that
> names it. The **Dependency** column names what has to be done first, from the dependencies the
> product backlog records and the order the task descriptions imply; `dependencies.md` carries the
> same relationships at item level. Status is the one this repository's evidence supports, and where
> it differs from the plan document the difference is listed in `sprint-backlogs.md`.

## Task size (from the workshop)

| Avoid | Use instead |
|---|---|
| "Do BPMN" | Identify process participants; Draft main BPMN flow; Add exception path for rejected referral |
| "Create system" | Create Camunda Form for referral submission; Implement external worker for validation |
| "Do testing" | Test normal workflow path |
| "Make presentation" | Record a decision and the reason for it |

| Task ID | Backlog ID | Task Description | First Owner | Second Owner | Estimate | Dependency | Acceptance Conditions | Evidence Required | Status |
|---|---|---|---|---|---|---|---|---|---|
| TB-001 | PB-001 | Team and participant list: record the named teams and their stated responsibilities, so that the process participants and the boundaries between them are agreed against the case study rather than assumed. | M2 | M5 | 2 | None | Table completed and agreed. | `docs/case-study-summary.md` section 2 | Done |
| TB-002 | PB-001 | Business rules: extract the stated rules into a numbered table with stable identifiers, so that a requirement or a test can cite the rule it demonstrates. | M2 | M1 | 5 | TB-001 | Every stated rule captured. | `docs/case-study-summary.md` section 5 (`BR-01` - `BR-47`) | Done |
| TB-003 | PB-001 | Exceptions: extract the exceptions and alternative paths the case describes, so that the model and the test plan can account for each one. | M4 | M2 | 3 | TB-001 | Every described exception captured. | `docs/case-study-summary.md` section 6 (`EX-01` - `EX-23`) | Done |
| TB-004 | PB-003 | Participants, lanes and pools: identify the participants and the boundaries between them. | M1 | M3 | 3 | TB-002, TB-003 | Boundaries agreed against the clinical, administrative and financial separation the case requires. | `models/operational/` | Done |
| TB-005 | PB-003 | Main BPMN flow: draft the referral-to-new-patient-appointment pathway end to end. | M1 | M3 | 8 | TB-004 | Modelled end to end, including the Consultant acceptance gate. | `models/operational/` | Done |
| TB-006 | PB-004 | Exception and alternative paths: add the rejection, missing-information, no-availability, contact-failure, payment and non-attendance paths. | M4 | M1 | 8 | TB-005 | Each exception is modelled or excluded with a recorded reason. | `models/operational/`; `tests/test-plan.md` section 6 (`DEF-01`, `DEF-02`) | Done |
| TB-007 | PB-003 | Deployment configuration: configure the model so it can be deployed and run. | M3 | M1 | 3 | TB-005 | Deploys to Camunda 8 Run without error. | The deployment output in `tests/evidence/operational-models_end-to-end_a7f0dd6_2026-09-21.txt` | Done |
| TB-008 | PB-003 | Pools, lanes and message flows: model the named teams as lanes and the exchanges between them as message flows. | M1 | M3 | 5 | TB-005 | Named teams appear as lanes; message flow crosses pools. | `models/operational/` | Done |
| TB-009 | PB-005 | Worker bindings: bind the external workers to the service tasks that call them. | M3 | M1 | 5 | TB-008 | Job types match the configuration; an instance completes. | `workers/`; `models/operational/` | Done |
| TB-010 | PB-003 | Deploy the operational model. | M3 | M1 | 3 | TB-009 | Deploys to Camunda 8 Run without error. | `tests/evidence/` | Done |
| TB-011 | PB-006 | Camunda Forms and task bindings: create the forms and bind them to their user tasks. | M4 | M2 | 8 | TB-008 | Bound, validated and exchanging the agreed variables. | `forms/`; `tests/test-plan.md` section 6 (`DEF-13`) | Done |
| TB-012 | PB-001 | Case study summary: complete the Part 1 answers from the case study text as a shared understanding for the group to confirm. | M2 | M5 | 8 | TB-001, TB-002, TB-003 | Every Part 1 discussion question is answered; the analysis sections state the group's answer and not the case text; any addition beyond the case is recorded as an assumption. | `docs/case-study-summary.md` | Done |
| TB-013 | PB-002 | Requirement traceability table: record each requirement against the element of the strategic and operational models that carries it, the implementation behind it and the test that demonstrates it. | M2 | M5 | 8 | TB-012 | Every requirement the strategic model carries has a row naming a strategic element, an operational element, an implementation and a test; the status column records whether it is supported once the evaluation is made. | `docs/requirements/requirements.md`; the evaluation in `docs/planning/plan-evaluation.md` section 3 | Done |
| TB-014 | PB-007 | Test plan criteria and scenarios: write measurable acceptance criteria linked to a requirement and a business rule, and the scenarios that demonstrate them, covering the main workflow, the alternatives and the exceptions. | M5 | M3 | 5 | TB-013 | Every criterion is measurable and linked to a requirement and a business rule; each scenario states its preconditions, data, actions, expected outcome and pass/fail condition; the test-case numbers match the ones the worker tests cite. | `tests/test-plan.md` sections 3 and 4 | Done |
| TB-015 | PB-007 | Run the scenarios and record the evidence: execute the scenarios at an identified version and record the result of each, stating what the run does not cover. | M5 | M3 | 5 | TB-014 | Every result names the version it was produced at; a failure or a partial result is recorded as plainly as a pass; each evidence file states its method, its limitations and how to reproduce it. | `tests/evidence/`; `tests/evidence/README.md` | In progress - 17 of 21 scenarios run, 4 never run, 3 defects open |
| TB-016 | PB-008 | Agree the plan, the estimation scale, the definition of done and the acceptance criteria, with M4. | M5 | M4 | 5 | None | The estimation scale is written down and referenced by the backlog; the definition of done covers the project-wide conditions and each artefact type; the acceptance criteria are agreed with M4 as the reciprocal second owner. | `docs/planning/project-plan.md` section 2; `docs/agile/definition-of-done.md`; `tests/test-plan.md` section 3 | Done |

Two tasks are recorded against the plan rather than in it and are worth naming here. `TB-006`
covers the exception paths that `DEF-01` and `DEF-02` were found in, and both were fixed at
`e852224` and re-tested; the fixes and the defects they close are what the Evidence cell points at.
`TB-011` delivered the eight forms, and the Evidence cell names the limitation rather than leaving
it to be discovered: 36 of the 55 user tasks bind no form (`DEF-13`), which is the largest open
alignment finding in the project.

The tasks selected into Sprints 3 and 4 are not broken down yet, so they have no `TB-###`
identifier. They are named with the backlog item each serves in `sprint-backlogs.md`, which is where
the next identifiers are assigned at sprint planning. `TB-017` onwards is free.
