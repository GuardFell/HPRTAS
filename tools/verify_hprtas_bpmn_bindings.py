# -*- coding: utf-8 -*-
"""Checks that the models, the workers and the forms agree with each other.

This is the static half of what `mvn test -Pengine` proves by running: it needs
no engine, and it catches the disagreements that only show up at run time.

  1  every service task names a job type a worker is registered for, and every
     worker that is configured is used by a model
  2  every user task is a Camunda user task with a form, and every form key
     resolves to a `.form` file that declares the same id
  3  every form file is bound to a user task, so no form is left unused
  4  a form's field keys are the process variables: single words, no duplicates,
     and each one is a variable the process actually uses
  5  the variable contract closes. Every variable a gateway branches on, and
     every variable a worker reads as required input, is written by a form field
     in the same model or returned by a worker the model uses
  6  a candidate group on a user task is a group the case study knows, and a
     lane carries the same group wherever it appears
  7  every error code a worker can raise has a catch event on the service task
     that runs it, so the worker's business error is a handled outcome and not an
     incident (`DEF-12` was the case that had no such check)

The workers' side of the contract is read from the worker sources themselves -
`INPUT_VARIABLES` and the keys of the `Vars.of(...)` they return - so the
configuration cannot drift from the code without this failing.

Run: python tools\\verify_hprtas_bpmn_bindings.py
"""
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# the repository root: <repo>/tools/<script>.py, so the tools run from any checkout
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(REPO, "models", "operational")
FORM_DIR = os.path.join(REPO, "forms")
WORKER_DIR = os.path.join(REPO, "workers", "src", "main", "java")
CONFIG = os.path.join(REPO, "workers", "config", "workers.default.json")

BPMN = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
ZEBBE = "{http://camunda.org/schema/zeebe/1.0}"

# the candidate groups the models assign to, one per lane of the case study's
# role table, spelled the same way wherever the lane appears
GROUPS = {"consultants", "medical-secretaries", "outpatient-bookings",
          "pathway-coordinators", "administrative-management", "clinical-nurse-specialists",
          "finance", "clinical-professionals", "call-handling", "treatment-bookings"}

# Variables the engine or the process shape supplies rather than a form or a
# worker. Anything else has to be produced.
ENGINE_VARIABLES = {"businessKey", "processInstanceKey", "elementId"}


def split_args(text):
    """Split a Java argument list on its top-level commas."""
    args, depth, current, in_string = [], 0, "", False
    i = 0
    while i < len(text):
        ch = text[i]
        if in_string:
            current += ch
            if ch == "\\":
                current += text[i + 1]
                i += 1
            elif ch == '"':
                in_string = False
        elif ch == '"':
            in_string = True
            current += ch
        elif ch in "([{":
            depth += 1
            current += ch
        elif ch in ")]}":
            depth -= 1
            current += ch
        elif ch == "," and depth == 0:
            args.append(current.strip())
            current = ""
        else:
            current += ch
        i += 1
    if current.strip():
        args.append(current.strip())
    return args


def call_arguments(source, start):
    """The text inside the parentheses of a call whose '(' is at `start`."""
    depth = 0
    for i in range(start, len(source)):
        if source[i] == "(":
            depth += 1
        elif source[i] == ")":
            depth -= 1
            if depth == 0:
                return source[start + 1:i]
    return ""


def string_literal(argument):
    m = re.match(r'^"([^"]*)"$', argument.strip())
    return m.group(1) if m else None


def worker_contract():
    """worker name -> dict(job_type, required inputs, optional inputs, outputs, errors)."""
    workers = {}
    for path in sorted(glob.glob(os.path.join(WORKER_DIR, "**", "*.java"), recursive=True)):
        source = open(path, encoding="utf-8").read()
        if "WorkerModule" not in source:
            continue
        name = re.search(r'public String name\(\)\s*\{\s*return\s+"([^"]+)"', source)
        job = re.search(r'public String taskType\(\)\s*\{\s*return\s+"([^"]+)"', source)
        if not name or not job:
            continue
        required, optional = [], []
        for m in re.finditer(r"FieldSpec\.order\s*\(", source):
            args = split_args(call_arguments(source, m.end() - 1))
            for i in range(0, len(args) - 1, 2):
                key = string_literal(args[i])
                spec = args[i + 1]
                if key is None:
                    continue
                (required if ".required()" in spec else optional).append(key)
        outputs = []
        for m in re.finditer(r"Vars\.of\s*\(", source):
            args = split_args(call_arguments(source, m.end() - 1))
            for i in range(0, len(args), 2):
                key = string_literal(args[i])
                if key is not None and key not in outputs:
                    outputs.append(key)
        # the codes this worker throws: a task that can raise a code with no catch
        # event on it turns the worker's business error into an incident
        errors = []
        for m in re.finditer(r"ErrorCode\.([A-Z][A-Z0-9_]*)", source):
            if m.group(1) not in errors:
                errors.append(m.group(1))
        workers[name.group(1)] = {"file": os.path.basename(path), "job_type": job.group(1),
                                  "required": required, "optional": optional,
                                  "outputs": outputs, "errors": errors}
    return workers


