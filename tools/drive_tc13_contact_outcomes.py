#!/usr/bin/env python3
"""Drive TC-13 - the telephone contact outcomes that are not a plain contact.

`tests/test-plan.md` TC-13 asks what happens when the patient is telephoned and the
call is unanswered, the number is wrong, or the patient asks for an alternative
appointment. Three instances of `core-1` are driven, one per outcome:

  * `unanswered` and `wrong_number` record the attempt (`N_OB_RecordAttempt`) and put
    the contact back (`N_OB_MergeContactPath` -> `N_OB_TelephonePatient`), which is
    completed a second time with `contacted`;
  * `alternative_requested` goes back into the booking (`N_OB_MergeBookingRequest`),
    the request is prepared again, and the second contact confirms.

The workers must be running (`cd workers && mvn compile exec:java`), because the
referral validation, the availability check and the notification are service tasks.
The user tasks are completed through the API with the variables a validated form
would supply, which is how every scenario in this repository is driven; no task is
claimed from Tasklist, so the Tasklist half of the record stays `DEF-08`.

Run:  python tools/drive_tc13_contact_outcomes.py
"""

import json
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
PROCESS = "core-1-referral-and-new-patient-appointment"


def call(url, body=None, method="POST"):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"content-type": "application/json"} if body is not None else {}
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            text = r.read().decode()
            return json.loads(text) if text.strip() else {}
    except urllib.error.HTTPError as e:
        raise SystemExit(f"!! {method} {url} -> HTTP {e.code}: {e.read().decode()[:400]}")


def post(route, body=None):
    return call(BASE + route, body if body is not None else {})


def open_tasks(pik):
    return post("/v2/user-tasks/search",
                {"filter": {"processInstanceKey": str(pik), "state": "CREATED"}}).get("items", [])


def wait_task(pik, element_id, timeout=120):
    t0 = time.time()
    while time.time() - t0 < timeout:
        for t in open_tasks(pik):
            if t["elementId"] == element_id:
                return t
        time.sleep(2)
    raise SystemExit(f"!! {element_id} did not appear within {timeout}s")


def complete(task, variables=None):
    post(f"/v2/user-tasks/{task['userTaskKey']}/completion", {"variables": variables or {}})


def elements(pik):
    return post("/v2/element-instances/search",
                {"filter": {"processInstanceKey": str(pik)}}).get("items", [])


def instance_state(pik):
    return call(f"{BASE}/v2/process-instances/{pik}", method="GET").get("state")


def start_referral(tag):
    pik = post("/v2/process-instances",
               {"processDefinitionId": PROCESS, "variables": {}})["processInstanceKey"]
    t = wait_task(pik, "N_MS_CheckReferral")
    complete(t, {"referralId": f"REF-TC13-{tag}", "patientId": f"PAT-TC13-{tag}",
                 "nhsNumber": "9434765919", "patientName": "Ada Lovelace",
                 "documentsComplete": True, "referringOrganisation": "St Mary GP Surgery"})
    t = wait_task(pik, "N_C_ClinicalReview")
    complete(t, {"decision": "accepted"})
    return pik


def prepare_request(pik, tag, window=14):
    t = wait_task(pik, "N_OB_PrepareRequest")
    complete(t, {"speciality": "Oncology", "priority": "routine", "requestedWindow": window,
                 "recipients": ["patient", "GP"], "documentType": "new_patient_clinic_letter"})


def telephone(pik, outcome, attempt):
    t = wait_task(pik, "N_OB_TelephonePatient")
    complete(t, {"patientId": "PAT-TC13", "contactMethod": "telephone",
                 "contactOutcome": outcome, "contactAttemptNumber": attempt,
                 "contactNotes": f"attempt {attempt}: {outcome}"})


def finish(pik):
    t0 = time.time()
    while time.time() - t0 < 90:
        if instance_state(pik) == "COMPLETED":
            return "COMPLETED"
        time.sleep(2)
    return instance_state(pik)


def drive(outcome, tag):
    print(f"== {outcome}")
    pik = start_referral(tag)
    print(f"   instance {pik}")
    prepare_request(pik, tag)
    telephone(pik, outcome, 1)
    if outcome in ("unanswered", "wrong_number"):
        t = wait_task(pik, "N_OB_RecordAttempt")
        complete(t, {"patientId": f"PAT-TC13-{tag}", "contactAttemptNumber": 1,
                     "contactOutcome": outcome, "contactNotes": "no answer at the recorded number"})
        telephone(pik, "contacted", 2)
    elif outcome == "alternative_requested":
        prepare_request(pik, tag, window=21)
        telephone(pik, "contacted", 1)
    state = finish(pik)
    reached = [e["elementId"] for e in elements(pik)]
    print(f"   final state: {state}")
    print(f"   contact elements: "
          f"{[e for e in reached if e in ('N_OB_TelephonePatient','N_OB_RecordAttempt','N_OB_ContactOutcome')]}")
    print(f"   ended at: {[e for e in reached if e.endswith('AppointmentArranged')]}")
    return pik, state, reached


def main():
    ok = True
    for outcome, tag in (("unanswered", "UNANS"), ("wrong_number", "WRONG"),
                         ("alternative_requested", "ALT")):
        pik, state, reached = drive(outcome, tag)
        if state != "COMPLETED" or "N_OB_AppointmentArranged" not in reached:
            ok = False
            print(f"   !! {outcome}: the appointment was not kept to its end")
        if outcome in ("unanswered", "wrong_number") and "N_OB_RecordAttempt" not in reached:
            ok = False
            print(f"   !! {outcome}: the attempt was not recorded")
    print("TC-13:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
