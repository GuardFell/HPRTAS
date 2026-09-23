# Sprint Backlogs

> **Agile Workshop, Part 6.** For each sprint: the sprint goal, stated as an outcome rather than a
> task list, and the tasks selected from the product backlog to reach it.
>
> The goals, the task rows, the owners, the estimates and the acceptance conditions below are the
> ones agreed in the group's project plan and product backlog document, transcribed here so that the
> sprint record can be read with the rest of the repository instead of beside it. The **status** of
> each row is the one this repository's evidence supports. Where the plan document and the records
> already kept here disagree, the difference is listed under [Differences from the plan
> document](#differences-from-the-plan-document) rather than resolved silently in either direction.
>
> Every task has a first owner and a second owner who can take it over. Owners are the role slots
> M1-M5 recorded in `../planning/project-plan.md` section 3. Estimates are story points on the scale
> agreed in section 2 of that plan: 1, 2, 3, 5, 8, 13.

## Sprint 1 - A deployable model of the referral-to-appointment pathway

**Sprint goal:** Produce a deployable BPMN model of the referral-to-new-patient-appointment pathway,
including the Consultant acceptance gate and one exception path, together with the agreed plan,
backlog and configuration management workflow.
**Sprint dates:** 15 - 20 September 2026

| Sprint | Task ID | Task Description | First Owner | Second Owner | Estimate | Acceptance Conditions | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| 1 | TB-001 | List the named teams and their stated responsibilities. | M2 | M5 | 2 | Table completed and agreed. | `../case-study-summary.md` section 2 | Done |
| 1 | TB-002 | Extract the stated business rules into a numbered table. | M2 | M1 | 5 | Every stated rule captured. | `../case-study-summary.md` section 5 (`BR-01` - `BR-47`) | Done |
| 1 | TB-003 | Extract the exceptions and alternative paths. | M4 | M2 | 3 | Every described exception captured. | `../case-study-summary.md` section 6 (`EX-01` - `EX-23`) | Done |
| 1 | TB-004 | Identify process participants, lanes and pools. | M1 | M3 | 3 | Boundaries agreed against clinical, administrative and financial separation. | `../../models/operational/` | Done |
| 1 | TB-005 | Draft the main BPMN flow, referral to new patient appointment. | M1 | M3 | 8 | Modelled end to end, including the Consultant acceptance gate. | `../../models/operational/` | Done |
| 1 | TB-007 | Configure the model for deployment. | M3 | M1 | 3 | Deploys to Camunda 8 Run without error. | `../../tests/evidence/operational-models_end-to-end_a7f0dd6_2026-09-21.txt` (deployment output) | Done |
| 1 | TB-016 | Agree the plan, estimation scale, definition of done and acceptance criteria, with M4. | M5 | M4 | 5 | Plan, definition of done and criteria clear and measurable; agreed by all five members. | `../planning/project-plan.md` section 2; `../agile/definition-of-done.md`; `../../tests/test-plan.md` section 3 | Done |

**How the sprint went.** The modelling target was met and passed: the pathway was modelled and the
first operational edition of three models was built, which the four `core-N` models then replaced
(`a7f0dd6`). The six external workers were delivered in this sprint although they were not planned
for it. The miss is the planning half of the goal - the product backlog, the task breakdown, the
sprint backlogs and the contribution matrix were still templates at the end of the sprint, and no
estimate was agreed in time to measure velocity. The cause and the cost are stated in
`../planning/project-plan.md` section 6 and `../planning/plan-evaluation.md` section 5.2 rather than
absorbed here.

## Sprint 2 - The integrated increment

**Sprint goal:** Deliver the integrated increment in which the model, the external workers and the
forms work together end to end for the normal pathway, the funding and payment gate and at least one
failure path, with test evidence against an identified version.
**Sprint dates:** 21 - 27 September 2026

| Sprint | Task ID | Task Description | First Owner | Second Owner | Estimate | Acceptance Conditions | Evidence | Status |
|---|---|---|---|---|---|---|---|---|
| 2 | TB-006 | Add the exception and alternative paths. | M4 | M1 | 8 | Each exception modelled or excluded with a reason. | `../../models/operational/`; `../../tests/test-plan.md` section 6 (`DEF-01`, `DEF-02`) | Done |
| 2 | TB-008 | Model the pools, lanes and message flows. | M1 | M3 | 5 | Named teams appear as lanes; message flow crosses pools. | `../../models/operational/` | Done |
| 2 | TB-009 | Bind the external workers to the service tasks. | M3 | M1 | 5 | Job types match the configuration; an instance completes. | `../../workers/`; `../../models/operational/` | Done |
| 2 | TB-010 | Deploy the operational model. | M3 | M1 | 3 | Deploys to Camunda 8 Run without error. | `../../tests/evidence/operational-models_end-to-end_a7f0dd6_2026-09-21.txt` | Done |
| 2 | TB-011 | Create the Camunda Forms and their task bindings. | M4 | M2 | 8 | Bound, validated and exchanging the agreed variables. | `../../forms/`; `../../tests/test-plan.md` section 6 (`DEF-13`) | Done |
| 2 | TB-012 | Complete the case study summary. | M2 | M5 | 8 | Every stated rule and exception captured. | `../case-study-summary.md` | Done |
| 2 | TB-013 | Record the requirements and the traceability table. | M2 | M5 | 8 | Every requirement traceable to a source. | `../requirements/requirements.md` | Done |
| 2 | TB-014 | Complete the test plan: the acceptance criteria and the scenarios. | M5 | M3 | 5 | Criteria measurable and linked to a requirement and a business rule. | `../../tests/test-plan.md` sections 3 and 4 | Done |
| 2 | TB-015 | Run the scenarios and record the evidence. | M5 | M3 | 5 | Normal, alternative and failure paths evidenced. | `../../tests/evidence/` | In progress - 17 of the 21 scenarios have a recorded result (11 pass, 6 pass in part); `TC-02`, `TC-13`, `TC-16` and `TC-20` are recorded as `Not run` |

**How the sprint went.** The integrated increment was delivered ahead of the sprint end and ahead of
its planned breadth: all four models, all eight forms and all six workers, with five scenarios each
reaching an end event and every service task in the four models reached at `a7f0dd6`. The
acceptance criteria and the scenarios were completed in this sprint although they were planned for
the next one, because a criterion with no test cannot be evaluated at the sprint review. Two things
are not closed: the forms and role-based access cannot be exercised without a signed-in user
(`DEF-07`, `DEF-08`), and three defects (`DEF-11`, `DEF-12`, `DEF-13`) were open against the
increment (`DEF-13` since closed at `a62e783`). See `../planning/project-plan.md` section 6 and
`../planning/plan-evaluation.md` section 5.3.

## Sprint 3 - The i\* models and the first review feedback

**Sprint goal:** Produce the i\* Strategic Dependency and Strategic Rationale models alongside the
strategic BPMN, with the requirement traceability extended to them, and close the defects and the
unrun scenarios the first release left open, folding the first review's feedback into the model,
the workers and the forms.
**Sprint dates:** 28 September - 15 October 2026 (see the note on dates below)

The tasks for this sprint are selected from the product backlog at sprint planning and are not yet
broken down or given `TB-###` identifiers, so the rows below name the work and the backlog item it
comes from rather than a task ID. Every row is one of the gaps recorded in
`../planning/plan-evaluation.md` section 7; none of it is new scope.

| Backlog item | Work selected | First Owner | Second Owner | Estimate | Acceptance Conditions | Evidence to produce |
|---|---|---|---|---|---|---|
| PB-009 | The i\* SD and SR models in `../../models/socio-technical/`, checked against each other: every dependency in the SD model has a rationale in the SR model, and every actor appears in both with the same goals. The strategic BPMN half of PB-009 is already delivered in `../../models/strategic/`. | M2 | M1 | 13 | Roles and external interactions shown; SD and SR consistent; the abstraction level explained. | `../../models/socio-technical/`; the traceability extended in `../requirements/requirements.md` |
| PB-008 | `DEF-11`: the urgent referral with no slot in the requested period loops for ever in `core-1`. Re-target the flow so it cannot cycle, then re-run the scenarios that reach it (`TC-05`, `TC-18`). | M1 | M3 | to be estimated | The instance leaves the branch for both a routine and an urgent referral; the re-run reaches an end event with no incident. | A corrected `core-1` and a re-run in `../../tests/evidence/` |
| PB-008 | `DEF-12`: `N_F_ProcessRefund` cannot catch the `PROHIBITED_FINANCIAL_DATA` its worker raises. Add the missing boundary event and re-run `TC-21`. | M3 | M1 | to be estimated | A refund carrying card details follows the error path instead of becoming an incident; `TC-21` still passes. | Done at `9138bcc`: `core-4` corrected, the error path driven on the engine, and `TC-21` re-run green |
| PB-006 | `DEF-13`: 36 of the 55 user tasks bound no form. Either the forms are built for those tasks, or the limitation is accepted for the second release and stated in the demonstration and in the evaluation. This was the largest open item in the project. | M4 | M2 | to be estimated | Every task the case requires a record from has a form whose fields are the variables the model reads, or a recorded decision that it does not and why. | `../../forms/`; `../planning/plan-evaluation.md` section 6 |
| PB-007 | Run the four scenarios that have no result. `TC-02` and `TC-16` need no new capability; `TC-13` and `TC-20` need the forms from the row above and a signed-in user first. | M5 | M3 | to be estimated | Each scenario has a result or a recorded reason it still cannot be run. | `../../tests/test-plan.md` section 5; `../../tests/evidence/` |
| PB-008 | Fold the first review's feedback into the model, the workers and the forms, and record each point against the decision, the action and the outcome. | M4 | M5 | to be estimated | Every feedback point is recorded with what was decided, what was done, the evidence and the outcome. | A response-to-feedback record; the corrected artefacts |
| PB-010 | Re-run the evaluation against the version produced in this sprint, so that the second release is judged against what it actually contains. | M5 | M2 | to be estimated | Every verdict is made against an identified version, and the criteria met and not met are restated from the new results. | `../planning/plan-evaluation.md` |

**Landed so far, mid-sprint.** The `PB-006` row is done at `a62e783`: 40 forms now cover all 55
user tasks, so `TC-13` and `TC-20` no longer wait on the `PB-006` row - only on the signed-in user
that `DEF-07` and `DEF-08` need. The `DEF-12` row is done at `9138bcc`; the `DEF-11` row is still open.

## Sprint 4 - Validation, evaluation and demonstration

**Sprint goal:** Validate the second release against the `release-1.0` baseline and complete the
alignment evaluation, the acceptance-criteria evaluation and the plan evaluation, with the
integrated system demonstrated.
**Sprint dates:** 16 - 21 October 2026 (see the note on dates below)

| Backlog item | Work selected | First Owner | Second Owner | Estimate | Acceptance Conditions | Evidence to produce |
|---|---|---|---|---|---|---|
| PB-008 | Re-run the worker suite, the smoke run and the end-to-end run against the second release, at an identified version. | M3 | M5 | to be estimated | Every result names the version it was produced at and states what it does not cover. | `../../tests/evidence/` |
| PB-010 | The acceptance-criteria evaluation: the initial and revised criteria, the actual results, the failures and a reasoned evaluation of both. | M5 | M2 | to be estimated | Each criterion is stated, given a verdict against the evidence, and explained where it is not met. | `../planning/plan-evaluation.md` sections 2 and 6 |
| PB-010 | The plan evaluation: a critical comparison of the original and the revised plan against actual progress, covering scope, priorities, estimates, allocation, dependencies, risks, integration and the sprint outcomes. | M5 | M1 | to be estimated | Each dimension is compared and the variance is explained rather than absorbed. | `../planning/project-plan.md` section 6; `../planning/plan-evaluation.md` section 5 |
| PB-008 | The demonstration of the updated and integrated system, with anything simulated or not implemented labelled as such. | M4 | M5 | to be estimated | The flow demonstrated is the one in the repository at an identified commit. | A demonstration script or slide deck, and the commit it demonstrates |
| PB-002 | Update `../requirements/requirements.md` with the verdicts the evaluations reach, including the requirements still not evidenced. | M2 | M5 | to be estimated | Every requirement carries a verdict, and a requirement that is not demonstrated says so. | `../requirements/requirements.md` |
| - | Tag the second release so the two releases can be compared. | M5 | M1 | 1 | The tag identifies the commit the second release was validated at. | `release-2.0` |

**Note on dates.** The two sprint windows above are the ones in `../planning/project-plan.md`
section 5, where they end at the 15 and 21 October milestones, before the second release on
22 October. The plan document splits the same period at 11 and 22 October instead. Sprint dates
there are recorded as proposals to confirm, and the two readings need reconciling at sprint
planning; the milestone dates in the plan are the ones used here because they are the ones the
deliverables are dated against.

## Differences from the plan document

Six rows differ between the group's plan document and the records already kept in this repository.
Each is left visible rather than resolved in favour of one document, because in each case one of the
two is a considered entry and the other is not obviously a correction of it.

| Row | In the plan document | In this repository | Why they are left as they are |
|---|---|---|---|
| TB-015 status | Done | In progress - the run and the recording are complete for every scenario that can be run today (17 of 21), and the four that cannot be run are named in `../../tests/test-plan.md` section 5 | The task's acceptance conditions are met, so it can be read as done; the four unrun scenarios are why the row still reads in progress here. Someone has to decide which of the two the sprint review records. |
| TB-012 estimate | 5 | 8 | Both estimates are on the agreed scale and neither was re-estimated after the fact; the difference is a disagreement about size, not a correction. |
| TB-013 estimate | 5 | 8 | Same as TB-012. |
| PB-007 estimate | 13 | 8 | The plan document sizes the whole test effort as an integrated increment; `../backlog/product-backlog.md` records it as an 8, the size of an artefact with its bindings. |
| PB-008 estimate | 8 | 3 | The plan document sizes the demonstration and plan maintenance at an integrated increment; the product backlog records it as a 3. |
| PB-002 status | Done | In review - the list is complete and traceable, but `DEF-05` and `DEF-06` are still open in it | The requirements list is complete; the two open items are about the mapping of `FR-021` and the older ID allocation still cited in the worker source and tests. |

Two further differences are not table rows. The plan document gives `TB-001` as "list the named teams
and their stated responsibilities"; the task breakdown kept here previously used `TB-001` for the
group's own responsibility table. This file follows the plan document, and the group's responsibility
table is recorded in `../planning/project-plan.md` section 3, which is where its content already
lived. And the plan document's Sprint 2 marks `TB-014` as not started, which the record here and the
test plan both contradict: `TB-014` was delivered in Sprint 2 and the plan document has since been
corrected.
