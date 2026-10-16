# Test Evidence

The output of the test runs, kept so that a result can be checked against the plan in
`../test-plan.md` and against the state of the repository that produced it.

## Naming convention

```
TC-<id>_<short-description>_<version>_<yyyy-mm-dd>.<ext>
```

For example, `TC-01_normal-referral-accepted_release-1.0_2026-09-25.png`.

`<version>` is the tag or the short commit the scenario was run at. A file whose name does not
carry a `TC-` id covers a run that spans several test cases, and what it covers is described in its
own header.

## The feedback-pass records

Two files in this directory were written by the pass of 2026-10-13 and name the working tree rather
than a commit, because the pass is uncommitted:

- `feedback-pass-static-checks-and-forms_working-tree_2026-10-13.txt` - the binding, layout, bundle
  and export checks, the form-validation policy and the `@bpmn-io/form-js` import and validation
  check.
- `workers-and-models_working-tree_2026-10-13.txt` - the unit suite and the engine run, with the
  twelve operational-model scenarios (including the declined confirmation, the reschedule and the
  pathway end to end) written out per task.

## What each file has to record

Every piece of evidence identifies the version under test, states what the run covers and what it
does not, and gives the method used so the run can be repeated. Failing results are written down as
plainly as passing ones, and a limitation that affects what the run can show is stated in the file
rather than left out.

Files are kept small: text logs are preferred over recordings or screenshots.

## The runs recorded here

**The last two convergences, on the working tree, 2026-10-15.** The newest record, and the only one
that describes the tree as it stands: two merging gateways were added to `core-2` and `core-3` after
`8d6f3ce`, so it names the working tree rather than a commit.

- `merge-gateways-and-the-verification-chain_working-tree_2026-10-15.txt` - the static checks, the
  engine deployment check, `mvn test -Pengine` (14 tests, 0 failures) and `TC-16` re-driven on the
  changed path. It also records two faults found in the check chain on the way: the engine-forms
  check could not follow `core-0`'s call activities and had been failing since `378b319`, and the
  engine's clock was made controllable through the runtime's `application.yaml`
  (`camunda.system.clock-controlled`) rather than through the `CAMUNDA_SYSTEMCLOCKCONTROLLED`
  environment variable, which did not take effect on this machine.

**The revised version, at `378b319`, 2026-10-15.** The records of the feedback pass: the suite
re-run at the commit the tag `submission-2026-10-22` was made on, and the two scenarios the plan
had never run, driven on the same tree. The tag was later re-pointed onto the sprint's later
commits, which change models but no form and no worker source. They supersede the `2c3c2eb` records
as the current ones, which are kept below as the record of what the tree did before the feedback
pass.

- `workers-and-models_378b319_2026-10-15.txt` - pass at all three levels: 52 unit tests (43 in
  `WorkersTest`, 9 in `PersistenceTest`) and 14 engine tests (2 smoke, 12 operational-model
  scenarios), 0 failures, 0 errors. The transcript is the tests' own report.
- `TC-13_telephone-contact-outcomes_378b319_2026-10-15.txt` - pass: three instances of `core-1`, one
  per telephone outcome the scenario names; each attempt recorded and no appointment lost. Driven by
  `tools/drive_tc13_contact_outcomes.py` with the workers running.
- `TC-16_letter-overdue-timer_378b319_2026-10-15.txt` - pass: the `P7D` boundary timer fired with no
  worker involved, the reminder and the escalation were taken, and the in-target letter ended on
  track with no reminder. The engine's clock was advanced for the run; the record says how, and what
  the alternative operations do on this build. Driven by
  `tools/drive_tc16_letter_overdue_timer.py`.

**The message exchange and the drawing repairs, at `2c3c2eb`.** The current worker-level and
model-level evidence, one commit past `57b3c1d`. It adds the fifth model and the worker that
publishes a message, so the engine run is 11 tests rather than 10 (2 the smoke fixture, 9 the
operational models) and the unit suite is 42 rather than 38. It supersedes the `57b3c1d` records as
the current ones, which are kept below as the record of what the tree did before the exchange
existed.

