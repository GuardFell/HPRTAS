# Plan and Alignment Evaluation

> **PB-010.** The evaluation the project is judged on: does what was built actually carry the
> requirements, how well do the models, the forms, the workers and the requirements agree with each
> other, and how did the plan compare with what happened.
>
> Recorded by M5. Every verdict below is made against an artefact in this repository at an
> identified version, not against an intention. Where a claim could not be checked, it says so.

## 1. What is evaluated, and against what

| Question | Answered by | Source |
|---|---|---|
| Does each requirement reach from the strategic model through an operational model into an implementation and a test? | Section 3 | The traceability table in `../requirements/requirements.md`, verified element by element against the four `core-N` models |
| Do the models, the forms and the workers agree with each other? | Section 4 | `models/operational/*.bpmn`, `forms/*.form`, `workers/src/` |
| Did the plan match what happened? | Section 5 | `project-plan.md` sections 5 and 6, `git log` |
| Are the acceptance criteria met? | `../../tests/test-plan.md` section 5, summarised in section 6 below | `tests/evidence/` |

**Version evaluated.** `69e01a1`, the commit `release-1.0` tags. The evaluation was first made at
`c8556ba`, when the workers were still in Node.js and 36 of the 55 user tasks bound no form; the
artefacts have changed since, so every verdict below was re-checked against the current tree rather
than carried over. Where a verdict moved because of that, the row says so and names the commit that
moved it. The evidence is the half that has not kept up: every run in `tests/evidence/` except one
was recorded before the workers were rewritten in Java (`4eb3fee`), so those records describe the
Node.js implementation and no longer describe the current code. They are kept because a result is a
record of what was run at a named version, not a claim about today's tree. The exception is
`workers_java-unit-smoke-and-models_76a7fdc_2026-09-22.txt`, the one run made against the Java
workers, and it is the current worker-level and model-level evidence.

## 2. Evaluation criteria

The standard applied, agreed as part of TB-016. A requirement is judged on the strongest claim the
evidence supports, and no verdict is rounded up.

| Verdict | Means |
|---|---|
| **Supported** | An element of the named model carries it, the implementation behind that element enforces it rather than merely allowing it, and a run in `tests/evidence/` demonstrates it. |
| **Partially supported** | The model carries it and something demonstrates it, but a named gap remains: an unrun branch, a rule that is recorded as data instead of enforced, or a step whose record cannot be captured. The gap is stated. |
| **Unsupported** | No element or implementation carries it, or an element exists but the behaviour contradicts the requirement. |
| **Gap identified** | The requirement is known to be unimplemented and is recorded as such rather than evaluated. A gap is not a verdict on an artefact - it is an admission that the artefact for it does not exist. |

A requirement is **not** counted as supported because an element with a suggestive name exists. The
check is what the recorded paths, the gateway conditions and the worker validations actually do.

## 3. Requirement traceability: strategic model to operational model to implementation

The rows below are the traceability table in `../requirements/requirements.md`, each one evaluated.
Every row was checked against the model XML at `69e01a1` rather than carried over from the
requirements list. Three rows moved as a result of re-checking them at that version, and each is
marked in place: FR-041 and FR-044, whose gap was the missing forms that `a62e783` supplied, and the
counts of gateways, service tasks and forms in section 4.

