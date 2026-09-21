# Requirements

`requirements.md` holds the requirement set and the traceability that links each requirement to
where it is implemented and where it is tested.

Identifiers are prefixed by the kind of statement they are:

- `FR-###` — a functional requirement, defined in `requirements.md`.
- `NFR-###` — a non-functional requirement, defined in `requirements.md`.
- `BR-###` — a business rule taken from the case study, defined in `../case-study-summary.md`.
- `AS-###` — an assumption made where the case study is silent, defined in
  `../case-study-summary.md`.

Every requirement is traceable to a source: a stage of the pathway, a stated business rule, or a
documented assumption. Identifiers are stable once assigned, and the columns that record where a
requirement is implemented and tested are kept pointing at the real artefacts so the traceability
does not drift away from the models, the workers and the evidence.
