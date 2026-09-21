# External Workers

Source code, dependencies and configuration for the workers that carry out the automated
activities in the operational model.

## Requirements

- Show how work is obtained (job type and worker registration)
- Show how input variables are used and how results are returned so execution continues
- Address invalid inputs and service failures
- Make any simulated external service explicit and explain its limitations

## Language decision

| Option | Start command | Notes |
|---|---|---|
| Java | `mvn spring-boot:run` | Requires Java 21 or 22; more ceremony for a small number of workers |
| Node.js | `npm install && npm start` | Node.js 22 is available; the Camunda 8 Node SDK keeps each worker short and the group can read them all |

**Decision:** Node.js · **Reason:** the workers are small, and a shorter implementation is easier
for the second owner to review and take over. The only runtime dependency is `@camunda8/sdk`, so
`npm install` is quick on every member's machine.

## How work is obtained

`npm start` connects to the Zeebe gRPC gateway and registers one worker per service task. The
registration is driven by `config/workers.default.json`, not by code, so a job type or a
concurrency change is a configuration change.

| Setting | Where it comes from | Value used |
|---|---|---|
| Gateway address | `ZEEBE_GRPC_ADDRESS` in `.env`, or the same key in the configuration file | `grpc://localhost:26500` |
| Authentication | `CAMUNDA_AUTH_STRATEGY` | `NONE` — Camunda 8 Run is installed unsecured, so no token is fetched |
| Job type | `workers.<name>.taskType` | The `<zeebe:taskDefinition type="...">` of the matching service task in the model |
| Concurrency | `workers.<name>.maxJobsToActivate` | 4–8, per worker |
| Job timeout | `workers.<name>.timeoutMs` | 15000 ms, 20000 ms for payment |

Every job is logged as one JSON line carrying the worker, the job key, the process instance, the
element id, the duration and the outcome, so the log can be attached as evidence. Set
`HPRTAS_LOG_FILE` to also write it to a file.

```
npm install        # dependencies
cp .env.example .env
npm run check      # validate the configuration and the wiring, without an engine
npm start          # run the workers
npm test           # unit tests, no engine required
npm run test:smoke # deploy the fixture and run both scenarios against the engine
```

## Workers

| Worker | Job type | Triggered activity | Input variables | Output variables | Simulated? |
|---|---|---|---|---|---|
| `referral-validation` | `validate-referral` | Document completeness check after referral receipt | `documentsComplete`, `missingItems`, `referringOrganisation` | `validationResult`, `requestedItems`, `missingInformationRequestedFrom`, `missingInformationRequestedDate` | No |
| `appointment-availability` | `check-appointment-availability` | Availability request to the scheduling service | `speciality`, `priority`, `requestedWindow`, `schedulingOutcome` (optional, scenario data) | `slotAvailable`, `appointmentDate`, `alternativeDate`, `withinRequestedWindow`, `appointmentWithinTwoWeeks`, `schedulingServiceOutcome` | Yes - external scheduling service |
| `treatment-availability` | `check-treatment-availability` | Treatment appointment booking, after the funding and payment gate | `proposedTreatment`, `treatmentStartDate`, `specialResources`, `clinicalAuthorised`, `treatmentOutcome` (optional, scenario data) | `externalServiceAvailable`, `treatmentSlotAvailable`, `appointmentReference`, `treatmentAppointmentDate`, `bookingStatus`, `treatmentRetryCount`, `duplicateAttempt` | Yes - external treatment, laboratory and imaging service |
| `payment-processing` | `process-payment` | Payment request to the payment service provider | `chargeAmount`, `fundingRoute`, `paymentReference`, `paymentOutcome` (optional, scenario data) | `paymentStatus`, `transactionReference`, `paymentDate`, `paidAmount`, `confirmationReceived`, `requiresInvestigation`, `duplicateAttempt` | Yes - external Payment Service Provider |
| `correspondence-dispatch` | `send-correspondence` | Appointment and clinic letter distribution | `recipients`, `documentType`, `channelPreference` (optional) | `dispatchDate`, `dispatchChannel`, `dispatchReference`, `documentType`, `recipients` | Yes - external correspondence service |

