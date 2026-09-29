# -*- coding: utf-8 -*-
"""Checks the forms bundled by operational model in `forms/form/`.

`forms/form/core-N/` holds a copy of every Camunda Form that model's user tasks
bind, so a demonstration can deploy one model with the forms it resolves without
picking them out of the full set. The copies are the demonstration's, not the
originals: `forms/*.form` is the set of record, and `forms/form/core-N/<id>.form`
has to be byte-identical to `forms/<id>.form` or the bundle has drifted from it.

This re-reads the five models and reports:

  1  a form a model binds that is missing from that model's folder
  2  a `.form` file in a folder that the model does not bind
  3  a copy that is not the same as the original of the same name
  4  a file in a folder that is neither a `.form` copy nor the folder's README

A copy is compared as content rather than as raw bytes, because `.gitattributes`
marks `*.form` as text: which line ending a checkout gives a file is the
checkout's business, and comparing the bytes would make this fail on a machine
that has `core.autocrlf` on. A copy whose bytes differ only in line endings is
reported as such and is not a problem. The committed blobs of the copies and the
originals are identical, which is what the bundle promises.

`forms/form/README.md` describes the bundle and the forms that appear in more
than one folder. `forms/by-core.md` documents the same arrangement without the
copies.

Run: python tools\\bundle_forms_by_core.py

It reports and changes nothing, and exits non-zero if the bundle does not match
the models. There is no mode that writes the copies: the bundle is complete, and
rewriting the files would only give them the line endings this checkout happens
to use, leaving `git status` reporting files that have not changed.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

# the repository root: <repo>/tools/<script>.py, so the tools run from any checkout
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(REPO, "models", "operational")
ORIGINAL_DIR = os.path.join(REPO, "forms")
BUNDLE_DIR = os.path.join(REPO, "forms", "form")

BPMN = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
ZEBBE = "{http://camunda.org/schema/zeebe/1.0}"

# the one file that is not a form copy and is expected to be there
ALLOWED_EXTRA = {"README.md"}


def core_of(model_path):
    """`core-1-referral-and-new-patient-appointment.bpmn` -> `core-1`."""
    return "-".join(os.path.basename(model_path).split("-")[:2])


def same_content(path, original):
    """(equal, equal_apart_from_line_endings) for two files."""
    a = open(path, "rb").read()
    b = open(original, "rb").read()
    if a == b:
        return True, False
    normalised = (a.replace(b"\r\n", b"\n") == b.replace(b"\r\n", b"\n"))
    return normalised, normalised


def bound_forms(model_path):
    """The form keys the model's user tasks bind, in the order they appear."""
    root = ET.parse(model_path).getroot()
    keys = []
    for task in root.iter(BPMN + "userTask"):
        definition = task.find(".//" + ZEBBE + "formDefinition")
        if definition is None:
            keys.append(None)
            continue
        keys.append(definition.get("formId"))
    return keys


def copies(core):
    """The `.form` files in one model's bundle folder."""
    folder = os.path.join(BUNDLE_DIR, core)
    if not os.path.isdir(folder):
        return None, []
    return folder, sorted(glob.glob(os.path.join(folder, "*.form")))


def check():
    problems = []
    total_copies = 0
    line_endings = 0
    models = sorted(glob.glob(os.path.join(MODEL_DIR, "*.bpmn")))
    print("%d operational models" % len(models))
    print()
    for model_path in models:
        core = core_of(model_path)
        keys = bound_forms(model_path)
        if any(k is None for k in keys):
            problems.append("%s: a user task binds no form" % core)
        bound = {k for k in keys if k}
        folder, found = copies(core)
        if folder is None:
            problems.append("%s: no folder at forms/form/%s" % (core, core))
            continue
        present = {os.path.splitext(os.path.basename(p))[0] for p in found}
        for name in sorted(bound - present):
            problems.append("%s: %s is bound by the model but not in forms/form/%s"
                            % (core, name, core))
        for name in sorted(present - bound):
            problems.append("%s: %s is in forms/form/%s but the model does not bind it"
                            % (core, name, core))
        for path in found:
            name = os.path.splitext(os.path.basename(path))[0]
            original = os.path.join(ORIGINAL_DIR, name + ".form")
            if not os.path.exists(original):
                problems.append("%s: %s has no original at forms/%s.form"
                                % (core, name, name))
                continue
            equal, only_endings = same_content(path, original)
            if not equal:
                problems.append("%s: %s is not the same as forms/%s.form"
                                % (core, name, name))
            elif only_endings:
                line_endings += 1
        for entry in sorted(os.listdir(folder)):
            if entry.endswith(".form") or entry in ALLOWED_EXTRA:
                continue
            problems.append("%s: %s is in forms/form/%s and is neither a form nor a README"
                            % (core, entry, core))
        total_copies += len(found)
        print("  %-8s %2d user tasks  %2d forms bound  %2d copies" % (
            core, len(keys), len(bound), len(found)))
    print()
    print("%d copies in %d folders" % (total_copies, len(models)))
    if line_endings:
        print("%d of them carry the line endings this checkout gave them, and match their"
              " original once those are set aside" % line_endings)
    print()
    if problems:
        for problem in problems:
            print("  - %s" % problem)
        print()
        print("FAIL: %d problems" % len(problems))
        return 1
    print("PASS: every copy matches the model that binds it and the original it was copied from")
    return 0


def main():
    args = sys.argv[1:]
    unknown = [a for a in args if a not in ("--check",)]
    if unknown:
        print("unknown argument(s): %s" % ", ".join(unknown))
        print(r"usage: python tools\bundle_forms_by_core.py [--check]")
        return 2
    return check()


if __name__ == "__main__":
    sys.exit(main())
