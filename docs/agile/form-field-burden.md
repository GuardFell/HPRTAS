# Form field burden and channel options

Two requirements ask for a review of the forms rather than a claim about them, and the review had not
been done. This records what was counted, how, and what the count does and does not show.

| Requirement | What it asks for | Where its verification is stated |
|---|---|---|
| NFR-009 | "Administrative recording must not reduce the time clinical staff have available for patient care." | "Count the fields and steps a clinician must complete in the clinical tasks (the forms in `forms/`) and review with the group." - `../requirements/requirements.md` |
| NFR-011 | "Patients must be able to receive communication in their preferred form, including accessible formats, translation support and assistance from an authorised representative." | "The channel preference is recorded on the user tasks and used by [the correspondence service]; review the options offered in the forms." - `../requirements/requirements.md` |

**Version measured.** `57b3c1d`, working tree clean. Every figure below is read out of the committed
`.form` files and the committed models, so it can be reproduced rather than taken on trust. A field's
`key` is the process variable it writes, so the number of fields in a form is the number of variables
its task supplies.

## What was counted

For each `.form` file: its leaf fields (everything that is not a visual `group`), how many top-level
sections it is divided into, and how many of those fields carry a `description`. For each user task
in `models/operational/`: the lane it sits in and the field count of the form it binds. A task's
burden is its bound form's field count, because a user task cannot be completed without its form.

## The totals

| | |
|---|---|
| Forms | 41 |
| Leaf fields across all forms | 293 |
| Sections across all forms | 55 |
| Fields carrying a description | 66 of 293 |
| User tasks in the four operational models | 56 elements over 54 distinct ids |
| Largest single form | 10 fields (`referral-check`, `referral-review`, `appointment-confirmation`, `cancellation-record`) |

## NFR-009 - the recording burden on clinical staff

The lanes that carry clinical responsibility, and what they ask a clinician to fill in:

| Lane | Tasks | Total fields | Mean per task | Largest form |
|---|---|---|---|---|
| Consultants | 4 | 35 | 8.8 | 10 (`referral-review`) |
| Consultant and clinical team | 4 | 32 | 8.0 | 9 (`patient-assessment`) |
| Clinical Nurse Specialist Team | 3 | 23 | 7.7 | 8 (`pathway-review`, `urgent-escalation`) |
| Other Clinical Professionals | 1 | 7 | 7.0 | 7 (`pre-cycle-review`) |
| **All clinical lanes** | **12** | **97** | **8.1** | |

The decision-bearing tasks, isolated from the purely recording ones, because NFR-009 is about the time
a decision takes and not only about keying:

| Task | Form | Fields | What the clinician is deciding |
|---|---|---|---|
| `N_C_ClinicalReview` | `referral-review` | 10 | accept, reject, request further information, or redirect a referral |
| `N_CL_AssessPatient` | `patient-assessment` | 9 | diagnosis, whether options were discussed, consent |
| `N_CL_AuthoriseTreatment` | `treatment-booking` | 7 | proposed treatment, start date, cycles, clinical authorisation |
| `N_CL_ModifyTreatment` | `treatment-modification` | 8 | whether a treatment modification is authorised, and whether it affects the charge |
| `N_CL_UrgentAuthorise` | `urgent-authorisation` | 8 | whether to start treatment before the funding question is settled |
| `N_OC_PreCycleReview` | `pre-cycle-review` | 7 | fitness to continue between chemotherapy cycles |
| `N_C_PrepareLetter` | `clinic-letter` | 9 | the clinical content of the letter |
| `N_C_ApproveLetter` | `clinic-letter` | 9 | clinical approval of the letter |
| `N_C_ReviewClinicalError` | `clinical-error-review` | 7 | whether a suspected clinical error is real, and the correction |
| `N_CNS_ClinicalAdvice` | `clinical-advice` | 7 | clinical advice on an enquiry |
| `N_CNS_PathwayReview` | `pathway-review` | 8 | continue, delay or discharge |
| `N_CNS_HighlightUrgent` | `urgent-escalation` | 8 | whether an urgent clinical concern is present |
| `N_MS_ProcessLetter` (administrative check of clinical content) | `letter-processing` | 8 | whether the letter is administratively correct or is returned |
| `N_AM_ContactConsultant` | `consultant-contact` | 6 | chasing an outstanding letter |
| **Total** | | **111** | mean 7.9 over 14 tasks |

**What the count shows.** No clinical task asks for more than ten fields, the mean is 8.1 across the
clinical lanes against 7.3 across the whole model set, and the clinical decision itself is always one
enumerated field plus a reason - the fields around it are the identifiers, the dates and the summary
that the case requires to be recorded (`BR-03`, `BR-11`, `BR-25`, `BR-27`). The recording burden is
therefore spread across the record the case demands rather than concentrated in any one form.

**What the count does not show, and should not be read as showing.**

- It is a **field count, not a time measurement.** No clinician was timed and no usability test was
  run. A form with four fields can cost more time than one with ten if the four are hard to answer or
  the values are not to hand.
- It says nothing about how long it takes to **find** the right task in Tasklist, or to read the
  patient's history before deciding. Those are outside the forms.
- `NFR-009` is a **Usability** requirement and this is one of its two stated verifications; the other
  is the review with the group, which has not happened.
- Four forms are shared between tasks, so counting per task counts a shared form more than once.
  That is deliberate here, because a task's burden is what the person completing that task faces.
- The clinician's own clinical decision is one field. If the concern behind NFR-009 is the *decision*
  rather than the *recording*, the number to argue about is 14 decision tasks, not 111 fields.