Two variables are worth calling out because the model depends on them:

- `appointmentWithinTwoWeeks` is **recomputed** by the worker from the slot actually found, not
  taken from the form. It is what the two-week telephone contact rule (BR-03) reads, and the rule
  applies to the real appointment date rather than to the requested one.
- `paymentStatus` is `not_required` when the funding route is `hospital`, `insurer` or `exempt`.
  The provider is not called at all in that case, which is how AS-08 is implemented.

## Where each worker runs in the operational models

The job type in the configuration is the `<zeebe:taskDefinition type="...">` of the matching
service task, so this table is the binding between the workers and the three operational models.
Every service task in those models is covered; the strategic model is a non-executable view and
contains no service tasks.

| Model | Service task | Job type | Worker |
|---|---|---|---|
| `referral-to-appointment.bpmn` | `ServiceTask_ValidateReferral` - Validate referral | `validate-referral` | `referral-validation` |
| `referral-to-appointment.bpmn` | `ServiceTask_CheckAppointmentAvailability` - Check availability | `check-appointment-availability` | `appointment-availability` |
| `referral-to-appointment.bpmn` | `ServiceTask_SendAppointmentNotification` - Send notification | `send-correspondence` | `correspondence-dispatch` |
| `treatment-authorisation-and-booking.bpmn` | `ServiceTask_CheckTreatmentAvailability` - Check treatment slots | `check-treatment-availability` | `treatment-availability` |
| `treatment-authorisation-and-booking.bpmn` | `ServiceTask_ProcessPayment` - Process payment | `process-payment` | `payment-processing` |
| `clinic-letter-and-pathway-monitoring.bpmn` | `ServiceTask_DispatchClinicLetter` - Send clinic letter | `send-correspondence` | `correspondence-dispatch` |
| `clinic-letter-and-pathway-monitoring.bpmn` | `ServiceTask_CheckFollowUpAvailability` - Check availability | `check-appointment-availability` | `appointment-availability` |

Two job types are used by more than one activity, so `send-correspondence` and
`check-appointment-availability` each serve two service tasks. The worker log carries the
`elementId`, which is what tells the two apart in the evidence.

## Error codes

A business error is thrown as a BPMN error, so the process follows its modelled error path instead
of stalling on an incident. The model needs one error catch event per code it wants to distinguish.
The models now do that: `Boundary_Error_Validation` catches
`MISSING_INFORMATION_NOT_SPECIFIED` and `Boundary_Error_BookingRequest` catches `INVALID_VARIABLE`
in `referral-to-appointment.bpmn`; `treatment-authorisation-and-booking.bpmn` catches
`UNAUTHORISED_BOOKING_REQUEST` and `INVALID_VARIABLE` on the treatment booking task and
`PROHIBITED_FINANCIAL_DATA` and `INVALID_VARIABLE` on the payment task.

**Every service task that can raise a business error now catches it.** Each error path returns the
token to the task that owns the request, so the request can be corrected and the step retried - that
is the pattern the models already used, and it is what the case study asks for: the recipient check
belongs to the Medical Secretaries and the follow-up request to the Outpatient Bookings Team.

