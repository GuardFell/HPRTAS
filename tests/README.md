# Testing

The test plan and the evidence that the scenarios were run.

`test-plan.md` is the plan: what is in scope and what is deliberately not, the acceptance criteria,
the test scenarios, the record of each execution, the defects and limitations found, and the
simulated components the testing depends on.

`evidence/` holds the output of the runs. `evidence/README.md` describes how the files are named and
what each one records.

`exports/test-plan.docx` is the same plan as a readable Word document, for reading and printing
rather than editing. It is generated from the Markdown by `exports/render-test-plan.py`, so the
Markdown stays the source of record and the two cannot drift: re-run the script after changing the
plan.

## Where it stands

The first release is tagged `release-1.0`. The runs recorded here were produced before that tag was
made, so **each result names the commit it was produced at**. `test-plan.md` opens with the version
the plan is written against; every evidence file names its own.

At `c8556ba`, seven of the ten acceptance criteria are met, three are not, and thirteen defects and
limitations are recorded. The evaluation of what that means for the project - which requirements are
supported, which are not, and where the plan and the delivery diverged - is in
`../docs/planning/plan-evaluation.md`, which is the companion to this plan rather than a summary of
it.

## What the testing covers

- **Acceptance criteria** are clear and measurable, and each is linked to the requirement or the
  business rule it demonstrates.
- **Every piece of evidence identifies the version under test**, a tag or a commit, so a result can
  be tied to the state of the repository that produced it.
- **Failing results are recorded as plainly as passing ones.** A criterion that is not met is
  explained rather than removed, and a limitation of a simulated component is written down where it
  affects what the tests can show.
- **Evidence has a level.** A worker test proves what a worker does and nothing about the model it
  serves, so a result is recorded at the level it was produced at and not carried up. Two of the
  defects in `test-plan.md` section 6 were invisible at worker level for exactly that reason.

The tests themselves are run from the worker project; `../workers/README.md` describes the four
commands and what each one covers. `mvn test` needs no engine; the other three do.
