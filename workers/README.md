# External Workers

The workers that carry out the automated activities in the operational models. They are written in
Java, and they are what sits behind every service task in `../models/operational/`: each worker takes
a job of its type off the gateway, does the work, and returns the result so the process carries on.

Some of that work is talking to systems outside the hospital — the scheduling service, the treatment
and laboratory service, the payment service provider and the correspondence service. Those systems
are not available here, so the workers call simulated versions of them, and what each simulation does
and does not model is written down below.

## Why Java

The project is built with Maven and runs on Java 21, which is the same runtime the engine uses. The
client is `io.camunda:camunda-client-java`, pinned to the engine's own release, so there is one
Camunda version in the repository rather than two that can drift apart.

Node.js with the Camunda 8 SDK was the other alternative. It keeps each worker short too, and the
workers are small either way, so that is not what decided it: it would put a second runtime and a
second Camunda client in the repository, each free to drift from the engine's release, and the plain
Java client keeps a worker just as short as the SDK does.

Spring Boot was the third option and was not taken either. The configuration here is layered over
three levels and resolved by the project's own `Config`, and handing that to a framework's
auto-configuration would mean two things deciding what the connection settings are. The client is
therefore told not to read the environment itself — everything it is given has already been resolved
in one place.

## How work is obtained

`mvn compile exec:java` connects to the Zeebe gRPC gateway and registers one worker per service task. The
registration is driven by `config/workers.default.json` rather than by code, so changing a job type
or a concurrency is a configuration change: each worker has a `taskType`, a `maxJobsToActivate` and a
`timeoutMs`, and the job type must match the `<zeebe:taskDefinition type="...">` of the service task
it serves.

Configuration is layered over three levels, each overriding the one before it:

1. `config/workers.default.json` — committed, the agreed defaults.
2. `config/workers.local.json` — optional, never committed, for one machine.
3. Environment variables — from `.env` or the shell.

A variable the environment already defines wins over the same variable in `.env`, so a committed file
can never override a machine's own setting. The connection settings keep the names they had when the
project used the Node SDK — `ZEEBE_GRPC_ADDRESS` carries its protocol (`grpc://localhost:26500`) and
`CAMUNDA_AUTH_STRATEGY` is `NONE`, because Camunda 8 Run is installed unsecured for API access, so no
credentials are sent and no token is fetched. The client spells a plaintext connection `http://` and a
secured one `https://`, so the scheme is translated on the way in and the configuration keeps the
vocabulary the models and the evidence already use.

The workers directory is the directory they were started from, or `HPRTAS_WORKERS_DIR` when one is
set. A missing configuration file is reported against the directory that was searched.

Every job is logged as one JSON line carrying the worker, the job key, the process instance, the
element id, the duration and the outcome, which is what makes the log usable as evidence. Setting
`HPRTAS_LOG_FILE` also writes it to a file.

```bash
cp .env.example .env
mvn test                                          # unit tests, no engine required
mvn compile exec:java -Dexec.args="--check"               # validate the configuration and the wiring
mvn compile exec:java                                     # run the workers
mvn compile exec:java -Dexec.args="--publish-message <name>"   # send a message a model waits for
mvn compile exec:java -Dexec.args="--publish-message <name> --correlation-key <value>"   # the same, addressed to a subscription
mvn test -Pengine                                 # the fixture and the models, engine running
mvn test -Pengine -Dtest=SmokeTest                # the fixture only
mvn test -Pengine -Dtest=OperationalModelsTest    # the five operational models only
mvn package                                       # a self-contained jar
```

The engine tests are tagged and left out of the default build, because they need Camunda 8 Run to be
up and they change what is deployed in it. They skip themselves, rather than fail, when the engine is
not answering — a skipped run is not a pass, and the skip says which address it tried.

## Starting a process by message

Two of the paths in `core-4` are begun by a message rather than from the Processes page: a
cancellation arriving and a patient enquiry arriving. Neither is requested by the hospital, so
neither can be an ordinary start event, and a process may hold only one of those — which is why they
are message start events. `--publish-message` sends the message they wait for:

```bash
mvn compile exec:java -Dexec.args="--publish-message patient-cancellation-or-non-attendance"
mvn compile exec:java -Dexec.args="--publish-message patient-enquiry --variables {\"enquiryType\":\"clinical\"}"
```

