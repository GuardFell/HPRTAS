# Project Plan

> Keep this current: it is compared against actual progress when the plan is evaluated. Sprint
> dates are proposals to confirm.
>
> Recorded by M5, with M4, as the joint task for the plan and the estimation scale. The tables name
> the role slots M1-M5 until real names are entered. Rows that are still blank are the group's to
> fill: they are deliberately left blank rather than guessed, because a plan that invents a
> commitment nobody made is worse than a plan with a visible hole in it.

## 1. Scope

### In scope - first release

| Item | Why it is in scope | Owner |
|---|---|---|
| The four `core-N` operational models in `models/operational/` | They are the executable system: referral to appointment, treatment authorisation with funding and payment, clinic letter and escalation, follow-up, cancellation, enquiry and refund. `AS-14` fixes this as the first-release scope. | |
| The 40 Camunda Forms in `forms/`, bound to their user tasks | A user task without its form cannot be completed as a Tasklist user would, so the models are not usable without them. | M4 |
| The six external workers and their simulated services in `workers/` | Every service task in the four models is bound to one of them, so the models would stall without them. | |
| The strategic model `models/strategic/patient-pathway-all-entities.bpmn` | The non-executable view of the whole pathway across all entities, and the only place the enquiry requirements sit at strategic level. It is not deployed. | |
| The requirements list and its traceability in `docs/requirements/requirements.md` | Every acceptance criterion in the test plan is linked to a requirement and a business rule, so the criteria cannot be read without it. | |
| The test plan and the evidence in `tests/` | The delivery gate: a criterion with no evidence has not been demonstrated. | M5 |
| The case study summary, backlog and planning documents in `docs/` | The shared understanding, the plan and the record of what was agreed that the rest of the work is built on. | |
| The Enterprise Architecture portfolio deliverable | A separate deliverable describing the enterprise, its information systems and the Zachman view; it carries its own conditions in `../agile/definition-of-done.md`. | |

### Explicitly out of scope

| Item | Reason for deferral | Target release |
|---|---|---|
| The i\* Strategic Dependency and Strategic Rationale models | Not produced yet. `models/socio-technical/` is described but empty of models. | Second release |
| Role-based access control (FR-045, NFR-001, NFR-002) | Not implemented. User tasks carry the candidate group of their lane, but no user is authenticated and no permission is enforced anywhere, so the separation of duties rests on the lane structure alone (`DEF-07`). | Second release |
| The audit trail (FR-046, FR-047, NFR-003) | Not implemented in the models or the workers; there is nothing to record it. | Second release |
| Management reporting (FR-050, NFR-010) | Not implemented; no reporting surface exists. | Second release |
| The downtime recording procedure (FR-049, NFR-006) | Not modelled and no procedure document exists. The pending-booking and dispatch-failure paths are the closest behaviour. | Second release |
| Patient identification and duplicate-patient matching (FR-048, NFR-004) | `N_MS_CheckReferral` records an identification check as a field, but no matching or duplicate check runs behind it. | Second release |
| Integration with the existing hospital systems | The prototype simulates every external service (`AS-12`). The scope of the integration is not defined in the case. | Not planned |
| Availability targets and record retention (NFR-005, NFR-013) | No target or retention period has been agreed (`AS-10`), so neither can be designed against or tested. | Second release |

## 2. Estimation approach

This is the scale `docs/backlog/product-backlog.md` refers to. Estimates are **story points**: a
relative size, not a duration.

| Item | Decision |
|---|---|
| Unit | Story points. One point is one small, well-understood piece of work; thirteen is an increment that has to be delivered across a whole sprint. |
| Scale | Modified Fibonacci: **1, 2, 3, 5, 8, 13**. Nothing is estimated below 1 or above 13; anything larger is split, because a task that cannot be finished inside one sprint is a sprint goal, not a task. |
| Meaning of each value | **1** - a single edit or correction with no design decision, such as re-pointing a reference after a rename. **2** - a small document or one evidence record, such as running a suite and writing up the result. **3** - one coherent artefact with clear acceptance conditions, such as one requirement group written up or one Camunda Form. **5** - one artefact that has to work against something else, such as one worker with its unit tests, or one operational model. **8** - an artefact plus its bindings, such as a model with its forms, or a worker bound to its service task and proven through the engine. **13** - an integrated increment: several models, the workers and the evidence together, for example the end-to-end run of the four models with the forms and the workers. |
| Uncertainty rule | Where the group cannot agree within one step on the scale, the higher value is taken and the reason is recorded. The disagreement is the estimate's uncertainty, and hiding it produces a plan nobody believes. |
| Who estimates | The whole group, in sprint planning. The first owner leads the estimate for their task and the second owner challenges it, because the second owner is the one who has to take it over. |
| Velocity | Measured, not assumed: the sum of the estimates of the tasks that met the definition of done in the sprint. Recorded in section 6 after each sprint and used to size the next one. |
| Re-estimation | Allowed, but never silently. A changed estimate is recorded in section 7 with the reason, and the task's acceptance conditions are re-checked at the same time. |
| Estimation of work already done | Retrospective estimates are avoided. Where a task was completed without an agreed estimate, the estimate is recorded as "not agreed" rather than back-filled, so the velocity figure is not corrupted. |

