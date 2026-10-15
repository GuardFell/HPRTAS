# -*- coding: utf-8 -*-
"""Applies the shared input-validation policy to the Camunda Forms.

The forms are the first line of defence against missing or invalid input: a field the process
cannot continue without is required, a reference has to look like a reference, a person's name has
to contain a letter, a count has to be within the range the process can act on, and a date that is
a future commitment cannot be set in the past. The workers re-check everything they read - that is
the second line - but a form that lets unusable input through turns a two-line fix at the desk into
a business error halfway through the pathway.

The policy is written down here rather than applied by hand, so the same rule reaches every form
that needs it, and re-running the script is a no-op. The `validate` object and `disallowPassedDates`
are the form-js properties the Tasklist renderer reads: `required`, `min`, `max`, `minLength`,
`maxLength`, `pattern` with `patternErrorMessage`, and `disallowPassedDates` on a date field.

The forms that carry the appointment-confirmation outcomes (`appointment-confirmation`,
`appointment-reschedule`, `cancellation-record`) are edited by hand alongside this, because what
they say is business content rather than policy.

Run: python tools\\apply_form_validation.py
"""
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FORM_DIR = os.path.join(REPO, "forms")

# A reference as the hospital writes it: letters, digits, dots, dashes and underscores, starting
# with a letter or a digit. REF-2026-0001, PAY-E2E-0001, TRT-2251799813685249:1 and CON-... all fit.
IDENTIFIER_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9._-]{2,39}$"
IDENTIFIER_MESSAGE = "Use the reference as the system writes it, for example REF-2026-0001."

# A person or role: has to contain at least one letter, so a name field cannot be answered with
# digits alone.
NAME_PATTERN = r"^(?=.*[A-Za-z]).+$"
NAME_MESSAGE = "Enter a name or role that contains letters."

# An NHS number is ten digits and nothing else (BR-46).
NHS_NUMBER_PATTERN = r"^[0-9]{10}$"
NHS_NUMBER_MESSAGE = "An NHS number is ten digits, for example 9434765919."
NHS_NUMBER_KEYS = {"nhsNumber"}

# Field keys that are references, in every form they appear in.
IDENTIFIER_KEYS = {
    "referralId", "patientId", "treatmentRequestId", "consultationId", "paymentReference",
    "transactionReference", "appointmentReference", "enquiryReference", "authorisationReference",
    "paymentsReference", "bookingReference",
}

# Field keys that name a person or a team.
NAME_KEYS = {
    "patientName", "confirmedBy", "checkedBy", "reviewedBy", "authorisingClinician",
    "assessingClinician", "advisedBy", "recordedBy", "notifiedBy", "processedBy", "monitoredBy",
    "escalatedBy", "issuedBy", "contactedBy", "verifiedBy", "approvedBy", "returnedBy",
    "highlightedTo", "escalatedTo", "reminderTo", "returnedTo", "notifiedTeam", "responsibleTeam",
    "fundingOrganisation", "referrerOrganisation", "requestedFrom",
}

# Free-text fields: how long they may be, and which of them must carry actual content.
TEXT_MAX_LENGTH = 2000
NARRATIVE_KEYS = {
    "decisionReason", "clinicalReason", "exceptionReason", "escalationReason",
    "clinicalErrorDescription", "delayReason", "modificationReason", "clinicalRiskReason",
    "proceedingWithoutPaymentReason", "returnNotes", "rescheduleNotes", "cancellationNotes",
}
NARRATIVE_MIN_LENGTH = 5

# Numbers the process acts on: the upper bound is what the case or the model can accommodate.
NUMBER_BOUNDS = {
    "numberOfCycles": 52,      # a year of weekly cycles
    "cycleNumber": 52,
    "reminderNumber": 52,      # weekly reminders for a year
    "contactAttemptNumber": 5,
    "daysOutstanding": 365,
    "requestedWindow": 365,
    "chargeAmount": 100000,
    "approvedAmount": 100000,
    "revisedChargeAmount": 100000,
    "refundAmount": 100000,
}

# Dates that are future commitments: the appointment cannot be booked, and the treatment cannot
# start, in the past.
FUTURE_DATE_KEYS = {
    "treatmentStartDate", "newRequestedDate", "alternativeDateOffered",
}

