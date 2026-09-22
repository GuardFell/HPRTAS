# Justification of Decisions

Why the operational process is modelled the way it is. This is the explanation the BPM & EA
presentation asks for: the process structure, the participant boundaries, how tasks are allocated,
how the gateways are arranged, how the process meets the outside world, how failure is handled, the
assumptions the models rest on, the alternatives that were rejected and the trade-offs accepted.

It is written against the four operational models in `../models/operational/`, the strategic models
in `../models/strategic/`, and the evidence in `../tests/evidence/`. Readable PDF exports of every
model are in `../models/exports/`. Where a decision has a cost, the cost is stated here rather than
left out: a justification that claims no downside is not a justification.

## 1. Process structure

**Four processes, not one.** The pathway was modelled as four executable processes rather than one
large one, because the case describes four points at which the work is picked up and handed on, and
those hand-offs are where the interesting rules live. `core-1` ends when the first appointment is
arranged, `core-2` when a treatment cycle has been reviewed, `core-3` when a letter is distributed
or delayed, `core-4` when a follow-up is booked or an episode closed. Each has a start an actor can
identify with, and each can be deployed, run and demonstrated on its own.

The cost is real: a patient's journey crosses process boundaries, so the four processes share a
patient and appointment identity rather than a single token, and nothing in the models stops a
second process from being started for the same patient. The alternative - one process with four
start events - would have kept a single instance but produced a diagram no one could read, and the
case's own stages are what the boundaries follow.

**The first edition was three processes and was replaced.** The models were first built as
`referral-to-appointment`, `treatment-authorisation-and-booking` and
`clinic-letter-and-pathway-monitoring`, then replaced by the four `core-N` processes at `a7f0dd6`.
The replacement was not a rename: elements were split, merged and re-identified, and the
traceability in `requirements/requirements.md` had to be re-pointed by hand. The reason for
replacing rather than extending is in section 8; the cost was a day of rework and a period in which
the traceability table no longer matched the models.

**Three layers, each answering a different question.** `models/strategic/` holds the high-level view
of the pathway across the organisation, `models/socio-technical/` the i\* view of who depends on
whom and why, and `models/operational/` the executable processes. The strategic model
`patient-pathway-all-entities.bpmn` is the one place where every entity in the case appears
together: eight pools and eleven lanes, not deployed, used to check that the operational split does
not lose a participant.

## 2. Participant boundaries

**A pool is an organisation that cannot be instructed; a lane is a team inside the hospital.**
Every pool in the models is outside the hospital's authority - the patient, the referring
organisation, the scheduling service, the correspondence service, the payment service provider, the
insurer and the treatment service. The hospital's own teams are lanes inside the single Hospital
Trust pool. That is the boundary that decides whether an interaction is a message flow (across a
pool, no control) or a sequence flow (inside the pool, the process drives it).

This matters in two places where a different choice was available. The patient is a **pool, not a
lane**, even though patients appear throughout: the hospital cannot make a patient attend or pay,
and modelling the patient as a lane would have implied an authority the hospital does not have. The
same reasoning puts the referring organisation in its own pool, so the request for missing
information is a message the referring organisation may or may not answer, not a task the hospital
performs.

**Clinical, administrative and financial work is separated by lane, and the separation is the rule.**
The case states repeatedly who may *not* do something, and a lane is where that becomes visible.
Medical Secretaries check documents but have no decision task; the Consultant decision sits in the
Consultants lane; refunds sit in the Finance Team lane and no clinical lane reaches them. The lanes
are bound to `candidateGroups` on the user tasks, so the separation is not only drawn but assigned.

**Coverage of the case's entities.** Eighteen of the case's twenty participants appear as a pool or
a lane. Two do not, and both are deliberate:

- The **Clinical Nurse Specialist Administrative Support Team** owns a lane in
  `patient-pathway-all-entities.bpmn` but no lane in any operational process, because no activity in
  the four processes is exclusively its own.
- The **patient's GP and other letter recipients** are not a pool. They are recorded as the
  `recipients` a letter is dispatched to, reached through the correspondence service pool. Making
  them a pool would add a pool with no activity in it.

## 3. Task allocation

