# Modelling choices and assumptions

> The explanation that goes with the two models in this folder: what they describe, where they come
> from, which dependum type each dependency was given, the one judgement the workshop drawing left
> open, and what the models deliberately do not say. The diagrams are in `../exports/` and their
> content is tabulated in `tables.md`.

## 1. What these models are, and where they come from

`sd-hprtas.json` and `sr-hprtas.json` are the **i\* Strategic Dependency** and **Strategic
Rationale** models of the referral pathway, redrawn from the **workshop goal model** in iStar 2.0.
They are views for analysis: they are not deployed, no worker is bound to them, and nothing in
`models/operational/` depends on them. What they carry that the operational models cannot is the
reasoning - who does what in the pathway, and what each actor needs from the others.

The source of the redrawing is the workshop material:

- the goal model as saved from piStar (`材料/goalModel.txt`, saved 12 October 2026), and
- the drawing of it, as it appears in the course material (`材料/goalDiagram (1).png`).

Where the two disagree, the drawing is followed - it is the version the reader has seen.

The model describes the pathway the **case study** describes: the patient, the reception desk, a
doctor and a specialist are all participants there, and the case's own pathway - registration, the
new patient appointment, the diagnosis, the review of test results - is what the workshop model
abstracts. It is **not** a model of the delivered release: the four actors here are the ones the
workshop material names, not the twenty lanes and pools the operational models carry, and no
element below claims a requirement id or a BPMN element. Reading the models against the delivered
processes is future work.

## 2. Actors

An **actor** is a party the pathway runs between: the party whose goal an element belongs to, and
the party a dependency is owed by or owed to. The four are:

| Actor | Kind | What it is here |
|---|---|---|
| `patient` | agent | The person the pathway exists for; the depender in every dependency but one. |
| `receptionist` | agent | The front desk: registration, appointments and payment. Named as the workshop model spells it ("Receptionst"). |
| `doctor` | role | The clinician who holds the diagnostic work and asks the specialist for the test review. |
| `specialist` | role | The expert the doctor consults for the review of the test results. |

`agent` and `role` follow iStar 2.0: an **agent** is a concrete party (a person), a **role** is a
characterisation of responsibilities that a party occupies. The doctor and the specialist are
roles in that sense - a particular clinician occupies them - while the patient and the receptionist
are the concrete people the pathway names.

## 3. The dependum types, and the one judgement

Every dependency is read off the workshop model; six are drawn there and the seventh is the
doctor's request for a specialist review. Each was given the dependum type its iStar 2.0 reading
supports:

| # | Depender → Dependee | Type | Why this type |
|---|---|---|---|
| SD-01 | patient → receptionist | goal | Registration is an outcome the desk is left to achieve, not a step-by-step task the patient directs. |
| SD-02 | patient → receptionist | task | Booking the appointment is work the desk carries out. |
| SD-03 | patient → receptionist | softgoal | The workshop model draws quick booking as a quality of the service: met to a degree, not met or not met. |
| SD-04 | patient → receptionist | resource | The payment taken, and the receipt given, are what the patient receives in return for the fee. |
| SD-05 | patient → doctor | goal | The diagnosis is the doctor's to reach. |
| SD-06 | patient → doctor | goal | The cancer type and site is the goal behind the diagnosis; the patient depends on the doctor for it. |
| SD-07 | doctor → specialist | task | The review of the test results is work the specialist performs. |

**The one judgement: SD-07, and which way it runs.** The workshop drawing shows a line between the
doctor and the specialist carrying "Review test results", and it does not settle which actor
depends on which - the line's end marker is not legible where it meets the doctor's boundary, and
the same text appears both as the specialist's element and, as an alternative route, inside the
doctor's rationale. The model takes the reading the clinical semantics support: the **doctor**
depends on the **specialist** for the review, because the review is the expertise the doctor does
not hold and therefore requests. The deliverable is drawn as a task, since a review is something
the specialist does rather than a thing handed over. This is the one place the model adds a
direction the source leaves open; a reader who disagrees with it can reverse the dependency in
`sd-hprtas.json` without touching anything else.

## 4. What the model deliberately does not say

- **Which HPRTAS lane or pool each actor corresponds to.** The model names four parties; the
  delivered release names twenty, and they do not line up one-to-one (the case's registration and
  booking work is spread across more than one administrative team). Mapping the two is a reading of
  the release rather than of the workshop model, and is left for the work that reads the models
  against the delivered processes.
- **Any requirement, business rule or model element.** The workshop model is not written against
  the requirements document, and inventing links to `FR-*` / `BR-*` ids would claim a traceability
  the source does not carry.
- **Any 'gap' element.** The earlier version of these models recorded needs the release does not
  meet as gap elements; the workshop model states no such needs, so this model has none. The check
  that a gap element carries no contribution link stays in `verify_hprtas_istar.py`, ready for the
  next version that introduces one.
- **The steps of the pathway the workshop model leaves out** - the referral arriving from the GP,
  the treatment booking, the letters and the follow-up. They belong to the operational models
  (`core-1` … `core-5`), not here.
