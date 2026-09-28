# Demonstration script

The demonstration `PB-008` asks for. It is written to be run against the repository at an identified
commit, in front of an audience, without editing anything while it runs.

**What it demonstrates:** the delivered system at an identified version - the five operational models,
the 41 Camunda Forms bound to their user tasks, and the seven external workers - with the normal path,
a decision that ends a referral, and one exception path that a defect used to block.

**Read `## What this does not demonstrate` before giving it.** Everything simulated or not
implemented is listed there, and it is part of the demonstration rather than an apology for it.

## The version being demonstrated

| | |
|---|---|
| Repository | `https://github.com/GuardFell/HPRTAS` |
| Commit to demonstrate | the commit this file is committed in; `git rev-parse --short HEAD` names it |
| Release tag | `submission-2026-09-29`, the tag on the commit this file is committed in. `release-1.0` names the earlier first release |
| Recorded run at that shape of the tree | `../../tests/evidence/core-5-message-exchange-and-layout-tidy_2c3c2eb_2026-09-28.txt` - all three levels and the checks, at the version that added `core-5`; the per-scenario paths are in `../../tests/evidence/operational-models-and-forms_end-to-end_57b3c1d_2026-09-28.txt` |
| Defects open at this version | `DEF-07` (no authentication, no enforced role separation), `DEF-08` (no form submitted by a signed-in user), `DEF-10` (simulated ledgers are per worker process), `DEF-14` (a condition on an activity's only outgoing flow is not evaluated) - `../../tests/test-plan.md` section 6 |

Say the commit out loud at the start. The test plan's rule is that a claim belongs to one version, and
the same rule applies to a demonstration.

## Before the audience arrives

The demonstration needs three things running, and only one of them can be recovered from quickly if it
is missing. Do this in advance.

### 1. The engine

```cmd
camunda-runtime\start-camunda.bat
```

First start takes about a minute. Check it **and read the version it reports**, because the repository
states 8.9.19 while the machine the recorded runs were made on has 8.10.0-alpha5 installed at
`D:\camunda\c8run-8.10.0-alpha5` - see the note under the execution record in
`../../tests/test-plan.md` section 5:

```bash
curl -s http://localhost:8080/v2/topology
```

Expect the broker to name a version and Tasklist to be at http://localhost:8080/tasklist, login
`demo` / `demo`. If the version is not 8.9.19, say so at the start of the demonstration: a result
belongs to the version it was produced on, and that applies to what you are about to show.

### 2. The models and the forms

A model points at its forms **by key**, so Tasklist needs both deployed or a user task shows no form.

```bash
cd models/operational
for f in *.bpmn; do curl -s -X POST "http://localhost:8080/v2/deployments" -F "resources=@$f;type=application/xml"; echo; done
cd ../../forms
for f in *.form; do curl -s -X POST "http://localhost:8080/v2/deployments" -F "resources=@$f;type=application/json"; echo; done
```

### 3. The workers - **leave this terminal visible**

```bash
cd workers
cp .env.example .env          # once
mvn compile exec:java
```

Wait for the line that names all seven:

```
HPRTAS external workers are running (7 workers). Press Ctrl-C to stop.
  validate-referral
  request-referral-documents
  check-appointment-availability
  check-treatment-availability
  process-payment
  send-correspondence
  process-refund
```

**This has to keep running for the whole demonstration.** The workers are separate processes, not part
of the engine. If they are not running, every service task stalls and the process sits where it is
with no error - which looks like a broken model rather than a stopped worker. Keeping this terminal on
screen, with the job log scrolling in it, is the clearest evidence that the automated steps are really
being carried out.

### 4. Sanity check, thirty seconds before you start

```bash
cd workers
mvn -q compile exec:java -Dexec.args="--check"      # the configuration and the wiring, no engine
python ../tools/verify_hprtas_bpmn_bindings.py      # the models, the forms and the workers agree
```

Both should pass. The bindings check prints `PASS: the models, the forms and the workers agree`.

## The demonstration

Three scenarios, about fifteen minutes with talking. Each one starts a new process instance, so a
scenario that goes wrong can be abandoned and restarted without disturbing the others.

**The one thing to know about the simulated services.** The scheduling, treatment and payment services
each keep an in-memory ledger keyed by the request, and they answer the same request the same way
every time. So a second booking with the same speciality, priority and time frame gets the *same*
slot, and the payment service treats a repeated payment reference as a duplicate on purpose. That is
what makes "no duplicate appointment" and "no second charge" demonstrable - and it also means you
cannot show two different scheduling outcomes on the same inputs. Vary the inputs, or restart the
workers to clear the ledgers (there is a note on that at the end).

---

### Scenario 1 - the main path: a referral arrives and an appointment is arranged

Go to **Tasklist → Processes**, start
`Core 1 - Referral receipt, clinical review and new patient appointment`.

Then work the instance in **Tasklist → Tasks**. Each task is assigned to a candidate group, so
**assign it to yourself first** - an unassigned task renders its form read-only and `Complete Task`
stays disabled.

| Step | Task in Tasklist | Lane | Fill in | What it shows |
|---|---|---|---|---|
| 1 | Check the referral and its supporting information | Medical Secretaries | `documentsComplete` **ticked**; referring organisation `St Mary GP Surgery`; a referral and patient identifier | A referral is registered and its supporting information checked against the expected list (`FR-001`, `FR-002`, `AC-01`) |
| - | *no task - the worker acts* | | | `validate-referral` runs; the process continues **only because the referral was ticked complete** |
| 2 | Review the referral clinically | Consultants | decision `accepted`, a reason | The Consultant acceptance gate. This is the gate `FR-006` is about: no appointment can be arranged until this decision is `accepted` (`AC-02`) |
| - | *no task - the worker acts* | | | `check-appointment-availability` asks the simulated scheduling service and returns a slot |
| 3 | Record the booking request | Outpatient Bookings Team | speciality `Oncology`, priority `routine`, requested time frame `14`, recipients `patient, GP`, document type `new_patient_clinic_letter` | The request a booking is made from (`FR-010`); the recipients and document type are what the correspondence worker is given (`FR-012`) |
| - | *no task - the worker acts* | | | `send-correspondence` dispatches the letter. **The appointment fell inside two weeks, so the model added the telephone path** - watch the next task appear, which it would not have done for a later appointment (`FR-014`) |
| 4 | Telephone the patient | Outpatient Bookings Team | outcome `contacted` | Every contact attempt and its outcome is recorded (`FR-013`) |

**End state:** the instance reaches `N_OB_AppointmentArranged` and completes. Show it in **Operate** -
the green path through the diagram is the evidence, and it is read from the engine rather than from
the test.

**Talking point.** Step 2 is the acceptance gate, and it is worth pausing on: the case requires that
nothing is booked before an authorised Consultant has accepted the referral. In the model that is not
a note in a document - `N_OB_PrepareRequest` has exactly two incoming flows and both are downstream of
an `accepted` decision, so the booking step is unreachable without it.

---

### Scenario 2 - an alternative path: the Consultant does not accept the referral

Start `core-1` again. Work the same first two tasks, but at step 2 choose **`rejected`** and give the
reason.

**End state:** the instance reaches `N_C_Rejected` and completes. The booking step is never reached.

This is the branch `TC-02` covers, and it is worth demonstrating because it is the one that proves the
gate from the other side: the same model, the same entry, and no appointment.

`redirected` and `further_information` are the other two answers on the same form and take their own
endings. `further_information` was not covered by the recorded run at `57b3c1d`, so if you show it,
show it as a demonstration and not as a tested result.

---

### Scenario 3 - an exception path: an urgent referral with no suitable slot

This is the path that used to be broken. `DEF-11` was that an urgent referral with no slot available
re-checked availability for ever - the scheduling service answers the same request with the same
answer, so the instance could never leave that branch. It was fixed at `57b3c1d`, and this scenario is
its re-run.

Start `core-1` again and work it as follows.

| Step | Task in Tasklist | Fill in | Why |
|---|---|---|---|
| 1 | Check the referral | `documentsComplete` ticked; `St Mary GP Surgery` | as scenario 1 |
| 2 | Review the referral clinically | decision `accepted` | the gate again |
| 3 | Record the booking request | speciality `Oncology`, priority **`urgent`**, requested time frame `14` | **priority `urgent` is what routes this differently.** `N_OB_UrgentReferral` branches on `priority = "urgent"` |
| 4 | Record that no suitable slot was found | outcome `none`, action `highlighted_for_pathway_team` | the situation is recorded and highlighted rather than the patient being booked outside the requested period (`FR-016`, `AC-05`) |
| 5 | Escalate the urgent referral | route `expedited_slot` | the urgent case leaves the booking process instead of re-checking (`FR-015`) |

**End state:** the instance reaches `N_PC_UrgentEscalated` and completes.

**What to point at.** In **Operate**, the availability check `N_OB_CheckAvailability` appears **once**
in the path. Before the fix it was revisited indefinitely. That single occurrence, plus the instance
completing, is the whole evidence that the loop is gone - and it is the kind of thing a diagram alone
would not have shown.

**If it does not take the urgent branch:** the input the branch reads is `priority`, and it has to
arrive as the string `urgent`. If it is left at `routine`, the instance takes the routine delay-review
path and ends at `N_PC_Referred` instead. Both endings are correct; only one is the exception path.

---

### Optional: the other four models, if there is time

Each is a separate process and is started separately.

| To show | Start | The step that matters | Verified by |
|---|---|---|---|
| Funding, payment and the between-cycle review | `core-2` | Funding route `patient`, a charge, then payment through the provider; then a pre-cycle review and a treatment modification that affects the charge | scenario 2 of the `57b3c1d` run |
| The clinic letter and its escalation | `core-3` | Letter prepared and approved, then an administrative check that can return it to the Consultant | scenario 3 |
| Follow-up, cancellation, enquiry and refund | `core-4` | A paid cancellation reaching the Finance Team, and an enquiry classified `clinical` so it is **not** answered from Call Handling | scenarios 4, 5 and 6 |
| The missing-information exchange, and the process that waits | `core-5` | The request the worker publishes as a message, and the instance sitting on the catch event until the reply arrives **correlated by the referral reference** | scenario 9 |

`core-4` has two **message** start events, for a cancellation arriving and for a patient enquiry
arriving. Neither is requested by the hospital, so neither can be an ordinary start event. Send them
from the workers project:

```bash
cd workers
mvn compile exec:java -Dexec.args="--publish-message patient-cancellation-or-non-attendance"
mvn compile exec:java -Dexec.args="--publish-message patient-enquiry"
```

The engine accepting the message is not proof that an instance appeared - check **Operate** for the
new instance. If the message name matches nothing deployed, the publication is still accepted and
correlates with nothing.

`core-5` is the other way round: it starts from the Processes page like any other model, and then
**waits**. Start it with a referral reference on the instance, complete *Record the missing
information*, and watch the instance stop at *Missing information received* - the service task before
it has published the request, and the process is now doing nothing but waiting for the answer. Release
it by publishing the reply with the same referral reference:

```bash
cd workers
mvn compile exec:java -Dexec.args="--publish-message missing-information-supplied --correlation-key REF-2026-0001"
```

Two things are worth pointing at. The first is that a publication whose correlation key no
subscription is waiting on is **accepted by the engine and correlated with nothing** - so the
observable proof is the instance moving in Operate, never the publisher's exit code. The second is
that this is what the `core-1` step does not do: `core-1` records the same request with a user task
standing in for the answer, and only `core-5` models the wait.

## What this does not demonstrate

Say these out loud. They are the difference between a demonstration and a claim.

1. **Nobody logs in as themselves.** Tasklist is `demo` / `demo`, and the candidate group on each task
   is not enforced by anything. Any user can see and claim any task. The lane structure is a
   *structural* separation of duties, not an access control (`DEF-07`).
2. **No audit trail.** Nothing records who did what and when, which is a Must requirement.
3. **The tasklist does not prove a form was submitted by a person.** In every recorded run, including
   the `57b3c1d` run, the user tasks were completed through the API with the variables the form would
   supply. The forms' field sets are the models' variable contracts, and they have been shown to
   deploy, import and resolve - but no run has a human filling one in end to end (`DEF-08`). If you
   fill a form in live during this demonstration, say clearly that it is the first time and that it is
   not in the evidence.
4. **The four external services are simulated, and they are in-memory.** No real scheduling, no real
   card processing, no printing or postage, no delivery confirmation, and the ledgers are cleared when
   the workers restart (`DEF-10`).
5. **The patient's communication preference is recorded, not honoured.** The forms offer `Post`,
   `Digital`, `Authorised Representative` and `Accessible Format`, and there is no printing,
   alternative-format production or interpreter behind any of them. There is no translation option in
   the list at all. `NFR-011` and `FR-051` are partially supported.
6. **Management reporting and the downtime procedure are not implemented** (`FR-049`, `FR-050`).
7. **The strategic and socio-technical models are not deployed and cannot be run.** They are views;
   Camunda rejects a deployment containing no executable process.
8. **One known condition is documentation rather than a guard.** On `N_CL_ModifyTreatment` the
   condition on its only outgoing flow is never evaluated by Camunda (`DEF-14`), so treat it as
   intent. `N_F_NotifyPatient` had the same fault and was fixed by moving the decision to a gateway.

The list of every known defect, with what is fixed and what is not, is `../../tests/test-plan.md`
section 6. Quote the count from there rather than from memory.

## If something goes wrong

| Symptom | Cause | Recovery |
|---|---|---|
| A task never appears, no error | The workers are not running, or the job type is not registered | Check the workers terminal. The job stays queued and the process waits where it is |
| A user task shows no form | The form was not deployed with the model, or the task is unassigned | Deploy `forms/*.form` as well; assign the task to yourself. A form is resolved when the task is **created**, so a form deployed after the task exists does not reach it - the task has to be created again |
| The instance sits on a service task | Same as the first row | Same |
| A condition seems ignored | It is on an activity's only outgoing flow (`DEF-14`) | Expect it, and say so. Conditions are enforced on gateways |
| Too many instances in Tasklist | Previous runs | Filter by process, or work in **Operate** where the diagram shows which instance is which |
| The Processes page offers a process of nearly the right name that this script never mentions | The engine keeps every definition ever deployed to it, and the models the `core-N` set replaced are still there - `referral-to-appointment`, `treatment-authorisation-and-booking` and `clinic-letter-and-pathway-monitoring` sit beside `core-1`, `core-2` and `core-3` | Start the one whose name begins **`Core 1`** ... **`Core 5`**. Those five are the delivered models; everything else on that page is an earlier edition or another exercise, and none of it is demonstrated here |

## Resetting between rehearsals

The simulated ledgers live in the workers, so restarting the workers clears every booking, payment
and refund reference the services remember:

```
Ctrl-C in the workers terminal, then: mvn compile exec:java
```

**Restart the engine only as a last resort.** It is slower, and `c8run stop` is unreliable - the
supported way to stop it is `camunda-runtime\stop-camunda.bat`, and the check that it really stopped
is that `http://localhost:8080/v2/topology` stops answering.

## The thirty-second version, if asked to summarise

> Five executable processes on Camunda 8, forty-one forms bound to their user tasks, and seven Java
> workers behind the service tasks. It runs the pathway from a referral arriving to the refund after a
> cancelled appointment. Nineteen of the twenty-one recorded test scenarios have a result - twelve
> pass, seven pass in part - and the two that do not are blocked on a signed-in user and on the
> seven-day letter timer. What is not there is authentication and the audit trail, and no form has
> been submitted by a signed-in user; both are recorded as open defects rather than left out.