- `core-5-message-exchange-and-layout-tidy_2c3c2eb_2026-09-28.txt` — pass at every level: 42 of 42
  unit tests, the three static checks, and 11 of 11 engine tests. It states what the run does not
  cover, which includes the two defects no test can close (`DEF-07`, `DEF-08`), the fact that the
  reply is published by the scenario rather than by a worker, and the fact that only `core-5` was
  laid out again, so the other six committed diagrams still carry the message-flow routing this
  version repairs.

**The tree at `57b3c1d`.** The tip of `main` when these two records were made, one commit past
`release-1.0`. Two records were made at this version on 2026-09-28: the three test levels, and the
four checks that are about the artefacts rather than about the workers. Between them they were the
current evidence, and they superseded the `76a7fdc` record — the tree has moved since, through
`a62e783` (every user task bound to a form), `9138bcc` (`DEF-12` closed) and `57b3c1d` (`DEF-11`
closed, and every model re-laid out).

- `workers_java-all-three-levels_57b3c1d_2026-09-28.txt` — pass at all three levels: 38 of 38 unit
  tests, the configuration check, 2 of 2 smoke scenarios and 8 of 8 operational-model scenarios. It
  is the first record that drives the escalation `DEF-11` was raised for, in scenario 8 of
  `OperationalModelsTest`, and the refund `DEF-12` was raised for, in scenario 5.
- `checks-static-deployment-and-engine-forms_57b3c1d_2026-09-28.txt` — the bindings, layout and
  export checks all pass, all four models and all 41 forms are accepted by the engine, and every
  model reaches a user task that binds a deployed form. It also records the refusal of the
  strategic model, which is the expected result rather than a failure.
- `operational-models-and-forms_end-to-end_57b3c1d_2026-09-28.txt` — **the same engine run as the
  first file, written out per scenario**: for every scenario and every user task, the element path
  the engine took and the variables the task was completed with, plus which test-plan scenarios each
  one is a result for. The first file states what each level concluded; this one is what those
  conclusions rest on, so a claim about a particular gateway can be checked without re-running the
  engine. It was produced by M4 from the same command at the same commit, and it agrees with the M5
  record on every result.

**The Java implementation, at `76a7fdc`.** The first record made against the Java implementation,
and the current one until `57b3c1d`. It covers all three levels at one version — the configuration
check, the unit suite with no engine, and both engine runs. It replaced the `c8556ba` unit record
and the `a7f0dd6` engine record as the current ones; both are kept below as the record of what the
Node.js implementation did.

- `workers_java-unit-smoke-and-models_76a7fdc_2026-09-22.txt` — pass at all three levels: 38 of 38
  unit tests, and 7 of 7 engine tests (2 the smoke fixture, 5 the operational models). The file
  states the three places where the Java implementation behaves differently from the Node.js one,
  and which of them this run does and does not prove.

**Worker component, at `c8556ba`.** The unit suite re-run against the Node.js version, which is the
one level that needs no engine. Thirty-eight tests, all passing, covering twelve of the twenty-one
test cases. It was the current worker-level evidence until `76a7fdc`.

- `workers_unit-suite_c8556ba_2026-09-21.txt` — pass, 38 of 38.

**Worker component, at `813fea6`.** Six test cases executed against the external workers before the
operational models existed. Each file says plainly what it does not cover.

- `TC-06_booking-without-clinical-authorisation_813fea6_2026-09-21.txt` — a Treatment Booking
  Request with no clinical authorisation is refused. Pass.
- `TC-07_payment-completed_813fea6_2026-09-21.txt` — a completed payment returns its status,
  reference, date and amount. Pass.
- `TC-08_payment-declined_813fea6_2026-09-21.txt` — a declined payment is recorded and may be
  retried without a second charge. Pass.
- `TC-09_payment-taken-without-confirmation_813fea6_2026-09-21.txt` — a payment taken without a
  returned confirmation is marked for investigation and not re-requested. Pass.
- `TC-11_worker-invalid-input_813fea6_2026-09-21.txt` — unusable input produces a controlled
  business error or a job failure rather than a crash. Pass.
- `TC-12_external-service-unavailable_813fea6_2026-09-21.txt` — an unavailable external service
  leaves the booking pending without creating a duplicate. Pass.

