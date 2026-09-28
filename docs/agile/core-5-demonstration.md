# Demonstrating `core-5`, the missing-information exchange

`demonstration-script.md` gives the demonstration as a whole, and covers this model in a single row of
its *Optional* table. This file is that row expanded into a scenario that can be given on its own.

**What it demonstrates:** the one place in the delivered system where a process genuinely **waits** -
a service task publishes a request as a BPMN message, the instance stops at an intermediate message
catch event, and a reply releases it only because it carries *this* referral's reference. Every other
model records the answer with a user task; this is the exchange with the wait in it.

It is written to be run against the repository at an identified commit, in front of an audience,
without editing anything while it runs.

**Read `## What this does not demonstrate` before giving it.** What is simulated, and what the
operator has to stand in for, is listed there, and it is part of the demonstration rather than an
apology for it.

## The version being demonstrated

| | |
|---|---|
| Repository | `https://github.com/GuardFell/HPRTAS` |
| Commit to demonstrate | the commit this file is committed in; `git rev-parse --short HEAD` names it |
| Release tag | `release-2.0` and `submission-2026-09-29`, the tags on the commit this file is committed in. `release-1.0` names the earlier first release |
| Recorded run at this shape of the tree | `../../tests/evidence/core-5-message-exchange-and-layout-tidy_2c3c2eb_2026-09-28.txt`, scenario 9 |
| The test case this scenario answers | `TC-03`, which passes at `2c3c2eb` - `../../tests/test-plan.md` section 4 |
| Defects open at this version | `DEF-07`, `DEF-08`, `DEF-10`, `DEF-14` - `../../tests/test-plan.md` section 6 |
| Rehearsed live | 2026-09-28 at `8cbed4d`, Camunda 8 Run 8.9.19 with the seven workers running: the three paths below were driven against the engine with the variables the forms supply, and every state quoted below was read back from the engine. The clicks are the ones `demonstration-script.md` describes |

Say the commit out loud at the start, as `demonstration-script.md` says. A claim belongs to one
version, and a demonstration is a claim.

## Why this model exists at all

`core-1` covers the same requirement - a referral that arrives without its supporting information -
and records it inside one process, with a user task standing in for the referring organisation's
answer. That is a fair way to draw a process the hospital controls end to end. It is not what the
exchange is: the referral does not receive the documents when a secretary decides it has, it receives
them when the referring organisation sends them, and until then the referral is doing nothing.

`core-5` is that second reading, and it is `FR-003` with `BR-01`, `BR-02` and `EX-01`: the Medical
Secretaries check the referral against the documentation it is expected to carry, record what is
missing and ask for it (`BR-01`, `BR-02`), and the instance waits for the answer (`EX-01`).

It is also the only model in the set whose service task calls **no simulated external service**. The
other side of this exchange is a participant, not a system the hospital calls, so nothing is
simulated: what leaves the pool is a message, and the engine is what delivers it.

## The two halves of the exchange

The two publications are not the same kind of thing, and the project keeps them apart.

| | the request | the reply |
|---|---|---|
| Message name | `missing-information-requested` | `missing-information-supplied` |
| Sent by | the worker `referral-document-request`, at `N_MS_RequestDocuments` | the referring organisation |
| Who sends it here | the worker, for real | the operator, with `--publish-message` - see below |
| Does a subscription exist | **no**: nothing in this repository waits for it | **yes**: `N_MS_DocumentsSupplied` waits on it |
| Correlation key | `referralId` is what the receiving side *would* key on | `=referralId`, read from the instance |
| Message id | `missing-information-requested:<referralId>:<jobKey>` | none is set by the demonstration |
| Time to live | two minutes (`MessagePublisher.TIME_TO_LIVE`) | two minutes |

Two consequences worth saying out loud:

- **The request is published into nothing.** No process in this repository subscribes to it, because
  the referring organisation's process is not part of what was built. The publication still succeeds,
  and the worker log still records it, and that is the honest picture: the hospital's half of the
  exchange goes out, and what the other participant does with it is outside the model.