| Catch event | Service task | Error code | Returns to |
|---|---|---|---|
| `Boundary_Error_Validation` | `ServiceTask_ValidateReferral` | `MISSING_INFORMATION_NOT_SPECIFIED` | `UserTask_ReviewReferralException` |
| `Boundary_Error_ReferralInput` | `ServiceTask_ValidateReferral` | `INVALID_VARIABLE` | `UserTask_ReviewReferralException` |
| `Boundary_Error_BookingRequest` | `ServiceTask_CheckAppointmentAvailability` | `INVALID_VARIABLE` | `UserTask_CorrectBookingRequest` |
| `Boundary_Error_NotificationRequest` | `ServiceTask_SendAppointmentNotification` | `INVALID_VARIABLE` | `UserTask_CorrectBookingRequest` |
| `Boundary_Error_TreatmentRequest` | `ServiceTask_CheckTreatmentAvailability` | `INVALID_VARIABLE` | `UserTask_CorrectTreatmentRequest` |
| `Boundary_Error_UnauthorisedBooking` | `ServiceTask_CheckTreatmentAvailability` | `UNAUTHORISED_BOOKING_REQUEST` | `UserTask_ReturnForAuthorisation` |
| `Boundary_Error_PaymentRequest` | `ServiceTask_ProcessPayment` | `INVALID_VARIABLE` | `UserTask_CorrectPaymentRequest` |
| `Boundary_Error_ProhibitedData` | `ServiceTask_ProcessPayment` | `PROHIBITED_FINANCIAL_DATA` | `UserTask_CorrectPaymentRequest` |
| `Boundary_Error_LetterDispatch` | `ServiceTask_DispatchClinicLetter` | `INVALID_VARIABLE` | `UserTask_ProcessAndDistributeLetter` |
| `Boundary_Error_FollowUpBooking` | `ServiceTask_CheckFollowUpAvailability` | `INVALID_VARIABLE` | `UserTask_ArrangeFollowUpAppointment` |

**The gateway's fallback must be declared.** Camunda does not treat a sequence flow without a
condition as a fallback: an exclusive gateway only falls back to the flow named in its `default`
attribute, and raises "Expected at least one condition to evaluate to true, or to have a default
flow" when no condition matches. Every exclusive gateway in the three operational models now
declares one.

| Error code | Raised by | Meaning |
|---|---|---|
| `INVALID_VARIABLE` | every worker | A required variable is missing, is of the wrong type, or the values contradict each other |
| `MISSING_INFORMATION_NOT_SPECIFIED` | `referral-validation` | The referral is marked incomplete but names no missing items, so nothing can be requested |
| `PROHIBITED_FINANCIAL_DATA` | `payment-processing` | Card or security details were supplied. The provider is **not** called (BR-06) |
| `UNAUTHORISED_BOOKING_REQUEST` | `treatment-availability` | The Treatment Booking Request has no clinical authorisation (BR-04) |

**Integration constraint, found by testing.** The variables attached to a BPMN error are written to
the scope of the activity that raised it. Once a boundary error event catches the error and the
token moves on, those variables are no longer visible in the process scope. The model must
therefore distinguish the reason for a failure by **which error catch event was taken**, not by
reading a variable that the failed worker wrote. Only `job.complete` variables reach the process.

## Simulated external services

| Simulated service | What it simulates | Behaviour implemented | Limitations |
|---|---|---|---|
| External scheduling service | Appointment availability | Returns `available` (a slot inside the requested window), `outside_window` (a slot later than the period the clinician asked for) or `none`, so BR-17, EX-06, EX-17, TC-05 and TC-18 can be exercised | Fixed lead times; no clinic capacity, room or clinician availability; no real diary |
| External treatment, laboratory and imaging service | Availability of treatment and investigation slots | Returns `available` or `unavailable`, so EX-07 and TC-12 can be exercised. An `unavailable` attempt is recorded and leaves the booking pending | No capacity data and no clinical constraints; the appointment is generated, not scheduled |
| External Payment Service Provider | Card payment processing | Returns `completed`, `declined` or `success_no_confirmation`, with a transaction reference, date and amount. Repeat requests for the same payment reference return the original transaction as a duplicate | No real card processing, clearing, settlement, 3-D Secure or fraud checks; refunds are recorded rather than executed |
| External correspondence service | Letter distribution | Records the dispatch with its channel, date and a reference | No printing, postage or delivery confirmation; the channel preference is recorded but not honoured end to end |

