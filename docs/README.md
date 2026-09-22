# Documentation

Everything that describes the project rather than implements it.

`case-study-summary.md` is the shared understanding of the case: the participants, the process
itself, the business rules, the exceptions and the assumptions made where the case is silent.

`requirements/` holds the functional and non-functional requirements, the business rules derived
from the case study, and the traceability that links each one to where it is implemented and where
it is tested.

`justification-of-decisions.md` explains why the operational process is structured the way it is:
the participant boundaries, the task allocation, the gateway fallbacks, the external interactions,
the exception handling, the assumptions, the alternatives rejected and the trade-offs accepted,
including the parts that are not done.

`backlog/` holds the product backlog, the task breakdown that follows from it, and the dependencies
between the items.

`planning/` holds the plan: the scope of each release, the estimation approach, the allocation of
work, the risks, the timeline and the comparison of what was planned against what actually
happened; and the evaluation made against it, which also judges each requirement the models carry
and the alignment between the models, the forms and the workers.

`agile/` holds the working agreements: what has to be true before work counts as done, and the
record of the contribution behind each task.

The documents are written in English and kept current as the project runs, because they are the
record of what was done rather than a plan written up at the end. The one thing they are not is
independent: `planning/plan-evaluation.md` is written against `requirements/requirements.md` and
`../tests/test-plan.md`, and reading any one of the three alone gives a partial picture of where the
project actually stands.
