# Tools

The scripts that lay the diagrams out, export them, and check that the models, the forms and the
workers still agree. They live here, with the project, so that a reader who clones this repository
can reproduce the diagrams and run the checks rather than take them on trust.

Every script resolves the repository root from its own location (`<repo>/tools/<script>.py`), so
they run from any checkout and need no configuration. Run them from anywhere:

```bash
python tools/verify_hprtas_bpmn_bindings.py
```

They need Python 3 and Pillow (`pip install pillow`). Three need more than that: the legibility check
uses the Windows OCR engine through PowerShell, the engine check needs the engine running, and
`export_istar_pngs.mjs` is run with Node 22+ and drives a headless Chrome or Edge - no packages to
install for any of them.

| Script | What it does | Needs |
|---|---|---|
| `bpmn_render.py` | The library the others share: reads a model's diagram interchange and draws it, and provides the geometry checks (`layout_problems`) that the layout and export checks measure against. Not run on its own. | Pillow |
| `relayout_hprtas_bpmn.py` | Redraws the diagram interchange of the models from their own content - one column per step, a lane per row, flows routed round what is already drawn, a text annotation in a band under the lanes where the model has one, and an activity's name inside its box in the models named by `NAMES_INSIDE` (the strategic view, which has to read as one picture) rather than under it - and leaves every other byte of the file alone, so a re-layout diffs as geometry. Pass model paths, or nothing for all of them. | Pillow |
| `export_hprtas_bpmn_pdf.py` | Writes `models/exports/<model>.pdf` and `.png` for every model, one page sized to the diagram so the labels stay readable, draws the model's text annotations and gives each PDF a fixed creation date so that exporting the same model twice produces the same bytes. | Pillow |
| `verify_hprtas_bpmn_bindings.py` | The static contract check, and the one to run before committing a model: every service task names a job type a worker registers, every user task is a Camunda user task with a form that resolves, every form is bound, every worker error code is caught, and the variable contract closes in both directions. | - |
| `verify_hprtas_bpmn_layout.py` | The layout checks: no element or label overlaps another, nothing is drawn outside its lane or the canvas, a name drawn inside its shape fits there, and the BPMN rules the lectures set out hold. Reports the crossings that the routing could not avoid. | Pillow |
| `verify_hprtas_bpmn_export.py` | Reads the exported PNGs back with Windows OCR and measures how much of the text in the model comes back, which is how a label drawn too small or on top of another is caught. The 0.6 threshold is the one the W02 checker uses. | Pillow, Windows OCR, `ocr_diagram_text.ps1` |
| `verify_hprtas_engine_forms.py` | The same binding claim, proved by running rather than by reading: deploys each model with its forms, starts it, and checks that the user task it reaches resolves a form that was deployed. | The engine |
| `bundle_forms_by_core.py` | The check for `forms/form/`, the copies of the forms bundled one folder per `core-N` model, which is the arrangement a demonstration deploys from: re-reads the five models and reports a form missing from a folder, a form in one the model does not bind, a copy that has drifted from its original, or a stray file. | - |
| `istar_geometry.py` | The placement the two i\* drawings share: the hub-and-ring arrangement of an SD view - the actor order chosen to leave the fewest lines crossing, every dependum on the line between the actors it joins, pulled off the other lines and the actor circles - and the row ordering of an SR page. Deterministic, so the same model always lays out the same way. Not run on its own; `istar_render.py` and `convert_istar_to_pistar.py` both call it. | - |
| `istar_render.py` | Draws the i\* models in `../models/socio-technical/` from their JSON sources - the Strategic Dependency drawing and the Strategic Rationale page for each actor - into `../models/exports/istar-*.pdf` and `.png`, and writes `../models/socio-technical/tables.md`. The layout comes from `istar_geometry.py` and is deterministic, so a difference in the output is a difference in the model. | Pillow |
| `verify_hprtas_istar.py` | Checks the two i\* models against each other and against themselves: the same actors in both, a rationale for every dependency, links that resolve, gap elements that carry no contribution, every dependency joining actors the model defines, and no two labels touching - using the same layout code `istar_render.py` draws with, so what it checks is what is drawn. | Pillow |
| `convert_istar_to_pistar.py` | Writes the i\* models into `../models/socio-technical/pistar/` in the piStar tool's own format - the SD drawing and its union page, one SR page per actor, and the two whole-model pages - so the same drawings can be opened and edited in piStar. The layout is computed here and is deterministic, so a difference in the output is a difference in the model. A contribution link that does not enter a quality is written as the means-end link iStar 2.0 makes it, and reported, so nothing the tool writes is a link piStar rejects on load. | - |
| `export_istar_pngs.mjs` | Writes the `../models/exports/pistar-*.png`, one per drawing under `../models/socio-technical/pistar/`, by driving the piStar tool in a headless browser: each model is loaded and the paper's SVG is rasterised onto a white canvas at the model's own pixels - what the tool's **File → Save image** produces. `--only <name>`, `--scale <n>`, `--tool <path>` and `--browser <path>` narrow it. | Node 22+, Chrome or Edge |
| `load_istar_pistar.mjs` | Loads every drawing under `../models/socio-technical/pistar/` into the piStar tool itself, in a headless browser, through the same code path the **Load model** button runs, and reports the tool's verdict: a model it refuses or reports as invalid fails here, cells and links included. The static checks cannot answer this - a file can be exactly what the sources generate and still be one the tool will not open. `--only <name>`, `--src <dir>`, `--tool <path>`, `--browser <path>` narrow it. | Node 22+, Chrome or Edge |
| `verify_hprtas_pistar.py` | Checks the piStar files against the sources they are generated from: every file is rebuilt and compared (`saveDate` aside), the drawing inside it is checked for contact and for cells outside the canvas, every dependency and link has to resolve inside its own file, the pages `pistar/README.md` names have to be the pages piStar reports, and every drawing has to have its exported PNG in `../exports/`. | - |
| `apply_form_validation.py` | Applies the input-validation policy to the forms: a reference takes the reference pattern, an NHS number ten digits, a name has to contain a letter, free text and the numbers the process acts on have bounds, and the dates that are future commitments are marked so the picker does not offer a past day. The tables in this script are the policy - `verify_hprtas_bpmn_bindings.py` imports them and checks the forms against them - so a rule cannot reach one form and miss another. Re-running it is a no-op. | - |
| `check_forms_formjs.mjs` | Imports every `.form` into `@bpmn-io/form-js-viewer`, the library Tasklist renders forms with, and runs each field through the renderer's own validator: an import error is a task that renders no form at all, and a rule that does not fire is a required field a user can submit empty. Asserts a complete confirmation reports nothing, a bad reference is refused with the form's own message, a name of digits alone is refused, and an NHS number that is not ten digits is refused. | Node 22+, `jsdom` and `@bpmn-io/form-js-viewer` installed where Node can find them (`NODE_PATH`) |
| `ocr_diagram_text.ps1` | The Windows OCR helper `verify_hprtas_bpmn_export.py` calls, one tile of a diagram at a time. Not run on its own. | - |