Three of the four simulated services keep a ledger keyed by the booking or payment reference. That
is what makes "no duplicate appointment" (FR-016, AC-10) and "no second charge" (FR-023, AC-10)
testable: the second request for the same key returns the first result instead of creating another.
The ledgers are in memory, so they are per worker process and are cleared when it restarts.

## Failure handling

| Failure | Detection | Behaviour | Evidence |
|---|---|---|---|
| Invalid or missing input variable | The worker validates its variables before doing any work | Business error `INVALID_VARIABLE`; where the model has a catch event the process follows its error path, and where it has none the error becomes an incident (see the note under Error codes). No rebooking and no recharging either way. A blank required reference and a contradictory pair (`documentsComplete = true` with missing items listed) are both treated this way | `tests/evidence/TC-11_worker-invalid-input_813fea6_2026-09-21.txt` |
| External service unavailable | The simulated treatment service returns `unavailable` | The booking stays `pending`, `appointmentReference` is `null`, `treatmentRetryCount` is incremented, and a later attempt does not create a second appointment | `tests/evidence/TC-12_external-service-unavailable_813fea6_2026-09-21.txt` |
| Payment taken but confirmation not returned | The provider response lacks the confirmation flag | `requiresInvestigation` is `true` and no further payment request is issued for that appointment (BR-07) | `tests/evidence/TC-09_payment-taken-without-confirmation_813fea6_2026-09-21.txt` |
| Payment declined | The provider returns `declined` | `paymentStatus` is `declined`, `paidAmount` is `null`, and a later attempt with the same reference is allowed because nothing was taken | `tests/evidence/TC-08_payment-declined_813fea6_2026-09-21.txt` |
| Card details supplied | The worker scans the variables for card and security field names | Business error `PROHIBITED_FINANCIAL_DATA`; the provider is not called; the field names are reported so the form can be corrected (BR-06, NFR-007) | `tests/evidence/TC-11_worker-invalid-input_813fea6_2026-09-21.txt` |
| Booking request without clinical authorisation | `clinicalAuthorised` is false or absent | Business error `UNAUTHORISED_BOOKING_REQUEST`; the request is not processed and no treatment appointment is created (BR-04) | `tests/evidence/TC-06_booking-without-clinical-authorisation_813fea6_2026-09-21.txt` |
| An unexpected exception in a handler | The handler wrapper catches it | `job.fail` with the message, so the broker retries the job rather than leaving it stalled. A handler that returns something other than a job outcome is failed the same way | `tests/evidence/TC-11_worker-invalid-input_813fea6_2026-09-21.txt` (tests 29 and 30) |
| Worker unavailable (not running) | No worker is registered for the job type | The job stays in the queue and the process waits where it is: with no worker registered the element instance stays `ACTIVE` at the service task and the token does not move on. The deadline still applies, and after the model's retries are exhausted the job raises an incident in Operate. Observed at `ServiceTask_ValidateReferral` | `tests/evidence/referral-to-appointment_normal-path_1b42bff_2026-09-21.txt` |

## Testing

`npm test` runs 30 tests with no engine. They call the same handlers the workers call, with the
simulated services running for real, so the behaviour is exercised rather than mocked. Each test
names the test case or business rule it belongs to.

`npm run test:smoke` deploys `test/fixtures/worker-smoke-test.bpmn` — a single linear process that
calls each worker once — starts the real workers and runs two process instances: the normal path,
and a booking without clinical authorisation. It asserts on the path each instance actually took,
read from the Orchestration Cluster API, so it proves the workers obtain work, return results and
let the process continue, and that a business error is caught by the model's boundary event.

Both runs are recorded in `tests/evidence/`. The workers were also exercised against the operational
models once those existed: the workers running, an instance of `referral-to-appointment` driven
through the Orchestration Cluster API, and the path read back from the engine. That run is recorded
in `tests/evidence/referral-to-appointment_normal-path_1b42bff_2026-09-21.txt`, together with the
one incident it produced. Forms and role-based access are still out of scope here and are tested
separately; see `../tests/test-plan.md`.