**First-release target.** The four models, the eight forms, the six workers and the test evidence
that goes with them, by 28 September 2026.

## 3. Task allocation

| Member | Primary responsibilities | Second-owner coverage for |
|---|---|---|
| M1 | | |
| M2 | | |
| M3 | | |
| M4 | The Camunda Forms and their binding to the user tasks (PB-006, TB-006); with M5, the plan, the estimation scale, the definition of done and the acceptance criteria (TB-016), holding the "are the forms and the demonstration acceptable" side of that pair while M5 holds "does the testing pass". | |
| M5 | Test plan and execution, and the acceptance criteria: write and run the tests (PB-007), complete the test plan's acceptance criteria and scenarios (TB-014), run the scenarios and record the evidence (TB-015), and, with M4, the plan, the estimation scale, the definition of done and the acceptance criteria (TB-016). The alignment and plan evaluation (PB-010): requirement traceability across the models, the evaluation criteria, and the comparison of plan against actual progress. | PB-001 and PB-002 (interpreting the case study and recording the requirements), PB-008 (the demonstration and keeping the plan current), TB-001, TB-002 and TB-003 (the named teams, the business rules and the exceptions in the case study), and TB-012 and TB-013 (the case study summary and the requirement traceability table). |

Every task in `docs/backlog/task-breakdown.md` has a first and a second owner. Second owners are
sufficiently informed to take over if a first owner cannot deliver in time.

Where M5 is the second owner, the obligation is not a signature. The requirement-traceability and
case-summary work is the input to the acceptance criteria M5 owns, and the plan M5 maintains is the
document the plan evaluation is performed against, so the second-owner rows for PB-008, TB-012 and
TB-013 are active rather than standby. M4 and M5 are mutual second owners on TB-016, so neither side
of the acceptance decision - "are the forms and the demonstration acceptable" (M4) and "does the
testing pass" (M5) - can be settled alone.

## 4. Risk register

| ID | Risk | Likelihood | Impact | Mitigation | Owner | Status |
|---|---|---|---|---|---|---|
| R-01 | The models, the forms and the workers drift apart: a form field name stops being the variable a gateway or a worker reads, or a job type stops matching the service task's `taskDefinition`. The forms and the model already disagreed once and the forms were changed to match. | High | High | The artefact-type criteria in `../agile/definition-of-done.md` require the binding to be checked before a form or model is called done, and the end-to-end run deploys the models and the forms together so a broken binding fails the run. | M4 | Open |
| R-02 | The engine cannot be run on every machine. `camunda-runtime/` is not in the repository, so the model-level and engine-level evidence cannot be reproduced by a member who does not have it, and the evidence stands on one member's machine. | High | Medium | The worker unit suite needs no engine and can be re-run by anyone at any commit, which is why it was re-recorded at `c8556ba`; the reproduction steps for the engine runs are written into each evidence file, and the missing runtime is stated as a limitation. | M5 | Open |
| R-03 | Role-based access and the audit trail are Must requirements and are not implemented, so the release cannot be accepted as meeting the case as written, however well the pathway runs. | Certain | High | Recorded as a gap in `../requirements/requirements.md` and as `DEF-07` in the test plan rather than left out, so the shortfall is a decision the group makes with the evidence in front of it, not an omission. | Group | Open |
| R-04 | Requirements with no element of their own and no evidence: FR-045 to FR-050, FR-021 (the mapping is unconfirmed) and FR-035 (carried on process documentation only). A requirement that neither the models nor the tests can point at is not demonstrable. | High | Medium | Each is marked `Not yet evidenced` in `../requirements/requirements.md` and is evaluated in `plan-evaluation.md`, so the size of the gap is known. | M5 | Open |
| R-05 | Superseded work. The first operational edition of three models was replaced by the four `core-N` models, so the evidence recorded at `1b42bff` and `e852224` no longer describes the delivered models and has to be re-run. | Certain | Medium | The superseded evidence is kept but marked as superseded in `../../tests/evidence/README.md`; the `a7f0dd6` run covers the current models, and only documentation has changed since. | M5 | Mitigated |
| R-06 | The process documents are not being kept current. The product backlog, the task breakdown, the sprint backlogs and the contribution matrix were still templates while the models, the forms and the workers were being delivered, so the plan cannot be evaluated against them and contribution cannot be evidenced from them. | Certain | High | This plan, the definition of done and the test plan are now recorded, and the evaluation in `plan-evaluation.md` states the variance plainly. The product backlog, the task breakdown, the sprint backlogs and the dependencies are now recorded from the group's plan document rather than left blank, and the differences between the two are listed in `../backlog/sprint-backlogs.md`. Still the group's to fill, because they are decisions rather than transcriptions: the contribution matrix rows for M1 to M4, the signatures on the definition of done, and the people columns of `../backlog/dependencies.md`. | Group | Mitigated |
| R-07 | Two ID allocations are in use at once. The worker source and the worker tests cite the earlier, coarser allocation of rule, exception and requirement IDs while the models cite the current one. | Certain | Low | The reconciliation table at the top of `../requirements/requirements.md` resolves every ID that appears in the older evidence; `DEF-06` records that the worker source and tests are affected too. | M5 | Open |
| R-08 | Results cannot be tied to a release. A result is tied to a commit, and a run made against a working tree that is not clean cannot be tied to anything. | High | Medium | Every evidence file names the commit and states whether the tree was clean; the definition of done requires the version to be identified. `release-1.0` tags the first release, made at `9ef26df`, re-pointed to `bdcc0e1` for the Java workers and re-pointed again to cover the completed form set (`a62e783`), so a result produced from here on can name the release as well as its commit. `DEF-11` and `DEF-12` are still open at the tag, so if they are fixed before 28 September the tag has to move with them, or the release has to be re-tagged after them. | M5 | Mitigated |

