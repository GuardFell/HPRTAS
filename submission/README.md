# Submission package - 22 October 2026

The files for the second submission, gathered so that what goes to Blackboard can be taken from one
folder, and so that everything else the submission rests on can be reached from the path named here.

## The version this package is taken from

| | |
|---|---|
| Repository | https://github.com/GuardFell/HPRTAS |
| Revised version (this submission) | tag **`submission-2026-10-22`** |
| Initial release | tag **`submission-2026-09-29`** (the tree `release-2.0` also names) |
| First-release baseline | tag **`release-1.0`** (`69e01a1`) |
| Sprint-review snapshots | **`sr3-2026-10-15`** (`8d6f3ce`) and **`sr4-2026-10-22`** |

The repository at the tag is the source of record. The copies below are byte-identical to the files
that tag holds; if a source changes before the deadline, refresh its copy in the same commit rather
than letting the two drift apart.

The submission goes through Blackboard. The repository link above is what covers the artefacts that
are not copied here.

## AISD presentation (UFCEP6-0-3)

| Deliverable | Where it is | In this folder |
|---|---|---|
| Presentation slides | `../HPRTAS - Second Release.pptx` - 16 slides, references and evidence links on the last | `AISD-presentation/HPRTAS - Second Release.pptx` |
| Updated solution | the repository at `submission-2026-10-22`; `../README.md` "The versions, and which to read" and "Running it" | the repository link |
| Feedback response record | `../docs/agile/response-to-feedback.md` - the 21 sheets, point by point, with the outstanding points and their reasons | `AISD-presentation/response-to-feedback.md` |
| Planning evidence | `../docs/planning/project-plan.md` (sections 1-7, the Sprint 3 and Sprint 4 tables in section 6), `../docs/backlog/`, `../docs/agile/sprint-reviews.md`, `../docs/agile/definition-of-done.md`, `../tests/test-plan.md`, `../tests/evidence/` | read from the repository; the two Word renderings are in `shared/` |
| Marked standups (SU3, SU4) | live at the scheduled sessions; the individual updates rest on `../docs/agile/contribution-matrix.md` and `../docs/backlog/sprint-backlogs.md` | live |
| Marked sprint reviews (SR3, SR4) | `../docs/agile/sprint-reviews.md`, each against the version its snapshot tag keeps | live |

## BPM&EA full portfolio (UFCEP4-0-3)

| Deliverable | Where it is | In this folder |
|---|---|---|
| Strategic BPMN models | `../models/strategic/` - the two views; their abstraction level, external interactions and the operational elements each activity merges are in their README | `BPM-EA-portfolio/strategic-bpmn/` |
| i\* SD and SR models | `../models/socio-technical/` - the two sources, `modelling-choices.md`, `tables.md` and the nine piStar files | `BPM-EA-portfolio/istar/` |
| Requirements alignment evaluation | `../docs/planning/plan-evaluation.md` sections 2-4, and the traceability table in `../docs/requirements/requirements.md` | `shared/plan-evaluation.docx`; the traceability table is read from the repository |
| Acceptance criteria evaluation | `../tests/test-plan.md` sections 3, 5 and 6, and `plan-evaluation.md` section 6 with the initial and the revised verdicts | `shared/test-plan.docx` |
| Supporting files | editable BPMN, readable PDF exports, editable i\* sources, and the written evaluation in Word | the three folders above |

## What is in this folder

```
submission/
├── README.md                                  this file
├── AISD-presentation/
│   ├── HPRTAS - Second Release.pptx            the slides
│   └── response-to-feedback.md                 the feedback response record
├── shared/
│   ├── plan-evaluation.docx                    alignment, plan and acceptance-criteria evaluation
│   └── test-plan.docx                          the test plan and its execution record
└── BPM-EA-portfolio/
    ├── strategic-bpmn/                         2 .bpmn, 2 .pdf, the models' README
    └── istar/                                  2 source JSON, 9 piStar JSON, 2 .pdf,
                                                modelling-choices.md, tables.md
```

## Not copied here, and why

- **Planning evidence, backlog records, test evidence and the requirement traceability** are read
  from the repository at the tag. Copying them would duplicate the source of record, and a duplicate
  is a second thing that has to be kept true.
- **The operational models, the forms and the workers** are the solution the presentation
  demonstrates; they are read from the repository, where the run instructions are.
- **The two marked standups and the two marked sprint reviews** are live sessions, not files. The
  records they produce are `../docs/agile/sprint-reviews.md`, the contribution matrix and the sprint
  backlogs.
