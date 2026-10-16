# Justification of Decisions

Why the operational process is modelled the way it is. This is the explanation the BPM & EA
presentation asks for: the process structure, the participant boundaries, how tasks are allocated,
how the gateways are arranged, how the process meets the outside world, how failure is handled, the
assumptions the models rest on, the alternatives that were rejected and the trade-offs accepted.

It is written against the six operational models in `../models/operational/` - the five stages and
`core-0`, which calls them - the strategic models
in `../models/strategic/`, and the evidence in `../tests/evidence/`. Readable PDF exports of every
model are in `../models/exports/`. Where a decision has a cost, the cost is stated here rather than
left out: a justification that claims no downside is not a justification.

## 1. Process structure

**The five stages are integrated by a sixth process that calls them.** The feedback this edition
answers asked for the processes to be integrated into one coherent end-to-end workflow, and the
integration is `core-0-patient-pathway-end-to-end`: an executable process with no task of its own
whose stages are **call activities** naming `core-5`, `core-1`, `core-2`, `core-3` and `core-4`, in
that order, with a gateway between them. It is started from its own start form with the referral the
instance belongs to, so the front door collects the referral's identifiers, the referring
organisation and whether the supporting information arrived - and that last answer is what decides
whether the documentation exchange runs first.

**The gates between stages read only what the process before them returned.** A call activity
returns its instance's variables to the caller when it completes, so `decision` and `slotAvailable`
gate the move from `core-1` to `core-2`, and `consentGiven` gates the move from `core-2` to `core-3`:
accepted-with-an-appointment-arranged continues, a referral that was rejected, redirected, queried or
escalated ends the pathway at the end event that says so, and a consultation the patient did not
proceed from ends it there. Nothing is decided by `core-0` itself: it holds no clinical or
administrative task, every check inside a stage stays with the teams, forms and workers that stage
binds, and the parties outside the hospital are drawn in the process that exchanges with them.

**The alternatives to call activities were weighed.** Chaining the processes with messages - each
stage publishing the message the next one starts on - was rejected because it makes the hand-off
asynchronous and unconditional: a referral that was rejected would still start the treatment process,
and the decision about whether to continue would have to be made inside the stage that receives the
message rather than at the boundary. Merging the five into one process was rejected for the reason
given below. The cost of the call activities is real and is stated in section 9: the pathway is one
instance of `core-0` plus one instance per stage, so a reader of Operate sees a tree rather than a
single token, and `core-4`'s follow-up stage is reached only when the pathway runs that far - its two
message-started paths (a cancellation arriving, an enquiry arriving) are started by those messages,
not by `core-0`.

**Five processes, not one.** The pathway was modelled as five executable processes rather than one
large one, because the case describes four points at which the work is picked up and handed on, and
those hand-offs are where the interesting rules live. `core-1` ends when the first appointment is
arranged, `core-2` when a treatment cycle has been reviewed, `core-3` when a letter is distributed
or delayed, `core-4` when a follow-up is booked or an episode closed, and `core-5` when the
referring organisation has supplied the documentation it was asked for. `core-5` is not a further
stage of the case but the missing-information exchange modelled as it really is: a request and a
reply crossing the referring organisation's pool as messages, where `core-1` records the same step
inside one process. Each has a start an actor can identify with, and each can be deployed, run and
demonstrated on its own.

The cost is real: a patient's journey crosses process boundaries, so the five processes share a
patient and appointment identity rather than a single token, and nothing in the models stops a
second process from being started for the same patient. The alternative - one process with five
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
together: one pool with a lane for each team that owns a stage of it, at one activity per stage, not
deployed, used to check that the operational split does not lose a team. The parties outside the
hospital are not drawn as pools here - at this level the model is the hospital's process, and each
exchange with an outside party is named in the documentation of the activity it belongs to, carried
as a dependency in the i* models, and drawn as a pool with its message flows in the operational
models.

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

**Coverage of the case's entities.** The operational models draw the parties outside the hospital
as their own pools. In the strategic view, which is drawn as one pool, the coverage is by team:
every internal entity of the case study has a lane. The i\* models in `../models/socio-technical/`
are the workshop goal model redrawn - the patient, the reception desk, the doctor and the
specialist - and are a view of that pathway rather than a second participant list; see
`../models/socio-technical/modelling-choices.md` section 4. Two of the case's participants are
deliberately not drawn anywhere as a pool or a lane:

- The **Clinical Nurse Specialist Administrative Support Team** carries no lane in the strategic model
  either, because no activity in the five processes is exclusively its own.
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