The name is the `name` of the `bpmn:message` the model declares, and the variables are its payload.
No correlation key is sent, and none can be: a message start event holds no subscription, so there is
nothing to correlate against. A correlation key belongs to the intermediate catch events a
long-running instance waits on, and that is the other half of messaging in this project: `core-4`
starts processes by message, and `core-5` is the one model that waits for one, at
`N_MS_DocumentsSupplied`, correlated by the referral reference. The two are set side by side under
*Publishing a message* below.

The engine recording the publication is not proof that an instance received it. A name no deployed
model declares, or a model that is not deployed, is accepted and correlated with nothing, and the
command says what it published rather than that it worked — so confirm the instance in Operate. The
end-to-end test does the same thing and then drives the instance that appeared, which is what
scenarios 5 and 6 of `OperationalModelsTest` are.

Before any worker is registered the gateway is asked for its topology. A worker that cannot reach the
gateway would otherwise start, look alive, and quietly take no work at all, which is the stalled job
described under *Failure handling* below.

## Publishing a message

`referral-document-request` is the one worker that sends rather than calls. What it sends is the
request `core-5` is about, and the model then waits for the answer - so the two halves of messaging
in this project are not the same kind of publication, and the client keeps them apart rather than
treating an empty key as no key at all:

| | message start event (`core-4`) | intermediate catch event (`core-5`) |
|---|---|---|
| Who sends it | the participant that starts the process | the worker `referral-document-request`, or `--publish-message` |
| Is it correlated | no: a start event holds no subscription | yes: the publication is matched to a subscription by a key |
| The key | none is sent (`.withoutCorrelationKey()`) | the referral, `referralId` |
| The variable | - | the instance has to hold `referralId` **before** it reaches the waiting step |

The guide's configuration check, filled in for the exchange in `core-5`:

| Setting | Value |
|---|---|
| Sending step | `N_MS_RequestDocuments` in `core-5-missing-information-message-exchange` |
| Task definition type | `request-referral-documents` |
| Worker | `referral-document-request`, registered against that job type |
| Published message name | `missing-information-requested` |
| Receiving message name | `missing-information-supplied` |
| Subscription correlation key | `=referralId`, a FEEL expression reading a process variable |
| Correlation key variable | `referralId`, written by the `missing-information-request` form before the waiting step is reached |
| Published correlation key | the `referralId` of the referral the request is about |
| Message id | `missing-information-requested:<referralId>:<jobKey>`, which is what makes a retry of one job one publication |
| Time to live | two minutes (`MessagePublisher.TIME_TO_LIVE`) |

**The reply is not the hospital's to send.** `missing-information-supplied` comes from the referring
organisation, so no worker here publishes it on the hospital's behalf: the demonstration sends it
with `--publish-message` (or from the engine test), which is standing in for the participant that
owns that message. Publishing it and seeing the engine accept it proves nothing on its own - a
publication correlated by a key no subscription is waiting on is accepted and correlated with
nothing - which is why scenario 9 of `OperationalModelsTest` publishes one addressed to a *different*
referral first and asserts the instance has not moved.

**Two minutes, not the guide's ten.** A message outlives its moment otherwise: one still held when a
later instance reaches a waiting step with the same key would move a process it was never meant for.
Two minutes is long enough for the receiving instance to get there and short enough that a stale
publication expires.

**A publication that cannot be made fails the job.** It is not a business rule being broken, so it
does not travel a modelled error path: the handler wrapper fails the job with one fewer retry and
the broker tries again. The message id is what keeps that retry from sending a second copy, which is
the duplicate the guide's step 6 warns about.

## The workers

**`referral-validation`** (job type `validate-referral`) checks that a referral has the supporting
information it needs. It reads `documentsComplete`, `missingItems` and `referringOrganisation`, and
writes `validationResult`, `requestedItems`, `missingInformationRequestedFrom` and
`missingInformationRequestedDate`. It calls no external service, and it deliberately records no
clinical judgement: whether a referral should be accepted is not its business.

**`referral-document-request`** (job type `request-referral-documents`) is the one worker that does
not call anything: it **publishes a message**. It reads `referralId`, `requestedFrom` and
`requestedItems`, publishes `missing-information-requested` correlated by the referral, and writes
`informationRequestStatus` and `informationRequestMessageKey`. It serves `N_MS_RequestDocuments` in
`core-5`, which is the sending half of the exchange that model waits on - see *Publishing a message*
below for what the correlation key does and why the publication carries an id.