**A user task is work a named team performs and can be held to; a service task is work a system
performs.** Every user task carries the Camunda user task marker, a candidate group matching its
lane, and - where the task needs structured input - a bound form. Every automated step is a service
task with a job type, and every job type has a worker registered against it. The split is not by
difficulty but by accountability: a step that a person must sign their name to is a user task even
if a system could do most of it.

**Validation is separated from decision.** `validate-referral` reports whether the documents are
complete and returns no clinical judgement; the decision to accept, reject, query or redirect is
`N_C_ClinicalReview`, a user task in the Consultants lane. That split exists because the case says
Medical Secretaries may not assess clinical suitability: if the worker returned an opinion, the
distinction the case draws would be lost in the implementation.

**Two variables are recomputed rather than trusted.** `appointmentWithinTwoWeeks` is recalculated by
the worker from the slot actually found, not taken from the form, because the two-week telephone
rule applies to the real appointment date. `paymentStatus` is `not_required` for the funding routes
where the patient pays nothing, and the provider is not called at all. Both are cases where the
value the process acts on is produced by the component that can actually know it.

## 4. Gateways

**Every exclusive gateway declares a default flow, and the default is the branch that keeps a human
in the loop.** Camunda does not treat a conditionless sequence flow as a fallback: a gateway falls
back only to the flow named in its `default` attribute, and raises an incident when no condition
matches. All 22 exclusive gateways in the four processes therefore declare one, and the flow each
names carries no condition of its own.

Which branch is the default is a deliberate choice, and it is always the conservative one: an
unrecognised referral decision becomes "further information" rather than an automatic rejection; a
missing priority is treated as routine and goes to a person to review rather than straight to an
automatic booking; an unknown refund decision records no refund rather than paying money out. The
rule applied throughout is that when the data does not say, the process must not act irreversibly
and must not act silently.

The default choices deliberately mirror the ones already reviewed in the models this edition
replaces, so the migration did not quietly change which branch an unusual case follows.

**One activity, not a gateway, needed the same treatment.** `N_CNS_PathwayReview` in `core-4` is a
user task with three conditional outgoing flows. It has the same failure mode as a gateway without a
default and now declares one.

## 5. External interactions

**Every call outside the hospital is a job worker against a service task, and every simulated
service says what it does not model.** The four external services the case describes - scheduling,
treatment and imaging, the payment service provider and correspondence - are simulated in
`../workers/src/main/java/uk/ac/uwe/hprtas/workers/services/`, and each states its limitations in `workers/README.md`: no clinic
capacity, no real diary, no card processing or settlement, no printing or delivery confirmation.
Three keep a ledger keyed by the booking or payment reference, which is what makes "no duplicate
appointment", "no second charge" and "no second refund" testable rather than asserted.

