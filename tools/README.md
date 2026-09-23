# Tools

The scripts that lay the diagrams out, export them, and check that the models, the forms and the
workers still agree. They live here, with the project, so that a reader who clones this repository
can reproduce the diagrams and run the checks rather than take them on trust.

Every script resolves the repository root from its own location (`<repo>/tools/<script>.py`), so
they run from any checkout and need no configuration. Run them from anywhere:

```bash
python tools/verify_hprtas_bpmn_bindings.py
```

They need Python 3 and Pillow (`pip install pillow`). Two of them need more: the legibility check
uses the Windows OCR engine through PowerShell, and the engine check needs the engine running.

| Script | What it does | Needs |
|---|---|---|
| `bpmn_render.py` | The library the others share: reads a model's diagram interchange and draws it, and provides the geometry checks (`layout_problems`) that the layout and export checks measure against. Not run on its own. | Pillow |
| `relayout_hprtas_bpmn.py` | Redraws the diagram interchange of the models from their own content - one column per step, a lane per row, flows routed round what is already drawn - and leaves every other byte of the file alone, so a re-layout diffs as geometry. Pass model paths, or nothing for all of them. | Pillow |
| `export_hprtas_bpmn_pdf.py` | Writes `models/exports/<model>.pdf` and `.png` for every model, one page sized to the diagram so the labels stay readable. | Pillow |
| `verify_hprtas_bpmn_bindings.py` | The static contract check, and the one to run before committing a model: every service task names a job type a worker registers, every user task is a Camunda user task with a form that resolves, every form is bound, every worker error code is caught, and the variable contract closes in both directions. | - |
| `verify_hprtas_bpmn_layout.py` | The layout checks: no element or label overlaps another, nothing is drawn outside its lane or the canvas, and the BPMN rules the lectures set out hold. Reports the crossings that the routing could not avoid. | Pillow |
| `verify_hprtas_bpmn_export.py` | Reads the exported PNGs back with Windows OCR and measures how much of the text in the model comes back, which is how a label drawn too small or on top of another is caught. The 0.6 threshold is the one the W02 checker uses. | Pillow, Windows OCR, `ocr_diagram_text.ps1` |
| `verify_hprtas_engine_forms.py` | The same binding claim, proved by running rather than by reading: deploys each model with its forms, starts it, and checks that the user task it reaches resolves a form that was deployed. | The engine |
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
you changed rather than on all of them.

The `.bpmn` files are generated in the sense that their diagram interchange is: a hand-edited
diagram is overwritten by the next re-layout. Edit the model, not the drawing.
