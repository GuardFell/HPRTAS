# Sprint Backlogs

> **Agile Workshop, Part 6.** For each sprint agree a sprint **goal** (an outcome, not a task
> list), then select a manageable set of tasks from the product backlog.
>
> A weak goal: "Do BPMN and forms". A useful goal: "Produce an executable BPMN model with the
> main workflow and one exception path ready for review."
>
> Sprint dates are proposals for the group to confirm.
>
> **How these tables are filled in.** Each row is recorded by the first owner of the task. M5 has
> recorded the M5 tasks selected into Sprint 2. The rows that are still blank in both sprints are
> tasks that were delivered without being written into a sprint first - the models, the forms and
> the workers all exist with evidence - and `docs/planning/plan-evaluation.md` section 5 accounts for
> that as the Sprint 1 variance rather than leaving it unexplained.

## Sprint 1 - A deployable model of the referral-to-appointment pathway

**Sprint goal (an outcome, not a task list):** Produce a deployable BPMN model of the referral-to-new-patient-appointment pathway, including the Consultant acceptance gate and one exception path, together with the agreed plan, backlog and configuration management workflow.
**Sprint dates:** 15 - 20 September 2026

| Sprint | Task ID | Task Description | First Owner | Second Owner | Estimate | Acceptance Conditions | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |

---

## Sprint 2 - The integrated workflow increment

**Sprint goal (an outcome, not a task list):** Deliver the integrated increment in which the model, external workers and forms work together end to end for the normal pathway, the funding and payment gate, and at least one failure path, with test evidence against an identified version.
**Sprint dates:** 21 - 27 September 2026

| Sprint | Task ID | Task Description | First Owner | Second Owner | Estimate | Acceptance Conditions | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| 2 | TB-016 | Agree the plan, the estimation scale, the definition of done and the acceptance criteria, with M4. | M5 | M4 | 5 | The estimation scale is written down and referenced by the backlog; the definition of done covers the project-wide conditions and each artefact type; the acceptance criteria are agreed with M4. | `docs/planning/project-plan.md`; `docs/agile/definition-of-done.md`; `tests/test-plan.md` | Done |
| 2 | TB-014 | Complete the test plan's acceptance criteria and scenarios. | M5 | | 5 | Every criterion is measurable and linked to a requirement and a business rule; each scenario states its preconditions, data, actions, expected outcome and pass/fail condition. | `tests/test-plan.md` sections 3 and 4 | Done |
| 2 | TB-015 | Run the scenarios and record the evidence. | M5 | | 5 | Every result names the version it was produced at; a partial result is recorded as plainly as a pass; each evidence file states its method and limitations. | `tests/evidence/`; `tests/evidence/README.md` | In progress - 17 of 21 scenarios run, 3 defects open (`DEF-11`, `DEF-12`, `DEF-13`) |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |

---

## Sprint 3 - Socio-technical models and refinements

**Sprint goal (an outcome, not a task list):** Produce the strategic BPMN plus the i* SD and SR models with requirement traceability, and fold the refinements identified in Sprint 2 into the model, workers and forms.
**Sprint dates:** 29 September - 15 October 2026

| Sprint | Task ID | Task Description | First Owner | Second Owner | Estimate | Acceptance Conditions | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |

---

## Sprint 4 - Validation, evaluation and walkthrough

**Sprint goal (an outcome, not a task list):** Validate the second release against the first release baseline and complete the alignment evaluation, the acceptance criteria evaluation and the plan evaluation.
**Sprint dates:** 16 - 21 October 2026

| Sprint | Task ID | Task Description | First Owner | Second Owner | Estimate | Acceptance Conditions | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |

---

## Sprint planning checklist

- [x] What is the sprint goal, stated as an outcome?
- [ ] Is the selected work realistic for the available time?
- [ ] Has every selected task got a first owner **and** a second owner?
- [ ] Are dependencies, risks and deadlines considered?
- [x] Can everyone explain what will be delivered by the end of this sprint?