## What is checked, and where

`verify_hprtas_bpmn_bindings.py` is the one that guards the agreements between the three artefact
types, and it is what `forms/README.md` describes from the forms' side. It fails on the drift that
a run cannot see: a form field whose key is not the variable the model reads, a worker error code
with no catch event, a service task pointed at a job type nothing registers.

The definitions of done these checks serve are in `../docs/agile/definition-of-done.md`.

## Working on the models

The order that works is: edit the model, `relayout_hprtas_bpmn.py` to redraw it,
`verify_hprtas_bpmn_layout.py` and `verify_hprtas_bpmn_bindings.py` to check it, then
`export_hprtas_bpmn_pdf.py` to regenerate the exports that are committed with it. A re-layout is
slow - it routes every flow and tries four orderings - so it is worth running it once on the models
you changed rather than on all of them. The export is reproducible: the same model produces the
same PDF bytes, so re-exporting the models you did not change is a no-op in `git status`.

The `.bpmn` files are generated in the sense that their diagram interchange is: a hand-edited
diagram is overwritten by the next re-layout. Edit the model, not the drawing.

## Working on the i\* models

The i\* models have the same shape of workflow, with the JSON in place of the `.bpmn`: edit
`models/socio-technical/sd-hprtas.json` or `sr-hprtas.json`, run `istar_render.py` to redraw the
exports and the tables, then `verify_hprtas_istar.py` to check it. The check is the one that catches
the drift a reader cannot see - a dependency with no rationale, a link to an element that has been
renamed, or a gap element that has quietly acquired a contribution link. Edit the
model, not the diagram: everything in `../models/exports/istar-*` and `tables.md` is generated.

The piStar files under `../models/socio-technical/pistar/` are generated from the same two JSONs, so
a change there means running `convert_istar_to_pistar.py` and then `export_istar_pngs.mjs`, which
redraws the PNGs those files produce; `verify_hprtas_pistar.py` is what
catches a source change that was converted for one form and not the other, and
`node tools/load_istar_pistar.mjs` asks the tool itself to open every file, which is the only check
that catches a drawing the tool will not accept. The tool needs one local patch to open our JSON
files: it ships accepting only `text/plain`, and a browser labels a `.json` file
`application/json` - see `../models/socio-technical/pistar/README.md`.
