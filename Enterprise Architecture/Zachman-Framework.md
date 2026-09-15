# Zachman Framework of Information Systems Architecture

The Zachman Framework is an ontology for describing an enterprise. It classifies architecture
artefacts on two axes: the **perspective** of the stakeholder who needs the description (rows), and
the **interrogative** that the description answers (columns).

## 1. The two-dimensional grid

| Perspective (row) | What — Data | How — Function | Where — Network | Who — People | When — Time | Why — Motivation |
| --- | --- | --- | --- | --- | --- | --- |
| **Planner** — scope / contextual | List of things important to the business | List of processes the business performs | List of locations in which the business operates | List of organisations important to the business | List of events significant to the business | List of business goals and strategies |
| **Owner** — enterprise / business model | Semantic model | Business process model | Business logistics system | Work-flow model | Master schedule | Business plan |
| **Designer** — system model | Logical data model | Application architecture | Distributed system architecture | Human interface architecture | Processing structure | Business rule model |
| **Builder** — technology model | Physical data model | System design | Technology architecture | Presentation architecture | Control structure | Rule design |
| **Implementer** — detailed representations | Data definition | Program | Network architecture | Security architecture | Timing definition | Rule specification |
| **Worker** — functioning enterprise | Actual data | Functioning function | Communications facilities | Functioning organisation | Business calendar | Functioning strategy |

## 2. Selected cells for the hospital trust

| Cell | Artefact for the trust |
| --- | --- |
| Planner / What | Patients, referrals, appointments, clinical episodes, staff, beds and funding arrangements |
| Planner / How | Referral, clinical review, appointment contact, treatment authorisation, funding and payment, clinic letter approval |
| Planner / Why | Safe and timely patient care, waiting-time targets, financial balance and accountability to the integrated care board |
| Owner / What | Entities in the patient administration system: patient, referral, appointment, episode, correspondence, invoice |
| Owner / How | Process models for the referral-to-treatment pathway, including the rejected and cancelled referral paths |
| Owner / Who | Clinical and administrative roles: referrer, clinical reviewer, appointments clerk, authorising clinician, finance officer |
| Designer / Where | Patient administration system, clinical review workflow, finance module and the interfaces between them |
| Designer / Why | Business rules that gate progress, for example a referral without a valid clinical priority cannot be booked |
| Builder / What | Data structures and the variable contract exchanged between the process and the external worker |
| Implementer / How | Executable process definition, forms and task bindings, worker service and its configuration |
| Worker / When | Clinic schedules, appointment slots and the order in which the user tasks are completed |

## 3. How the grid is used in this portfolio

The framework is used as a checklist rather than as a method. Each deliverable is positioned in the
grid, which makes gaps in the architecture visible: a cell with no artefact is a question the group
has not yet answered. Only the cells relevant to the scope of the portfolio are populated above.

## Reference

Zachman, J. A. (1987) 'A framework for information systems architecture', *IBM Systems Journal*,
26(3), pp. 276-292.