- **The reply is not the hospital's to send.** `missing-information-supplied` belongs to the referring
  organisation, so no worker publishes it on the hospital's behalf. The demonstration sends it by
  hand, and says so: it is standing in for the participant that owns the message. What makes this
  worth watching rather than a formality is the negative half in step 4.

## The model, element by element

`models/operational/core-5-missing-information-message-exchange.bpmn` - a collaboration of two
participants, one lane, and eight elements in the only process.

| Element | Kind | What it is |
|---|---|---|
| `N_MS_ReferralReceived` | start event | The referral has arrived with its supporting information incomplete |
| `N_MS_RecordMissingItems` | user task | Record what is missing, who to ask and how - form `missing-information-request`, candidate group `medical-secretaries` |
| `N_MS_RequestDocuments` | service task | Job type `request-referral-documents`; the worker publishes the request |
| `B_MS_RequestInvalid` | boundary error event | On the service task; catches `INVALID_VARIABLE` and returns the request to the task that owns it (`F8`) |
| `N_MS_DocumentsSupplied` | intermediate message catch event | **The wait.** Message `missing-information-supplied`, subscription correlated by `=referralId` |
| `N_MS_CheckSuppliedDocuments` | user task | The same documentation checklist applied to what has arrived - form `referral-check` |
| `N_MS_DocumentsComplete` | exclusive gateway | `complete` goes to the end; the default flow `F7` goes back to `N_MS_RecordMissingItems` |
| `N_MS_ReadyForReview` | end event | The referral is ready for clinical review |

The pool is the `Hospital Trust`; the second participant, `Referring GP or another hospital`, is drawn
without a process, and the two message flows are the request going out and the reply coming in.

The gateway declares `default="F7"`, so a run that leaves the documentation incomplete cannot fall
through the gateway by accident: `complete` is the conditional flow and still-incomplete is the
default. That is `DEF-01`'s rule, and the model follows it.

## Before the audience arrives

Three things have to be running, and one of them cannot be recovered from quickly if it is missing.
`demonstration-script.md` has the general version of all three; what follows is the minimum for this
model alone.

### 1. The engine

```cmd
camunda-runtime\start-camunda.bat
curl -s http://localhost:8080/v2/topology
```

Tasklist is at http://localhost:8080/tasklist, login `demo` / `demo`.

### 2. This model and its two forms

A model points at its forms **by key**, so both halves have to be deployed or the user tasks show no
form. Only this model is needed:

```bash
cd /d/BPM/HPRTAS
curl -sS -X POST "http://localhost:8080/v2/deployments" -F "resources=@models/operational/core-5-missing-information-message-exchange.bpmn;type=application/xml"
curl -sS -X POST "http://localhost:8080/v2/deployments" -F "resources=@forms/missing-information-request.form;type=application/json"
curl -sS -X POST "http://localhost:8080/v2/deployments" -F "resources=@forms/referral-check.form;type=application/json"
```

Each response names the version it was deployed as, and the number will be a high one - this engine
has been deployed to many times. It means nothing to the audience; what matters is that the response
names the process and both forms rather than an error.

**If you are in PowerShell rather than Git Bash or cmd**, four things differ, and they are the whole
list. Each was checked on PowerShell 5.1:

| In Git Bash / cmd | In PowerShell |
|---|---|
| `cd /d/BPM/HPRTAS` | `cd D:\BPM\HPRTAS` |
| `curl` | `curl.exe` - in PowerShell `curl` is an alias for `Invoke-WebRequest`, which has no `-F` |
| `curl -d '{"json":…}'` | `Invoke-RestMethod -Method Post -Uri … -ContentType "application/json" -Body '{"json":…}'` - PowerShell passes a quoted JSON body to a native command mangled, and the engine answers `400 Failed to read request` |
| `mvn -Dexec.args="a b"` | `mvn "-Dexec.args=a b"` - quoting only the value makes PowerShell split the argument and Maven reports `Unknown lifecycle phase ".args=…"`. **Quote the whole `-D…` argument**, as written below |

### 3. The workers - **leave this terminal visible**

```bash
cd workers
mvn compile exec:java
```

Wait for the line that names all seven job types, and find `request-referral-documents` in it:

```
HPRTAS external workers are running (7 workers). Press Ctrl-C to stop.
  validate-referral
  request-referral-documents
  check-appointment-availability
  ...
```

**The banner lists job types, not worker names.** The worker that serves
`request-referral-documents` is called `referral-document-request`, and the log lines it prints name
it that way - that is the line to point at in step 2.

This terminal has to keep running for the whole demonstration. The workers are separate processes, not
part of the engine: if they are not running, the service task stalls with no error, which looks like a
broken model rather than a stopped worker.

### 4. Thirty seconds before you start

```bash
cd workers
mvn -q compile exec:java "-Dexec.args=--check"
cd ..
python tools/verify_hprtas_bpmn_bindings.py
```

Both should pass. The bindings check is worth more here than usual: it includes a rule that the
variable a message subscription is correlated by is written by a form or a worker in the same model,
and prints, if it is not, that the instance "would raise an incident when it reached the event instead
of waiting on it". That rule passing is what says step 1 below can set the correlation key at all.

## The demonstration

Three paths, about ten minutes with talking. Each starts a new instance, so one that goes wrong can be
abandoned and the next started without disturbing it.

### Step 0 - decide the referral reference, and start the instance

In **Tasklist → Processes**, start
`Core 5 - Missing information requested from, and supplied by, the referring organisation`.

Before you start it, say what the reference is going to be - `REF-DEMO-0001` - because everything in
this demonstration hangs off it. **The instance has no start form**, so there is nothing to fill in
when it starts; the reference arrives at step 1, in the first task's form.

> If your Tasklist offers a JSON box for variables when starting a process with no start form, putting
> `{"referralId": "REF-DEMO-0001"}` there also works - but the form is what the model relies on, and
> the value you type in step 1 is the one that counts.

### Step 1 - record the missing information

Work the instance in **Tasklist → Tasks**. Assign the task to yourself first: an unassigned task
renders its form read-only and `Complete Task` stays disabled.

| Field | Value | Why it matters |
|---|---|---|
| `Referral ID` | `REF-DEMO-0001` | **This is the correlation key.** Required, and the value the reply has to carry |
| `Items Requested` | `investigation results` and `diagnostic reports`, one per line | Required; the worker refuses a request that names nothing |
| `Requested From` | `St Mary GP Surgery` | The organisation being asked |
| `Method` | `Letter` | How the request went out |
| `Date Requested` | today | When |
| `Requested By` | your name | Who |
| `Notes` | anything, or nothing | - |

Complete the task. What to point at: this form is the model's variable contract, and the value in
`Referral ID` is what the next step keys on. There is no other place the instance gets it.

### Step 2 - the worker publishes the request

There is no task for this; the instance moves on its own, and the evidence is in the **workers
terminal**. The line to look for names the worker, the message and the key it published under:

```
... "message":"request for missing information published" ... "worker":"referral-document-request"
    "correlationKey":"REF-DEMO-0001" "itemCount":2 ...
```

The job also writes two variables back: `informationRequestStatus` = `sent`, and
`informationRequestMessageKey`, the key the engine recorded the publication under. The instance now
holds a record that the request went out, which is more than `core-1` records at the same step.

Say plainly what this does and does not mean: the message left the pool, and nothing on this engine
was waiting for it, because the referring organisation's process does not exist here.

### Step 3 - the instance is waiting

In **Operate**, the instance is now sitting on `Missing information received`. The green path stops
there. This is the whole point of the model: the referral is **not** proceeding on the assumption that
the documents will arrive, and no task is open anywhere.

Worth pausing on: the subscription was created with the key `REF-DEMO-0001`, taken from the form at
step 1. A subscription whose key cannot be read raises an incident instead of waiting, and the check in
"Before the audience arrives" is what says this model cannot be in that state.

### Step 4 - a reply for another referral changes nothing (**the part to slow down for**)

Publish the reply addressed to a *different* referral, in a **second** terminal - the workers are
holding the first one. (Compiling again while they run is fine; the two do not disturb each other.)

```bash
cd workers
mvn compile exec:java "-Dexec.args=--publish-message missing-information-supplied --correlation-key REF-DEMO-0002"
```

The command reports that the message was published, and prints its own warning:

```
Published "missing-information-supplied" as message <message key> correlated by "REF-DEMO-0002".
  The engine recorded the publication. That is not proof that a process instance received it: ...
```

Now show the instance again in **Operate**: it has not moved. Same message name, same engine, a
different patient - and nothing happened.

**This is the step that makes the rest mean anything.** If the demonstration published only the right
key, it would look identical whether the correlation key did anything or nothing at all. The engine
accepts a publication no subscription is waiting on and correlates it with nothing, so the observable
proof is always the instance moving, never the publisher's exit code.

### Step 5 - the reply for this referral

In that same second terminal - and from the `workers` directory, because that is where the POM is:

```bash
cd workers
mvn compile exec:java "-Dexec.args=--publish-message missing-information-supplied --correlation-key REF-DEMO-0001"
```

The instance leaves `Missing information received` and `Check the supplied documents` appears in
Tasklist. Same command as step 4, one value different, and this time a referral moved.

Publish it while you are talking, not after: a publication that is not correlated within two minutes
expires. The instance's wait does not expire - it will still be there - so if you are slow, publish
again.

### Step 6 - check the supplied documents, and the end

Assign the new task to yourself and work it - this is the **same** documentation checklist as step 1,
applied to what has arrived, which is the point of it:

| Field | Value |
|---|---|
| `Referral ID` | `REF-DEMO-0001` (still required) |
| `Supporting Information Received` | `investigation results, diagnostic reports` |
| `Referral Documents Complete` | **ticked** |
| `Patient Identification Confirmed` | **ticked** (required) |
| `Checked By` | your name |

Complete it. The instance takes the `complete` flow and reaches
`Referral ready for clinical review`, and the instance completes. Show that in Operate.

### The other two endings

Both are worth a minute each, and both were driven in the rehearsal.

**Still incomplete - the chase, not an abandonment.** Run the same path again, and at the last step
leave `Referral Documents Complete` **unticked** and put `diagnostic reports` in `Missing Items`. The
gateway takes its default flow and `Record the missing information` appears again: a second request
goes out for the same referral. The model repeats the request rather than dropping the referral, which
is `BR-01`'s answer taken literally.

**A request the worker refuses.** The form makes `Items Requested` required, so a Tasklist user cannot
submit a request that names nothing - which is deliberate, and means this path has to be driven through
the API. Complete the first task with an empty list instead:

```bash
curl -sS -X POST "http://localhost:8080/v2/user-tasks/<taskKey>/completion" \
     -H "Content-Type: application/json" \
     -d '{"variables":{"referralId":"REF-DEMO-0003","requestedFrom":"St Mary GP Surgery","requestedItems":[]}}'
```

The worker refuses it, the boundary event catches `INVALID_VARIABLE`, and `Record the missing
information` is waiting again - the request is corrected by the person who owns it rather than the
instance stalling on an incident. The workers terminal shows the refusal as one log line ending
`"worker":"referral-document-request" ... "outcome":"businessError","errorCode":"INVALID_VARIABLE"`,
and **no message is published**, which is the part worth saying: a request that cannot be answered is
not sent.

## Reading the state back from the engine

Operate is enough for the demonstration. If someone asks for the machine-readable version, the engine
answers these read-only, with the instance key as a **string**:

```bash
curl -sS -X POST http://localhost:8080/v2/element-instances/search -H "Content-Type: application/json" \
     -d '{"filter":{"processInstanceKey":"<instance key>"}}'
curl -sS -X POST http://localhost:8080/v2/variables/search -H "Content-Type: application/json" \
     -d '{"filter":{"processInstanceKey":"<instance key>"}}'
```

The element query is how the path is read back - the completed elements in order are the evidence that
the instance went where the model says, and an element with `state: ACTIVE` on
`N_MS_DocumentsSupplied` is what "waiting" looks like from outside.

One practical note from the rehearsal: these queries reported a step a moment after the click that
caused it. If a query seems to show the previous state, read it again rather than concluding the
process is stuck.

## The forms this model binds

Two, and both are also bound in `core-1` - the same forms are the variable contract for every task
that binds them. `../../forms/by-core.md` lists them by model; `../../forms/README.md` is the reference
for the set.

