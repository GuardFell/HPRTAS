# Socio-Technical (i\*) Models

The i\* models of the referral pathway, in both notations: **Strategic Dependency** (SD), which
says who depends on whom for what, and **Strategic Rationale** (SR), which says what each actor is
trying to achieve and how.

The models describe the **referral pathway as the workshop goal model draws it** - four actors:
the **Patient**, the **Receptionst** at the front desk, the **Doctor**, and the **Specialist** the
doctor consults. They are views for analysis: they are not deployed, and nothing in the repository
is bound to them. What they carry that the other models cannot is the reasoning - who does what in
the pathway, and what each actor needs from the others.

## What is here

| File | What it is |
|---|---|
| `sd-hprtas.json` | The SD model: 4 actors and 7 dependencies - the six the workshop model states, drawn from the patient outward, and the doctor's request for a specialist test review. |
| `sr-hprtas.json` | The SR model: one page per actor - 5 elements for the patient, 6 for the reception desk, 10 for the doctor and 3 for the specialist - each citing the SD dependencies it accounts for. |
| `modelling-choices.md` | The explanation: what an actor is here, which dependum type each dependency was given and why, and the one judgement the workshop drawing left open. **Read this with the diagrams.** |
| `tables.md` | **Generated.** Every dependency and every element as a table, for reading and for tracing. |
| `pistar/` | The same models in the piStar tool's own format, one file per drawing, for opening and editing in the tool. `pistar/README.md` documents the mapping. |
| `README.md` | This file. |

The diagrams are in `../exports/`: `pistar-sd-pathway.png` (the SD model, one canvas),
`pistar-sr-<actor>.png` (one page per actor), `pistar-sr-overview.png` (every actor's goals and
tasks) and the whole-model pages `pistar-sd-all.png` / `pistar-sr-all.png`. The repository's own
renderer draws the same pages as `istar-sd-hprtas.pdf` (one page) and `istar-sr-hprtas.pdf` (four
pages), beside them a PNG of each page; `python tools/istar_render.py` writes those, and both PDFs
are committed in `../exports/` as the readable PDF form of these models.

## The notation

iStar 2.0, the notation the i\* literature uses. Every page carries a legend as well.

| Shape | Element |
|---|---|
| circle | **actor** - the patient, the reception desk, the doctor, the specialist |
| rounded rectangle | **goal** - something to be achieved, left to the actor to achieve |
| hexagon | **task** - something to be carried out |
| rectangle | **resource** - something supplied or consumed |
| cloud | **soft goal** - a need met to a satisfactory degree rather than met or not met |

**In the SD model** a dependency is drawn as a dependum - in the shape of its own type - on the line
between the two actors, with the arrow at the actor depended on. So reading `A --[goal: text]--> B`
gives "A depends on B for text". The drawing is a hub with a ring of actors round it, the way the
workshop model draws it: the patient sits at the centre (every dependency the model states runs
from the patient), the reception desk, the doctor and the specialist sit on the ring, and every
dependum sits on the line between the two actors it joins. Several dependencies between the same
pair are drawn as several lines side by side. `istar_geometry.py` holds that placement, so the
piStar drawing and the renderer's PDF carry the same shape.

**In the SR model** an actor boundary carries that actor's elements, grouped by kind: soft goals at
the top, then hard goals, then tasks, then resources. Each row is ordered so that linked elements
sit near each other, a task refined into its parts keeps those parts beside it, and a link that
would otherwise be drawn across an element takes a bend round it, so a page reads as rows joined by
short lines. The links are:

| Link | Meaning |
|---|---|
| `means-end` | a line with an arrowhead at the element it serves - one way, among the alternatives, of achieving or carrying it out |
| `decomposition` | a line with a bar at the parent, labelled AND or OR: the parts that together make it up |
| `needed-by` | a line with a small circle at its task end, from a resource the task needs to the task that needs it |
| `contribution` | a line with an arrowhead, labelled `make`, `help`, `some+`, `unknown`, `some-`, `hurt` or `break` |

**A dependency is drawn only where the workshop model states one.** An interface the drawing leaves
unstated - there is one, which `modelling-choices.md` section 3 names - is recorded there rather
than invented here.

## What the models say, in one line each

- **Every dependency the model draws runs from the patient, except one.** The patient depends on
  the reception desk for registration, an appointment, a quick service and the payment, and on the
  doctor for the diagnosis and the determination of the cancer type and site. Nothing depends on
  the patient: the hospital cannot make a patient attend or pay, so what it needs from the patient
  can only be asked for.
- **The booking quality is a soft goal, as the workshop model draws it.** How quickly the desk
  answers is met to a degree rather than met or not met, so `quick booking` is a softgoal dependum
  and not a task.
- **The diagnostic work is the doctor's own, and the diagnosis decomposes.** Reviewing the
  patient's history, performing clinical appraisal and assessing whether admission is needed are
  the AND parts of `Diagnose the patient`; the cancer type and site is reached with the doctor's
  own expertise or by the specialist's review of the test results, both means-end alternatives.
- **The one dependency that leaves the patient is the doctor's.** The doctor depends on the
  specialist for the review of the test results, drawn as a task because the review is work the
  specialist performs.

## Regenerating and checking

The JSON files are the models; the diagrams and the tables are generated from them, so the two cannot
drift apart.

```bash
python tools/istar_render.py            # redraw models/exports/istar-*.png|pdf and tables.md
python tools/istar_render.py --pdf-only # the pages only, no PNGs
```

```bash
python tools/verify_hprtas_istar.py     # check the models against each other and the drawings
```

```bash
python tools/convert_istar_to_pistar.py # rebuild the piStar files under pistar/ from the same sources
node tools/export_istar_pngs.mjs        # redraw the pistar-*.png in models/exports/ from those files
python tools/verify_hprtas_pistar.py    # check them against the sources and their own layout
node tools/load_istar_pistar.mjs        # ask the piStar tool itself to open every file
```

The check is the one to run before committing a change here. It fails on: an actor in one model and
not the other; a dependency with no rationale in the SR model, or an SR element citing a dependency
that does not exist; a link that points at nothing; a gap element that carries a contribution; a
dependency joining an actor the model does not define; and any pair of labels the layout has brought
into contact. It uses the same layout code the renderer uses, so what it checks is what is drawn.
`tools/README.md` says what each script needs.

The piStar files under `pistar/` are generated from the same two JSONs, and
`verify_hprtas_pistar.py` checks them the same way: each file is rebuilt from the sources and
compared against what is committed, the drawing inside it is checked for contact and for cells
outside the canvas, and every reference and link in it has to resolve.

Nothing here needs an engine, and nothing here needs the models to be deployable - they are not.
