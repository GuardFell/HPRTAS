# Models

The BPMN models of the business process, as `.bpmn` files. Build them from a **Camunda 8** template
in Camunda Modeler; a model built from the Camunda 7 template will not deploy.

The models are arranged by the level they describe:

```
models/
|-- strategic/        the high-level business process across the organisation
|-- socio-technical/  i* Strategic Dependency (SD) and Strategic Rationale (SR) models
`-- operational/      the executable processes Camunda runs
```

Everything in `operational/` is meant to be deployed and executed. The strategic and
socio-technical models are views for analysis and are not deployed.

`exports/` holds a readable PDF export of every model, one page per model, drawn from the diagram
interchange in the `.bpmn` itself, together with the same diagram as a PNG for use in a slide deck.
The page is sized to the diagram with element labels at 10 pt, because a diagram this wide cannot be
read at A4. Regenerate them with `python tools/export_hprtas_bpmn_pdf.py`; the diagram interchange
in each model is what it draws from, so a model whose diagram was redrawn has to be exported again.
`tools/README.md` lists what each script does and what it needs.

The i\* models are exported the same way, but from their own sources rather than from a `.bpmn`:
`python tools/istar_render.py` writes `istar-sd-hprtas.pdf` (the SD model, one page) and
`istar-sr-hprtas.pdf` (one page per actor, four pages), with a PNG of each page beside them, and
`python tools/verify_hprtas_istar.py` checks the models behind them; `socio-technical/README.md`
describes what they contain and the notation they use. The PNGs in `exports/` are the same
drawings as exported from piStar - `pistar-*.png`, one per drawing: the SD model
(`pistar-sd-pathway.png`, with `pistar-sd-all.png` and the overview `pistar-sd-overview.png`), one
per actor's SR page, and the whole-model pages (`pistar-sr-all.png`, `pistar-sr-overview.png`) -
drawn from the piStar files
under `socio-technical/pistar/`: regenerate the files with `python tools/convert_istar_to_pistar.py`,
the PNGs with `node tools/export_istar_pngs.mjs`, and check both with
`python tools/verify_hprtas_pistar.py` (`socio-technical/pistar/README.md` documents the mapping).
Both drawings are placed by `tools/istar_geometry.py`, so the PDF pages and the PNGs carry the same
shape.

## Rules for a model

- Name elements meaningfully. `Task_1`, `Gateway_2` and `Process_3` say nothing to a reader.
- An operational process has to be executable and deployable, with its diagram information for
  every element it contains.
- Use a **Camunda user task**, not a *User task (legacy)*. Only a Camunda user task appears in
  Tasklist; a legacy task needs a job worker to move the process on and is invisible to staff.
- Write gateway conditions in **FEEL** (`= accepted`), not in the Camunda 7 style (`${...}`), which
  Camunda 8 does not evaluate.
- One process per file, and the file name matches the process.
- A release is tagged before it is submitted: `release-1.0` for the first release and `release-2.0` for the second, with `submission-2026-09-29` on the tree the project is submitted at.

## Roles and task assignment

Every user task in the operational models carries the **candidate group of the lane it is drawn in**,
so the separation of duties the case asks for is not only drawn but assigned. Eleven lanes carry ten
groups - the two clinical lanes share `consultants`, because the case names one Consultant role for
the tasks an authorised clinical professional performs:

| Lane | Candidate group | Tasks |
|---|---|---|
| Medical Secretaries | `medical-secretaries` | 9 |
| Consultants | `consultants` | 4 |
| Consultant and clinical team | `consultants` | 4 |
| Other Clinical Professionals | `clinical-professionals` | 1 |
| Clinical Nurse Specialist Team | `clinical-nurse-specialists` | 3 |
| Outpatient Bookings Team | `outpatient-bookings` | 13 |
| Treatment and Chemotherapy Bookings Team | `treatment-bookings` | 8 |
| Patient Pathway Coordinators | `pathway-coordinators` | 5 |
| Administrative Management Team | `administrative-management` | 2 |
| Call Handling Team | `call-handling` | 2 |
| Finance Team | `finance` | 10 |

`verify_hprtas_bpmn_bindings.py` checks the assignment rather than trusting it: every user task has
to carry a candidate group, the group has to be one the case study names, and a lane has to be given
the same group wherever it appears in the five modelled processes.

**What this is and is not.** A candidate group is the deployment-side half of role-based access: the
task is addressed to a role, and Tasklist shows it to whoever is in that group. It is **not** an
enforced permission: Camunda 8 Run is installed unsecured for API access, every demonstration signs
in as the same `demo` user, and nothing stops an operator from claiming any task. The authentication,
the group membership and the authorisation behind the groups are the part that is not implemented
(`DEF-07` in `../tests/test-plan.md`), and the lane structure must not be read as evidence that they
are.

## Deploying a model

```bash
curl -X POST "http://localhost:8080/v2/deployments" \
     -F "resources=@models/operational/<model>.bpmn;type=application/xml"
```

A model that binds Camunda Forms also needs those forms deployed, because the user tasks refer to
them by key.