**Input validation has two layers, and the first one is the form.** A field the process cannot
continue without is `required`; a reference has to look like a reference; a person's name has to
contain a letter, so `12345` is not a name; an NHS number has to be ten digits (BR-46); a number the
process acts on has an upper bound; and a date that is a future commitment - a treatment start date,
a rescheduled date, an alternative offered - is marked so the picker does not offer a day in the
past. The rules are **one policy applied from one place** (`tools/apply_form_validation.py`, whose
tables the bindings check imports and re-checks) rather than 43 hand-edited files, so a rule cannot
reach one form and miss another, and a form cannot quietly lose it. `tools/check_forms_formjs.mjs`
then drives the rules through `@bpmn-io/form-js-viewer`, the library Tasklist renders forms with, and
asserts that each one fires.

The second layer is the worker, which re-checks everything it reads and refuses what it cannot act
on. That is deliberately not redundant: the form is what stops unusable input at the desk, where it
can be corrected in seconds, and the worker is what stops it if the task was completed by an API
call or by a client that ignored the form. The two layers are documented side by side because the
division between them is a design decision - the form carries what a field can check about itself,
the worker carries what needs the other variables (a referral marked complete that also lists missing
items, an NHS number that is the wrong shape, an amount that is not a number).

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
matches. All 26 exclusive gateways in the five processes therefore declare one, and the flow each
names carries no condition of its own.

**Where paths come together, a merging gateway says so.** Twenty-eight convergence points across the
five stage processes - the places where a repair path returns to the task that owns the request, an
alternative decision re-joins, or a boundary event's outcome lands - are drawn as an explicit
**exclusive merge gateway** rather than as several arrows arriving at one activity. In Camunda the two
behave the same way, so this is not a behaviour change: it is the diagram saying what it means, and
the feedback asked for it. Each gateway carries the documentation of what it merges and why. The
gateways are named for the convergence they represent (`N_OB_MergeContactPath`, `N_F_MergeRefundRequest`),
so a reader of the model or of a run's element path can see them.

**Every convergence is drawn, and none is left at the task.** Two were left at the task in the
previous edition, and each now merges through a gateway. The first is the pair of payment-error
repair paths into `N_F_CorrectPaymentRequest` in `core-2`, which that edition recorded as its one
exception with a layout reason: the corridor under `N_F_ProcessPayment` carries the task's label and
the two boundary events' labels, and a return path routed across the payment column crossed the
task's own box. The merge gateway needs no such crossing - `N_F_MergePaymentCorrection` sits between
the boundary events and the task they feed, in the one free band the corridor leaves - so the
exception was retired rather than kept. The second is the pair of paths into `N_AM_EscalateHigher`
in `core-3`, which was not recorded as an exception anywhere; `N_AM_MergeEscalation` merges it. No
convergence in the six models is left at a task now, and the layout check passes on both.

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

**The hospital's own records live in a database, not in the workers' memory.** The case's assumption
AS-02 - the HPAS becomes the system of record - was, until this edition, only half true: the
simulated services kept their ledgers in maps, so an appointment, a payment or a refund was known only
to the worker process that had made it, and a restart forgot everything (`DEF-10`). The workers now
persist the patient register, the referrals, the appointments, the payment and refund ledger, the
dispatches and the outstanding information requests in an embedded H2 database
(`workers/src/main/java/uk/ac/uwe/hprtas/workers/db/Database.java`), which is the database Camunda 8
Run itself keeps its data in. One connection serves every worker thread and every operation takes its
lock, which is the same discipline the in-memory ledgers used: the check and the write that follows
it have to be one step, or two jobs can both find a reference unpaid and both take money.

**The process reads what earlier runs wrote, and that is the point of it.** The identifier the case
gives the referring organisation is the NHS number, so that is what the register is looked up by
(BR-46, FR-048): a patient the hospital already holds is matched to the record rather than registered
again, a name that disagrees with the record is reported as a disagreement instead of being written
over it - which of the two is right is a person's to say - and one NHS number arriving under a second
hospital identifier is reported as a conflict rather than stored as a second patient. What is
retrieved is used: a letter that names no channel is dispatched on the channel the patient's own
record holds (NFR-011, FR-051), and a booking request for a different period supersedes the
appointment the earlier request made instead of leaving two live appointments on the register. The
tests drive each of those (`PersistenceTest`), including one that closes the store and opens it
again to show a settled payment is still a duplicate - which is the part a memory ledger could never
show.

