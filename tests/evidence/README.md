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
with the real workers: the normal referral path, authorisation and payment through the provider the
clinic letter, the follow-up, and the refund. Every instance reached an end event. This is the
current model-level evidence.

- `operational-models_end-to-end_a7f0dd6_2026-09-21.txt` — pass.

**Superseded, kept as the record of what was run at those commits.** These two were run against the
operational models of the first edition, which the four `core-N` models have since replaced.

- `referral-to-appointment_normal-path_1b42bff_2026-09-21.txt` — the normal path with the models and
  the workers together. Pass, with one uncaught-error incident recorded inside the file.
- `error-paths-and-urgent-path_e852224_2026-09-21.txt` — six scenarios covering four worker error
  paths, the urgent no-slot path, the normal path to completion and one declared-default branch. No
  incident.

## What is still open

The worker-level test cases above were executed against the external workers only. What is still to
be evidenced is the part of a test case that belongs to the **forms** or to role-based access in
Tasklist, which has to be run end to end against a release, and the execution record in
`../test-plan.md` stays blank until then.