| Requirement | Strategic element | Operational element | Implementation | Test | Verdict | Justification, and the gap where there is one |
|---|---|---|---|---|---|---|
| FR-005 - route the referral to the Consultant and record the decision, its reason and the decision maker | Consultants lane: `N_C_ClinicalReview`, `N_C_ReferralDecision` | core-1: `N_C_ClinicalReview`, `N_C_ReferralDecision`, `N_C_Rejected`, `N_C_Redirected` | Model user tasks, no worker; `referral-review` form | `referral-to-appointment_normal-path_1b42bff_2026-09-21.txt`; `operational-models_end-to-end_a7f0dd6_2026-09-21.txt` | **Partially supported** | The check the requirements list asked for **verifies**: `N_OB_PrepareRequest` has exactly two incoming flows, `F12` (accepted) and `F33` (another appointment requested by the patient, which is downstream of an accepted referral), so no path reaches the booking step without `decision = "accepted"` and the branches for rejected, redirected and further information each terminate separately. **Gap:** only the `accepted` branch has been run (`TC-02` is not run), so the three decision branches are verified by reading the conditions and by nothing else. The reason and decision-maker fields are on the `referral-review` form, which no run has submitted. |
| FR-014 - telephone contact as well as a letter when the appointment is within two weeks | Outpatient Bookings lane: `N_OB_WithinTwoWeeks`, `N_OB_TelephonePatient` | core-1: `N_OB_WithinTwoWeeks`, `N_OB_TelephonePatient`, `N_OB_ContactOutcome` | Model branch; `appointmentWithinTwoWeeks` recomputed by `appointment-availability` from the slot found | `operational-models_end-to-end_a7f0dd6_2026-09-21.txt`; worker unit tests | **Partially supported** | The branch is correct and is the harder half done properly: `F29` tests `appointmentWithinTwoWeeks = true` and its default `F30` is the later path, so a missing value takes the safe branch, and the worker recomputes the flag from the slot actually found rather than trusting the form. **Gap:** the boundary at exactly fourteen days is untested, and `AS-13` counts whole calendar days, so the off-by-one case is a real one. `TC-04` passes at worker level only. |
| FR-020 - a treatment change is accepted only as a formal, authorised request | clinical lane: `N_C_ModifyTreatment` | core-2: `N_CL_ModifyTreatment` | Model user task; `treatment-booking` form; no worker | No dedicated evidence | **Partially supported** | The model provides one route to a treatment change - a user task in the clinical lane, reached from the between-cycle review - and no informal route exists in it, so "requests made only by email or telephone must not be processed" holds by construction. **Gap:** nothing actively refuses an informal request, because there is no channel through which one could arrive; that is a model boundary rather than an enforced rule. `F41` refers a charge-affecting modification to `N_F_ReviewFinancialImpact`, which is right. The interaction with the urgent path (`FR-021`) has not been examined and the requirements list flags it as unconfirmed. |
| FR-024 - a paid appointment that is cancelled, postponed or changed goes to the Finance Team | Finance lane: `N_F_DetermineRefund`, `N_F_ProcessRefund` | core-4: `N_F_PaidAffected`, `N_F_RetentionDecision`, `N_F_RefundDecision` | Model user tasks; `refund-processing` worker; `refund` form | `operational-models_end-to-end_a7f0dd6_2026-09-21.txt` (refund scenario) | **Supported** | The check the requirements list asked for **verifies**: `N_F_RetentionDecision` and `N_F_ProcessRefund` are both in the Finance Team lane and carry `candidateGroups="finance"`, so no clinical or administrative lane owns the decision. The branch is guarded twice (`F17` on `paidAppointment = true`, and `F23` testing the same variable again), the refund is made only against a payment the same run settled with the provider, and the refund worker refuses a payment that was never settled. **Caveat carried forward:** the candidate group is not enforced by anything, because role-based access is not implemented (`DEF-07`); the separation is structural, not enforced. |
| FR-028 - record the payment result and store no complete card information | Finance lane: `N_F_ProcessPayment` with the request leaving the pool as a message | core-2: `N_F_ProcessPayment`, `N_F_CorrectPaymentRequest` (boundary `B_F_ProhibitedData`) | `payment-processing`, `payment-service-provider`; card data rejected in `Validate.java` | `TC-07`, `TC-11`, `operational-models_end-to-end_a7f0dd6_2026-09-21.txt` | **Supported** | The check the requirements list asked for **verifies at worker level**: the variables returned carry status, reference, date and amount only, and a request carrying card or security details raises `PROHIBITED_FINANCIAL_DATA` before the provider is called - and the test asserts the provider was not called rather than assuming it. `N_F_ProcessPayment` catches both codes its worker can raise. **Gap:** the equivalent check does not hold on the refund path - see `DEF-12`. |
| FR-038 - flag a letter not completed within seven days and include it in pathway monitoring | Patient Pathway Coordinators lane: `N_PC_LetterWithinSevenDays`, `N_PC_IssueReminder` | core-3: `N_PC_MonitorCorrespondence`, `N_PC_SevenDays`, `N_PC_IssueReminder` | Model branch and a timer; no worker | `operational-models_end-to-end_a7f0dd6_2026-09-21.txt` | **Partially supported** | The seven-day rule is genuinely enforced rather than asserted: `B_C_LetterOverdue` is a timer boundary event with duration `P7D` on `N_C_ApproveLetter`, so the delayed path starts from the clock without a worker being involved, and `N_PC_SevenDays` tests `letterWithinSevenDays`. **Gaps:** `TC-16` is not run, so no evidence exists that the timer fires and the path is entered; and the escalation thresholds are weaker than the seven-day rule - `N_PC_OverdueDuration` branches on an `overdueDuration` variable supplied as input (`one_to_three_months` / `over_three_months`) rather than on a timer, so a month and three months are recorded as data, not measured. `F14`'s condition and its default also mean the reminder path is taken when the value is absent, which is the safe direction. |
| FR-041 - record, classify, prioritise and route every enquiry | Call Handling lane: `N_CH_ClassifyEnquiry`, `N_CH_EnquiryType`; CNS Admin Support lane: `N_CNSA_RecordEnquiry` | core-4: `N_CH_ContactReceived`, `N_CH_ClassifyEnquiry`, `N_CH_EnquiryType`, `N_CH_EnquiryAnswered` | Model user tasks and their forms; no worker | `operational-models_end-to-end_a7f0dd6_2026-09-21.txt` | **Partially supported** | Routing is modelled and correct - `N_CH_EnquiryType` sends `administrative` to `N_CH_AnswerAdmin`, `financial` to `N_F_AnswerFinance`, and its default to `N_CNS_ClinicalAdvice`, which is the safe direction for a clinical enquiry. **Moved at `a62e783`:** this row read **Unsupported** while every task on the path bound no form (`DEF-13`), because `BR-37` requires the record to show when the enquiry was received, who handled it, the team responsible, the response and whether it was resolved, and none of it had a capture surface. The forms now exist - `N_CH_ContactReceived`, `N_CH_ClassifyEnquiry`, `N_CH_AnswerAdmin`, `N_CNSA_RecordEnquiry` and `N_CNS_HighlightUrgent` each bind one - so the record can be captured. **Gap:** no run reaches this path at all, and no form has been submitted by a signed-in user (`DEF-08`), so the capture is verified by the forms' own contracts and by nothing else. |
| FR-044 - urgent clinical concerns are highlighted immediately | CNS Team lane: `N_CNS_Urgency` | core-4: `N_CNS_Urgency`, `N_CNS_HighlightUrgent` | Model branch; urgency rules not agreed (`AM-01`) and left configurable (`NFR-012`) | `operational-models_end-to-end_a7f0dd6_2026-09-21.txt` | **Partially supported** | The branch is right and fail-safe: `N_CNS_Urgency`'s default flow `F39` is the highlight path, so an unresolved or absent `urgentClinicalConcern` value highlights the concern rather than dismissing it. **Gaps:** the complete urgency rules are still not agreed (`AM-01`, `AS-03`), so what the demonstration treats as urgent has to be stated wherever it is shown; and no run reaches this branch. `N_CNS_HighlightUrgent` bound no form when this row was first evaluated, so "highlight immediately" had no recorded outcome behind it; **that half of the gap closed at `a62e783`** (`DEF-13`), and the task now binds one. |
| NFR-002 - access restricted by role, with clinical, administrative and financial duties kept separate | Lane structure: eleven lanes in the Hospital Trust pool, clinical and Finance lanes apart | Lanes of core-1 to core-4 | `forms/` holds the forms; no permission configuration exists | No evidence file | **Gap identified** | The lane structure is real and is verified: each user task carries the `candidateGroups` of its lane, and no lane mixes a clinical decision with a financial one. Beyond that the requirement is unmet. Nothing authenticates a user, nothing enforces a candidate group, and the `demo`/`demo` login on an unsecured local engine grants every task to anyone. A reader must not take the lane structure as evidence of access control. Depends on the role model (`AS-04`) and on which Finance roles hold financial authority (`AS-07`). |
| NFR-004 - accurate patient identification, and less risk of the wrong patient when information arrives from elsewhere | Medical Secretaries lane receives the referral message flow from the referring organisation pool | core-1: `N_MS_CheckReferral` records the patient identification check | Recorded as a task field; no matching or duplicate check exists | No evidence file | **Gap identified** | `N_MS_CheckReferral` has no form either (`DEF-13`), so even the recorded check has no capture surface. There is no patient-matching or duplicate-detection logic anywhere in the models or the workers, so `BR-46` is unimplemented rather than partly implemented. |

