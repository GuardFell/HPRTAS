# The models as tables

> **Generated** by `python tools/istar_render.py` from `sd-hprtas.json` and
> `sr-hprtas.json` - do not edit by hand. These tables are the content of the
> diagrams; the prose that explains the modelling choices is in
> `modelling-choices.md`.

Baseline: the workshop goal model of the referral pathway (材料/goalModel.txt and goalDiagram (1).png, 12 Oct 2026)

## 1. The Strategic Dependency model

`A -> B` reads *A depends on B for the dependum*. The `Rationale` column names the
SR elements that account for the dependency. Every dependency here is one the
workshop goal model states; the interface it leaves unstated, and why, is recorded
in `modelling-choices.md` section 3.

| # | Depender | Dependee | Type | Dependum | Rationale |
|---|---|---|---|---|---|
| SD-01 | patient | receptionist | goal | registration | patient `G1`<br>patient `T1`<br>receptionist `G1`<br>receptionist `T1` |
| SD-02 | patient | receptionist | task | appointment booking | patient `G1`<br>patient `T2`<br>receptionist `T2` |
| SD-03 | patient | receptionist | softgoal | quick booking | patient `S1` |
| SD-04 | patient | receptionist | resource | payment | patient `S2`<br>patient `T2`<br>receptionist `T3` |
| SD-05 | patient | doctor | goal | diagnosis | doctor `G1` |
| SD-06 | patient | doctor | goal | determine the cancer type and site | doctor `G2`<br>doctor `T4` |
| SD-07 | doctor | specialist | task | review the test results | doctor `S3`<br>doctor `T5`<br>specialist `G1`<br>specialist `T1` |

## 2. The Strategic Rationale model

One page per actor. `Accounts for` names the SD dependencies the element carries;
`Links` the rationale links leaving it.

### Patient `patient`

*Get registered, be given an appointment quickly, pay the fee, and reach a diagnosis.*

| Kind | Id | Element | Accounts for | Links | Gap |
|---|---|---|---|---|---|
| softgoal | S1 | Registration and booking are quick | SD-03 | - | - |
| softgoal | S2 | The cost is clear before payment | SD-04 | - | - |
| goal | G1 | Registered and given an appointment | SD-01, SD-02 | help -> S1 | - |
| task | T1 | Give personal and insurance details at the desk | SD-01 | means-end -> G1 | - |
| task | T2 | Request an appointment and make the payment | SD-02, SD-04 | means-end -> G1, help -> S2 | - |

### Receptionst `receptionist`

*Serve the patient at the front desk: register, book, and take the payment without keeping anyone waiting.*

| Kind | Id | Element | Accounts for | Links | Gap |
|---|---|---|---|---|---|
| softgoal | S1 | Every patient is served without friction | - | - | - |
| goal | G1 | The patient's details are recorded correctly | SD-01 | - | - |
| goal | G2 | The registration and booking service is delivered | - | - | - |
| task | T1 | Record the patient's details | SD-01 | means-end -> G1, means-end -> G2 | - |
| task | T2 | Arrange the appointment with the doctor | SD-02 | means-end -> G2, help -> S1 | - |
| task | T3 | Take the payment and issue the receipt | SD-04 | means-end -> G2 | - |

### Doctor `doctor`

*Diagnose the patient, determine the cancer type and site, and bring in the specialist where the test results need that expertise.*

| Kind | Id | Element | Accounts for | Links | Gap |
|---|---|---|---|---|---|
| softgoal | S1 | The diagnosis is accurate and reached without delay | - | - | - |
| softgoal | S2 | The diagnosis follows the pathway's rules | - | - | - |
| softgoal | S3 | The test review comes back promptly | SD-07 | - | - |
| goal | G1 | Diagnose the patient | SD-05 | help -> S1, help -> S2 | - |
| goal | G2 | Determine the cancer type and site | SD-06 | means-end -> G1 | - |
| task | T1 | Review the patient's history | - | decomposition -> G1 (and), help -> S1 | - |
| task | T2 | Perform clinical appraisal | - | decomposition -> G1 (and), help -> S1, help -> S2 | - |
| task | T3 | Assess whether admission is needed | - | decomposition -> G1 (and), help -> S2 | - |
| task | T4 | Determine the cancer type and site from the clinical picture | SD-06 | means-end -> G2 | - |
| task | T5 | Bring in the specialist to review the test results | SD-07 | means-end -> G2, help -> S3 | - |

### Specialist `specialist`

*Review the test results the doctor sends, against the clinical question asked.*

| Kind | Id | Element | Accounts for | Links | Gap |
|---|---|---|---|---|---|
| softgoal | S1 | The review is timely and unambiguous | - | - | - |
| goal | G1 | The test results are reviewed when the doctor requests it | SD-07 | - | - |
| task | T1 | Review the test results against the clinical question | SD-07 | means-end -> G1, help -> S1 | - |

