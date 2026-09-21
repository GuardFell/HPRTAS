# Test Evidence

Evidence of the testing conducted against `../test-plan.md`.

## Naming convention

```
TC-<id>_<short-description>_<version>_<yyyy-mm-dd>.<ext>
```

Example: `TC-01_normal-referral-accepted_release-1.0_2026-09-25.png`

## Rules

- Identify the **version under test** (tag or commit) in every piece of evidence
- Record failing results as plainly as passing ones
- Keep files small; prefer text logs to large recordings
- Link each item back to a row in `../test-plan.md` sections 5 and 6

## Index

| Test case | File | Date | Version | Result |
|---|---|---|---|---|
| TC-06 | `TC-06_booking-without-clinical-authorisation_813fea6_2026-09-21.txt` | 2026-09-21 | `813fea6` | Pass (worker component) |
| TC-07 | `TC-07_payment-completed_813fea6_2026-09-21.txt` | 2026-09-21 | `813fea6` | Pass (worker component) |
| TC-08 | `TC-08_payment-declined_813fea6_2026-09-21.txt` | 2026-09-21 | `813fea6` | Pass (worker component) |
| TC-09 | `TC-09_payment-taken-without-confirmation_813fea6_2026-09-21.txt` | 2026-09-21 | `813fea6` | Pass (worker component) |
| TC-11 | `TC-11_worker-invalid-input_813fea6_2026-09-21.txt` | 2026-09-21 | `813fea6` | Pass (worker component) |
| TC-12 | `TC-12_external-service-unavailable_813fea6_2026-09-21.txt` | 2026-09-21 | `813fea6` | Pass (worker component) |
| end to end | `workers_end-to-end-smoke_813fea6_2026-09-21.txt` | 2026-09-21 | `813fea6` | Pass (workers through the engine) |
| normal path | `referral-to-appointment_normal-path_1b42bff_2026-09-21.txt` | 2026-09-21 | `1b42bff` | Pass (model and workers together, instance completed); one uncaught-error incident recorded inside the file |
|  |  |  |  |  |

### Scope of the items above

These six test cases were executed against the **external workers** only, from before the
operational models existed. Each item states plainly what it does not cover. The operational models
now exist and the workers have been exercised against them; what is still open is the part of a
test case that belongs to the **forms** or to role-based access, which has to be evidenced end to
end against `release-1.0`, and the execution record in `../test-plan.md` section 5 stays blank
until then.