**Message flows carry what crosses a boundary.** Twenty message flows across the four operational
processes, each naming what is exchanged ("availability request", "appointment letter", "approved
refund request"). No sequence flow crosses a pool boundary, which is what keeps the participant
boundaries meaningful rather than decorative.

**The refund is a separate job type, not a payment with the sign reversed.** The first edition
reused `process-payment` for refunds, which meant a refund was validated as a charge: it demanded
`chargeAmount` and `fundingRoute`, and on a hospital- or insurer-funded appointment the payment
worker short-circuits to `not_required` without calling the provider at all, so the refund never
left the hospital. `process-refund` now asks the provider whether the reference was ever settled,
refunds against what was actually taken, refuses to pay out more than was taken, and never refunds
the same appointment twice.

## 6. Exception handling

**A business rule that stops the work is thrown as a BPMN error, and every service task that can
raise one catches it.** Fourteen boundary error events across the four processes, one per error code
per activity. Each error path returns the token to the task that owns the request, so the request is
corrected and the step retried rather than the process being abandoned: the referral exceptions
return to the Medical Secretaries, the booking and dispatch failures to the Outpatient Bookings
Team, the refund error to the Finance Team.

**The reason for a failure is told apart by which catch event was taken, not by a variable.** This
was found by testing, not by design: the variables attached to a BPMN error are written to the scope
of the activity that raised it, and are not visible in the process scope once a boundary event
catches the error. An early version of `core-1` tried to distinguish the reasons by reading a
variable the failed worker had written, which silently never worked. The models now carry one catch
event per code so the routing is structural.

**"No slot" and "service unavailable" are outcomes, not errors.** When the scheduling service finds
nothing in the requested window, the worker completes normally with `slotAvailable: false` and an
alternative date; the process then decides, and `N_OB_RecordNoSlot` keeps a person in the loop. The
case requires a case with no suitable slot to be highlighted rather than quietly booked outside the
period the clinician asked for, and the model does that instead of treating it as a failure.

## 7. Assumptions

The models rest on fourteen recorded assumptions rather than on guesses made silently. They are in
`case-study-summary.md` section 10 with their rationale and the risk if they are wrong; the ones
that shape the structure most are:

- **AS-01** one specialist service and one HPAS instance serves all the named teams. This is what
  allows a single Hospital Trust pool with lanes rather than several hospital pools.
- **AS-02** the HPAS becomes the system of record; letters, email and telephone remain channels.
  This is why correspondence is a dispatch the process records rather than a conversation it models.
- **AS-05** clinical authorisation may be given by a Consultant or another authorised clinical
  professional; administrative roles may not. This is the rule `treatment-availability` enforces as
  `UNAUTHORISED_BOOKING_REQUEST`.
- **AS-06** urgent referrals follow an expedited path through the same booking process rather than a
  separate process. This is why `N_OB_UrgentReferral` routes to a review rather than to a second
  process.
- **AS-13** the seven-day letter target and the escalation thresholds are calendar periods measured
  from the appointment date.

Where the case is silent rather than merely unclear, the ambiguity is recorded too - the urgency
rules for enquiries (AS-03) are an assumption precisely because the case states they do not exist
yet.

## 8. Alternatives considered

**One process instead of four.** Rejected. It would have kept one instance per patient but produced a
diagram with four start events and no readable shape, and the case's stages are what the boundaries
follow.

**Keeping the first edition's three models and extending them.** Rejected, and this is the decision
with the highest cost. The three models did not cover the follow-up, cancellation, enquiry and
refund stages the case describes, and their element set had been built before the requirements were
written, so extending them would have meant re-identifying elements anyway. Rebuilding as four
`core-N` processes made the coverage visible and let each process be run on its own. The cost was a
migration with no rename path, paid once.

**Java with Spring Boot for the workers.** Rejected in `workers/README.md`. Six small workers do not
need the ceremony, and the Camunda 8 Node SDK keeps each of them short enough for the second owner
to read in full.

**Camunda 7.** Rejected on evidence, not preference: the platform binaries are no longer published,
and the course requires Camunda 8. The first edition accidentally used Camunda 7 form-binding syntax
(`camunda:formKey`), which Camunda 8 ignores; the models now bind forms with
`zeebe:formDefinition formId`, which is the mechanism that resolves against a deployed `.form`.

**Reusing `process-payment` for refunds.** Rejected after it was tried; see section 5.

**Modelling the patient as a lane.** Rejected; see section 2.

## 9. Trade-offs accepted, and what is not done

These are the costs of the choices above, recorded rather than hidden. The defects are numbered in
`../tests/test-plan.md` section 6.

**Known structural gaps.** An urgent referral with no slot in the requested period loops in
`core-1` instead of ending (`DEF-11`). The refund service task catches `INVALID_VARIABLE` but not
`PROHIBITED_FINANCIAL_DATA`, which the refund worker can also raise (`DEF-12`). Neither is
hidden: both are in the defect table, and the second is a one-line model change.

**Thirty-six of the fifty-five user tasks bind no form** (`DEF-13`). The tasks a scenario exercises
are covered, and the models are runnable end to end, but a task with no form has no defined variable
contract. The end-to-end evidence therefore supplies those variables at task completion rather than
from a form, and says so.

**Role-based access and the audit trail are not implemented** (`DEF-07`). Both are Must
requirements. The lane-to-candidate-group bindings are the deployment-side half of the first, and
nothing is done for the second. The requirements table records both as not met rather than
reclassifying them.

**The simulated services are per-process and in memory** (`DEF-10`), so a duplicate-prevention
result holds only within one worker process. The ledger is what makes the rule testable, and its
limitation is stated where the rule is claimed.

**The models are wider than a page.** The PDF exports in `../models/exports/` are sized to the
diagram with element labels at 10 pt, which is what keeps them readable; a diagram this wide cannot
be read at A4 without the labels becoming illegible. Each export is one page and is meant to be
read on screen, zoomed, rather than printed on one sheet.