# Fields the process or the case cannot do without, which the survey found unmarked.
# `missingItems` is deliberately not here: it is required when the documents are not complete and
# meaningless when they are, and a form cannot express "required unless". Its description says so,
# and the worker refuses the contradictory pair.
REQUIRED_KEYS = {
    "appointmentReference", # a cancellation is recorded against an appointment
    "contactAttemptNumber", # an attempt is recorded with its number
    "paymentReference",     # a refund is against a payment
    "refundDecision",
}

FIELD_KEY_ORDER = ["type", "subtype", "dateLabel", "timeLabel", "key", "label", "description",
                   "validate", "values", "conditional", "disallowPassedDates", "defaultValue",
                   "components"]


def order_field(field):
    """Rebuild a field with its keys in the order the Modeler writes them, so diffs stay small."""
    ordered = {}
    for key in FIELD_KEY_ORDER:
        if key in field:
            ordered[key] = field[key]
    for key, value in field.items():
        if key not in ordered:
            ordered[key] = value
    return ordered


def ensure_validate(field):
    validate = field.get("validate")
    return validate if isinstance(validate, dict) else {}


def apply_policy(path):
    """Applies the policy to one form, and says what it changed."""
    raw = open(path, encoding="utf-8").read()
    newline = "\r\n" if "\r\n" in raw else "\n"
    data = json.loads(raw)
    changes = []

    def walk(components):
        for index, component in enumerate(components):
            key = component.get("key")
            if key:
                validate = ensure_validate(component)
                if key in IDENTIFIER_KEYS:
                    if validate.get("pattern") != IDENTIFIER_PATTERN:
                        validate["pattern"] = IDENTIFIER_PATTERN
                        validate["patternErrorMessage"] = IDENTIFIER_MESSAGE
                        changes.append("%s: reference pattern" % key)
                if key in NHS_NUMBER_KEYS:
                    if validate.get("pattern") != NHS_NUMBER_PATTERN:
                        validate["pattern"] = NHS_NUMBER_PATTERN
                        validate["patternErrorMessage"] = NHS_NUMBER_MESSAGE
                        changes.append("%s: NHS number pattern" % key)
                if key in NAME_KEYS:
                    if validate.get("pattern") != NAME_PATTERN:
                        validate["pattern"] = NAME_PATTERN
                        validate["patternErrorMessage"] = NAME_MESSAGE
                        changes.append("%s: name pattern" % key)
                    if "maxLength" not in validate:
                        validate["maxLength"] = 120
                        changes.append("%s: maxLength" % key)
                    validate.pop("minLength", None)
                if component.get("type") == "textarea" and "maxLength" not in validate:
                    validate["maxLength"] = TEXT_MAX_LENGTH
                    changes.append("%s: maxLength" % key)
                if key in NARRATIVE_KEYS:
                    if validate.get("minLength") != NARRATIVE_MIN_LENGTH:
                        validate["minLength"] = NARRATIVE_MIN_LENGTH
                        changes.append("%s: minLength" % key)
                if key in NUMBER_BOUNDS:
                    bound = NUMBER_BOUNDS[key]
                    if validate.get("max") != bound:
                        validate["max"] = bound
                        changes.append("%s: max %d" % (key, bound))
                if key in FUTURE_DATE_KEYS and not component.get("disallowPassedDates"):
                    component["disallowPassedDates"] = True
                    changes.append("%s: no past dates" % key)
                if key in REQUIRED_KEYS and not validate.get("required"):
                    validate["required"] = True
                    changes.append("%s: required" % key)
                if validate:
                    component["validate"] = validate
            if component.get("components"):
                walk(component["components"])

    walk(data.get("components", []))
    data["components"] = [order_field(component) for component in data["components"]]

    if changes:
        text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
        if newline == "\r\n":
            text = text.replace("\n", "\r\n")
        open(path, "w", encoding="utf-8", newline="").write(text)
    return changes


def main():
    changed = 0
    for name in sorted(os.listdir(FORM_DIR)):
        if not name.endswith(".form"):
            continue
        path = os.path.join(FORM_DIR, name)
        changes = apply_policy(path)
        if changes:
            changed += 1
            print("%s" % name)
            for change in changes:
                print("    " + change)
    if changed == 0:
        print("Every form already carries the validation policy.")
    else:
        print()
        print("%d form(s) updated. Run bundle_forms_by_core.py to refresh forms/form/." % changed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
