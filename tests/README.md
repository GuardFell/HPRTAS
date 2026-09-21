# Testing

The test plan and the evidence that the scenarios were run.

`test-plan.md` is the plan: what is in scope and what is deliberately not, the acceptance criteria,
the test scenarios, the record of each execution, the defects and limitations found, and the
simulated components the testing depends on.

`evidence/` holds the output of the runs. `evidence/README.md` describes how the files are named and
what each one records.

## What the testing covers

- **Acceptance criteria** are clear and measurable, and each is linked to the requirement or the
  business rule it demonstrates.
- **Every piece of evidence identifies the version under test**, a tag or a commit, so a result can
  be tied to the state of the repository that produced it.
- **Failing results are recorded as plainly as passing ones.** A criterion that is not met is
  explained rather than removed, and a limitation of a simulated component is written down where it
  affects what the tests can show.

The tests themselves are run from the worker project; `../workers/README.md` describes the four
commands and what each one covers.
