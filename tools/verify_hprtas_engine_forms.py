# -*- coding: utf-8 -*-
"""Deploy the operational models and the forms they bind, and prove the binding
holds on the engine.

The static checks in `verify_hprtas_bpmn_bindings.py` prove the model and the
form agree on paper. Only the engine can prove that a task actually resolves its
form, so this does what the README tells a person to do - deploy every model and
every form - and then starts each model and looks at the user task it reaches.

  * every `.form` in `forms/` deploys
  * every `.bpmn` in `models/operational/` deploys, with its process definition
    created
  * an instance of each model starts, and the user task it lands on carries a
    form key that is one of the deployed forms

The models are started and then cancelled, so the engine is left as it was found
apart from the deployments, which is what a demonstration would leave behind.

The engine has to be running. If it is not answering, this skips and says so
rather than failing: a skipped run is not a pass.

Run: python tools\\verify_hprtas_engine_forms.py
"""
import glob
import json
import os
import subprocess
import sys
import time

# the repository root: <repo>/tools/<script>.py, so the tools run from any checkout
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(REPO, "models", "operational")
FORM_DIR = os.path.join(REPO, "forms")
BASE = "http://localhost:8080/v2"


def api(path, payload=None):
    """POST a JSON body (or nothing) and return (status, parsed body)."""
    command = ["curl", "-sS", "-X", "POST", BASE + path,
               "-H", "Content-Type: application/json",
               "-w", "\n%{http_code}"]
    if payload is not None:
        command += ["-d", json.dumps(payload)]
    out = subprocess.run(command, capture_output=True, text=True, errors="replace")
    body, _, status = out.stdout.rpartition("\n")
    try:
        return int(status.strip()), json.loads(body) if body.strip() else None
    except ValueError:
        return 0, {"raw": out.stdout[:400]}


def upload(files):
    """Deploy a set of resources in one call, as a person would."""
    command = ["curl", "-sS", "-X", "POST", BASE + "/deployments", "-w", "\n%{http_code}"]
    for path, content_type in files:
        command += ["-F", "resources=@%s;type=%s" % (path, content_type)]
    out = subprocess.run(command, capture_output=True, text=True, errors="replace")
    body, _, status = out.stdout.rpartition("\n")
    try:
        return int(status.strip()), json.loads(body) if body.strip() else None
    except ValueError:
        return 0, {"raw": out.stdout[:600]}


def engine_up():
    out = subprocess.run(["curl", "-sS", "-o", os.devnull, "-w", "%{http_code}",
                          "--max-time", "12", BASE + "/topology"],
                         capture_output=True, text=True)
    return out.stdout.strip() == "200"


def forms_of(model_path):
    """The form files a model binds, by reading the keys out of the model."""
    import xml.etree.ElementTree as ET
    zeebe = "{http://camunda.org/schema/zeebe/1.0}"
    root = ET.parse(model_path).getroot()
    keys = {definition.get("formId")
            for definition in root.iter(zeebe + "formDefinition") if definition.get("formId")}
    return [os.path.join(FORM_DIR, key + ".form") for key in sorted(keys)]


def process_id(model_path):
    import xml.etree.ElementTree as ET
    bpmn = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
    return ET.parse(model_path).getroot().find(bpmn + "process").get("id")


def main():
    if not engine_up():
        print("SKIP: no engine is answering on %s - start it with "
              "camunda-runtime\\start-camunda.bat" % BASE)
        return 0

    problems = []
    forms = sorted(glob.glob(os.path.join(FORM_DIR, "*.form")))
    status, body = upload([(path, "application/json") for path in forms])
    # the engine answers with one entry per resource, and a form's key is the key
    # a user task resolves to, so this is what a task is checked against
    deployed_forms = {entry["form"]["formKey"]: entry["form"]["formId"]
                      for entry in (body or {}).get("deployments", []) if entry.get("form")}
    print("%-46s %s" % ("%d forms" % len(forms),
                        "deployed" if status == 200 else "FAILED %d" % status))
    if status != 200:
        problems.append("the forms did not deploy: %s" % (body or {}).get("detail", body))
    elif len(deployed_forms) != len(forms):
        problems.append("%d form files deployed %d forms" % (len(forms), len(deployed_forms)))

    for path in sorted(glob.glob(os.path.join(MODEL_DIR, "*.bpmn"))):
        name = os.path.basename(path)
        resources = [(path, "application/xml")]
        # the model goes with the forms it binds, so a form bound as a
        # deployment resource resolves either way
        for form in forms_of(path):
            if form not in [r[0] for r in resources]:
                resources.append((form, "application/json"))
        status, body = upload(resources)
        if status != 200:
            detail = (body or {}).get("detail", body)
            print("%-46s %s" % (name, "REJECTED"))
            problems.append("%s was rejected by the engine: %s" % (name, detail))
            continue
        # a deployment that carries forms mints their versions, so the keys a
        # task resolves are the ones this deployment handed out
        keys_here = dict(deployed_forms)
        keys_here.update({entry["form"]["formKey"]: entry["form"]["formId"]
                          for entry in (body or {}).get("deployments", []) if entry.get("form")})
        found = [d for d in (body or {}).get("deployments", [])
                 if (d.get("processDefinition") or {}).get("processDefinitionId")]
        if not found:
            problems.append("%s deployed but created no process definition" % name)
            continue
        definition = found[0]["processDefinition"]["processDefinitionId"]

        status, body = api("/process-instances", {"processDefinitionId": definition})
        if status != 200 or not body:
            problems.append("%s deployed but would not start: %s" % (name, (body or {}).get("detail")))
            continue
        instance = body.get("processInstanceKey")

        task = None
        for _ in range(20):
            status, body = api("/user-tasks/search", {"filter": {"processInstanceKey": str(instance)}})
            items = (body or {}).get("items", [])
            task = next((i for i in items if i.get("state") == "CREATED"), None)
            if task:
                break
            time.sleep(0.5)
        if task is None:
            problems.append("%s started but reached no user task" % name)
        else:
            form_key = task.get("formKey")
            if not form_key:
                problems.append("%s: user task %s carries no form key"
                                % (name, task.get("elementId")))
            elif form_key not in keys_here:
                problems.append("%s: user task %s resolves form key %r, which is not one of the "
                                "deployed forms" % (name, task.get("elementId"), form_key))
            else:
                print("%-46s %s -> %s binds %s"
                      % (name, definition, task.get("elementId"), keys_here[form_key]))
        api("/process-instances/%s/cancellation" % instance, {})

    print()
    if problems:
        for problem in problems:
            print("  - %s" % problem)
        print("FAIL: %d problems" % len(problems))
        return 1
    print("PASS: every model deploys, starts, and reaches a task that binds a deployed form")
    return 0


if __name__ == "__main__":
    sys.exit(main())
