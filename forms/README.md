# Camunda Forms

The Camunda Forms the staff fill in, one `.form` file per form, and the user tasks in the
operational models they are bound to.

## How a form is bound to a task

Every `.form` file carries a top-level `id`, which is the form's key. A user task refers to that key
from its extension elements:

```xml
<bpmn:userTask id="N_C_ClinicalReview" name="Clinical review of referral">
  <bpmn:extensionElements>
    <zeebe:userTask />
    <zeebe:formDefinition formId="referral-review" />
    <zeebe:assignmentDefinition candidateGroups="consultants" />
  </bpmn:extensionElements>
</bpmn:userTask>
```

Two things have to hold for Tasklist to render the form. The attribute is **`formId`**, and it sits
on **`<zeebe:formDefinition>`** inside `<bpmn:extensionElements>`. An attribute named
`camunda:formKey` on the task itself is Camunda 7 syntax: Camunda 8 ignores it, and because the
`camunda:` prefix was never declared the models stopped being well-formed XML and would not deploy
at all. The `.form` file must also declare the matching `id` and be deployed as a resource together
with the model, or Tasklist has no form to resolve the key against.

A form's field names are the process variables its task writes, so a field name and the name a
gateway condition or a worker reads have to be the same word. Where a form and a model disagreed,
the form was changed to match the model.

## The forms and what they cover

`referral-review.form` is bound to the consultant's clinical review of a referral in `core-1`. It
records the patient and referral identifiers, the referring organisation, the clinical summary and
the decision with its reason. It writes the decision as `decision`, with the values the gateway
tests: `accepted`, `rejected`, `further_information` and `redirected`.

`booking-request.form` is bound to preparing and correcting a booking request in `core-1`, and to
arranging and correcting a follow-up request in `core-4`. It records the speciality, the priority,
the time frame the clinician asked for and any requirements the patient has.

`patient-contact.form` is bound to the telephone contact and the retry in `core-1`, and to recording
a cancellation or non-attendance in `core-4`. It records the contact method, its outcome and any
notes.

`treatment-booking.form` is bound to recording consent and authorising a request, to authorising a
treatment modification and to correcting a booking input in `core-2`. It records the proposed
treatment, the start date, the number of cycles, any special resources, and the clinical
authorisation with the clinician who gave it.

`funding-route.form` is bound to determining the funding route in `core-2`: whether the treatment is
funded by the hospital, covered by an approved insurer or funding organisation, or paid for by the
patient, with notes on the decision.

`payment.form` is bound to calculating the charge and to correcting a payment request in `core-2`.
It records the charge, the funding route and the payment reference the provider is asked to settle.

`refund.form` is bound to the Finance Team's decision on a paid appointment and to correcting a
refund request in `core-4`. It records the payment reference being refunded, the decision
(`full`, `partial` or `none`), the amount where the refund is partial, the reason, and any notes.
The decision is written as `refundDecision` because that is the variable the gateway tests and the
refund worker validates.

`clinic-letter.form` is bound to preparing and approving a clinic letter and to confirming its
recipients in `core-3`. It records the consultation it follows, the letter content, the diagnosis,
the treatment decisions, the follow-up arrangements and the recipients.

## How the forms are built

Related fields are grouped into sections, so a long form is read in parts rather than as one list of
fields. Fields the process cannot proceed without are marked required, which stops an incomplete
submission that would only fail later in a worker. Enumerated values use dropdowns, so the value a
form writes is one the gateway condition or the worker's validation accepts, and the person filling
it in sees a label rather than a code. Free-text clinical notes and decision reasons use text areas
with room to write in. Each form has a short kebab-case `id`, because that id is the key the model
refers to and it has to stay stable.

## Testing the forms

The forms are exercised as part of the scenarios in `../tests/test-plan.md`, and the end-to-end run
described in `../tests/evidence/README.md` deploys them alongside the models so the bindings are
proved to resolve. The runs in `../tests/evidence/` that predate the forms passed their variables
with the task completion call instead, so they do not exercise the form bindings.