**Every call outside the hospital is a job worker against a service task, and every simulated
service says what it does not model.** The four external services the case describes - scheduling,
treatment and imaging, the payment service provider and correspondence - are simulated in
`../workers/src/main/java/uk/ac/uwe/hprtas/workers/services/`, and each states its limitations in `workers/README.md`: no clinic
capacity, no real diary, no card processing or settlement, no printing or delivery confirmation.
Three keep a ledger keyed by the booking or payment reference, which is what makes "no duplicate
appointment", "no second charge" and "no second refund" testable rather than asserted.

**Message flows carry what crosses a boundary.** Twenty-two message flows across the five operational
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
raise one catches it.** Sixteen boundary error events across the five processes, one per error code
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

**One process instead of five.** Rejected. It would have kept one instance per patient but produced a
diagram with five start events and no readable shape, and the case's stages are what the boundaries
follow.

**Keeping the first edition's three models and extending them.** Rejected, and this is the decision
with the highest cost. The three models did not cover the follow-up, cancellation, enquiry and
refund stages the case describes, and their element set had been built before the requirements were
written, so extending them would have meant re-identifying elements anyway. Rebuilding as four
`core-N` processes made the coverage visible and let each process be run on its own. The cost was a
migration with no rename path, paid once.

**Node.js for the workers, with the Camunda 8 SDK.** Rejected in `workers/README.md`. It keeps each
worker short, but it puts a second runtime and a second Camunda client in the repository, free to
drift from the engine's release; Java 21 is the runtime the engine already runs on, so the workers
and the engine move together. The Spring Boot starter was not taken either: the configuration is
layered over three levels and resolved by the project's own `Config`, and a framework's
auto-configuration would leave two parties deciding what the connection settings are.

**Camunda 7.** Rejected on evidence, not preference: the platform binaries are no longer published,
and the course requires Camunda 8. The first edition accidentally used Camunda 7 form-binding syntax
(`camunda:formKey`), which Camunda 8 ignores; the models now bind forms with
`zeebe:formDefinition formId`, which is the mechanism that resolves against a deployed `.form`.

**Reusing `process-payment` for refunds.** Rejected after it was tried; see section 5.

**Modelling the patient as a lane.** Rejected; see section 2.

## 9. Trade-offs accepted, and what is not done

These are the costs of the choices above, recorded rather than hidden. The defects are numbered in
`../tests/test-plan.md` section 6.

**Known structural gaps.** An urgent referral with no slot in the requested period used to loop in
`core-1` instead of ending (`DEF-11`); that is fixed at `57b3c1d`, where the escalation ends the
booking process instead of returning the token to the availability check. The refund service task
caught `INVALID_VARIABLE` but not `PROHIBITED_FINANCIAL_DATA`, which the refund worker can also
raise; that is fixed at `9138bcc`, and fixing it turned up a fault of the same family - a condition
on the only outgoing flow of an activity is never evaluated (`DEF-14`), so two conditions in
`core-2` read as guards and were not. None of this is hidden: all three are in the defect table.

**Every user task now binds a form** (`DEF-13`, closed at `a62e783`). This was the largest gap in
the first edition. Thirty-six of the fifty-five tasks had no form at all, so their variable contract
was declared nowhere; and the eight that existed had never rendered a field, because a group's
children belong under `components` and a group's `path` prefixed every child key with a name no
gateway read. The end-to-end evidence at `a7f0dd6` supplies the variables at task completion rather
than from a form and says so; `../forms/README.md` records what the forms now cover and what has not
been driven through Tasklist.

**Role-based access and the audit trail are not implemented** (`DEF-07`). Both are Must
requirements. The lane-to-candidate-group bindings are the deployment-side half of the first, and
nothing is done for the second. The requirements table records both as not met rather than
reclassifying them.

**The simulated services are per-process and in memory** (`DEF-10`), so a duplicate-prevention
result holds only within one worker process. The ledger is what makes the rule testable, and its
limitation is stated where the rule is claimed.

**The record store is a prototype, not the hospital's system of record.** It is a single-file H2
database with no access control, no backup, no retention policy and no audit trail of who read
what, and the demonstration runs it in memory so that nothing is left behind. It shows that the
process retrieves and updates stored records rather than only its own variables; it does not meet the
data-protection, retention or audit requirements the case states, and it is not offered as if it did
(`DEF-07`, `FR-046`, `FR-047`).

**The models are wider than a page.** The PDF exports in `../models/exports/` are sized to the
diagram with element labels at 10 pt, which is what keeps them readable; a diagram this wide cannot
be read at A4 without the labels becoming illegible. Each export is one page and is meant to be
read on screen, zoomed, rather than printed on one sheet.
