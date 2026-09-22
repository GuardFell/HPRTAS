# Dependency Mapping

> **Agile Workshop, Part 5.** For each item: what must be completed first, which items depend on it,
> who must be informed if it is delayed, and the backup plan.
>
> The **Must be completed first** column is the dependency the product backlog records for each
> item. The task-level dependencies are in `task-breakdown.md`, which names the task that has to be
> done first for each `TB-###`. The **Who must be informed** and **Backup plan** columns are the
> group's to fill: they record a decision about people, not a fact about the work, and leaving them
> blank says that no one has agreed one yet. The backup plan that does exist for the largest
> dependencies is the second owner on every task, which is what makes a task recoverable when the
> first owner cannot deliver.

## Backlog item dependencies

| Item | Must be completed first | Items depending on this | Risk if it slips |
|---|---|---|---|
| PB-001 | None | PB-002, PB-009, PB-011 | The whole requirements and modelling chain rests on the shared reading of the case; a change here reaches every artefact. |
| PB-002 | PB-001 | PB-003, PB-006, PB-007, PB-010 | The criteria and the traceability are written against the requirement IDs, so a change re-opens both. |
| PB-003 | PB-002 | PB-004, PB-005, PB-008 | The exception paths, the worker bindings and the demonstration all attach to the operational models. |
| PB-004 | PB-003 | PB-007, PB-008 | Defects in the exception paths are only visible when a scenario drives them; `DEF-01` and `DEF-02` were both found this way. |
| PB-005 | PB-003 | PB-006, PB-007, PB-008 | A worker that does not return the agreed variables stops the model at the service task, so nothing downstream can be demonstrated. |
| PB-006 | PB-002, PB-003 | PB-007, PB-008 | A form field name that is not the variable the model reads breaks the binding silently; `DEF-13` is the same fault at a larger scale. |
| PB-007 | PB-002, PB-003, PB-004, PB-005, PB-006 | PB-008, PB-010 | The two evaluations are made against the test results, so a criterion with no evidence cannot be evaluated. |
| PB-008 | PB-003, PB-004, PB-005, PB-006, PB-007 | - | Nothing depends on it, and that is the risk: the demonstration is the deliverable, not an input to one. |
| PB-009 | PB-001 | - | The i\* models are deferred to the second release and nothing first-release depends on them. |
| PB-010 | PB-002, PB-007 | PB-008 | The plan evaluation reads the test results; run before the tests, it reports intentions rather than outcomes. |
| PB-011 | PB-001 | - | It carries its own conditions in `../agile/definition-of-done.md`; it depends on the case study reading only. |

## Critical path

```
PB-001 -> PB-002 -> PB-003 -> PB-005 -> PB-007 -> PB-010 -> PB-008
                      |
                      +-> PB-004 -*  (feeds PB-007)
                      +-> PB-006 -*  (feeds PB-007)
```

The chain that decides the release is the one through the operational model, the workers and the
tests: nothing can be demonstrated, and no criterion can be evaluated, until the model runs with its
workers and a scenario has been driven through it. The three defects that were open (`DEF-11`,
`DEF-12`, `DEF-13`; `DEF-13` since fixed at `a62e783`) sit on that chain - `DEF-11` and `DEF-12` on
`PB-003` and `PB-005`, `DEF-13` on `PB-006` - which is why the first release is dated against them
in `../planning/plan-evaluation.md` section 7.

## External dependencies

| Dependency | Owner | Risk if unavailable | Mitigation |
|---|---|---|---|
| The Camunda 8 Run engine and its Java 21 runtime | M3 | No model-level or engine-level evidence can be produced or reproduced, which leaves the worker unit suite as the only run anyone can repeat (`R-02`). | The engine runtime is documented in `../../README.md` and the reproduction steps are written into each evidence file; the unit suite needs no engine. |
| The group's meeting time for sprint planning and the review | M1 | Estimates, the sprint selection and the definition of done stay unagreed, which is `R-06` and the reason velocity cannot be measured. | Tasks are recorded with a first and a second owner so they can be taken over; the plan states the variance instead of absorbing it. |
| The first-release date, 28 September 2026 | M1 | A date that cannot move turns the remaining work into a scope decision: what is deferred to the second release (`AS-14`, `DEF-07`). | The deferrals are recorded in `../planning/project-plan.md` section 1 with the reason, so the decision is visible rather than implied. |