**`appointment-availability`** (job type `check-appointment-availability`) asks the simulated
scheduling service for a new patient appointment. It reads `speciality`, `priority` and
`requestedWindow`, and writes `slotAvailable`, `appointmentDate`, `alternativeDate`,
`withinRequestedWindow`, `appointmentWithinTwoWeeks` and `schedulingServiceOutcome`. It serves the
new patient appointment in `core-1` and the follow-up appointment in `core-4`.

**`treatment-availability`** (job type `check-treatment-availability`) books the treatment
appointment through the simulated treatment, laboratory and imaging service, after the funding and
payment gate. It reads `proposedTreatment`, `treatmentStartDate`, `specialResources` and
`clinicalAuthorised`, and writes `externalServiceAvailable`, `treatmentSlotAvailable`,
`appointmentReference`, `treatmentAppointmentDate`, `bookingStatus`, `treatmentRetryCount` and
`duplicateAttempt`.

**`payment-processing`** (job type `process-payment`) sends the payment request to the simulated
Payment Service Provider. It reads `chargeAmount`, `fundingRoute` and `paymentReference`, and writes
`paymentStatus`, `transactionReference`, `paymentDate`, `paidAmount`, `confirmationReceived`,
`requiresInvestigation` and `duplicateAttempt`.

**`correspondence-dispatch`** (job type `send-correspondence`) sends appointment letters and clinic
letters through the simulated correspondence service. It reads `recipients`, `documentType` and an
optional `channelPreference`, and writes `dispatchDate`, `dispatchChannel`, `dispatchReference`,
`documentType` and `recipients`. It serves four service tasks across the models.

**`refund-processing`** (job type `process-refund`) sends an approved refund to the simulated
Payment Service Provider. It reads `paymentReference`, `refundDecision`, `refundAmount` for a
partial refund and `refundReason`, and writes `refundStatus`, `refundReference`, `refundDate`,
`refundedAmount` and `duplicateRefundAttempt`.

A refund is not a payment with the sign reversed, which is why it has its own job type rather than
reusing `process-payment`. The money moves out, so the provider has to confirm that the payment
reference was settled at all — refunding one that never was would create money. A refund may be full
or partial, so the amount is checked against what was actually taken rather than against a charge
the hospital is asking for. And a refund against a payment that has already been refunded returns
the first refund rather than paying out twice.

Two variables are worth calling out because the models depend on them. `appointmentWithinTwoWeeks`
is **recomputed** by the worker from the slot actually found, not taken from the form, because the
two-week telephone rule applies to the real appointment date rather than to the one that was asked
for. `paymentStatus` is `not_required` for the funding routes where the patient pays nothing, and in
that case the provider is not called at all.

## Where the workers run in the models

The job type in the configuration is the `<zeebe:taskDefinition type="...">` of the matching
service task, so this is the binding between the workers and the five operational processes.

In `core-1`, `N_MS_ValidateReferral` runs `referral-validation`, `N_OB_CheckAvailability` runs
`appointment-availability` and `N_OB_SendNotification` runs `correspondence-dispatch`.

In `core-2`, `N_TB_CheckAvailability` runs `treatment-availability`, `N_TB_NotifyPatient` runs
`correspondence-dispatch` and `N_F_ProcessPayment` runs `payment-processing`.

In `core-3`, `N_MS_SendLetter` runs `correspondence-dispatch`.

In `core-4`, `N_OB_CheckAvailability` runs `appointment-availability`, `N_OB_SendFollowUpLetter`
runs `correspondence-dispatch` and `N_F_ProcessRefund` runs `refund-processing`.

In `core-5`, `N_MS_RequestDocuments` runs `referral-document-request`, the worker that publishes
rather than calls.

Every service task in the operational models is covered. The strategic and socio-technical models
contain no service tasks, so nothing is bound to them.

`send-correspondence` serves four service tasks and `check-appointment-availability` two, so the
job type alone does not say which activity a job came from. The worker log carries the `elementId`,
which is what tells them apart in the evidence.

## Error codes

