# Product Backlog

> **Agile Workshop, Part 3.** The full list of work required for the project: modelling,
> implementation, testing, review and evidence. Keep the list prioritised and the status current.
> Estimates are story points on the scale agreed in `docs/planning/project-plan.md`.
>
> **How this table is filled in.** Each row is recorded by the first owner of that item, who also
> sets the priority, the estimate and the acceptance conditions with the group in sprint planning.
> The rows below are the items in the group's project plan and product backlog document, which is
> the backlog of record: the identifiers, the priorities, the estimates, the owners, the
> dependencies and the acceptance conditions are the group's. Where a row was already recorded here
> with its own wording or its own estimate, that record is kept rather than overwritten, and the
> columns it left blank are filled from the plan. Where the two disagree the difference is listed in
> `sprint-backlogs.md` under "Differences from the plan document" instead of being resolved quietly.

## Rules

- Avoid vague items: "Do BPMN", "Create system", "Do testing", "Make presentation".
- **First owner** leads delivery; **second owner** can continue if the first owner cannot.
- Evidence must be a path inside this repository, never "it is in the chat".
- Status: `Not started` / `In progress` / `Blocked` / `In review` / `Done` / `Dropped`.

| ID | Backlog Item | Description | Priority | Related Requirement | Estimate | First Owner | Second Owner | Dependencies | Acceptance Conditions | Evidence | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| PB-001 | Agree the case study interpretation | Read the case study, answer the Part 1 discussion questions and record the shared understanding the rest of the work is built on. | Must | Case study, all sections | 5 | M2 | M5 | None | Every Part 1 question is answered; the analysis sections carry the group's answer rather than the case text; each business rule and exception has a stable ID; and it is agreed by all five members. | `docs/case-study-summary.md` | Done |
| PB-002 | Record the requirements | Turn the case study into a traceable requirements list: functional and non-functional requirements, each with a source, a type and where it is implemented and tested. | Must | Case study, all sections | 8 | M2 | M5 | PB-001 | Every requirement is traceable to a source and has a stable ID; the list names where each is implemented and where it is tested. | `docs/requirements/requirements.md` | In review - the list is complete and traceable, but two things testing found are still open in it: the worker source and tests cite the earlier ID allocation, and the FR-021 mapping is unconfirmed (`DEF-05`, `DEF-06` in `tests/test-plan.md`). |
| PB-003 | Model the operational process | Executable BPMN from referral to follow-up, with the acceptance gate. | Must | FR-001 - FR-044 | 13 | M1 | M3 | PB-002 | Deploys without error; the normal, alternative and exception paths execute. | `models/operational/` | Done |
| PB-004 | Model the exception paths | Rejection, missing information, no availability, contact failure, payment, non-attendance. | Must | FR-011, FR-022 - FR-030 | 8 | M4 | M1 | PB-003 | Each exception is modelled or excluded with a recorded reason. | `models/operational/`; the defects in `tests/test-plan.md` section 6 | Done |
| PB-005 | Implement the external workers | Workers for the automated activities, with validation and failure handling. | Must | FR-007, FR-016, FR-019 - FR-025 | 13 | M3 | M1 | PB-003 | Workers take variables and return results; failures are handled. | `workers/`; `tests/evidence/` | Done |
| PB-006 | Create the Camunda Forms | Forms for referral, clinical review and funding or payment. | Must | FR-002, FR-004, FR-018 | 8 | M4 | M2 | PB-002, PB-003 | Bound to the correct tasks with validation; the variables are exchanged correctly. | `forms/` | Done - 40 forms cover all 55 user tasks, closing `DEF-13` at `a62e783`. The eight forms delivered earlier turned out never to have rendered a field: a group's children belong under `components`, and a group's `path` prefixed every child key. Both faults, and the two others found with them, are recorded in `forms/README.md`. |
| PB-007 | Write and execute the tests | Write the test plan with acceptance criteria and scenarios, and execute them, recording results against an identified version of the solution. | Must | All FR and NFR; BR-01 - BR-47 | 8 | M5 | M3 | PB-002, PB-003, PB-004, PB-005, PB-006 | Every acceptance criterion is measurable and linked to a requirement and a business rule; the main workflow, the alternatives, the exceptions and the relevant non-functional expectations are covered; every result names the version it was produced at; a failure is recorded as plainly as a pass. | `tests/test-plan.md`; `tests/evidence/` | In progress - the plan, its ten criteria and its twenty-one scenarios are complete, and 17 of the 21 have a recorded result; the four that do not, and the three criteria they affect, are why the item is not closed. |
| PB-008 | Demonstration and plan maintenance | Keep the project plan current as the work proceeds, and prepare and give the demonstration of the delivered increment. | Must | N/A - process | 3 | M4 | M5 | PB-003 - PB-006 | The plan is compared against actual progress and the variance is recorded rather than absorbed; the demonstration shows the flow that exists at an identified commit and labels anything simulated. | `docs/planning/project-plan.md` | In progress - the plan, the backlog, the sprint backlogs and the evaluation are recorded at `release-1.0`; the demonstration has not been given and no script or slide deck is in the repository yet. |
| PB-009 | Model the strategic process and the i\* models | Strategic BPMN plus the i\* SD and SR models, with the abstraction level explained. | Must | NFR-012, AM-01 relate; no single FR | 13 | M2 | M1 | PB-001 | Roles and external interactions are shown; the SD and SR models are consistent with each other. | `models/strategic/`, `models/socio-technical/` | In progress - the strategic BPMN is delivered and exported; `models/socio-technical/` holds no model yet, and the i\* half is deferred to the second release by `docs/planning/project-plan.md` section 1. |
| PB-010 | Evaluate alignment and the plan | Evaluate the delivered solution against the requirements and the plan: requirement traceability across the models, the criteria the evaluation is made against, and the comparison of planned against actual progress. | Must | All FR and NFR | 8 | M5 | M2 | PB-002, PB-007 | Every requirement the strategic model carries has a verdict - supported, partially supported or unsupported - with the gap named where there is one; requirements with no model element are accounted for; the plan is compared with what happened and the causes are stated. | `docs/planning/plan-evaluation.md` | Done - made against `c8556ba`, and it is the document that found `DEF-11`, `DEF-12` and `DEF-13`; it has to be re-run against the second release. |
| PB-011 | Prepare the enterprise architecture artefact | Description of the enterprise and its information systems, with the Zachman Framework. | Must | - | 8 | M2 | M1 | PB-001 | Description of 150-300 words; framework cells populated for the agreed scope. | `Enterprise Architecture/` | Done |

Items numbered after `PB-011` were placeholder rows and have been removed: the backlog of record
defines eleven items, and all eleven are in the table above. A new item takes the next free
identifier rather than filling a reserved row.