| Form file | Bound to | Fields |
|---|---|---|
| `forms/missing-information-request.form` | `N_MS_RecordMissingItems` | `referralId`, `requestedItems`, `requestedFrom`, `requestMethod`, `requestDate`, `requestedBy`, `requestNotes` |
| `forms/referral-check.form` | `N_MS_CheckSuppliedDocuments` | `referralId`, `patientId`, `patientName`, `referringOrganisation`, `supportingDocuments`, `documentsComplete`, `missingItems`, `patientIdentificationConfirmed`, `checkedBy`, `checkNotes` |

Three fields carry the demonstration:

- **`referralId`** in the request form is the correlation key's source. It is not decoration: the
  subscription at `N_MS_DocumentsSupplied` is keyed on it, and `verify_hprtas_bpmn_bindings.py` fails
  if the model ever stops writing it.
- **`requestedItems`** is what the worker sends. Required, and a list, so an empty one is refused
  rather than sent as a request for nothing.
- **`documentsComplete`** decides the gateway. Ticked ends the exchange; unticked asks again.

## The code behind each step

| File | What it is | What to look at |
|---|---|---|
| `models/operational/core-5-missing-information-message-exchange.bpmn` | The model | The `<bpmn:message>` with `zeebe:subscription correlationKey="=referralId"`, the catch event, and the boundary error event |
| `forms/missing-information-request.form`, `forms/referral-check.form` | The two forms | `id` is the `formId` the model binds; the `key` of each field is the variable name |
| `workers/src/main/java/uk/ac/uwe/hprtas/workers/workers/ReferralDocumentRequest.java` | **The publishing worker** | `MESSAGE_NAME`, `INPUT_VARIABLES`, and `messageIdFor` - the id that makes a retry of one job one publication |
| `workers/src/main/java/uk/ac/uwe/hprtas/workers/Main.java` | Registers the workers; carries `publishMessage` | The `--publish-message` / `--correlation-key` / `--variables` handling, and why a start event sends no key |
| `workers/src/main/java/uk/ac/uwe/hprtas/workers/ClientMessagePublisher.java` | Publishes through the gateway | `.correlationKey(...)` and `.messageId(...)`, set together |
| `workers/src/main/java/uk/ac/uwe/hprtas/workers/MessagePublisher.java` | The contract, and `TIME_TO_LIVE` | Two minutes, and the comment saying why not the guide's ten |
| `workers/src/main/java/uk/ac/uwe/hprtas/workers/Validate.java`, `FieldSpec.java` | Input checking | `FieldSpec.list().required()` - why an empty list is a problem |
| `workers/src/main/java/uk/ac/uwe/hprtas/workers/ErrorCode.java` | Business error codes | `INVALID_VARIABLE`, the code the boundary event catches |
| `workers/config/workers.default.json` | Worker registration | The `referral-document-request` entry: job type, concurrency, timeout |
| `workers/src/test/java/uk/ac/uwe/hprtas/workers/WorkersTest.java` | Unit tests, no engine | The four tests for this worker: the message name and key; a retry publishing once; an empty request refused *and nothing published*; a failed publication failing the job rather than returning a business error |
| `workers/src/test/java/uk/ac/uwe/hprtas/workers/OperationalModelsTest.java` | Engine tests | Scenario 9 - **including the reply for another referral, which is step 4** |
| `tools/verify_hprtas_bpmn_bindings.py` | The contract checker | The rule that a subscription's correlation variable is written by a form or a worker in the same model |
| `../../tests/evidence/core-5-message-exchange-and-layout-tidy_2c3c2eb_2026-09-28.txt` | The recorded run | Scenario 9, and the negative half as it was reported |
| `models/exports/core-5-missing-information-message-exchange.pdf` and `.png` | The exported diagram | For slides. It reads back at 92% of its words by OCR, the highest of the seven diagrams |

Everything above except the two `--publish-message` commands is what runs in production: the model is
deployed, the forms are deployed, the worker is registered against its job type, and the engine
correlates. The operator's command is the only stand-in.

## What this does not demonstrate

Say these out loud. They are the difference between a demonstration and a claim.