**Workers through the engine, at `813fea6` and `a7f0dd6`.** Both smoke runs exercise the workers
against a purpose-built linear fixture, which proves a worker registers, receives a job of its type,
returns its result and lets the process continue, and that a business error is caught by a boundary
event. The `a7f0dd6` run registers six workers rather than five, because the refund job type was
split out of the payment worker by then.

- `workers_end-to-end-smoke_813fea6_2026-09-21.txt` — pass.
- `workers_end-to-end-smoke_a7f0dd6_2026-09-21.txt` — pass.

**The operational models, at `a7f0dd6`.** Five scenarios driven through the four `core-N` models
with the real workers: the normal referral path, authorisation and payment through the provider, the
clinic letter, the follow-up, and the refund. Every instance reached an end event. It was the current
model-level evidence until `76a7fdc`; the workers have since been rewritten in Java, so it now
describes the Node.js implementation rather than the one under test.

- `operational-models_end-to-end_a7f0dd6_2026-09-21.txt` — pass.

**Superseded, kept as the record of what was run at those commits.** These two were run against the
operational models of the first edition, which the `core-N` models have since replaced.

- `referral-to-appointment_normal-path_1b42bff_2026-09-21.txt` — the normal path with the models and
  the workers together. Pass, with one uncaught-error incident recorded inside the file.
- `error-paths-and-urgent-path_e852224_2026-09-21.txt` — six scenarios covering four worker error
  paths, the urgent no-slot path, the normal path to completion and one declared-default branch. No
  incident. **The urgent no-slot path it covers is the one that later looped for ever in `core-1`
  (`DEF-11`, fixed at `57b3c1d`); the fix it describes was in the models that were replaced, not in
  the ones delivered.**

## What is still open

- **The forms and role-based access.** No scenario has been completed by a signed-in user through
  Tasklist, so every user task variable in every run above was supplied with the completion call.
  The form contract is proved by the variables the model expects, not by a person filling a form in
  (`DEF-08`). The `57b3c1d` run is the first made after the forms were completed, so a form now
  exists for every user task - 41 forms for 56 user tasks at `57b3c1d` (`DEF-13`, closed at
  `a62e783` and extended by that commit); the bindings are proved
  on the engine by `../tools/verify_hprtas_engine_forms.py`, which is recorded in the second
  `57b3c1d` file. What is still not proved is a person using one. Role-based access cannot be
  exercised at all (`DEF-07`).
- **The exception paths that no engine run has driven.** The informal-request refusal in the
  between-cycle review, the suspected-clinical-error return in `core-3`, the offer-another-appointment
  and pathway-review branches of the cancellation, and the third decision of the referral
  (`further_information`). The unit suite covers the worker side of several of them, which is not the
  same as covering the model's branch. Three paths that used to belong on this list are now driven by
  the `57b3c1d` run: the no-slot-in-period case at model level, in scenario 8, where the urgent
  variant reaches the escalation `DEF-11` was raised for and the instance completes; the two
  non-accepting referral decisions, in scenario 7; and the enquiry routing, in scenario 6. The
  cancellation's other two branches are **not** driven - scenario 5 takes `offerAnotherAppointment`
  and `pathwayReviewRequired` both false, so it exercises the Finance-referral branch only.
- **The job-failure path and the shutdown path are unproven end to end.** No run has driven a job
  failure through the engine, so the retry behaviour the Java client had to express explicitly is
  only covered at handler level, and no run has sent the workers a Ctrl-C.
- **Two scenarios have never been run**: `TC-13` and `TC-16`. `TC-02` and `TC-20` were on this list
  until the `57b3c1d` run, which gave each a partial result: `TC-02` has two of its three decisions
  driven and `TC-20` has its routing driven but not the recording `BR-37` asks for. `TC-13` needs a
  signed-in user and `TC-16` needs the seven-day timer to fire, so neither can be closed by a run
  alone. `../test-plan.md` section 5 states which rows moved and why.
- **The engine runs stand on one machine's engine.** The deployment versions printed in the
  `76a7fdc` record are that development instance's, not first deployments.

