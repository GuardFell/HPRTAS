# Strategic Models

The high-level view of the business process: the stages of the pathway the release supports and the
team responsible for each. They show how the work fits together across the organisation before it is
narrowed down into the processes Camunda runs.

`patient-pathway-all-entities.bpmn` is the whole pathway, with a lane for each of the six teams that
owns a stage of it - **ten flow objects**: a message start event, the eight stages, one end event.

`referral-to-treatment-pathway.bpmn` is the same pathway up to the point where treatment is
delivered - **eight flow objects**: a message start event, the six stages, one end event. The stages
after treatment, and the patient's own part in them, are in the model above.

These models are views for analysis. They contain no service tasks and are not executable, so they
are not deployed; Camunda rejects a deployment that contains no executable process. The processes
that are deployed are in `../operational/`, and the strategic activities connect to them there.

## Why one pool

The lecture's guidance for a strategic model is to abstain from multiple pools, and that is what
these are: **one pool, the Hospital Trust, with a lane for each team that owns a stage** - six lanes in
`patient-pathway-all-entities.bpmn`, five in `referral-to-treatment-pathway.bpmn`. A lane with no
activity of its own is not drawn: the teams whose work sits inside a merged stage are named in that
activity's documentation instead.

The parties outside the hospital - the patient, the referring organisation, the scheduling,
correspondence, treatment and payment services, and the funding organisation - are not drawn as
pools here, because the picture then needs a message flow crossing the page for every exchange and
stops being readable at one sheet. What each of them exchanges with the hospital is stated instead
in the documentation of the activity that makes the exchange, and the same relationship is carried
as a dependency in `../socio-technical/`. Where the boundary is *implemented* - the black-box pool
and its message flows - is in `../operational/`, which is the level at which it matters.

## How the activities are drawn

In `patient-pathway-all-entities.bpmn` each activity carries its **name inside the box**, which is how a
task is drawn by default in Camunda Modeler and keeps the model at the level of a single picture: the
box is cut to the name, the lane needs no band under the shape for a label, and the whole pathway
reads as one row of activities. The other model labels under the shape, like the operational
diagrams. `tools/relayout_hprtas_bpmn.py` holds that choice per model (`NAMES_INSIDE`) so a re-layout
reproduces it, and `tools/verify_hprtas_bpmn_layout.py` checks that a name drawn inside its box fits
there.

## The two notes

Each model carries two text annotations at the foot of the pool, and they are its only artifacts (the
lecture allows up to eight):

| Note | Why it is there |
|---|---|
| *Customer: the patient, referred by a GP or another hospital.* | The strategic-model requirements ask that it be clear who the customer of the process is. |
| *Systems involved: the scheduling, correspondence, treatment and payment services.* | The same requirements ask a strategic model to carry information about computer systems and/or human tasks. Every activity here is a human task; this note names the systems those tasks exchange information with, each of which is a black-box pool with its own message flows in `../operational/`. |

They are drawn inside the pool, under the last lane, rather than attached to one activity, because
they describe the process as a whole; the layout works out the band and the pool grows to hold it.

## The level these models are drawn at

The W02 strategic-modelling lecture sets the bar: **comprehensible on one sheet, ten or fewer flow
objects**, responsibilities assigned, and no attempt to account for every contingency. That is what
decides the level here - **one activity per stage of the pathway**, not one per team, and no
gateways.

So a strategic activity is a stage, and it merges the operational tasks that stage is made of. The
merge is not a summary anyone has to guess at: **each activity's documentation lists the operational
elements it merges**, by id, with the business rules and requirements it carries. Read one activity
in the model and you have the index to the part of `../operational/` it stands for.