### 3.1 Requirements with no strategic row

The table above covers the requirements that the strategic model carries. The rest of the list was
swept for requirements that no verdict covers, and the outcome is recorded here so the gaps are all
in one place. This is the part of the evaluation the requirements list itself cannot produce,
because it only tracks what it already knows about.

| Group | Requirements | Where they stand |
|---|---|---|
| Enquiries | FR-041 - FR-044 | Evaluated above. FR-041 fails on the record, not on the routing; FR-042 and FR-043 are carried by `N_CH_EnquiryType` and are as sound as the routing, but with no form and no run they cannot be accepted either. |
| Access, audit and reporting | FR-045 - FR-047, FR-050 | No model element and no implementation. Recorded as `Not yet evidenced` and as `DEF-07`. The audit trail is the one that matters most: `BR-43` and `BR-44` are Must rules and nothing in the system produces or protects an audit record. |
| Patient identification | FR-048 | Gap identified above. |
| Downtime | FR-049 | No procedure, no element. The pending-booking and dispatch-failure paths record the recording side only. |
| Urgent postponement of treatment | FR-021 | The elements that must carry it (`N_CL_ClinicalRisk`, `N_CL_UrgentAuthorise`) exist and both are in the Consultant and clinical team lane, and their gateway defaults are fail-safe (`F31` default goes to `N_F_NotifyPatient`). But the element documentation does not cite FR-021, `BR-23` or `EX-16`, so the mapping is inferred from behaviour rather than declared by the model. Recorded as `DEF-05` and left **unconfirmed** rather than called supported. |
| Audit relationship between patient, referral, request, appointment, funding, payment and refund | FR-035 | Carried only by references passed between the models (`paymentReference`, `appointmentReference`) and recorded on process documentation with no element of its own. **Partially supported**: the references do join the models, and the refund scenario exercises the join end to end against a reference the payment scenario settled, which is the strongest evidence in the repository for it - but there is no auditable record, only the reference, and nothing retains it after the instance completes. |

