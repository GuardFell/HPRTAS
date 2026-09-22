# Definition of Done

> A shared definition of done: the artefact meets its agreed acceptance conditions, has been
> reviewed, is accessible to the team, and is integrated or linked to the relevant evidence.
> **A first draft is not done.**
>
> Recorded by M5, with M4, as the joint task for the plan, the estimation scale and the acceptance
> criteria. The five role slots below confirm it at the sprint review; until a slot is dated, that
> member has not agreed this version. It is applied to every task in
> `../backlog/task-breakdown.md`.

## Project-wide definition of done

Every task meets all eight conditions, whatever it produces.

| # | Condition | How it is evidenced |
|---|---|---|
| 1 | Its agreed acceptance conditions are met. | The task's own Acceptance Conditions cell in `../backlog/task-breakdown.md`, checked off rather than the task simply being called finished. |
| 2 | It is in the repository at a commit. Work in a chat, on one machine or in a private folder is not done. | The commit that adds or changes it. The evidence must be a path inside this repository. |
| 3 | It has been run, not only read. A model is deployed and driven to an end state; a worker is executed; a form is deployed with its model and the variables it writes are checked against the gateways and workers that read them; a document is reviewed by someone else. | A file in `../../tests/evidence/` naming the version it was run at, or an equivalent record for a non-executable artefact. |
| 4 | The version it was produced against is identified, and a result can be reproduced from that version. | A commit or a tag in the evidence file's name and header, plus a "Reproduce with" section. `release-1.0` tags the first release; a result produced before it names its commit, and one produced after it names the tag as well. |
| 5 | It is consistent with the artefacts around it: element documentation in the models cites the requirement, rule and exception IDs it implements; a form field name is the same word as the variable a gateway or a worker reads; a worker's job type is the `taskDefinition` type of the service task it serves. | The consistency checks listed under the artefact types below, and the reviewer's confirmation. |
| 6 | It has been reviewed by someone other than its author - in practice the second owner, who must be able to take the task over. | The reviewer named against the task, or the second owner's confirmation that they can continue it. |
| 7 | What it does not do is written down. A limitation, a simulated component and a gap are recorded; a criterion that is not met is explained rather than removed. | The defects and limitations table in `../../tests/test-plan.md`; the `Not yet evidenced` and `Gap identified` rows in `../requirements/requirements.md`. |
| 8 | It does not break what was already accepted. The worker suite and the engine runs still pass at the new commit. | The full test run at the new version, in `../../tests/evidence/`, and the absence of an open incident in Operate. |

## Task-type specific criteria

| Artefact type | Done when | Evidence |
|---|---|---|
| Requirement or business rule | It is recorded with a stable ID, is traceable to a source (a pathway stage, a `BR-###` or an `AS-###`), and names where it is implemented and where it is tested. | Its row in `../requirements/requirements.md`. A requirement whose implementation or test cell still reads "Not yet evidenced" is not done. |
| BPMN operational model | It deploys without error; every exclusive gateway declares a default flow; every service task that can raise a business error has a boundary error event for that code; every user task carries the Camunda user task marker, its candidate group and its form key, and the matching `.form` file is deployed with it. | Deployment output and a run in `../../tests/evidence/` showing each gateway routing on what a worker or a form returned, with no incident. |
| Camunda Form | It deploys with its model and the binding resolves in Tasklist; every field name is the variable the model reads; enumerated values are values a gateway condition or a worker validation accepts; fields the process cannot proceed without are required. | The deployment output in `../../tests/evidence/operational-models_end-to-end_a7f0dd6_2026-09-21.txt`, and the form's row in `../../forms/README.md`. |
| External worker | It registers against the gateway with the job type of its service task, receives a job, returns the agreed variables and lets the process continue; unusable input produces a controlled business error rather than a crash; a genuine defect fails the job so the broker retries it. | The unit tests in `../../workers/test/workers.test.js` and the smoke run in `../../tests/evidence/`. |
| Test case and evidence | The scenario states its preconditions, data, actions, expected outcome and pass/fail condition; the evidence names the version, states what the run covers and what it does not, gives the method and a way to reproduce it, and records a failure as plainly as a pass. | Its row in `../../tests/test-plan.md` section 4 and its file in `../../tests/evidence/`. A test plan row with no evidence and no "Not run" is not done. |
| Documentation or decision record | It says what was decided, why, and what was rejected; it is consistent with the artefacts it describes; any claim in it that testing has since disproved is corrected or withdrawn. | The document itself, with its change log or the commit that records the correction. |
| Demonstration or presentation | The flow demonstrated is the one in the repository at an identified commit; anything shown that is not implemented is labelled as simulated or not implemented. | The demo script or slide deck, plus the commit it demonstrates. |
| Enterprise architecture deliverable | It is complete against its own brief, internally consistent with the models and the requirements, and reviewed. | `../../Enterprise Architecture/`, reviewed by a member who did not write it. |

## Agreed by

| Name | Date |
|---|---|
| M1 | |
| M2 | |
| M3 | |
| M4 | |
| M5 | |

## Change log

| Date | Change | Agreed by |
|---|---|---|
| 21 Sep 2026 | Definition of done recorded by M5 with M4: eight project-wide conditions and eight artefact types, derived from the working agreements already visible in the repository (element documentation carrying requirement IDs, the default-flow rule, the error catch-event rule, evidence named by version, the defects and limitations table). | M5 |
| | | |
| | | |
