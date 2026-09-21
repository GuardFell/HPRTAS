# Product Backlog

> **Agile Workshop, Part 3.** The full list of work required for the project: modelling,
> implementation, testing, review and evidence. Keep the list prioritised and the status current.
> Estimates are story points on the scale agreed in `docs/planning/project-plan.md`.
>
> **How this table is filled in.** Each row is recorded by the first owner of that item, who also
> sets the priority, the estimate and the acceptance conditions with the group in sprint planning.
> M5 has recorded the rows M5 owns or second-owns; the remaining rows are still blank because their
> owners have not recorded them, and `docs/planning/plan-evaluation.md` section 5.4 treats that as
> the plan risk it is rather than as an omission here. A blank row is not a row that was not done -
> it is a row that was not planned in writing, and several of them were delivered.

## Rules

- Avoid vague items: "Do BPMN", "Create system", "Do testing", "Make presentation".
- **First owner** leads delivery; **second owner** can continue if the first owner cannot.
- Evidence must be a path inside this repository, never "it is in the chat".
- Status: `Not started` / `In progress` / `Blocked` / `In review` / `Done` / `Dropped`.

| ID | Backlog Item | Description | Priority | Related Requirement | Estimate | First Owner | Second Owner | Dependencies | Acceptance Conditions | Evidence | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| PB-001 | Case study interpretation | Read the case study, answer the Part 1 discussion questions and record the shared understanding the rest of the work is built on. | Must | Case study, all sections | | | M5 | None | Every Part 1 question is answered; the analysis sections carry the group's answer rather than the case text; each business rule and exception has a stable ID. | `docs/case-study-summary.md` | Done |
| PB-002 | Requirements record | Turn the case study into a traceable requirements list: functional and non-functional requirements, each with a source, a type and where it is implemented and tested. | Must | Case study, all sections | | | M5 | PB-001 | Every requirement is traceable to a source and has a stable ID; the list names where each is implemented and where it is tested. | `docs/requirements/requirements.md` | In review - the list is complete and traceable, but two things testing found are still open in it: the worker source and tests cite the earlier ID allocation, and the FR-021 mapping is unconfirmed (`DEF-05`, `DEF-06` in `tests/test-plan.md`). |
| PB-003 | | | | | | | | | | | |
| PB-004 | | | | | | | | | | | |
| PB-005 | | | | | | | | | | | |
| PB-006 | | | | | | | | | | | |
| PB-007 | Write and execute the tests | Write the test plan with acceptance criteria and scenarios, and execute them, recording results against an identified version of the solution. | Must | All FR and NFR; BR-01 - BR-47 | 8 | M5 | | PB-003, PB-004, PB-005, PB-006 | Every acceptance criterion is measurable and linked to a requirement and a business rule; the main workflow, the alternatives, the exceptions and the relevant non-functional expectations are covered; every result names the version it was produced at; a failure is recorded as plainly as a pass. | `tests/test-plan.md`; `tests/evidence/` | In progress |
| PB-008 | Demonstration and plan maintenance | Keep the project plan current as the work proceeds, and prepare and give the demonstration of the delivered increment. | Must | N/A - process | 3 | | M5 | PB-003 - PB-006 | The plan is compared against actual progress and the variance is recorded rather than absorbed; the demonstration shows the flow that exists at an identified commit and labels anything simulated. | `docs/planning/project-plan.md` | In progress |
| PB-009 | | | | | | | | | | | |
| PB-010 | Alignment and plan evaluation | Evaluate the delivered solution against the requirements and the plan: requirement traceability across the models, the criteria the evaluation is made against, and the comparison of planned against actual progress. | Must | All FR and NFR | 8 | M5 | | PB-002, PB-007 | Every requirement the strategic model carries has a verdict - supported, partially supported or unsupported - with the gap named where there is one; requirements with no model element are accounted for; the plan is compared with what happened and the causes are stated. | `docs/planning/plan-evaluation.md` | Done |
| PB-011 | | | | | | | | | | | |
| PB-012 | | | | | | | | | | | |
| PB-013 | | | | | | | | | | | |
| PB-014 | | | | | | | | | | | |
| PB-015 | | | | | | | | | | | |
| PB-016 | | | | | | | | | | | |
| PB-019 | | | | | | | | | | | |
| PB-020 | | | | | | | | | | | |
| PB-021 | | | | | | | | | | | |
| PB-022 | | | | | | | | | | | |
| PB-023 | | | | | | | | | | | |
| PB-024 | | | | | | | | | | | |