## 4. Alignment between the models, the forms and the workers

Checked structurally at `69e01a1`, not inferred from the runs. These are the findings from that
check; the ones that are defects are in `../../tests/test-plan.md` section 6 and are cross-referenced
rather than repeated.

**Supported by the check.**

- Every service task in the four models is bound to a job type that a worker registers, and every
  job type in `workers/config/workers.default.json` serves at least one service task. Ten service
  tasks, six workers, no orphans in either direction.
- Every exclusive gateway in the four models declares a `default` flow and the flow it names carries
  no condition of its own, which is what `DEF-01` was about. Verified on all 23 gateways; every
  default is the safe branch (an unhandled value falls to the exception path, not to the happy one).
- Every service task that can raise a business error catches every code its worker can raise, with
  one exception: `N_F_ProcessRefund` (`DEF-12`, fixed at `9138bcc`).
- Every user task carries the Camunda user task marker and a candidate group, so no task is
  invisible in Tasklist and none is unassigned.
- Every form field name used by the models resolves to a form that exists, and the 41 `.form`
  files carry the ids the models refer to. No model points at a missing form.

**Not supported by the check.**

- **`N_F_ProcessRefund` cannot catch `PROHIBITED_FINANCIAL_DATA`** (`DEF-12`), so a refund carrying
  card details stops the process. The worker test for that behaviour passes, which is the point:
  the worker level cannot see a missing catch event on the model. **Closed at `9138bcc`**; the
  catch event and its error path now exist, and the residual limitation - no form field can clear
  card details that have already reached the process - is recorded with the defect.
- **Closed since this evaluation: every user task now binds a form** (`DEF-13`, closed at
  `a62e783`). This was the largest alignment finding in the project, and it is worth recording what
  it was: 36 of the 55 user tasks bound no form, and a task with no form has no variable contract,
  so the variables it must write were declared nowhere and the only way to satisfy a downstream
  gateway that reads them was to inject them with the task completion call - which is exactly what
  the end-to-end run does. The run therefore demonstrates the models, not the Tasklist experience
  the case describes. Forty forms now cover all 55 tasks. **What has not closed with it:** the
  forms have still never been submitted by a signed-in user, so the variable contracts are verified
  by the models' own expectations and by nothing else (`DEF-08`).
- **Added after this evaluation.** `DEF-12` is closed at `9138bcc`, and fixing it found a third fault of
  the same family: a sequence-flow condition on the only outgoing flow of an activity is never
  evaluated, so two conditions in `core-2` read as guards and were not (`DEF-14`). One of them,
  `retryPayment`, now decides at a gateway and both answers have been driven; the other,
  `affectsCharge`, is corrected but still not enforced. The conditions out of gateways - which is
  every decision this evaluation checked - are unaffected.
