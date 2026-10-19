# Sprint Reviews

> The record of the two marked sprint reviews that close Sprint 3 and Sprint 4. Each review is
> recorded against the version it assessed, and that version is kept as a tag, so the increment can
> be read as it stood at the review rather than as the tree stands afterwards - the specification
> asks for a version or snapshot for each review.
>
> The sprint goals and the tasks are the ones in `../backlog/sprint-backlogs.md`, and the
> planned-against-actual comparison is in `../planning/project-plan.md` section 6. The record is
> written from the project's own documents: it carries the increment, the acceptance evidence and
> the decisions the group acted on, and it does not attribute to a tutor anything the group did not
> write down.

## Review 3 (SR3) - the i\* models and the first review's feedback

| | |
|---|---|
| Sprint | Sprint 3, 28 September - 15 October 2026 |
| Sprint goal | Produce the i\* Strategic Dependency and Strategic Rationale models alongside the strategic BPMN, with the requirement traceability extended to them, and close the defects and the unrun scenarios the first release left open, folding the first review's feedback into the model, the workers and the forms. |
| Assessed focus | AISD: prioritised feedback actions, implemented improvements and the revised sprint plan. BPM&EA: draft strategic BPMN and i\* SD/SR models with initial requirements traceability. |
| Version reviewed | **`sr3-2026-10-15`** - `8d6f3ce`, the tree at the end of Sprint 3 |
| The increment | The i\* SD and SR models in `../../models/socio-technical/` (4 actors and 7 dependencies; 4 rationale pages and 24 elements) with the nine piStar files and the drawings; the two strategic views in `../../models/strategic/`; the feedback pass at `378b319` - `core-0` calling the five stages, the H2 record store, 43 forms and the corrected models; the response to the first review's 21 sheets; the revised alignment and acceptance-criteria evaluation; the submission deck. |
| Acceptance evidence | `tools/verify_hprtas_istar.py` and `verify_hprtas_pistar.py` pass on that tree, as do the bindings and layout checks; the suite at `378b319` is 52 unit tests and 14 engine tests with 0 failures; `TC-13` and `TC-16` are driven and recorded; the test plan carries a result for 25 of 25 scenarios - 18 pass and 7 pass in part. |
| What was not finished, and why | The signed-in-user half of the form work is not exercised (`DEF-07`, `DEF-08`); the demonstration is scripted and rehearsed but not yet given; `TC-13` and `TC-16` were driven at the sprint boundary rather than inside it, because the forms and the clock control arrived late. |
| Backlog changes for Sprint 4 | The Sprint 4 selection in `../backlog/sprint-backlogs.md`: the re-runs at an identified version (`PB-008`, M3/M5), the acceptance-criteria evaluation (`PB-010`, M5/M2), the plan evaluation (`PB-010`, M5/M1), the demonstration (`PB-008`, M4/M5), the requirements update (`PB-002`, M2/M5) and the tag (`-`, M5/M1). |

## Review 4 (SR4) - validation, evaluation and demonstration

| | |
|---|---|
| Sprint | Sprint 4, 16 - 21 October 2026 |
| Sprint goal | Validate the second release against the `release-1.0` baseline and complete the alignment evaluation, the acceptance-criteria evaluation and the plan evaluation, with the integrated system demonstrated. |
| Assessed focus | AISD: validated improvements, evidence of responses to feedback and an evaluation of planned against actual progress. BPM&EA: refined strategic and i\* models, the alignment evaluation and the acceptance-test evidence. |
| Version reviewed | **`sr4-2026-10-22`** - the tree at the end of Sprint 4, which is also the version the second release is submitted at |
| The increment | The second release: six executable processes, 43 forms, eight workers and the H2 record store, the two converging gateways added to `core-2` and `core-3`, the strategic views' notes, and the `core-5` re-layout. Beside the system: the revised alignment evaluation, the acceptance-criteria evaluation at the revised version, the plan evaluation, the feedback response record, and the demonstration script. |
| Acceptance evidence | The four records of 2026-10-15: the suite at `378b319` (52 unit tests, 14 engine tests), `TC-13`, `TC-16`, and the run on the changed tree that drives the two gateways and re-drives `TC-16` on the path that carries `N_AM_MergeEscalation`. Every scenario has a result - 25 of 25, 18 pass and 7 in part; the ten criteria are 8 met and 2 partial, both partials stated with their cause; the static checks pass on the tagged tree. |
| What was not finished, and why | `DEF-07` (role-based access is assigned, not enforced) and `DEF-08` (no form submitted by a signed-in user) are open and are the largest gaps; `TC-13`'s signed-in half cannot be exercised without them; the correspondence form still offers no translation option and no screen-reader pass has been made. The demonstration is this review's to give - the script states what it does not demonstrate. |
| Backlog changes after the review | The submission tag, and the outstanding items above recorded in `../../tests/test-plan.md` section 6 and `../planning/plan-evaluation.md` section 7 rather than carried as unstated scope. |

## The snapshot tags

| Tag | Commit | What it keeps |
|---|---|---|
| `sr3-2026-10-15` | `8d6f3ce` | The tree at the end of Sprint 3: the i\* models, the feedback pass and its records, the revised evaluations, the deck |
| `sr4-2026-10-22` | the tree at the end of Sprint 4 | The version the second release is reviewed and submitted at |

The review tags sit beside the submission tags rather than replacing them: `submission-2026-09-29`
is the initial release as submitted, and `submission-2026-10-22` is the revised version. If a later
commit changes the tree before a review, the review tag is re-pointed with the reason in its
annotation, as the submission tag already is.