A business rule that stops the work is thrown as a BPMN error, so the process follows its modelled
error path instead of stalling on an incident. The model needs one error catch event per code it
wants to distinguish, and every service task that can raise one has it.

- `INVALID_VARIABLE` — a required variable is missing, is of the wrong type, or the values
  contradict each other. Every worker can raise it.
- `MISSING_INFORMATION_NOT_SPECIFIED` — raised by `referral-validation` when a referral is marked
  incomplete but names no missing items, so nothing can be requested.
- `PROHIBITED_FINANCIAL_DATA` — raised by `payment-processing` and `refund-processing` when card or
  security details are supplied. The provider is not called at all.
- `UNAUTHORISED_BOOKING_REQUEST` — raised by `treatment-availability` when a Treatment Booking
  Request has no clinical authorisation.

Each error path returns the token to the task that owns the request, so the request is corrected and
the step retried rather than the process being abandoned. In `core-1` the referral exceptions return
to `N_MS_ReviewException` and `N_MS_CorrectReferralInput`, and the booking and dispatch failures to
`N_OB_CorrectRequest` and `N_OB_RecordDispatchFailure`. In `core-2` the unauthorised booking returns
to `N_TB_ReturnRequest`, the unusable booking input to `N_TB_CorrectBookingInput`, the notification
failure to `N_TB_RecordNotificationFailure`, and both payment failures to
`N_F_CorrectPaymentRequest`. In `core-3` a dispatch failure returns to
`N_MS_HandleDispatchFailure`. In `core-4` the follow-up input error returns to `N_OB_CorrectRequest`,
the dispatch failure to `N_OB_RecordDispatchFailure`, and the refund error to
`N_F_CorrectRefundRequest`. In `core-5` an unusable request returns to `N_MS_RecordMissingItems`,
which is the task that owns it: the referral reference, the organisation to ask and the items are
all recorded there, so correcting the request needs no second task.

`core-3` also carries a timer boundary event of seven days on `N_C_ApproveLetter`, which is what
starts the delayed-letter path without a worker being involved.

**A gateway's fallback has to be declared.** Camunda does not treat a sequence flow without a
condition as a fallback: an exclusive gateway only falls back to the flow named in its `default`
attribute, and raises "Expected at least one condition to evaluate to true, or to have a default
flow" when no condition matches. Every exclusive gateway in the five operational models declares
one, and the flow it names carries no condition of its own.

**A variable written on an error does not reach the process.** The variables attached to a BPMN
error are written to the scope of the activity that raised it, and once a boundary error event
catches the error and the token moves on, they are no longer visible in the process scope. A model
therefore has to distinguish the reason for a failure by **which error catch event was taken**, not
by reading a variable the failed worker wrote. Only the variables returned by a completed job reach
the process.

## The simulated external services

**The scheduling service** returns `available` for a slot inside the requested window,
`outside_window` for a slot later than the period the clinician asked for, or `none`. Lead times are
fixed, and there is no clinic capacity, room or clinician availability behind it and no real diary.

**The treatment, laboratory and imaging service** returns `available` or `unavailable`. An
unavailable attempt is recorded and leaves the booking pending. It has no capacity data and no
clinical constraints, and the appointment it returns is generated rather than scheduled.

**The Payment Service Provider** handles both directions. A payment returns `completed`, `declined`
or `success_no_confirmation`, with a transaction reference, a date and an amount, and a repeat
request for the same payment reference returns the original transaction as a duplicate. A refund is
recorded against a settled payment and returns `refunded`, or `duplicate`, `no_settled_payment`,
`amount_too_high` or `invalid_amount`. There is no real card processing, clearing, settlement, 3-D
Secure or fraud checking, and refunds are recorded rather than executed.

**The correspondence service** records a dispatch with its channel, date and a reference. There is
no printing, no postage and no delivery confirmation, and the channel preference is recorded and
echoed but not honoured end to end.

Three of the four keep a ledger keyed by the booking or payment reference, and that is what makes
"no duplicate appointment", "no second charge" and "no second refund" testable: a second request for
the same key returns the first result instead of creating another. The ledgers are in memory, so
they are per worker process and are cleared when it restarts.