## 5. Timeline

| Date | Milestone | Evidence produced |
|---|---|---|
| 17 Sep 2026 | Case study summary and the first requirements draft recorded from the case study text. | `docs/case-study-summary.md` sections 1-10; `docs/requirements/requirements.md` |
| 20 Sep 2026 | Sprint 1 ends. A deployable model of the referral-to-appointment pathway, the Consultant acceptance gate and one exception path; the plan, the backlog and the configuration management workflow agreed. | The first operational edition of the models; partial - see section 6 |
| 21 Sep 2026 | External workers delivered, the four `core-N` models replace the first edition, the forms are bound to their user tasks, and the models are run end to end against the workers. | `813fea6`, `1b42bff`, `e852224`, `a7f0dd6`; `tests/evidence/` |
| 27 Sep 2026 | Sprint 2 ends. The integrated increment: models, workers and forms working end to end for the normal pathway, the funding and payment gate and at least one failure path, with evidence against an identified version. | `tests/evidence/` |
| 28 Sep 2026 | **Initial release.** | Tagged commit; all evidence against it |
| 15 Oct 2026 | Sprint 3 ends. Strategic BPMN plus the i\* SD and SR models with requirement traceability; the refinements found in Sprint 2 folded back in. | `models/strategic/`, `models/socio-technical/` |
| 21 Oct 2026 | Sprint 4 ends. The second release validated against the first, with the alignment evaluation, the acceptance-criteria evaluation and the plan evaluation complete. | `plan-evaluation.md` |
| 22 Oct 2026 | **Second release.** | Tagged commit |

## 6. Planned vs actual

> The Actual and Variance columns are completed after each sprint, from what actually happened.
> Where a dimension has no Actual recorded, it has not been assessed yet - that is not the same as
> it having gone to plan.

### Sprint 1

**Goal as written:** produce a deployable BPMN model of the referral-to-new-patient-appointment
pathway, including the Consultant acceptance gate and one exception path, together with the agreed
plan, backlog and configuration management workflow.
**Dates:** 15 - 20 September 2026