def model_facts(path):
    root = ET.parse(path).getroot()
    process = root.find(BPMN + "process")
    facts = {"path": path, "name": os.path.basename(path), "process": process,
             "service_tasks": [], "user_tasks": [], "condition_variables": set(),
             "groups": set(), "default_flows": set(), "lane_group": {}}
    lane_of = {}
    for lane in process.iter(BPMN + "lane"):
        for ref in lane.findall(BPMN + "flowNodeRef"):
            lane_of[ref.text] = lane.get("name") or lane.get("id")
    for el in process.iter():
        tag = el.tag
        if tag == BPMN + "serviceTask":
            definition = el.find(".//" + ZEBBE + "taskDefinition")
            facts["service_tasks"].append(
                {"id": el.get("id"), "name": el.get("name") or "",
                 "job_type": definition.get("type") if definition is not None else None,
                 "has_user_task": el.find(".//" + ZEBBE + "userTask") is not None})
        elif tag == BPMN + "userTask":
            form = el.find(".//" + ZEBBE + "formDefinition")
            assignment = el.find(".//" + ZEBBE + "assignmentDefinition")
            groups = (assignment.get("candidateGroups") or "") if assignment is not None else ""
            for group in filter(None, (g.strip() for g in groups.split(","))):
                facts["groups"].add(group)
                facts["lane_group"].setdefault(lane_of.get(el.get("id"), "?"), set()).add(group)
            facts["user_tasks"].append(
                {"id": el.get("id"), "name": el.get("name") or "",
                 "form": form.get("formId") if form is not None else None,
                 "zeebe_user_task": el.find(".//" + ZEBBE + "userTask") is not None})
        elif tag in (BPMN + "exclusiveGateway", BPMN + "inclusiveGateway"):
            default = el.get("default")
            if default:
                facts["default_flows"].add(default)
    for flow in process.iter(BPMN + "sequenceFlow"):
        condition = flow.find(BPMN + "conditionExpression")
        if condition is None or not condition.text:
            continue
        if flow.get("id") in facts["default_flows"]:
            continue
        # a quoted value in a FEEL comparison is a literal, not a variable
        expression = re.sub(r'"[^"]*"', " ", condition.text)
        facts["condition_variables"].update(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", expression))
    # = is FEEL assignment, and/or/not/true/false/null/duration keywords are not variables
    keywords = {"true", "false", "null", "and", "or", "not", "if", "then", "else",
                "some", "every", "satisfies", "in", "between", "instance", "of",
                "return", "for", "P", "PT", "D", "H", "M"}
    facts["condition_variables"] -= keywords
    # the error codes each service task catches: a worker's business error becomes an
    # incident on the task unless a catch event there names the code
    codes = {e.get("id"): e.get("errorCode") for e in root.iter(BPMN + "error")}
    facts["error_catch"] = {}
    for boundary in process.iter(BPMN + "boundaryEvent"):
        definition = boundary.find(BPMN + "errorEventDefinition")
        if definition is None:
            continue
        code = codes.get(definition.get("errorRef"))
        if code:
            facts["error_catch"].setdefault(boundary.get("attachedToRef"), set()).add(code)
    return facts


def form_facts():
    forms = {}
    for path in sorted(glob.glob(os.path.join(FORM_DIR, "*.form"))):
        data = json.load(open(path, encoding="utf-8"))
        keys, problems = [], []

        def walk(components):
            for component in components:
                if component.get("path"):
                    problems.append("%s: component %r carries a path, which nests its fields"
                                    % (os.path.basename(path), component.get("label")))
                key = component.get("key")
                if key:
                    keys.append(key)
                    if not re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", key):
                        problems.append("%s: field key %r is not a single-word variable"
                                        % (os.path.basename(path), key))
                if component.get("components"):
                    walk(component["components"])
                elif component.get("type") == "group":
                    problems.append("%s: group %r has no components, so it renders empty"
                                    % (os.path.basename(path), component.get("label")))

        walk(data.get("components", []))
        duplicates = {k for k in keys if keys.count(k) > 1}
        for key in sorted(duplicates):
            problems.append("%s: field key %r appears %d times" % (os.path.basename(path), key, keys.count(key)))
        forms[os.path.basename(path)[:-len(".form")]] = {
            "path": path, "id": data.get("id"), "keys": keys, "problems": problems}
    return forms


def main():
    problems = []
    workers = worker_contract()
    configured = json.load(open(CONFIG, encoding="utf-8"))["workers"]
    job_types = {w["job_type"]: name for name, w in workers.items()}

    # the configuration and the code have to agree before anything is checked against them
    for name, settings in configured.items():
        if name not in workers:
            problems.append("config/workers.default.json registers %r, which no worker implements" % name)
        elif workers[name]["job_type"] != settings["taskType"]:
            problems.append("%s is configured as %r but implements %r"
                            % (name, settings["taskType"], workers[name]["job_type"]))
    for name in workers:
        if name not in configured:
            problems.append("worker %r is not in config/workers.default.json" % name)

    forms = form_facts()
    for name, form in forms.items():
        problems += form["problems"]
        if form["id"] != name:
            problems.append("%s.form declares id %r" % (name, form["id"]))

    used_job_types, used_forms = set(), set()
    lane_groups = {}
    produced_by_workers = set()
    for worker in workers.values():
        produced_by_workers.update(worker["outputs"])

    for path in sorted(glob.glob(os.path.join(MODEL_DIR, "*.bpmn"))):
        facts = model_facts(path)
        model = facts["name"]
        model_forms = set()
        model_workers = set()
        for task in facts["service_tasks"]:
            if task["has_user_task"]:
                problems.append("%s: service task %s carries the Camunda user task marker, so "
                                "Tasklist would show an automated step" % (model, task["id"]))
            if not task["job_type"]:
                problems.append("%s: service task %s has no job type" % (model, task["id"]))
            elif task["job_type"] not in job_types:
                problems.append("%s: service task %s runs job type %r, which no worker registers"
                                % (model, task["id"], task["job_type"]))
            else:
                used_job_types.add(task["job_type"])
                model_workers.add(job_types[task["job_type"]])
                worker_name = job_types[task["job_type"]]
                caught = facts["error_catch"].get(task["id"], set())
                for code in workers[worker_name]["errors"]:
                    if code not in caught:
                        problems.append(
                            "%s: service task %s runs worker %s, which can raise %s, but the task "
                            "catches only %s, so that error becomes an incident instead of a "
                            "handled outcome"
                            % (model, task["id"], worker_name, code,
                               ", ".join(sorted(caught)) or "nothing"))
        for task in facts["user_tasks"]:
            if not task["zeebe_user_task"]:
                problems.append("%s: user task %s is not a Camunda user task, so it never appears "
                                "in Tasklist" % (model, task["id"]))
            if not task["form"]:
                problems.append("%s: user task %s binds no form" % (model, task["id"]))
                continue
            model_forms.add(task["form"])
            if task["form"] not in forms:
                problems.append("%s: user task %s binds form %r, which is not in forms/"
                                % (model, task["id"], task["form"]))
            else:
                used_forms.add(task["form"])
                if not forms[task["form"]]["keys"]:
                    problems.append("%s: form %s carries no field" % (model, task["form"]))
        for group in sorted(facts["groups"]):
            if group not in GROUPS:
                problems.append("%s: candidate group %r is not a role the case study names"
                                % (model, group))
        for lane, groups in facts["lane_group"].items():
            if len(groups) > 1:
                problems.append("%s: lane %r hands its tasks to more than one group: %s"
                                % (model, lane, ", ".join(sorted(groups))))
            for group in groups:
                lane_groups.setdefault(lane, set()).add(group)

        # what this model reads has to be written in this model: a form bound
        # here, or a worker this model runs
        produced_here = set(_worker_outputs(model_workers, workers))
        for form in model_forms:
            if form in forms:
                produced_here = produced_here | set(forms[form]["keys"])
        for variable in sorted(facts["condition_variables"]):
            if variable in ENGINE_VARIABLES or variable in produced_here:
                continue
            problems.append("%s: gateway reads %r, which no form in this model writes and no "
                            "worker it runs returns" % (model, variable))
        for worker_name in sorted(model_workers):
            for variable in workers[worker_name]["required"]:
                if variable in produced_here:
                    continue
                problems.append("%s: worker %s needs %r, which no form in this model writes and no "
                                "worker it runs returns" % (model, worker_name, variable))

    for lane, groups in sorted(lane_groups.items()):
        if len(groups) > 1:
            problems.append("lane %r is given the group %s in different models"
                            % (lane, " and ".join(sorted(groups))))

    for job_type in sorted(set(job_types) - used_job_types):
        problems.append("worker %r (job type %s) is registered but no model uses it"
                        % (job_types[job_type], job_type))
    for name in sorted(set(forms) - used_forms):
        problems.append("form %s is bound to no user task" % name)

    print("%d operational models, %d forms, %d workers" % (
        len(glob.glob(os.path.join(MODEL_DIR, "*.bpmn"))), len(forms), len(workers)))
    print()
    if problems:
        for problem in problems:
            print("  - %s" % problem)
        print()
        print("FAIL: %d problems" % len(problems))
        return 1
    print("PASS: the models, the forms and the workers agree")
    return 0


def _worker_outputs(names, workers):
    outputs = set()
    for name in names:
        outputs.update(workers[name]["outputs"])
    return outputs


if __name__ == "__main__":
    sys.exit(main())