- **An urgent referral with no slot loops for ever in `core-1`** (`DEF-11`). It was found by reading
  the two gateway conditions against the scheduling service's determinism, and running the current
  scenarios would not have found it, because they all use `priority = "routine"`.
- **The artefacts have changed since this evaluation was first made, and it has been re-checked.**
  Between `c8556ba` - the version the first edition evaluated - and `69e01a1`, the workers were
  rewritten from Node.js into Java (`4eb3fee`), all 40 forms were written or repaired (`a62e783`),
  the refund catch event was added and the retry decision moved onto a gateway (`9138bcc`), and the
  diagrams were laid out again (`91ee6d4`, `69e01a1`). `git diff --stat c8556ba HEAD -- models
  workers forms` is what that looks like: 118 files. Everything in sections 3 and 4 above was
  re-read against the current tree for that reason.
- **The evidence has not changed with the artefacts, and cannot be made to.** Every run in
  `tests/evidence/` was recorded against the Node.js workers or against the models as they stood on
  21 September. Re-running them needs an engine, which is why they are kept as the record of what
  was run at a named version rather than presented as results for `release-1.0`. The Java-era run
  that does exist - `workers_java-unit-smoke-and-models_76a7fdc_2026-09-22.txt`, the unit suite, the
  smoke fixture and the operational models after the rewrite - is cited in
  `../../tests/test-plan.md` section 5 beside the Node-era results it supersedes.
- **Added after this evaluation - and it changes the answer on the forms.** `DEF-13` is closed at
  `a62e783`: 40 forms now cover all 55 user tasks. Writing the missing ones showed that the eight
  the first edition counted as delivered had never rendered a field - a group's children belong
  under `components`, and a group's `path` prefixed every child key - so statements in the first
  edition about a form binding resolving described `c8556ba` and not the tree. The four faults,
  every binding, and what is and is not verified now, are in `../../forms/README.md`.

## 5. Plan evaluation: planned against actual

Source: `project-plan.md` sections 5 and 6, and `git log`. Dates are the commit dates recorded in
the repository.

### 5.1 What was delivered, and when

| Date | What landed | Commits |
|---|---|---|
| 17 Sep 2026 | The case study summary (sections 1-10) and the first requirements draft | `52508a1`, `fddd9d4` |
| 21 Sep 2026 | The external workers with their tests, the recorded evidence, the forms bound to their user tasks, the four `core-N` models replacing the first edition, and the end-to-end run | `813fea6`, `1b42bff`, `e852224`, `38367e8`, `524a6a4`, `a7f0dd6`, `bf79865` |
| 21 Sep 2026 | The READMEs rewritten as functional descriptions, the traceability re-pointed at the `core-N` models | `10c9324`, `d94006f`, `c8556ba` |
| 22 Sep 2026 | Every user task bound to a Camunda Form, closing `DEF-13`: 32 forms added and the eight that existed repaired, after they were found never to have rendered a field | `a62e783` |

### 5.2 Sprint 1 - 15 to 20 September 2026

| Dimension | Planned | Actual | Verdict |
|---|---|---|---|
| Models | One deployable model of the referral-to-appointment pathway with the acceptance gate and one exception path | Three models of the first edition, then four `core-N` models covering the whole pathway | **Exceeded** |
| Workers | Not planned for this sprint | Six workers with unit tests and two engine runs | **Ahead of plan** |
| Plan, backlog and configuration management workflow | Part of the stated sprint goal | **Not delivered.** The product backlog, task breakdown, sprint backlogs and contribution matrix are still templates | **Missed** |
| Estimates | Every task estimated on the agreed scale | None recorded | **Not assessable** |
| Evidence | Evidence that the sprint goal was met | Worker-level and model-level runs, each naming its commit and stating its limits | **On plan** |

**Assessment.** Sprint 1 delivered more of the system than it planned and less of the process. The
miss is the one that has cost the most: because the backlog and the sprint backlogs were never
filled in, no estimate was ever agreed, velocity cannot be measured for any sprint, and this
evaluation has to compare the plan against the git history and the evidence files instead of against
the project's own records. The cause is not a shortage of time - 12,645 lines were added to the
repository in the sprint that was supposed to deliver one model. It is that the modelling and the
workers produce artefacts that can be run and reviewed, and a backlog produces a table that cannot,
so the visible work won. The definition of done now requires the acceptance conditions to be checked
off in the task's own row, which makes an unfilled row visible at the sprint review instead of after
it.

