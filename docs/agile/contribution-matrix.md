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
| M5 | 3 | | | |
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
| M5 | Test plan and execution, the acceptance criteria, and the alignment and plan evaluation. The test plan now carries ten measurable acceptance criteria linked to requirements and business rules, twenty-one scenarios with the numbering the worker tests use, an execution record for 17 of them across three versions, and a defects and limitations table of thirteen entries. That last table is the part with the most value and the least visibility: of the thirteen, `DEF-11` (an urgent referral with no slot loops for ever in `core-1`), `DEF-12` (`N_F_ProcessRefund` cannot catch the `PROHIBITED_FINANCIAL_DATA` its worker raises) and `DEF-13` (36 of 55 user tasks bind no form) were found by checking the models against the requirements, not by running the scenarios - running the current scenarios would not have found any of them. The alignment and plan evaluation then converts those findings into verdicts: seven of the ten acceptance criteria met, three not, and the four reasons the plan and the delivery diverged. | | Handover: `DEF-11` and `DEF-12` are small fixes - a flow re-target and one boundary event - but each needs its scenario re-run afterwards, and `TC-13`, `TC-16` and `TC-20` need forms and a signed-in user before they can be run at all. The first release also needs a tag, or the second release cannot be compared against it. |