1. **The reply is published by the operator, not by a worker.** `missing-information-supplied` belongs
   to the referring organisation, and no process for that participant exists here. What the
   demonstration shows is that the *wait and the correlation* are real - not that another organisation
   can be relied on to send anything.
2. **Nobody logs in as themselves.** Tasklist is `demo` / `demo`, and the candidate group on the two
   tasks is not enforced by anything. The lane structure is a structural separation of duties, not an
   access control (`DEF-07`).
3. **No audit trail**, which is a Must requirement (`DEF-07`).
4. **No form has been submitted by a signed-in user in any recorded run.** The evidence for this model
   completes the tasks through the API with the variables the form supplies (`DEF-08`). If you fill
   the forms in live, say that it is the first time it has been done and that it is not in the
   evidence.
5. **The two-minute time to live is not the model's.** It is the publisher's choice in
   `MessagePublisher`, and it is a limitation as much as a safeguard: a reply published while the
   instance is not yet waiting is gone two minutes later.
6. **The five `core-N` models apart from this one are not shown here.** `demonstration-script.md` has
   the rest, including the two models `core-4` is started by as messages - which is the other half of
   messaging, and the opposite case: a start event holds no subscription and is sent with no
   correlation key at all.

## If something goes wrong

| Symptom | Cause | Recovery |
|---|---|---|
| The task shows no form | The form was not deployed, or the task is unassigned | Deploy `forms/*.form` as well; assign the task to yourself. A form is resolved when the task is **created**, so one deployed after the task exists does not reach it - the task has to be created again |
| The instance sits on `Request the missing information`, no error | The workers are not running, or `request-referral-documents` is not registered | Check the workers terminal. The job stays queued |
| The instance sits on `Missing information received` and the reply seems ignored | The publication carried a different reference, or it expired | Read the key you published with against the `Referral ID` in the form, and publish again |
| An incident on the catch event instead of a wait | The correlation key could not be read - in practice, no `referralId` on the instance | Check the form's `Referral ID`; `verify_hprtas_bpmn_bindings.py` is the static version of this check |
| The Processes page offers a process of nearly the right name | The engine keeps every definition ever deployed, and earlier editions are still there | Start the one whose name begins **`Core 5`** |
| A Tasklist or Operate view looks a step behind | The read queries report shortly after the write | Read again; do not conclude the instance is stuck |
| `Unknown lifecycle phase ".args=…"` | The shell split the `-Dexec.args=…` argument - PowerShell does this when only the value is quoted | Quote the whole argument: `mvn compile exec:java "-Dexec.args=--publish-message …"`, or publish over REST |
| `There is no POM in this directory` | Maven was run from the repository root; the POM is in `workers/` | `cd workers` first. Running from the root with `-f workers\pom.xml` needs `HPRTAS_WORKERS_DIR` set to `workers` as well, because the configuration is read from the **working directory** rather than the POM's |
| One publication released two instances | Both were started with the same referral reference | Intended: the correlation key is not a unique instance identifier. Give each rehearsal its own reference |

## Resetting between rehearsals

Cancel the rehearsal instances and start again - `Cancel` in Operate, or:

```bash
curl -sS -X POST http://localhost:8080/v2/process-instances/<instance key>/cancellation
```

Give each rehearsal its own referral reference. Two instances waiting on the same one are both
released by a single publication, which is correct behaviour and confusing to watch.

Restarting the workers is not needed for this model: it uses no simulated service, so there is no
ledger to clear. Restart the engine only as a last resort, and with
`camunda-runtime\stop-camunda.bat` if you do - `c8run stop` is unreliable, and the check that it
really stopped is that `http://localhost:8080/v2/topology` stops answering.

## The thirty-second version, if asked to summarise

> A referral arrives without its supporting documentation. The secretaries record what is missing, and
> a worker publishes the request as a BPMN message to the referring organisation. The process then
> stops - genuinely stops, on a message subscription keyed on the referral reference - until the reply
> arrives. We show a reply for a different referral changing nothing, and then the right one releasing
> it. The reply in the demonstration is published by hand, because the referring organisation is a
> participant outside the system we built; the wait and the correlation are the engine's.