The other sprint-1 cost is the model migration. Replacing three models with four was the right
decision and it was made on the evidence, but it re-pointed every requirement reference by hand,
superseded two evidence files, and - as section 4 shows - reintroduced two faults that had already
been fixed in the models it replaced. A migration is a re-plan, and a re-plan needs its acceptance
criteria re-run. That is now recorded in `project-plan.md` section 7 and is why `DEF-11` and
`DEF-12` matter more than their size suggests.

### 5.3 Sprint 2 - 21 to 27 September 2026 (in progress)

| Dimension | Planned | Actual | Verdict |
|---|---|---|---|
| Integrated increment | Models, workers and forms end to end for the normal pathway, the funding and payment gate and one failure path | Delivered at `a7f0dd6`: four models, eight forms, six workers, five scenarios each reaching an end event, and every service task in the four models reached | **Exceeded** |
| Forms | Bound to their user tasks and deployed with the models | Delivered; the bindings resolve, but 36 of 55 tasks still bind no form at this version | **Partially met** *at this version* - `DEF-13` is closed at `a62e783`, where 40 forms cover all 55 tasks, so the dimension is met at `release-1.0`; what is still missing is a submitted form (`DEF-08`) |
| Evidence against an identified version | A run naming its version | Three model-level records and a re-run of the unit suite at `c8556ba` | **On plan** |
| Estimates | Every chosen task estimated | None recorded | **Not assessable** |
| Acceptance criteria | Not planned for this sprint | Ten criteria and twenty-one scenarios defined, the execution record opened, and seven of the ten met | **Ahead of plan** |

### 5.4 Where the plan was wrong

1. **The plan assumed the plan itself was the deliverable's by-product.** It is not. The backlog and
   the sprint records have to be filled in as the work happens or they cannot be reconstructed -
   which is what the contribution matrix's own note says, and it was right.
2. **The plan had no task for the migration.** Replacing the first edition with the `core-N` models
   was not in any sprint goal, and it consumed work in the sprint that was also meant to deliver the
   integrated increment. Model migrations need their own task, their own estimate and a re-run of
   the acceptance criteria.
3. **The plan treated a run as evidence of the level above it.** The worker tests pass on
   `refund-processing`'s card-detail refusal while the model cannot catch the error it raises
   (`DEF-12`). Evidence has a level, and a claim made at the wrong level is not evidence.
4. **The plan put role-based access and the audit trail in scope for the first release by leaving
   them in the requirements as Must.** They are not implemented, they are not planned tasks, and the
   first release cannot be accepted as meeting the case while they are missing (`DEF-07`). They are
   now explicitly deferred to the second release in `project-plan.md` section 1, which is a
   decision, not an omission.

## 6. Acceptance criteria evaluation

Full detail is in `../../tests/test-plan.md` section 5. The criteria are the ones defined there; the
verdicts below were re-checked at `release-1.0`, where two of them moved because the defects behind
them were fixed. **Every underlying run still dates from the Node.js workers, so a verdict of "met"
means the criterion was demonstrated at the version the evidence names, not that it has been
re-demonstrated since the rewrite.**