The ledgers are also the one place where running on a JVM rather than on Node changes the code
rather than only its shape. Job workers are served by more than one thread, so the check that a
reference is unknown and the record that it is now known are made together under the ledger's lock.
Without that, two jobs for the same booking key could both find nothing and both book, which is
exactly the duplicate the ledger is there to prevent.

## Failure handling

**Invalid or missing input** is caught by the worker validating its variables before doing any work,
and becomes `INVALID_VARIABLE`. Where the model has a catch event the process follows its error
path; where it has none the error becomes an incident, which is still a recorded and visible
outcome. Either way there is no rebooking and no recharging. A blank required reference, a
contradictory pair such as `documentsComplete` being true with missing items listed, and a
non-numeric amount are all treated this way.

**An external service being unavailable** is reported through the treatment service returning
`unavailable`. The booking stays `pending`, `appointmentReference` is `null`,
`treatmentRetryCount` is incremented, and a later attempt does not create a second appointment.

**A payment taken without a returned confirmation** sets `requiresInvestigation` and stops any
further payment request being issued for that appointment.

**A declined payment** leaves `paidAmount` null, and because nothing was taken a later attempt with
the same reference is allowed.

**Card or security details in the variables** are caught by scanning the variable names. The worker
raises `PROHIBITED_FINANCIAL_DATA` without calling the provider, and reports the field names it
found so the form that supplied them can be corrected.

**A refund that cannot be paid out** — no settled payment for the reference, a refund larger than
what was paid, or a partial refund that states no amount — raises `INVALID_VARIABLE` and no money
moves. A second refund for a payment already refunded returns the first one marked as a duplicate.

**An unexpected exception in a handler** is caught by the handler wrapper and turned into a job
failure carrying the message, so the broker retries the job rather than leaving it stalled. A
handler that returns nothing at all is failed the same way.

The failure consumes one of the job's retries rather than setting them to zero. The client has no way
to leave the retry count unset, and an unset count reaches the broker as zero, which raises an
incident immediately — so the wrapper passes one fewer than the job currently has, which spends the
model's own retry count and leaves the incident for when that count is exhausted.

**No worker registered for a job type** leaves the job in the queue and the process waits where it
is: the element instance stays active at the service task and the token does not move on. The job
deadline still applies, and once the model's retries are exhausted the job raises an incident in
Operate.

## Testing

`mvn test` runs the worker unit tests with no engine. They call the same handlers the workers call,
with the simulated services running for real, so the behaviour is exercised rather than mocked, and
each test names the test case or business rule it belongs to.

`mvn test -Pengine -Dtest=SmokeTest` deploys a single linear fixture, `src/test/resources/worker-smoke-test.bpmn`,
which calls each worker once, starts the real workers and runs two process instances: the normal
path, and a booking without clinical authorisation. It asserts on the path each instance took, read
back from the Orchestration Cluster API, which proves that a worker registers against the gateway,
receives a job of its type, returns its result and lets the process continue, and that a business
error is caught by the model's boundary event.

`mvn test -Pengine -Dtest=OperationalModelsTest` drives the delivered models rather than a fixture.
It deploys the five operational models and the forms, starts the workers and runs nine scenarios:
the normal referral path in `core-1` to the appointment being arranged; authorisation, funding,
payment and the between-cycle review in `core-2`; the clinic letter in `core-3` to its distribution;
a follow-up requested and booked in `core-4`; a paid appointment cancelled and refunded in `core-4`;
an urgent clinical enquiry routed to the clinical team in `core-4`; the two decisions that end
`core-1` before an appointment; the urgent referral with no slot escalated out of the booking; and
the message exchange in `core-5`, where the request is published by the worker and the reply is
correlated by the referral the instance is waiting on.

The refund scenario is run after the payment scenario on purpose: the refund it records is made
against the payment reference the earlier scenario settled with the provider, which is the only way
to show the refund worker working against a transaction that really exists rather than one the test
assumed. The scenarios therefore declare the order they run in rather than leaving it to the test
runner.

Scenario 9 publishes one reply addressed to a different referral before the real one, and asserts the
instance has not moved: a publication whose key no subscription is waiting on is accepted by the
engine and correlated with nothing, so a scenario that only published the right key would pass
whether or not the correlation key did anything.

Both engine runs are recorded in `../tests/evidence/`, named after the commit they were run at.
Forms and role-based access are out of scope here and are tested separately; see
`../tests/README.md`.