| Dimension | Planned | Actual | Variance and cause |
|---|---|---|---|
| Models | A deployable model of the referral-to-appointment pathway with the acceptance gate and one exception path. | Delivered, and exceeded: the pathway was modelled and the first edition of three operational models was built, then replaced by the four `core-N` models at `a7f0dd6`. | Ahead of plan on modelling volume. The replacement was not a rename - elements were split, merged and re-identified - so the cost of the migration was not in the plan for Sprint 1. |
| Workers | Not planned for Sprint 1. | Six workers delivered, with unit tests and two engine runs. | Ahead of plan. The workers were delivered before the sprint that planned them. |
| Plan, backlog and configuration management workflow | Explicitly part of the Sprint 1 goal. | Not delivered. `product-backlog.md`, `task-breakdown.md` and `contribution-matrix.md` were still templates at the end of the sprint. | A clear miss against the stated goal, and the cause matters: the modelling and the workers were visible and reviewable, and the planning tables were not, so effort went to the visible work. This is `R-06`. The consequence is in section 7 - the plan cannot be evaluated against a table nobody filled in. |
| Estimates | Each task estimated on the agreed scale. | No estimates recorded. | Not assessable. Velocity cannot be measured for Sprint 1 because no estimates were agreed, and back-filling them now would corrupt the figure (section 2). |
| Evidence | Evidence that the sprint goal was met. | Delivered at worker level (`813fea6`) and model level (`1b42bff`, `e852224`), each naming its commit. | On plan. The evidence names its version and states its limitations, which is what made the two defects below findable. |

**Defects found in this sprint's work:** `DEF-01` (21 gateways with no default flow, so their
fallback branches were unreachable) and `DEF-02` (four service tasks that could raise
`INVALID_VARIABLE` with no catch event, so a business error became an incident). Both were found by
running the scenarios rather than by reading the models, and both were fixed at `e852224`.

### Sprint 2

**Goal as written:** deliver the integrated increment in which the model, external workers and forms
work together end to end for the normal pathway, the funding and payment gate, and at least one
failure path, with test evidence against an identified version.
**Dates:** 21 - 27 September 2026 (in progress at the time of writing)

| Dimension | Planned | Actual | Variance and cause |
|---|---|---|---|
| Integrated increment | Models, workers and forms end to end for the normal pathway, the funding and payment gate and one failure path. | Delivered at `a7f0dd6` and re-run at `c8556ba`: all four models, all eight forms and all six workers, with five scenarios each reaching an end event, plus the failure paths at worker level. | Ahead of plan on breadth - every service task in the four models was reached, not just the planned path. |
| Forms | Bound to their user tasks and deployed with the models. | Delivered; the bindings resolve in the deployment. | On plan, with the limitation that no form has been completed by a signed-in user through Tasklist (`DEF-08`). |
| Evidence against an identified version | A run naming the version it was produced at. | `operational-models_end-to-end_a7f0dd6_2026-09-21.txt` and a re-run of the unit suite at `c8556ba`. | On plan. Both name the commit, state what they do not cover, and give reproduction steps. |
| Estimates | Each chosen task estimated on the agreed scale. | No estimates recorded. | Not assessable, same cause as Sprint 1. |
| Acceptance criteria | Not planned for this sprint. | Ten criteria and twenty-one scenarios defined, and the execution record opened. | Ahead of plan: TB-016 and TB-014 were completed in this sprint rather than later, because a criterion with no test cannot be evaluated at the sprint review. |

### Sprint 3

| Dimension | Planned | Actual | Variance and cause |
|---|---|---|---|
| | | | |
| | | | |
| | | | |
| | | | |
| | | | |

### Sprint 4

| Dimension | Planned | Actual | Variance and cause |
|---|---|---|---|
| | | | |
| | | | |
| | | | |
| | | | |
| | | | |

## 7. Plan revisions

| Date | Revision | Reason | Evidence |
|---|---|---|---|
| 21 Sep 2026 | The operational models were re-planned from three models to four `core-N` models. | The first edition's division did not match the pathway stages, so elements were split, merged and re-identified. The change is a re-plan rather than a rename, which is why the earlier evidence no longer describes the delivered models. | `a7f0dd6`; `models/operational/README.md` |
| 21 Sep 2026 | The first release was fixed at the four `core-N` models. | Recorded as `AS-14` so that a requirement demonstrated outside the four models is reported where it is demonstrated instead. | `docs/case-study-summary.md` section 10 |
| 21 Sep 2026 | Test evidence was re-planned from one run per scenario to a run per level, named after the commit it was produced at. | The scenario-level naming could not express a worker-level result that is not yet a model-level one, and the missing engine on some machines makes the level explicit. | `tests/evidence/README.md`; `tests/evidence/workers_unit-suite_c8556ba_2026-09-21.txt` |
| 21 Sep 2026 | The plan and the definition of done were recorded, and the estimation scale agreed, in this sprint rather than in Sprint 1. | They were part of the Sprint 1 goal and were not delivered; recording them late is a revision of the plan, and the variance is stated in section 6 rather than absorbed. | This document; `docs/agile/definition-of-done.md` |
