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

## What each file has to record

Every piece of evidence identifies the version under test, states what the run covers and what it
does not, and gives the method used so the run can be repeated. Failing results are written down as
plainly as passing ones, and a limitation that affects what the run can show is stated in the file
rather than left out.

Files are kept small: text logs are preferred over recordings or screenshots.

## The runs recorded here

**The Java implementation, at `76a7fdc`.** The workers were rewritten in Java, and this is the first
record made against that implementation. It covers all three levels at one version — the
configuration check, the unit suite with no engine, and both engine runs — and it is the current
worker-level and model-level evidence. It replaces the `c8556ba` unit record and the `a7f0dd6`
engine record as the current ones; both are kept below as the record of what the Node.js
implementation did.

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
operational models of the first edition, which the four `core-N` models have since replaced.

- `referral-to-appointment_normal-path_1b42bff_2026-09-21.txt` — the normal path with the models and
  the workers together. Pass, with one uncaught-error incident recorded inside the file.
- `error-paths-and-urgent-path_e852224_2026-09-21.txt` — six scenarios covering four worker error
  paths, the urgent no-slot path, the normal path to completion and one declared-default branch. No
  incident. **The urgent no-slot path it covers is now the one that loops for ever in `core-1`; the
  fix it describes was in the models that were replaced, not in the ones delivered.**

## What is still open

- **The forms and role-based access.** No scenario has been completed by a signed-in user through
  Tasklist, so every user task variable in every run above was supplied with the completion call.
  The form contract is proved by the variables the model expects, not by a person filling a form in
  (`DEF-08`). The runs above also predate the forms: 36 of the 55 user tasks had no form at all when
  they were made, so for those tasks there was no form to prove (`DEF-13`, closed at `a62e783`;
  `../forms/README.md` records what the forms now cover). Role-based access cannot be exercised at
  all (`DEF-07`).
- **The exception paths that no engine run has driven.** The between-cycle review's refusals, the
  suspected-clinical-error return in `core-3`, the another-appointment and pathway-review branches
  of the cancellation, and the no-slot-in-period case at model level. The unit suite covers the
  worker side of several of them, which is not the same as covering the model's branch. The
  `76a7fdc` record lists them.
- **The job-failure path and the shutdown path are unproven end to end.** No run has driven a job
  failure through the engine, so the retry behaviour the Java client had to express explicitly is
  only covered at handler level, and no run has sent the workers a Ctrl-C.
- **Four scenarios have never been run**: `TC-02`, `TC-13`, `TC-16` and `TC-20`.
- **The engine runs stand on one machine's engine.** The deployment versions printed in the
  `76a7fdc` record are that development instance's, not first deployments.