The one thing the count does support is a comparison, and the comparison is unflattering in one
place: **`referral-review` at 10 fields is the joint-largest form in the repository and it is the
Consultant's core decision form.** Three of its fields are the decision, its reason and the
authorising clinician; the rest are identifiers and the clinical summary. If the group wants to
reduce clinical recording time, that form is where to start, and the reduction would come from
removing fields the model does not read rather than from removing the decision.

## NFR-011 - the communication channels the forms offer

Four forms carry the `channelPreference` field, all as a `select`, and all four offer the same four
options:

| | |
|---|---|
| Field | `channelPreference`, label `Channel Preference` |
| Type | `select` (an enumerated choice, so the value written is one the correspondence service accepts) |
| Options | `Post`, `Digital`, `Authorised Representative`, `Accessible Format` |
| Forms | `booking-request`, `appointment-confirmation`, `clinic-letter`, `dispatch-failure` |

Measured against what NFR-011 and FR-051 ask for:

| Asked for | Offered | Status |
|---|---|---|
| Postal communication | `Post` | met |
| Digital communication | `Digital` | met |
| Accessible formats | `Accessible Format` | met as a recorded preference |
| Assistance from an authorised representative | `Authorised Representative` | met as a recorded preference |
| Translation support | - | **not offered as a channel choice** |

**What this does not show.** The option list is complete apart from translation support, but the
recorded preference is not honoured end to end: the correspondence service records and echoes the
channel, and there is no printing, postage, delivery confirmation, alternative-format production or
interpreter booking behind any of the four. `tests/test-plan.md` section 7 states this as a
limitation of the simulation, and `../planning/plan-evaluation.md` records FR-051 as **Partially
supported** for the same reason. An option in this list is a decision the staff can record; it is not
a format the patient is shown to receive.

Translation support is the one gap in the enumerated list rather than behind it: a translator cannot
be requested by choosing a value here, and nothing on any form records a language or an interpreter
requirement.

## Accessibility of the forms themselves

`LO1` and the case's accessibility requirements are about the patient's communication, not about the
form widgets, but the forms are the interface the staff use and their accessibility was worth
checking. What is present, read from the 41 committed forms:

- **Every field has a `label`**, and `@bpmn-io/form-js` associates a field's label with its control,
  so every one of the 293 fields is reachable by a screen reader as a named control rather than an
  unlabelled input.
- **66 of 293 fields carry a `description`.** The ones that matter most are the boolean fields whose
  `false` answer is meaningful - `documentsComplete`, `consentGiven`, `fitToContinue`,
  `suspectedClinicalError`, `paidAppointment`, `urgentClinicalConcern`, `affectsCharge`,
  `letterWithinSevenDays`, `pathwayClosed`. These cannot be marked `required` (a required checkbox
  must be ticked, which would make the `false` branch unreachable from the form), so the description
  is the only thing telling the person filling it in what leaving it unticked means. That rule and
  the reason for it are in `../../forms/README.md`.
- **Long forms are divided into sections** - 55 groups across the 41 forms, two or three on the
  longer ones - so a form is read in parts rather than as one list of fields.
- **Enumerated answers use dropdowns**, 26 forms, so the value written is always one the gateway
  condition or the worker validation accepts, and a mistyped free-text value cannot reach a gateway.

What has **not** been done, and should not be claimed:

- **No screen-reader session has been run.** The label association above is read from the form
  schemas and from how the renderer works; nobody has driven these forms with NVDA, JAWS or VoiceOver,
  and no keyboard-only pass has been recorded.
- **No contrast, font-size or zoom check** has been made against WCAG 2.2 at any level, and no
  conformance level is claimed.
- **The forms are unreachable without a signed-in user** (`DEF-07`, `DEF-08`), so their accessibility
  cannot be demonstrated end to end in this prototype at all: the only observed use of a form is an
  API call completing a task.
- Only 66 of 293 fields carry a description, so the other 227 are label-only. That is adequate for a
  date or an identifier and may not be for a field whose expected value is a convention.

## What was changed as a result of this review

Nothing in `forms/`. The review found one enumerated gap - translation support in the channel list -
and adding a value to `channelPreference` would change a variable contract that
`workers/src/main/java/uk/ac/uwe/hprtas/workers/services/CorrespondenceService.java` validates, so it
is a decision with the worker owner rather than a form change. It is recorded here as a gap with a
proposed action instead of being made silently.

| Finding | Proposed action | Owner |
|---|---|---|
| `channelPreference` offers no translation or interpreter option (NFR-011, FR-051) | Add a value to the list and accept it in the correspondence service, or record the decision not to | M4 with M3 |
| No screen-reader or keyboard pass over the forms | Run one against a deployed Tasklist and record it where the other evidence is kept | M4 |
| `referral-review` is the largest form and is the Consultant's decision form (NFR-009) | Review its 10 fields against what `N_C_ClinicalReview`'s gateways and the case actually read, and drop what nothing reads | M4 with M1 |

## Reproducing the numbers

The counts are read from the committed artefacts, not typed in. The measurement script is not in the
repository (it was written for this review); it reads `forms/*.form` as JSON, walks each form's
`components` collecting the leaves (descending into `group.components`), parses
`models/operational/*.bpmn` for `<bpmn:userTask>` elements and their `<zeebe:formDefinition formId>`,
and maps each task to its lane through the model's `<bpmn:lane>` / `<bpmn:flowNodeRef>` elements.
Keying the task map by **model and id together** matters: `N_OB_CorrectRequest` and
`N_OB_RecordDispatchFailure` each appear once in `core-1` and once in `core-4`, and keying on the id
alone silently drops one of each pair and reports 54 where there are 56.

`python ../../tools/verify_hprtas_bpmn_bindings.py` checks the binding half of this without an engine.
