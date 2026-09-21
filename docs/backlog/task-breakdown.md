# Task Breakdown

> **Agile Workshop, Part 4.** Each backlog item broken into manageable tasks. A task is small
> enough to complete within one sprint. Every task has a first and a second owner.
>
> **How this table is filled in.** Each row is recorded by the first owner of that task, with the
> estimate set on the scale in `docs/planning/project-plan.md`. M5 has recorded the rows M5 owns or
> second-owns. The rows that are still blank are those whose owners have not recorded them yet:
> several of the tasks behind them were delivered - the models, the forms and the workers all exist
> with evidence - but they were never written down as tasks, which is the plan variance reported in
> `docs/planning/plan-evaluation.md` section 5.4.

## Task size (from the workshop)

| Avoid | Use instead |
|---|---|
| "Do BPMN" | Identify process participants; Draft main BPMN flow; Add exception path for rejected referral |
| "Create system" | Create Camunda Form for referral submission; Implement external worker for validation |
| "Do testing" | Test normal workflow path |
| "Make presentation" | Record a decision and the reason for it |

| Task ID | Backlog ID | Task Description | First Owner | Second Owner | Estimate | Dependency | Acceptance Conditions | Evidence Required | Status |
|---|---|---|---|---|---|---|---|---|---|
| TB-001 | - | Team responsibility table: record who is responsible for what, so that every task has a first and a second owner and no task has neither. | | M5 | 2 | None | Every member's primary responsibility and their second-owner coverage is recorded; every task in this table has both owners named. | This table, and `docs/planning/project-plan.md` section 3 | In progress |
| TB-002 | | | | | | | | | |
| TB-003 | | | | | | | | | |
| TB-004 | | | | | | | | | |
| TB-005 | | | | | | | | | |
| TB-006 | | | | | | | | | |
| TB-007 | | | | | | | | | |
| TB-008 | | | | | | | | | |
| TB-009 | | | | | | | | | |
| TB-010 | | | | | | | | | |
| TB-011 | | | | | | | | | |
| TB-012 | PB-001 | Case study summary: complete the Part 1 answers from the case study text as a shared understanding for the group to confirm. | | M5 | 8 | None | Every Part 1 discussion question is answered; the analysis sections state the group's answer and not the case text; any addition beyond the case is recorded as an assumption. | `docs/case-study-summary.md` | Done |
| TB-013 | PB-002 | Requirement traceability table: record each requirement against the element of the strategic and operational models that carries it, the implementation behind it and the test that demonstrates it. | | M5 | 8 | TB-012 | Every requirement the strategic model carries has a row naming a strategic element, an operational element, an implementation and a test; the status column records whether it is supported once the evaluation is made. | `docs/requirements/requirements.md`; the evaluation in `docs/planning/plan-evaluation.md` section 3 | Done |
| TB-014 | PB-007 | Complete the test plan's acceptance criteria and scenarios: write measurable acceptance criteria linked to a requirement and a business rule, and the scenarios that demonstrate them, covering the main workflow, the alternatives and the exceptions. | M5 | | 5 | TB-013 | Every criterion is measurable and linked to a requirement and a business rule; each scenario states its preconditions, data, actions, expected outcome and pass/fail condition; the test-case numbers match the ones the worker tests cite. | `tests/test-plan.md` sections 3 and 4 | Done |
| TB-015 | PB-007 | Run the scenarios and record the evidence: execute the scenarios at an identified version and record the result of each, stating what the run does not cover. | M5 | | 5 | TB-014 | Every result names the version it was produced at; a failure or a partial result is recorded as plainly as a pass; each evidence file states its method, its limitations and how to reproduce it. | `tests/evidence/`; `tests/evidence/README.md` | In progress - 17 of 21 scenarios run, 4 never run, 3 defects open |
| TB-016 | PB-007 | Agree the plan, the estimation scale, the definition of done and the acceptance criteria, with M4. | M5 | M4 | 5 | None | The estimation scale is written down and referenced by the backlog; the definition of done covers the project-wide conditions and each artefact type; the acceptance criteria are agreed with M4 as the reciprocal second owner. | `docs/planning/project-plan.md` section 2; `docs/agile/definition-of-done.md`; `tests/test-plan.md` section 3 | Done |
| TB-017 | | | | | | | | | |
| TB-018 | | | | | | | | | |
| TB-019 | | | | | | | | | |
| TB-020 | | | | | | | | | |
| TB-021 | | | | | | | | | |
| TB-022 | | | | | | | | | |
| TB-023 | | | | | | | | | |
| TB-024 | | | | | | | | | |
| TB-025 | | | | | | | | | |
| TB-026 | | | | | | | | | |
| TB-027 | | | | | | | | | |
| TB-028 | | | | | | | | | |
| TB-031 | | | | | | | | | |
| TB-032 | | | | | | | | | |
| TB-033 | | | | | | | | | |
| TB-034 | | | | | | | | | |
| TB-035 | | | | | | | | | |
| TB-036 | | | | | | | | | |
| TB-037 | | | | | | | | | |
| TB-038 | | | | | | | | | |
| TB-039 | | | | | | | | | |
| TB-040 | | | | | | | | | |
| TB-041 | | | | | | | | | |
| TB-042 | | | | | | | | | |
| TB-043 | | | | | | | | | |
| TB-044 | | | | | | | | | |
| TB-045 | | | | | | | | | |
| TB-046 | | | | | | | | | |
| TB-047 | | | | | | | | | |
