# Contribution Matrix

> Record contribution with evidence as the project runs - it cannot be reconstructed at the end.
> Evidence must be a repository path, a commit or an artefact reference.
>
> **How this table is filled in.** Each member records their own contribution, with the group
> agreeing the share at the sprint review. M5 has recorded the M5 rows. The blank rows are the other
> members'. Note that a member's obligation on a shared item is not limited to the row where they are
> the first owner: where a task has a second owner, that member's work on it belongs in their row too.

## Contribution by sprint

| Member | Sprint | Work contributed (backlog / task IDs) | Evidence (commits, artefacts, records) | Group-agreed share of the sprint |
|---|---|---|---|---|
| M1 | | | | |
| M2 | | | | |
| M3 | | | | |
| M4 | | | | |
| M5 | 1 | Second owner on PB-001, PB-002, TB-012 and TB-013 - the case study interpretation and the requirements record, which are the input to the acceptance criteria M5 owns. | `docs/case-study-summary.md`; `docs/requirements/requirements.md` (second-owner review; no separate artefact) | |
| M1 | | | | |
| M2 | | | | |
| M3 | | | | |
| M4 | | | | |
| M5 | 2 | First owner: TB-016 with M4 (plan, estimation scale, definition of done, acceptance criteria), TB-014 (the test plan's acceptance criteria and scenarios), TB-015 (run the scenarios and record the evidence), PB-007 (write and execute the tests), PB-010 (requirement traceability, the evaluation criteria, the plan compared with actual). Second owner on PB-008 (the plan is kept current) and TB-001. | `docs/planning/project-plan.md`; `docs/agile/definition-of-done.md`; `tests/test-plan.md`; `tests/evidence/workers_unit-suite_c8556ba_2026-09-21.txt`; `tests/evidence/README.md`; `tests/README.md`; `docs/planning/plan-evaluation.md`; `docs/backlog/product-backlog.md`, `task-breakdown.md`; the corrections recorded in `docs/requirements/requirements.md`. The three defects found by checking the models against the requirements rather than by running them are recorded as `DEF-11`, `DEF-12` and `DEF-13` in `tests/test-plan.md` section 6. | |
| M1 | | | | |
| M2 | | | | |
| M3 | | | | |
| M4 | | | | |
| M5 | 3 | Second owner on the `DEF-11` and `DEF-12` rows of PB-008, each of which needed its scenarios re-run once the fix landed, and first owner on the PB-007 row: run the scenarios that had no result. All three test levels, and the bindings, layout, export and engine-form checks, were run at `57b3c1d` - the first version at which both fixes are committed. | `tests/evidence/workers_java-all-three-levels_57b3c1d_2026-09-28.txt` (38 of 38 unit tests, the configuration check, 2 of 2 smoke scenarios, 8 of 8 operational-model scenarios); `tests/evidence/checks-static-deployment-and-engine-forms_57b3c1d_2026-09-28.txt` (the bindings, layout and export checks pass, 4 of 4 models and 41 of 41 forms deploy, the strategic model is refused as designed); the re-run recorded in `tests/test-plan.md` section 5; the `DEF-11` and `DEF-12` rows updated with the commit each is now proved at | |
| M1 | | | | |
| M2 | | | | |
| M3 | | | | |
| M4 | | | | |
| M5 | 4 | | | |

## Summary

| Member | Contribution evidenced in the repository | Group-agreed share | Notes and any handover |
|---|---|---|---|
| M1 | | | |
| M2 | | | |
| M3 | | | |
| M4 | | | |
| M5 | Test plan and execution, the acceptance criteria, and the alignment and plan evaluation. The test plan now carries ten measurable acceptance criteria linked to requirements and business rules, twenty-one scenarios with the numbering the worker tests use, an execution record for 17 of them across three versions, and a defects and limitations table of fourteen entries. That last table is the part with the most value and the least visibility: of the fourteen, `DEF-11` (an urgent referral with no slot loops for ever in `core-1`), `DEF-12` (`N_F_ProcessRefund` cannot catch the `PROHIBITED_FINANCIAL_DATA` its worker raises) and `DEF-13` (36 of 55 user tasks bind no form) were found by checking the models against the requirements, not by running the scenarios - running the current scenarios would not have found any of them. The alignment and plan evaluation then converts those findings into verdicts: seven of the ten acceptance criteria met, three not, and the four reasons the plan and the delivery diverged. | | Handover: `DEF-11` and `DEF-12` are small fixes - a flow re-target and one boundary event - but each needs its scenario re-run afterwards, and `TC-13`, `TC-16` and `TC-20` need forms and a signed-in user before they can be run at all. The first release also needs a tag, or the second release cannot be compared against it. (Later: `release-1.0` was made at `9ef26df`, which still leaves `DEF-11` and `DEF-12` open at the tag.) (Later still: the forms landed at `a62e783`, where 40 forms cover all 55 user tasks, so `TC-13` and `TC-20` now wait only on the signed-in user that `DEF-07` and `DEF-08` need; the tag was then moved forward onto the commit carrying the completed form set, and `DEF-11` and `DEF-12` are still open at it.) (Later still: `DEF-12` was fixed at `9138bcc`, where the missing catch event was added, driven on the engine and guarded by a check in the bindings verifier; `DEF-11` remains open.) (Later still, 2026-09-28: `DEF-11` was closed at `57b3c1d`, which also re-laid out every model and added the task and form the escalation needs; all three levels were then re-run at that commit, and scenario 8 of `OperationalModelsTest` drives the escalation to completion, so the re-runs both fixes needed are done and recorded. The PB-007 scenarios are not all run: scenario 7 now drives two of TC-02's three decisions, but the "further information" decision and TC-16 have still not been driven, and TC-13 and TC-20 still need a signed-in user. Two things were also measured rather than assumed and neither is yet acted on: `release-1.0` resolves to `69e01a1`, one commit behind the `57b3c1d` the re-run was made at, so the tag does not cover the tree these records describe; and `57b3c1d` added a 56th user task and a 41st form while the documents that state the counts - `forms/README.md`, `docs/planning/`, `docs/backlog/` and this table - still say 55 and 40.) |
