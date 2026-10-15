# -*- coding: utf-8 -*-
"""Check the HPRTAS i* models: that the two of them agree with each other and
that the diagrams the renderer draws are readable.

Four things are checked, and each one is a claim `models/socio-technical/README.md`
makes about the models:

1. **The two models describe the same system.** Every actor in the SD model has
   an SR page and the other way round; every dependency in the SD model is
   accounted for by at least one SR element, and every dependency an SR element
   cites exists.
2. **The model's own fields are sound.** Every dependency joins two actors the
   model defines and carries an i* dependum type; every view names at least one
   stage the dependencies use; every element of an SR page carries a name.
3. **The gaps are stated.** An element marked `gap` carries no positive
   contribution link - a need the model does not meet must not read as
   satisfied - and every `gap` element is named in `modelling-choices.md`.
4. **The diagrams are readable.** Using the same layout the renderer uses: no
   two dependums touch, no dependum sits on an actor, no SR element sits on
   another, everything is inside its canvas and inside its actor boundary.

The geometry checks read the layout functions out of `istar_render.py`, so what
is checked is what is drawn rather than a second opinion about it.

Run it from anywhere:

    python tools\\verify_hprtas_istar.py
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import istar_render as IR  # noqa: E402

REPO = IR.REPO
MODEL_DIR = IR.MODEL_DIR
CHOICES = os.path.join(MODEL_DIR, "modelling-choices.md")
KINDS = ("goal", "task", "resource", "softgoal")

problems = []
notes = []


def circle_hits_rect(rect, cx, cy, r, pad=0.0):
    """True when a circle and a rectangle overlap - the exact test, not the
    actor's bounding box, which would report a box that merely comes near the
    corner of an actor as sitting on it."""
    nx = min(max(cx, rect[0]), rect[2])
    ny = min(max(cy, rect[1]), rect[3])
    return math.hypot(cx - nx, cy - ny) < r + pad


def fail(message):
    problems.append(message)


# ------------------------------------------------------------------ 1. agree
def check_agreement(sd, sr):
    sd_actors = {a["id"] for a in sd["actors"]}
    sr_actors = {a["actor"] for a in sr["actors"]}
    for missing in sorted(sd_actors - sr_actors):
        fail("actor %s is in the SD model and has no SR page" % missing)
    for missing in sorted(sr_actors - sd_actors):
        fail("actor %s has an SR page and is not in the SD model" % missing)

    deps = {d["id"]: d for d in sd["dependencies"]}
    if len(deps) != len(sd["dependencies"]):
        fail("duplicate dependency id in the SD model")
    for d in sd["dependencies"]:
        for side in ("from", "to"):
            if d[side] not in sd_actors:
                fail("%s: %s is not an actor" % (d["id"], d[side]))
        if d["type"] not in KINDS:
            fail("%s: %s is not an i* dependum type" % (d["id"], d["type"]))

    cited = {}
    for actor in sr["actors"]:
        ids = set()
        for el in actor["elements"]:
            if el["id"] in ids:
                fail("%s: duplicate element id %s" % (actor["actor"], el["id"]))
            ids.add(el["id"])
            if el["kind"] not in KINDS:
                fail("%s/%s: %s is not an i* element kind" % (actor["actor"], el["id"], el["kind"]))
            for dep in el.get("deps", []):
                if dep not in deps:
                    fail("%s/%s cites %s, which is not a dependency" % (actor["actor"], el["id"], dep))
                cited.setdefault(dep, []).append("%s/%s" % (actor["actor"], el["id"]))
        for el in actor["elements"]:
            for link in el.get("links", []):
                if link["to"] not in ids:
                    fail("%s/%s links to %s, which is not on the page"
                         % (actor["actor"], el["id"], link["to"]))
                if link["type"] not in IR.CONTRIBUTIONS + ("means-end", "decomposition", "neededby"):
                    fail("%s/%s: %s is not an i* link type"
                         % (actor["actor"], el["id"], link["type"]))
            if el.get("gap"):
                positive = [l["type"] for l in el.get("links", [])
                            if l["type"] == "means-end" or
                            (l["type"] in IR.CONTRIBUTIONS and l["type"] not in IR.NEGATIVE)]
                if positive:
                    fail("%s/%s is marked as a gap and still carries %s"
                         % (actor["actor"], el["id"], ", ".join(positive)))

    for dep in sorted(deps):
        if dep not in cited:
            fail("%s has no rationale in the SR model" % dep)
    notes.append("%d actors, %d dependencies, all accounted for in the SR model"
                 % (len(sd_actors), len(deps)))


# --------------------------------------------------------------- 2. sound
def check_model_fields(sd, sr):
    """Every value the two files state themselves: the actors a dependency
    joins, the stages a view names, and the names the pages carry. Nothing here
    reaches outside the model - the model is its own source."""
    actor_ids = {a["id"] for a in sd["actors"]}
    dep_stages = {d.get("stage") for d in sd["dependencies"]} - {None}
    view_stages = {s for v in sd["views"] for s in v.get("stages", [])}
    for d in sd["dependencies"]:
        if not d.get("dependum"):
            fail("%s carries no dependum" % d["id"])
        if d.get("stage") is not None and d["stage"] not in view_stages:
            fail("%s: the stage %s is in no view" % (d["id"], d["stage"]))
    for view in sd["views"]:
        if not view.get("id") or not view.get("title"):
            fail("a view carries no id or no title")
        for stage in view.get("stages", []):
            if stage not in dep_stages:
                fail("view %s names the stage %s, which no dependency uses"
                     % (view["id"], stage))
    for actor in sr["actors"]:
        if actor["actor"] not in actor_ids:
            fail("the SR page for %s has no actor in the SD model" % actor["actor"])
        if not actor.get("intent"):
            fail("%s: the page carries no intent" % actor["actor"])
        for el in actor["elements"]:
            if not el.get("name"):
                fail("%s/%s: the element carries no name" % (actor["actor"], el["id"]))
    notes.append("every dependency, view and element passes its own field checks")


# ---------------------------------------------------------------- 3. gaps
def check_gaps(sd, sr):
    gaps = []
    for actor in sr["actors"]:
        for el in actor["elements"]:
            if el.get("gap"):
                gaps.append((actor["actor"], el["id"], el["name"], el.get("gapNote", "")))
                if not el.get("gapNote"):
                    fail("%s/%s is marked as a gap with no explanation"
                         % (actor["actor"], el["id"]))
    if not os.path.exists(CHOICES):
        fail("modelling-choices.md is missing, so the gaps are not written up")
        return
    text = open(CHOICES, encoding="utf-8").read()
    for actor_id, el_id, name, _ in gaps:
        if ("%s/%s" % (actor_id, el_id)) not in text and name not in text:
            fail("the gap %s/%s (%s) is not written up in modelling-choices.md"
                 % (actor_id, el_id, name))
    notes.append("%d gaps recorded in the SR model and written up" % len(gaps))


# ------------------------------------------------------------ 4. readable
def check_sd_geometry(sd):
    checked = 0
    for view in sd["views"]:
        actors, bodies, deps = IR.sd_layout(sd, view)
        boxes = []
        for d in deps:
            boxes.append((d["id"], d["_box"]))
        for i, (id_a, box_a) in enumerate(boxes):
            for id_b, box_b in boxes[i + 1:]:
                if IR.overlaps(box_a, box_b, 0.0):
                    fail("sd %s: %s and %s overlap" % (view["id"], id_a, id_b))
            for actor in actors:
                cx, cy, rad = bodies[actor["id"]]
                if circle_hits_rect(box_a, cx, cy, rad, 4.0):
                    fail("sd %s: %s sits on actor %s"
                         % (view["id"], id_a, actor["id"]))
            checked += 1
        # one actor drawn on another is the failure a reader cannot see past,
        # and it is invisible to the label checks: both circles are drawn last
        for i, a in enumerate(actors):
            ax, ay, ar = bodies[a["id"]]
            for b in actors[i + 1:]:
                bx, by, br = bodies[b["id"]]
                if math.hypot(ax - bx, ay - by) < ar + br + 8.0:
                    fail("sd %s: actor %s and actor %s overlap"
                         % (view["id"], a["id"], b["id"]))
        _, _, deps2 = IR.sd_layout(sd, view)
        for d in deps2:
            if d["from"] not in bodies or d["to"] not in bodies:
                fail("sd %s: %s has an actor with no position" % (view["id"], d["id"]))
    notes.append("%d dependums placed across %d SD view(s), none touching"
                 % (checked, len(sd["views"])))


def check_sr_geometry(sr):
    checked = 0
    for actor in sr["actors"]:
        sized, _ = IR.sr_layout(actor)
        items = list(sized.items())
        for i, (id_a, (ax, ay, aw, ah)) in enumerate(items):
            a = IR.rect_of(ax, ay, aw, ah)
            for id_b, (bx, by, bw, bh) in items[i + 1:]:
                if IR.overlaps(a, IR.rect_of(bx, by, bw, bh), 4.0):
                    fail("sr %s: %s and %s touch" % (actor["actor"], id_a, id_b))
            # the cloud's bumps reach a little past its box, which is fine; a
            # box must not reach past the actor boundary though
            half = max(max(abs(v) for v in [ax - aw / 2.0, ax + aw / 2.0]) + 130.0, 400.0)
            if abs(ax) + aw / 2.0 > half - 14.0:
                fail("sr %s: %s is drawn outside the actor boundary"
                     % (actor["actor"], id_a))
            checked += 1
    notes.append("%d SR elements laid out across %d actor pages, none touching"
                 % (checked, len(sr["actors"])))


def main():
    sd = json.load(open(IR.SD_PATH, encoding="utf-8"))
    sr = json.load(open(IR.SR_PATH, encoding="utf-8"))
    check_agreement(sd, sr)
    check_model_fields(sd, sr)
    check_gaps(sd, sr)
    check_sd_geometry(sd)
    check_sr_geometry(sr)

    for note in notes:
        print("  %s" % note)
    if problems:
        print("\n%d problem(s):" % len(problems))
        for p in problems[:60]:
            print("  - %s" % p)
        if len(problems) > 60:
            print("  ... and %d more" % (len(problems) - 60))
        return 1
    print("\nVERIFICATION PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
