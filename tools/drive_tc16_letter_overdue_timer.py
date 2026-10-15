#!/usr/bin/env python3
"""Drive TC-16 - a clinic letter left outstanding past its seven-day target.

`tests/test-plan.md` TC-16 asks that the seven-day boundary timer on
`N_C_ApproveLetter` (`B_C_LetterOverdue`, `P7D`) fires without a worker being involved,
and that the delayed letter enters pathway monitoring, takes the reminder and the
escalation, and stops when the letter is in target. Two instances of `core-3` are
driven:

  A. the monitor records the letter as still outstanding -> `N_PC_IssueReminder`,
     `N_PC_OverdueDuration` (`over_three_months`), `N_AM_EscalateHigher`,
     `N_AM_Escalated`;
  B. the monitor records it within the target -> `N_PC_LetterOnTrack`, and no reminder.

No worker is needed: the delayed path the timer enters has no service task on it.

The clock. A seven-day timer cannot be waited out, so the engine clock is advanced
with the clock actuator. On Camunda 8 Run 8.9.19, observed on 2026-10-15:

  * the engine has to be started with the clock controllable, or the actuator answers
    `403 ... but it is immutable`: `set CAMUNDA_SYSTEMCLOCKCONTROLLED=true` before
    `camunda-runtime\\start-camunda.bat`;
  * `POST /actuator/clock/add {"offsetMilli": 691200000}` fires the `P7D` timer within
    seconds. `POST /actuator/clock/pin` (an absolute instant) did not fire it within
    three minutes, and `DELETE /actuator/clock`, which moves the clock back, left the
    secondary-storage view stale - instances created afterwards returned `404` from the
    process-instance endpoints until the engine was restarted;
  * this script therefore leaves the clock advanced and the engine is restarted
    afterwards, rather than resetting the clock under a running engine.

Run:  CAMUNDA_SYSTEMCLOCKCONTROLLED=true camunda-runtime\\start-camunda.bat
      python tools/drive_tc16_letter_overdue_timer.py
      camunda-runtime\\stop-camunda.bat        (restores the system clock)
"""

import json
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
ACT = "http://localhost:9600"
PROCESS = "core-3-clinic-letter-and-pathway-escalation"


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


def clock(method, body=None, path="/actuator/clock"):
    return call(ACT + path, body, method)


def open_tasks(pik):
    return post("/v2/user-tasks/search",
                {"filter": {"processInstanceKey": str(pik), "state": "CREATED"}}).get("items", [])


def wait_task(pik, element_id, timeout=180):
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


def drive(label, letter_within_seven_days):
    print(f"== {label}")
    pik = post("/v2/process-instances",
               {"processDefinitionId": PROCESS, "variables": {}})["processInstanceKey"]
    print(f"   instance {pik}")
    t = wait_task(pik, "N_C_PrepareLetter")
    complete(t, {"consultationId": "CONS-E2E-0002", "letterContent": "Seen in clinic; plan discussed.",
                 "authoringClinician": "Dr Chen", "recipients": ["patient", "GP"],
                 "documentType": "new_patient_clinic_letter"})
    wait_task(pik, "N_C_ApproveLetter")
    print("   N_C_ApproveLetter is open with the seven-day boundary timer armed")

    advanced = clock("POST", {"offsetMilli": 8 * 24 * 3600 * 1000}, "/actuator/clock/add")
    print(f"   clock advanced by eight days: {advanced.get('instant')}")
    t = wait_task(pik, "N_PC_MonitorCorrespondence")
    print("   the boundary timer fired and the delayed path is entered")

    complete(t, {"consultationId": "CONS-E2E-0002", "letterWithinSevenDays": letter_within_seven_days,
                 "monitoringDate": "2026-10-23", "daysOutstanding": 8,
                 "monitoredBy": "Pathway Coordinator", "monitoringNotes": "letter still outstanding"})

    if not letter_within_seven_days:
        t = wait_task(pik, "N_PC_IssueReminder")
        complete(t, {"consultationId": "CONS-E2E-0002", "reminderNumber": 1,
                     "reminderDate": "2026-10-23", "overdueDuration": "over_three_months",
                     "reminderTo": "Dr Chen", "reminderResponse": "no response",
                     "issuedBy": "Pathway Coordinator"})
        t = wait_task(pik, "N_AM_EscalateHigher")
        complete(t, {"consultationId": "CONS-E2E-0002", "escalatedTo": "Higher Management",
                     "escalationDate": "2026-10-23",
                     "escalationReason": "letter overdue over three months",
                     "escalatedBy": "Administrative Manager"})

    t0 = time.time()
    while time.time() - t0 < 60:
        if instance_state(pik) == "COMPLETED":
            break
        time.sleep(2)
    state = instance_state(pik)

    reached = [(e["elementId"], e["state"]) for e in elements(pik)]
    interesting = ("N_C_ConsultationDone", "N_C_PrepareLetter", "N_C_ApproveLetter",
                   "B_C_LetterOverdue", "N_PC_MonitorCorrespondence", "N_PC_SevenDays",
                   "N_PC_IssueReminder", "N_PC_OverdueDuration", "N_AM_EscalateHigher",
                   "N_AM_Escalated", "N_PC_LetterOnTrack")
    print(f"   final state: {state}")
    print(f"   path: {' -> '.join(e for e, s in reached if e in interesting)}")
    for e, s in reached:
        if e in ("N_C_ApproveLetter", "B_C_LetterOverdue", "N_PC_IssueReminder",
                 "N_AM_Escalated", "N_PC_LetterOnTrack"):
            print(f"     {e:26s} {s}")
    return pik, state, [e for e, s in reached]


def main():
    ok = True
    pik_a, state_a, elems_a = drive("A - the letter is still outstanding", False)
    if "N_PC_IssueReminder" not in elems_a or "N_AM_Escalated" not in elems_a or state_a != "COMPLETED":
        ok = False
        print("   !! A did not take the reminder and escalation path to its end")

    pik_b, state_b, elems_b = drive("B - the letter was completed within the target", True)
    if "N_PC_IssueReminder" in elems_b or "N_PC_LetterOnTrack" not in elems_b or state_b != "COMPLETED":
        ok = False
        print("   !! B did not end on track without a reminder")

    print("TC-16:", "PASS" if ok else "FAIL")
    print("Stop the engine now (camunda-runtime\\stop-camunda.bat) so the system clock is restored.")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