| Criterion | Verdict | Basis |
|---|---|---|
| AC-01 normal pathway, referral completeness and patient contact | **Met** at worker level | TC-01, TC-03, TC-04 pass; the model-level normal path passes at `a7f0dd6`. The Tasklist half is limited by `DEF-08`. |
| AC-02 clinical decisions and the gates that depend on them | **Met** | TC-06 passes at worker and model level; the structural check confirms no path reaches the booking step without acceptance. |
| AC-03 funding, payment and the confirmation gate | **Met** | TC-07 and TC-08 pass, and the gate is `F26`/`F20` in core-2, reading what the worker returned. |
| AC-04 exception routing and the investigation flag | **Met** | TC-09 and TC-12 pass; the investigation branch is `F28` and the fallbacks are fail-safe. |
| AC-05 no suitable slot, and urgent referrals | **Met** - moved at the `DEF-11` fix | The no-slot half passes, and the urgent half now escalates out of the booking process instead of looping. Driven on the engine in scenario 8 of `OperationalModelsTest`: the availability check is reached once, the routine delay review is not taken, and the instance completes. |
| AC-06 clinic letter, seven-day target and escalation | **Partially met** | The letter path and the `P7D` timer are right; the timer's path has never been run (TC-16), and the escalation thresholds are input data rather than measured periods. |
| AC-07 follow-up, cancellation and enquiries | **Partially met** - moved at `a62e783` | The follow-up and cancellation paths pass in part. This row read **Not met** while the enquiry half was unsupported for want of forms (`DEF-13`); the forms exist now, so what remains is that no run reaches the enquiry path and none of its tasks has been completed by a signed-in user (`DEF-08`). |
| AC-08 refund and separation of duties | **Met** with a caveat | TC-21 passes end to end against a settled reference. The caveat is that the separation is structural, not enforced, because role-based access does not exist. |
| AC-09 data minimisation | **Met** - moved at `9138bcc` | TC-07 and TC-11 pass and the provider-call assertion holds. The refund path was the exception, because `N_F_ProcessRefund` could not catch the `PROHIBITED_FINANCIAL_DATA` its worker raises (`DEF-12`); the catch event was added at `9138bcc`. Residual limitation: no form field can clear card details that have already reached the process, so the refusal repeats. |
| AC-10 controlled failure and no duplicates | **Met** | TC-08, TC-11 and TC-12 pass; the duplicate guards are asserted, not assumed. |

**Eight of ten are now fully met; the other two are partial.** No defect in the delivered models
still blocks a criterion: **`DEF-11`** (AC-05, the urgent no-slot loop) is fixed and driven, so the
blocker set is the unrun branches rather than a fault. AC-06 and AC-07 are partial for want of a run
rather than for want of an artefact, and AC-01, AC-07 and AC-10 all have a half that needs a
signed-in user before it can be exercised (`DEF-07`, `DEF-08`). `DEF-12` and `DEF-13` are closed - the refund catch event
at `9138bcc` and the forms at `a62e783` - and each needs its scenario re-run before the criterion it
blocked can be called demonstrated, because both were found by checking rather than by running.

## 7. Conclusions and actions

1. **The delivery is further ahead than the plan and weaker than the evidence suggests.** The
   pathway runs end to end and the worker level is thorough. The gaps are concentrated in exactly the
   places a run cannot see: a missing boundary event, a branch no scenario drives, and a condition
   that reads as a guard without being one. The first edition of this list led with "a task with no
   form"; that one is closed (`DEF-13`), and the shape of the remaining gaps is unchanged by it.
2. **One defect must be fixed and re-tested before the initial release.** `DEF-11` - re-target the
   urgent flow in `core-1` so it cannot cycle - is the only open defect that is a fault in the
   delivered models. The other two the first edition listed here, `DEF-12` (the missing catch event)
   and `DEF-13` (the forms for the 36 tasks), **are done:** the catch event at `9138bcc` and the
   forms at `a62e783`, where 40 forms came to cover all 55 user tasks. Both are inside
   `release-1.0`. Neither is *demonstrated* yet, because the scenario behind each has not been re-run
   since it was fixed, and that is the work item that replaces them here.
3. **The unrun scenarios are the cheapest remaining work.** TC-02, TC-13, TC-16 and TC-20 need no
   code, only a run - except that TC-13 and TC-20 need a signed-in user, which makes them depend on
   `DEF-07`. `DEF-13` used to stand in front of them as well; with the forms written, the signed-in
   user is the only thing still missing.
4. **The plan process has to start now, not at the review.** The backlog, the task breakdown, the
   sprint backlogs and the contribution matrix are the project's own record of what was planned and
   who did what, and none of them can be reconstructed afterwards. **Partly done:** the product
   backlog, the task breakdown, the sprint backlogs and the dependencies are recorded at
   `release-1.0`, from the group's plan document; the contribution matrix still has the M1 to M4
   rows, which are their owners' to record.
5. **The first release needs a tag.** Every result in this repository is tied to a commit, and a
   release with no tag cannot be compared against the second release, which is what Sprint 4 is for.
   **Done:** `release-1.0` tags the first release. It was made at `9ef26df`, re-pointed to
   `bdcc0e1` so that it covered the Java workers, and re-pointed a third time onto `69e01a1` so that
   it covers the completed form set and the refund fix. `DEF-12`, `DEF-13` and `DEF-14` are all
   inside it, and `git log release-1.0..HEAD` is empty. `DEF-11` is the one defect still open at the
   tag, so the tag and that fix have to be brought together before the release is accepted.
