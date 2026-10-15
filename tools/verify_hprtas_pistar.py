# -*- coding: utf-8 -*-
"""Check the piStar form of the i* models: that every file still is what the
sources generate, and that the drawing inside it is sound.

Four things are checked, and each is a claim `models/socio-technical/pistar/README.md` makes:

1. **The files are the sources, converted.** Each model is rebuilt from
   `sd-hprtas.json` and `sr-hprtas.json` by the same code that wrote it, and
   compared with the file that is committed - the `saveDate` aside, which records
   when it was written. A source change that was not converted, or a file edited
   by hand, fails here. One file per drawing: one SD view per view the model
   defines and the union of them, one SR page per actor, the combined SR page
   that carries every actor at once, and the two overview pages - the SD model
   as its single drawing, and the activities.
2. **The drawing is sound.** Everything sits inside the declared canvas, no two
   dependums touch, no dependum sits on an actor, no actor sits on another - the
   same layout checks the converter makes before it writes.
3. **Every reference resolves.** A dependency joins two actors that are in the
   file; a link joins two cells that are in the file; an SR link joins two
   elements of its own page; a contribution carries a label piStar knows.
4. **What the README says is in the files.** Every gap element carries the tint
   and the `Gap` property the README describes, and every page whose contribution
   links piStar reports on load is named in the README, so a page that starts
   failing cannot go unrecorded.

Run it from anywhere:

    python tools\\verify_hprtas_pistar.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import convert_istar_to_pistar as C  # noqa: E402

REPO = C.REPO
SRC = os.path.join(REPO, "models", "socio-technical")
OUT = C.OUT

LINK_TYPES = ("DependencyLink", "AndRefinementLink", "OrRefinementLink",
              "NeededByLink", "ContributionLink")

problems = []
notes = []


def fail(message):
    problems.append(message)


def strip_save_date(model):
    """The model as it should compare: everything but when it was written."""
    model = dict(model)
    model.pop("saveDate", None)
    return model


def build(sd, sr):
    """Every file the converter writes, in memory: name -> model."""
    files = {}
    for view in sd["views"]:
        files["sd-%s.json" % view["id"]] = C.sd_view_model(
            sd, view.get("stages", []), "HPRTAS SD - %s" % view["title"],
            view.get("note", ""), hub=view.get("hub"))
    all_stages = sorted({d["stage"] for d in sd["dependencies"] if d.get("stage")})
    files["sd-all.json"] = C.sd_view_model(
        sd, all_stages, "HPRTAS SD - every dependency (the whole model)",
        "Every actor and every dependency of the SD model, on one canvas.")
    for actor in sr["actors"]:
        files["sr-%s.json" % actor["actor"]] = C.sr_page_model(actor, sd)
    files["sr-all.json"] = C.sr_all_model(
        sd, sr, "HPRTAS SR - every actor's rationale, and every dependency",
        "The whole Strategic Rationale view on one canvas: every actor's "
        "elements and links, with the dependencies drawn round them.")
    files["sd-overview.json"] = C.sd_view_model(
        sd, [], "HPRTAS SD - the referral pathway",
        "The whole SD model on one canvas: four actors and the six "
        "dependencies between them.",
        dep_ids=[d["id"] for d in sd["dependencies"]])
    files["sr-overview.json"] = C.sr_overview_model(
        sd, sr, "HPRTAS SR - every actor's goals and tasks (overview)",
        "Each actor's goals and the tasks it carries out about them, "
        "with the links between them. The complete rationale is the "
        "per-actor pages and the combined page.")
    return files


# ------------------------------------------------------------------ 1. one to one
def check_files(built):
    committed = sorted(f for f in os.listdir(OUT) if f.endswith(".json"))
    for name in sorted(built):
        path = os.path.join(OUT, name)
        if not os.path.exists(path):
            fail("%s is generated but not committed" % name)
            continue
        with open(path, encoding="utf-8") as handle:
            saved = json.load(handle)
        if strip_save_date(saved) != strip_save_date(built[name]):
            fail("%s differs from what the sources generate - re-run "
                 "convert_istar_to_pistar.py" % name)
    for name in committed:
        if name not in built:
            fail("%s is committed but no longer generated" % name)
    notes.append("%d file(s) match the sources they are generated from" % len(built))


# ------------------------------------------------------------- 2. the layout
def check_layout(built):
    dependums = elements = 0
    for name, model in sorted(built.items()):
        label = name[:-5]
        if any(a["nodes"] for a in model["actors"]):
            elements += C.check_sr_all(model, label, problems)
        elif model["dependencies"]:
            dependums += C.check_sd(model, label, problems)
        else:
            elements += C.check_sr(model, label, problems)
        C.check_canvas(model, label, problems)
    notes.append("%d dependums and %d rationale elements laid out without contact"
                 % (dependums, elements))


# --------------------------------------------------------- 3. what it refers to
def check_references(built, sr):
    for name, model in sorted(built.items()):
        label = name[:-5]
        actors = {a["id"] for a in model["actors"]}
        ids = set(actors)
        for actor in model["actors"]:
            if actor["type"] not in ("istar.Actor", "istar.Agent", "istar.Role"):
                fail("%s: %s is not a piStar actor type" % (label, actor["type"]))
            for node in actor["nodes"]:
                if node["id"] in ids:
                    fail("%s: duplicate cell id %s" % (label, node["id"]))
                ids.add(node["id"])
                if node["type"] not in ("istar.Goal", "istar.Task",
                                        "istar.Resource", "istar.Quality"):
                    fail("%s: %s is not a piStar node type" % (label, node["type"]))
                if not node["id"].startswith(actor["id"] + "-"):
                    fail("%s: %s is not named after its actor" % (label, node["id"]))
        for dep in model["dependencies"]:
            if dep["id"] in ids:
                fail("%s: duplicate cell id %s" % (label, dep["id"]))
            ids.add(dep["id"])
            for side in ("source", "target"):
                if dep[side] not in actors:
                    fail("%s: %s %s is not an actor in the file" % (label, dep["id"], side))
            if dep["type"] not in ("istar.Goal", "istar.Task",
                                   "istar.Resource", "istar.Quality"):
                fail("%s: %s is not a piStar dependum type" % (label, dep["type"]))
        linked = {dep["id"]: 0 for dep in model["dependencies"]}
        for link in model["links"]:
            if link["type"] not in ("istar." + t for t in LINK_TYPES):
                fail("%s: %s is not a piStar link type" % (label, link["type"]))
            for side in ("source", "target"):
                if link[side] not in ids:
                    fail("%s: link %s %s is not a cell in the file"
                         % (label, link["id"], side))
            if link["type"] == "istar.ContributionLink":
                if link.get("label") not in C.PISTAR_CONTRIBUTIONS:
                    fail("%s: %s has no label piStar knows" % (label, link["id"]))
            elif "label" in link:
                fail("%s: %s carries a label its type does not take" % (label, link["id"]))
            if link["type"] == "istar.DependencyLink":
                for side in ("source", "target"):
                    if link[side] in linked:
                        linked[link[side]] += 1
        for dep_id, halves in linked.items():
            if halves != 2:
                fail("%s: %s is drawn with %d dependency line(s), not 2" % (label, dep_id, halves))
        if model["orphans"]:
            fail("%s: the file has orphan elements" % label)
        if (any(a["nodes"] for a in model["actors"])
                and len(model["actors"]) not in (1, len(sr["actors"]))):
            fail("%s: an SR page carries %d actors - neither one nor all %d"
                 % (label, len(model["actors"]), len(sr["actors"])))
        if model["dependencies"] and any(a["nodes"] for a in model["actors"]):
            if len(model["actors"]) != len(sr["actors"]):
                fail("%s: the combined page carries %d actors, not %d"
                     % (label, len(model["actors"]), len(sr["actors"])))
    notes.append("every dependency, dependum, element and link resolves inside its file")


# -------------------------------------------------- 4. what the README claims
def check_claims(sd, sr, built, rewritten):
    gaps = [(actor["actor"], el["id"])
            for actor in sr["actors"] for el in actor["elements"] if el.get("gap")]
    for actor_id, el_id in gaps:
        cell_id = "%s-%s" % (actor_id, el_id)
        for name, model in built.items():
            cells = [n for a in model["actors"] for n in a["nodes"] if n["id"] == cell_id]
            if not cells:
                continue
            cell = cells[0]
            if model["display"].get(cell_id, {}).get("backgroundColor") != C.GAP_COLOUR:
                fail("%s is a gap element and is not tinted" % cell_id)
            if "Gap" not in cell.get("customProperties", {}):
                fail("%s is a gap element with no Gap property" % cell_id)
    notes.append("%d gap element(s) carry the tint and the Gap property" % len(gaps))

    readme = os.path.join(OUT, "README.md")
    if not os.path.exists(readme):
        fail("README.md is missing, so nothing about these files is written up")
        return
    text = open(readme, encoding="utf-8").read()
    pages = sorted({entry[0] for entry in rewritten})
    for page in pages:
        if page not in text:
            fail("sr-%s.json has a contribution link that had to be rewritten and the page is "
                 "not named in README.md" % page)
    if rewritten:
        notes.append("%d contribution link(s) on %d page(s) written as means-end because iStar 2.0 "
                     "allows a contribution only into a quality" % (len(rewritten), len(pages)))
    else:
        notes.append("no contribution link outside a quality, so nothing piStar reports on load")


def check_exports(built):
    """Every drawing has the PNG that was exported from the tool beside it."""
    exports = os.path.normpath(os.path.join(SRC, "..", "exports"))
    for name in sorted(built):
        png = os.path.join(exports, "pistar-" + name[:-5] + ".png")
        if not os.path.exists(png):
            fail("%s has no exported PNG in ../exports/" % name)
            continue
        with open(png, "rb") as handle:
            if handle.read(8) != b"\x89PNG\r\n\x1a\n":
                fail("%s is not a PNG" % os.path.basename(png))
    notes.append("%d drawing(s) have their exported PNG beside the other exports" % len(built))


def main():
    with open(os.path.join(SRC, "sd-hprtas.json"), encoding="utf-8") as handle:
        sd = json.load(handle)
    with open(os.path.join(SRC, "sr-hprtas.json"), encoding="utf-8") as handle:
        sr = json.load(handle)

    del C.REWRITTEN[:]                   # filled by the rebuild below
    built = build(sd, sr)
    rewritten = list(C.REWRITTEN)

    check_files(built)
    check_layout(built)
    check_references(built, sr)
    check_claims(sd, sr, built, rewritten)
    check_exports(built)

    for note in notes:
        print("  %s" % note)
    if problems:
        print("\n%d problem(s):" % len(problems))
        for problem in problems[:60]:
            print("  - %s" % problem)
        if len(problems) > 60:
            print("  ... and %d more" % (len(problems) - 60))
        return 1
    print("\nVERIFICATION PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