| # | Strategic activity | Lane | Merges (models/operational/) |
|---|---|---|---|
| 1 | Register and check the referral | Medical Secretaries | core-1 `N_MS_ReferralReceived`, `N_MS_CheckReferral`, `N_MS_ValidateReferral`, `N_MS_DocumentsComplete`, `N_MS_RequestMissing`, `N_MS_ReviewException`, `N_MS_CorrectReferralInput`; core-5 `N_MS_RecordMissingItems`, `N_MS_RequestDocuments`, `N_MS_DocumentsSupplied`, `N_MS_CheckSuppliedDocuments`, `N_MS_ReadyForReview` |
| 2 | Review the referral and decide | Consultants | core-1 `N_C_ClinicalReview`, `N_C_ReferralDecision`, `N_C_Rejected`, `N_C_Redirected` |
| 3 | Book the appointment and inform the patient | Outpatient Bookings Team | core-1 `N_OB_PrepareRequest`, `N_OB_CheckAvailability`, `N_OB_SlotAvailable`, `N_OB_SendNotification`, `N_OB_WithinTwoWeeks`, `N_OB_TelephonePatient`, `N_OB_ContactOutcome`, `N_OB_RecordAttempt`, `N_OB_AppointmentArranged`, `N_OB_RecordNoSlot`, `N_OB_CorrectRequest`, `N_OB_RecordDispatchFailure`, `N_OB_UrgentReferral`, `N_PC_ReviewDelay`, `N_PC_EscalateUrgent` |
| 4 | Assess and authorise the treatment | Consultants | core-2 `N_CL_PatientAttends`, `N_CL_AssessPatient`, `N_CL_ConsentDecision`, `N_CL_NotProceeding`, `N_CL_AuthoriseTreatment` |
| 5 | Determine funding and take payment | Finance Team | core-2 `N_F_DetermineFunding`, `N_F_FundingApprovalRequired`, `N_F_RecordApproval`, `N_F_PaymentRequired`, `N_F_CalculateCharge`, `N_F_ProcessPayment`, `N_F_PaymentStatus`, `N_F_CorrectPaymentRequest`, `N_F_InvestigatePayment`, `N_F_NotifyPatient`, `N_F_RetryDecision`, `N_F_ReviewFinancialImpact` |
| 6 | Book, deliver and review the treatment | Treatment and Chemotherapy Bookings Team | core-2 `N_TB_VerifyAuthorisation`, `N_TB_CheckAvailability`, `N_TB_AvailabilityDecision`, `N_TB_ConfirmAppointment`, `N_TB_NotifyPatient`, `N_TB_ReturnRequest`, `N_TB_KeepPending`, `N_OC_PreCycleReview`, `N_OC_FitToContinue`, `N_CL_ModifyTreatment`, `N_CL_ClinicalRisk`, `N_CL_UrgentAuthorise` |
| 7 | Send the clinic letter and escalate delay *(whole-pathway model only)* | Medical Secretaries | core-3 `N_C_ConsultationDone`, `N_C_PrepareLetter`, `N_C_ApproveLetter`, `N_C_ReviewClinicalError`, `B_C_LetterOverdue`, `N_MS_ProcessLetter`, `N_MS_ErrorDecision`, `N_MS_ConfirmRecipients`, `N_MS_SendLetter`, `N_MS_HandleDispatchFailure`, `N_PC_MonitorCorrespondence`, `N_PC_SevenDays`, `N_PC_IssueReminder`, `N_PC_OverdueDuration`, `N_AM_ContactConsultant`, `N_AM_EscalateHigher` |
| 8 | Handle follow-up, cancellation and refund *(whole-pathway model only)* | Call Handling Team | core-4 `N_OB_FollowUpRequested`, `N_OB_ArrangeFollowUp`, `N_OB_WithinPeriod`, `N_OB_ConfirmFollowUp`, `N_OB_CancellationReceived`, `N_OB_RecordCancellation`, `N_OB_AnotherAppointment`, `N_OB_NotifyReferrer`, `N_PC_ReviewUnbooked`, `N_CNS_PathwayReview`, `N_CNS_ClinicalAdvice`, `N_CNS_Urgency`, `N_CNS_HighlightUrgent`, `N_CH_ContactReceived`, `N_CH_ClassifyEnquiry`, `N_CH_EnquiryType`, `N_CH_AnswerAdmin`, `N_F_PaidAffected`, `N_F_RetentionDecision`, `N_F_RefundDecision`, `N_F_ProcessRefund`, `N_F_AnswerFinance` |

An activity sits in the lane of the team that owns it. Where a stage is carried out by more than one
team - the clinic letter is written and approved by the Consultants, processed by the Medical
Secretaries and chased by the Patient Pathway Coordinators - the owning lane is the one in the table
above, and the other teams are named in the activity's documentation, which records who does what
for each merged element.

**The teams whose work sits inside a merged activity carry no lane, and are named instead.** Four
teams take part in a stage whose activity is owned by another team - Other Clinical Professionals in
the treatment stage, the Clinical Nurse Specialist Team, the Patient Pathway Coordinators and the
Administrative Management Team in the stages after it - and each is named, with what it does, in the
documentation of the activity it works in. A lane is drawn only where a team owns an activity, which
is what keeps the pool free of bands with nothing in them. The Clinical Nurse Specialist
Administrative Support Team carries no lane at all, here or in any operational process, because no
activity in the five processes is exclusively its own, which is where that row of the participant
coverage rests (`../../docs/justification-of-decisions.md` section 2).

**Where the model says one thing and the release says another, the model follows the release.**
Every external exchange named in an activity's documentation is one the release actually makes,
including the two halves of the missing-information exchange with the referring organisation
(`core-5`). The funding approval is the exception, and it is stated as such: the release records the
approval as data and has no interface to the funding organisation, which is a limitation recorded in
`../../docs/case-study-summary.md` section 7 rather than something the model hides.

## Reading them alongside the other views

The pathway here is also drawn as i\* models in `../socio-technical/`, which carry what this level
cannot: who depends on whom for what, which needs the release meets and which it does not. The three
views answer different questions - what the stages are and who owns them (here), who depends on whom
(i\* SD), and what each actor is trying to achieve (i\* SR).

## Changing them

Edit the `.bpmn` and then run, in this order:

```bash
python tools/relayout_hprtas_bpmn.py models/strategic/<model>.bpmn   # redraw the diagram interchange
python tools/verify_hprtas_bpmn_layout.py                            # the layout and BPMN rules
python tools/export_hprtas_bpmn_pdf.py                               # the committed PDF and PNG
```

A strategic model has no message flows, no sequence-flow conditions, no service tasks and no forms,
so the binding check (`verify_hprtas_bpmn_bindings.py`) does not cover it; the layout check does, and
the export check (`verify_hprtas_bpmn_export.py`) reads the exported PNGs back with OCR to prove the
labels are readable. The diagram interchange is a snapshot: a hand-drawn diagram is not preserved by a
re-layout, so change the model and re-run rather than moving boxes in the file.
