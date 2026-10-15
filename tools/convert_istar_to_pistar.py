# -*- coding: utf-8 -*-
"""Convert the i* models into piStar-tool JSON files.

Reads the two source models written by the group

    models/socio-technical/sd-hprtas.json   (4 actors, 6 dependencies)
    models/socio-technical/sr-hprtas.json   (one page per actor, 20 elements)

and writes, for the piStar tool (http://www.cin.ufpe.br/~jhcp/pistar/, version
2.1.0), one piStar JSON file per drawing - the same drawings `istar_render.py`
produces as the PDFs:

    models/socio-technical/pistar/sd-<view>.json    the SD views, one file each
    models/socio-technical/pistar/sd-all.json       the union of the views
    models/socio-technical/pistar/sr-<actor>.json   one SR page per actor
    models/socio-technical/pistar/sr-all.json       every actor on one canvas
    models/socio-technical/pistar/sd-overview.json  the SD model as one drawing
    models/socio-technical/pistar/sr-overview.json  every actor's goals and tasks

The piStar schema is the one documented in piStar's `docs/JSON.md` and produced
by piStar's own File > Save model; `models/socio-technical/pistar/README.md`
documents the mapping decisions (actor kinds, link types, link directions) and
what is lost. `verify_hprtas_pistar.py` checks the committed files against these
sources, so run it after changing either.

Usage:
    python tools/convert_istar_to_pistar.py
    python tools/convert_istar_to_pistar.py --check     # layout report only
    python tools/convert_istar_to_pistar.py --out DIR   # write the files somewhere else
"""
import argparse
import datetime
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import istar_geometry as G  # noqa: E402

# the names the rest of this file uses for the shared geometry
seg_hit_box = G.seg_hit_box
segments_cross = G.segments_cross
_box_center = G.box_center
_shift = G.shift_box

# the repository root, from this file's own location (<repo>/tools/<script>.py)
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "models", "socio-technical", "pistar")

PISTAR_TOOL = "pistar.2.1.0"

# --------------------------------------------------------------- the mapping
# i* element kind -> piStar node type. piStar draws quality as the cloud the
# group's soft goals use, so softgoal maps there and not to a goal.
KIND_TO_TYPE = {
    "goal": "Goal",
    "softgoal": "Quality",
    "task": "Task",
    "resource": "Resource",
}

# The group's actor kinds (a person the pathway runs between, a professional
# role whose holder answers the dependency, or the delivered system itself) ->
# the three iStar 2.0 actor kinds piStar knows. A role is a characterisation of
# responsibilities, an agent is a concrete party: the professional roles are
# roles, and the patients, the receptionists and the delivered system are
# concrete, so they are agents.
ACTOR_KIND_TO_TYPE = {
    "role": "Role",
    "system": "Agent",
    "external": "Agent",
    "agent": "Agent",
}

# The group's link types -> piStar link types. means-end is drawn with the
# arrowhead at the goal it serves, which is piStar's Or-refinement; a
# decomposition labelled AND is piStar's And-refinement. Contribution labels
# piStar does not know are mapped to the nearest one it does.
LINK_TO_TYPE = {
    "means-end": "OrRefinementLink",
    "decomposition": "AndRefinementLink",   # mode "or" -> OrRefinementLink
    "neededby": "NeededByLink",             # resource -> task, as piStar takes it
}
CONTRIBUTION_LABEL = {
    "make": "make",
    "help": "help",
    "hurt": "hurt",
    "break": "break",
    "some+": "help",
    "some-": "hurt",
    "unknown": "help",
    "equal": "help",
}
PISTAR_CONTRIBUTIONS = ("make", "help", "hurt", "break")

# iStar 2.0 (Guide, Table 1) - and piStar, which enforces it - allows a
# contribution link only into a Quality: a contribution into a hard goal makes
# the tool report "Invalid model" on load. A task that supports a hard goal is
# what the guide calls an OR-refinement - a way of fulfilling the goal - so any
# such link is written as an Or-refinement link and reported, rather than
# written as a link the tool rejects.
REWRITTEN = []

# --------------------------------------------------------------- text metrics
# piStar draws Arial 12px (bold for the element name) and wraps at the element
# width; these are the estimates the layout is computed from. Sizes are written
# into the file's display section, so what the layout measured is what piStar
# draws - the wrapping is only used to count lines.
CHAR_W = 6.7
LINE_H = 16.0
# kind -> (min width, max width, pad x, pad y, min height, max lines)
SIZING = {
    "goal":     (120, 300, 16, 16, 44, 4),
    "resource": (120, 300, 16, 16, 44, 4),
    "task":     (140, 330, 36, 12, 52, 4),
    "softgoal": (130, 300, 32, 20, 58, 4),
}

ACTOR_SIZE = 112.0        # what an actor's circle takes on the canvas
DEP_GAP_X = 44.0          # minimum clear space between two dependums
DEP_GAP_Y = 30.0


def wrap_lines(text, width, pad_x):
    """Greedy word wrap at `width`, counting the lines piStar would need."""
    usable = max(20.0, width - 2.0 * pad_x)
    per_line = max(1, int(usable / CHAR_W))
    lines, current = 0, ""
    for word in str(text).split():
        candidate = word if not current else current + " " + word
        if len(candidate) <= per_line:
            current = candidate
        else:
            lines += 1
            current = word
    if current:
        lines += 1
    return max(1, lines)


def node_size(text, kind):
    """The box an element is given, in piStar pixels.

    The smallest width between the kind's limits whose wrapped text stays within
    the line budget, and the height that holds those lines."""
    min_w, max_w, pad_x, pad_y, min_h, max_lines = SIZING[kind]
    width = min_w
    while width < max_w and wrap_lines(text, width, pad_x) > max_lines:
        width += 5.0
    lines = wrap_lines(text, width, pad_x)
    height = max(min_h, lines * LINE_H + 2.0 * pad_y)
    return round(width, 2), round(height, 2)


def rect(x, y, w, h):
    return (x, y, x + w, y + h)


def overlap(a, b, gap_x=DEP_GAP_X, gap_y=DEP_GAP_Y):
    return not (a[2] + gap_x <= b[0] or b[2] + gap_x <= a[0] or
                a[3] + gap_y <= b[1] or b[3] + gap_y <= a[1])


def push_apart(a, b, gap_x=DEP_GAP_X, gap_y=DEP_GAP_Y, move_a=True, move_b=True):
    """Move the two boxes the small amount that stops them overlapping."""
    acx, acy = (a[0] + a[2]) / 2.0, (a[1] + a[3]) / 2.0
    bcx, bcy = (b[0] + b[2]) / 2.0, (b[1] + b[3]) / 2.0
    dx = (a[2] - a[0] + b[2] - b[0]) / 2.0 + gap_x - abs(acx - bcx)
    dy = (a[3] - a[1] + b[3] - b[1]) / 2.0 + gap_y - abs(acy - bcy)
    if dx <= 0 or dy <= 0:
        return a, b
    if dx < dy:                                  # separate along the shallower axis
        shift = dx / (2.0 if (move_a and move_b) else 1.0)
        direction = 1.0 if acx <= bcx else -1.0
        if move_a:
            a = (a[0] - direction * shift, a[1], a[2] - direction * shift, a[3])
        if move_b:
            b = (b[0] + direction * shift, b[1], b[2] + direction * shift, b[3])
    else:
        shift = dy / (2.0 if (move_a and move_b) else 1.0)
        direction = 1.0 if acy <= bcy else -1.0
        if move_a:
            a = (a[0], a[1] - direction * shift, a[2], a[3] - direction * shift)
        if move_b:
            b = (b[0], b[1] + direction * shift, b[2], b[3] + direction * shift)
    return a, b


def circle_rect_overlap(box, cx, cy, r, pad=10.0):
    nx = min(max(cx, box[0]), box[2])
    ny = min(max(cy, box[1]), box[3])
    return math.hypot(cx - nx, cy - ny) < r + pad


# ------------------------------------------------------------------ SD layout
# An SD view is drawn as a hub with a ring of actors round it, the way the i*
# tutorials draw one: the actor the view's dependencies run through most often
# - or the one the view names as its hub, which is how the workshop model's
# drawing puts the patient at the centre - sits in the middle, the rest sit on
# one ring, and each dependum sits on the line between the two actors it joins,
# where a reader looks for it. The placement itself is in `istar_geometry.py`,
# because `istar_render.py` draws the same shape; this file supplies the sizes
# piStar draws with and the JSON they go into.
RING_BASE_R = G.RING_BASE_R


def place_sd_view(actors, deps, hub=None):
    """Where every actor and every dependum of one view goes, in piStar pixels."""
    ids = [a["id"] for a in actors]
    radius_of = {a["id"]: ACTOR_SIZE / 2.0 for a in actors}
    order, centers, boxes = G.place_ring_view(
        ids, deps,
        size_of=lambda d: node_size(d["dependum"], d["type"]),
        circle_of=lambda aid: radius_of[aid],
        gap_x=DEP_GAP_X, gap_y=DEP_GAP_Y, hub=hub)
    return order, centers, boxes


def sd_view_model(sd, view_ids, name, note, dep_ids=None, hub=None):
    """One piStar model: the actors of a set of views, and their dependums.

    `dep_ids` picks the dependencies by name instead of by stage, which is how
    the overview drawing keeps the basic pathway and leaves the rest to the
    complete views; `hub` names the actor the ring is drawn round."""
    if dep_ids is None:
        deps = G.view_dependencies(sd, {"stages": view_ids})
    else:
        deps = [d for d in sd["dependencies"] if d["id"] in dep_ids]
    used = []
    for d in deps:
        for side in ("from", "to"):
            if d[side] not in used:
                used.append(d[side])
    actors = [a for a in sd["actors"] if a["id"] in used]
    placed, centers, boxes = place_sd_view(actors, deps, hub=hub)
    rank = {aid: i for i, aid in enumerate(placed)}
    actors.sort(key=lambda a: rank[a["id"]])

    # --- assemble the piStar JSON
    model_actors, display = [], {}
    for a in actors:
        cx, cy = centers[a["id"]]
        x, y = cx - ACTOR_SIZE / 2.0, cy - ACTOR_SIZE / 2.0
        custom = {"Evidence": a.get("evidence", "")}
        if a.get("note"):
            custom["Note"] = a["note"]
        model_actors.append({
            "id": a["id"],
            "text": a["name"],
            "type": "istar." + ACTOR_KIND_TO_TYPE[a["kind"]],
            "x": round(x, 2), "y": round(y, 2),
            "nodes": [],
            "customProperties": custom,
        })
        # collapsed hides the actor boundary, so an SD view draws the plain
        # circles the notation uses; alt+click expands it in the tool
        display[a["id"]] = {"collapsed": True}

    model_deps, model_links = [], []
    for d in deps:
        box = boxes[d["id"]]
        w, h = box[2] - box[0], box[3] - box[1]
        custom = {"Evidence": d.get("evidence", "")}
        for key, label in (("rules", "Rules"), ("reqs", "Requirements"),
                           ("nfrs", "NFRs"), ("exceptions", "Exceptions")):
            if d.get(key):
                custom[label] = ", ".join(d[key])
        if d.get("note"):
            custom["Note"] = d["note"]
        model_deps.append({
            "id": d["id"],
            "text": d["dependum"],
            "type": "istar." + KIND_TO_TYPE[d["type"]],
            "x": round(box[0], 2), "y": round(box[1], 2),
            "source": d["from"],
            "target": d["to"],
            "customProperties": custom,
        })
        model_links.append({"id": d["id"] + "-depender", "type": "istar.DependencyLink",
                            "source": d["from"], "target": d["id"]})
        model_links.append({"id": d["id"] + "-dependee", "type": "istar.DependencyLink",
                            "source": d["id"], "target": d["to"]})
        display[d["id"]] = {"width": round(w, 2), "height": round(h, 2)}

    return assemble(actors=model_actors, dependencies=model_deps, links=model_links,
                    display=display, name=name, note=note, source="sd-hprtas.json")


# ------------------------------------------------------------------ SR layout
SR_GAP_X = 76.0           # clear space between two elements of a row
SR_GAP_Y = 160.0          # clear space between two rows - a link's label is
                          # drawn at its middle, and a contribution link runs
                          # from one row to the next
SR_ROW_ORDER = ("softgoal", "goal", "task", "resource")
# what the SR ordering is scored on, in the order it matters: a link drawn
# across an element, a label landing on one, two labels on top of each other,
# and two links crossing
SR_WEIGHTS = (15.0, 15.0, 10.0, 6.0)
# a gap element's tint - the source draws them dashed, piStar cannot dash a node
GAP_COLOUR = "#EFD9D9"


def row_order(actor):
    """The elements of each row, ordered to keep the links short (the ordering
    itself is in `istar_geometry.py`, shared with `istar_render.py`)."""
    return G.row_order(actor, neighbours=sr_neighbours(actor))


def sr_neighbours(actor):
    """Which element each element is linked to, from either end, for the
    ordering to pull linked elements together."""
    neighbours = {}
    for el in actor["elements"]:
        for link in el.get("links", []):
            neighbours.setdefault(el["id"], []).append(link["to"])
            neighbours.setdefault(link["to"], []).append(el["id"])
    return neighbours


def sr_links(actor):
    """Every link the page draws, once each: a relation the sources record from
    both ends is one line, so it is one pair here."""
    ids = {el["id"] for el in actor["elements"]}
    pairs, seen = [], set()
    for el in actor["elements"]:
        for link in el.get("links", []):
            if link["to"] not in ids:
                continue
            key = frozenset((el["id"], link["to"]))
            if key in seen:
                continue
            seen.add(key)
            pairs.append((el["id"], link["to"]))
    return pairs


def clip_to_rect(inner, target):
    """Where the line from the centre of `inner` to the centre of `target`
    leaves `inner` - where piStar stops the line at the shape."""
    icx, icy = (inner[0] + inner[2]) / 2.0, (inner[1] + inner[3]) / 2.0
    dx = (target[0] + target[2]) / 2.0 - icx
    dy = (target[1] + target[3]) / 2.0 - icy
    if dx == 0 and dy == 0:
        return icx, icy
    hw, hh = (inner[2] - inner[0]) / 2.0, (inner[3] - inner[1]) / 2.0
    sx = hw / abs(dx) if dx else float("inf")
    sy = hh / abs(dy) if dy else float("inf")
    s = min(sx, sy)
    return icx + dx * s, icy + dy * s


def sr_cost(placement, links):
    """How a page reads: a link running through an element that is not its own,
    a link's label landing on one (piStar draws the label at the line's
    middle), two labels on top of each other, and two links crossing."""
    boxes = {eid: (x, y, x + w, y + h) for eid, (x, y, w, h) in placement.items()}
    segments, mids = [], []
    through = labels = crossings = crowded = 0
    for source, target in links:
        a, b = boxes[source], boxes[target]
        p, q = clip_to_rect(a, b), clip_to_rect(b, a)
        segments.append((p, q))
        mid = ((p[0] + q[0]) / 2.0, (p[1] + q[1]) / 2.0)
        mids.append(mid)
        for eid, box in boxes.items():
            if eid in (source, target):
                continue
            if seg_hit_box(p, q, box, 2.0):
                through += 1
                break
        for eid, box in boxes.items():
            if eid in (source, target):
                continue
            if (box[0] - 20.0 <= mid[0] <= box[2] + 20.0 and
                    box[1] - 9.0 <= mid[1] <= box[3] + 9.0):
                labels += 1
                break
    for i in range(len(segments)):
        for j in range(i + 1, len(segments)):
            if segments_cross(segments[i][0], segments[i][1],
                              segments[j][0], segments[j][1]):
                crossings += 1
    for i in range(len(mids)):
        for j in range(i + 1, len(mids)):
            if (abs(mids[i][0] - mids[j][0]) < 34.0 and
                    abs(mids[i][1] - mids[j][1]) < 15.0):
                crowded += 1
    w_through, w_labels, w_crowded, w_cross = SR_WEIGHTS
    return (w_through * through + w_labels * labels +
            w_crowded * crowded + w_cross * crossings)


def sr_path_points(source_box, target_box, vertices):
    """The path piStar draws for a link: the source's centre, the vertices, the
    target's centre."""
    return [_box_center(source_box)] + [(v["x"], v["y"]) for v in vertices] + \
           [_box_center(target_box)]


def sr_path_clear(points, boxes, source, target):
    """True when no part of the path runs across an element that is not one of
    the two it joins."""
    for p, q in zip(points, points[1:]):
        for eid, box in boxes.items():
            if eid in (source, target):
                continue
            if seg_hit_box(p, q, box, 2.0):
                return False
    return True


def sr_path_label_clear(points, boxes, source, target):
    """True when the path's middle - where piStar draws the link's label - is
    clear of every element but the two ends."""
    lengths = [math.hypot(q[0] - p[0], q[1] - p[1])
               for p, q in zip(points, points[1:])]
    total = sum(lengths)
    if total <= 0.0:
        return True
    walked, half, mid = 0.0, total / 2.0, points[-1]
    for (p, q), length in zip(zip(points, points[1:]), lengths):
        if walked + length >= half:
            t = (half - walked) / (length or 1.0)
            mid = (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)
            break
        walked += length
    for eid, box in boxes.items():
        if eid in (source, target):
            continue
        if (box[0] - 20.0 <= mid[0] <= box[2] + 20.0 and
                box[1] - 9.0 <= mid[1] <= box[3] + 9.0):
            return False
    return True


def _path_length(points):
    return sum(math.hypot(q[0] - p[0], q[1] - p[1]) for p, q in zip(points, points[1:]))


def sr_route(source_box, target_box, boxes, source, target, strips, margin=26.0):
    """A bend that takes the link round what is in the way, or nothing.

    Two shapes are tried, and the shortest one that is clear of every element
    (and whose middle is clear of every element, because that is where the label
    goes) wins: a Z that crosses the page in an empty band between two rows -
    the shape a person draws by hand - and a shorter detour round the corner of
    the element that is in the way. A detour that is much longer than the
    straight line is not worth drawing, and the straight line is kept instead:
    a page-wide sweep round a small obstruction reads worse than the line it
    was avoiding."""
    sx, sy = _box_center(source_box)
    tx, ty = _box_center(target_box)
    straight = _path_length([(sx, sy), (tx, ty)])
    best, best_length = None, None
    candidates = []
    for yc in strips:
        candidates.append([(sx, yc), (tx, yc)])
    for eid, box in boxes.items():
        if eid in (source, target):
            continue
        if not seg_hit_box((sx, sy), (tx, ty), box, 2.0):
            continue
        for xc in (box[0] - margin, box[2] + margin):
            for yc in (box[1] - margin, box[3] + margin):
                candidates.append([(sx, yc), (xc, yc)])
                candidates.append([(xc, sy), (xc, yc)])
    for waypoints in candidates:
        vertices = [{"x": round(x, 2), "y": round(y, 2)} for x, y in waypoints]
        points = sr_path_points(source_box, target_box, vertices)
        length = _path_length(points)
        if length > straight * 1.45:
            continue
        if not sr_path_clear(points, boxes, source, target):
            continue
        if not sr_path_label_clear(points, boxes, source, target):
            continue
        if best_length is None or length < best_length:
            best, best_length = vertices, length
    return best


def sr_page_model(actor, sd):
    """One piStar model: one actor's rationale, the elements grouped in rows."""
    by_id = {el["id"]: el for el in actor["elements"]}
    order = row_order(actor)
    rows = {kind: [by_id[eid] for eid in order[kind]] for kind in SR_ROW_ORDER}

    sized = {}
    for kind in SR_ROW_ORDER:
        for el in rows[kind]:
            sized[el["id"]] = node_size(el["name"], kind)

    def place(rows):
        """Each row centred over the others: the widest row decides the page
        width, and every element sits where its own row puts it."""
        row_width = {}
        for kind in SR_ROW_ORDER:
            if rows[kind]:
                row_width[kind] = (sum(sized[el["id"]][0] for el in rows[kind])
                                   + SR_GAP_X * (len(rows[kind]) - 1))
        widest = max(row_width.values()) if row_width else 400.0
        center_x = 100.0 + widest / 2.0
        out, top = {}, 300.0
        for kind in SR_ROW_ORDER:
            if not rows[kind]:
                continue
            x = center_x - row_width[kind] / 2.0
            row_h = 0.0
            for el in rows[kind]:
                w, h = sized[el["id"]]
                out[el["id"]] = (x, top, w, h)
                x += w + SR_GAP_X
                row_h = max(row_h, h)
            top += row_h + SR_GAP_Y
        return out

    # --- settle the order: swaps inside a row, kept while they improve how the
    # --- page reads. The barycentre order above is the starting point; this is
    # --- what takes a link off the element it was drawn across.
    links = sr_links(actor)
    placed = place(rows)
    best = sr_cost(placed, links)
    if best > 0.0:
        improved, passes = True, 0
        while improved and passes < 8:
            improved = False
            passes += 1
            for kind in SR_ROW_ORDER:
                row = rows[kind]
                for i in range(len(row)):
                    for j in range(i + 1, len(row)):
                        row[i], row[j] = row[j], row[i]
                        candidate = place(rows)
                        cost = sr_cost(candidate, links)
                        if cost < best:
                            best = cost
                            placed = candidate
                            improved = True
                        else:
                            row[i], row[j] = row[j], row[i]
    sized = placed

    model_actors = [{
        "id": actor["actor"],
        "text": actor["name"],
        "type": "istar." + ACTOR_KIND_TO_TYPE[actor_kind(actor, sd)],
        "x": 60.0, "y": 60.0,
        "nodes": [],
        "customProperties": {"Intent": actor.get("intent", ""),
                             "Evidence": actor.get("evidence", "")},
    }]

    display = {}
    nodes = []
    for kind in SR_ROW_ORDER:
        for el in rows[kind]:
            x, yy, w, h = sized[el["id"]]
            custom = {"Evidence": el.get("evidence", "")}
            if el.get("deps"):
                custom["AccountsFor"] = ", ".join(el["deps"])
            if el.get("gap"):
                custom["Gap"] = el.get("gapNote", "not met in this release")
            nodes.append({
                "id": actor["actor"] + "-" + el["id"],
                "text": el["name"],
                "type": "istar." + KIND_TO_TYPE[kind],
                "x": round(x, 2), "y": round(yy, 2),
                "customProperties": custom,
            })
            # the source draws a gap - a need the case states and this release
            # does not meet - as a dashed element; a node cannot be dashed in
            # piStar, so it is tinted instead, and the Style tab resets it
            entry = {"width": round(w, 2), "height": round(h, 2)}
            if el.get("gap"):
                entry["backgroundColor"] = GAP_COLOUR
            display[actor["actor"] + "-" + el["id"]] = entry
    model_actors[0]["nodes"] = nodes

    # --- the links, each relation once, in the direction that reads as
    # --- "the means to the end" / "the contributor to what it contributes to"
    kinds = {el["id"]: el["kind"] for el in actor["elements"]}
    relations = set()
    for el in actor["elements"]:
        for link in el.get("links", []):
            if link["to"] in kinds:
                relations.add((el["id"], link["to"], link["type"], link.get("mode")))

    def reads_forward(s, t, kind):
        if kind in ("help", "hurt", "make", "break", "some+", "some-", "unknown", "equal"):
            return (kinds[t] in ("goal", "softgoal")
                    and kinds[s] not in ("goal", "softgoal"))
        if kind in ("decomposition", "neededby") or (kinds[s] == "task" and kinds[t] == "task"):
            return s < t                        # recorded from both ends: keep one, deterministically
        return kinds[t] == "goal" and kinds[s] != "goal"

    model_links = []
    seen = set()
    for (s, t, kind, mode) in sorted(relations, key=lambda r: (r[0], r[1])):
        if (t, s, kind, mode) in relations:                 # recorded from both ends
            if not reads_forward(s, t, kind):
                continue
        key = (s, t, kind, mode)
        if key in seen:
            continue
        seen.add(key)
        if kind in PISTAR_CONTRIBUTIONS or kind not in LINK_TO_TYPE:
            label = CONTRIBUTION_LABEL.get(kind)
            if label is None:
                continue
            if kinds[t] != "softgoal":           # iStar 2.0: contributions enter a quality
                REWRITTEN.append((actor["actor"], s, kinds[s], t, kinds[t], kind))
                model_links.append({
                    "id": "%s-%s-%s" % (actor["actor"], s, t),
                    "type": "istar.OrRefinementLink",
                    "source": actor["actor"] + "-" + s,
                    "target": actor["actor"] + "-" + t,
                })
                continue
            model_links.append({
                "id": "%s-%s-%s" % (actor["actor"], s, t),
                "type": "istar.ContributionLink",
                "source": actor["actor"] + "-" + s,
                "target": actor["actor"] + "-" + t,
                "label": label,
            })
        else:
            link_type = LINK_TO_TYPE[kind]
            if kind == "decomposition" and (mode or "and").lower() == "or":
                link_type = "OrRefinementLink"
            # piStar draws a refinement's marker at its target and takes that end as
            # the parent, so a decomposition is written from the part to the whole -
            # the direction its means-end links are written in too. A neededBy link
            # is written resource -> task, which is also what piStar takes.
            src, tgt = (t, s) if kind == "decomposition" else (s, t)
            model_links.append({
                "id": "%s-%s-%s" % (actor["actor"], src, tgt),
                "type": "istar." + link_type,
                "source": actor["actor"] + "-" + src,
                "target": actor["actor"] + "-" + tgt,
            })

    # --- a link whose straight line would be drawn across an element, or whose
    # --- label would land on one, is bent round it. This is the last pass, on
    # --- the links that are actually drawn: it runs after the order is settled,
    # --- so the bends are added to the drawing and never to the search.
    prefix = actor["actor"] + "-"
    draw_boxes = {eid: (x, y, x + w, y + h)
                  for eid, (x, y, w, h) in sized.items()}
    row_index = {}
    for r, kind in enumerate(SR_ROW_ORDER):
        for el in rows[kind]:
            row_index[el["id"]] = r
    row_bounds = {}
    for r, kind in enumerate(SR_ROW_ORDER):
        if not rows[kind]:
            continue
        row_bounds[r] = (min(sized[el["id"]][1] for el in rows[kind]),
                         max(sized[el["id"]][1] + sized[el["id"]][3] for el in rows[kind]))
    bands = {}
    present = sorted(row_bounds)
    for a, b in zip(present, present[1:]):
        bands[b] = (row_bounds[a][1] + row_bounds[b][0]) / 2.0     # the band above row b
    for (s, t, kind, mode) in sorted(relations, key=lambda r: (r[0], r[1])):
        link_id = "%s-%s-%s" % (actor["actor"], s, t)
        if s not in draw_boxes or t not in draw_boxes:
            continue
        if not any(l["id"] == link_id for l in model_links):
            continue
        straight = sr_path_points(draw_boxes[s], draw_boxes[t], [])
        if (sr_path_clear(straight, draw_boxes, s, t) and
                sr_path_label_clear(straight, draw_boxes, s, t)):
            continue
        rs, rt = row_index[s], row_index[t]
        low, high = min(rs, rt), max(rs, rt)
        candidates = [bands[r] for r in present if low <= r <= high and r in bands]
        candidates.sort(key=lambda y: abs(y - (sized[s][1] + sized[s][3] + sized[t][1]) / 2.0))
        vertices = sr_route(draw_boxes[s], draw_boxes[t], draw_boxes, s, t, candidates)
        if vertices:
            display.setdefault(link_id, {})["vertices"] = vertices

    return assemble(actors=model_actors, dependencies=[], links=model_links,
                    display=display,
                    name="HPRTAS SR - %s" % actor["name"],
                    note=actor.get("intent", ""), source="sr-hprtas.json")


# ------------------------------------------------- the whole model in one page
BOUNDARY_PAD = 30.0       # how far outside its elements piStar draws a boundary
SHELF_GAP_X = 170.0       # room between two boundaries for the dependums on their lines
SHELF_GAP_Y = 230.0


# ------------------------------------------------------------ the overviews
# The two drawings that carry a model on one canvas, cut back to what a reader
# meeting the models for the first time needs. The SD model now fits on one
# canvas itself, so its "overview" is the full drawing; the SR overview keeps
# each actor's goals and the tasks it carries out about them and nothing else.
#
# What the SR overview keeps: the goals an actor wants and the tasks it carries
# out about them, with the links between those two kinds - and nothing else.
# The qualities, the gap elements, the contributions that measure them, the
# resources and the dependencies are the detail the per-actor pages carry.
SR_OVERVIEW_KINDS = ("goal", "task")
SR_OVERVIEW_LINKS = ("means-end", "decomposition")


def sr_overview_actor(actor):
    """One actor with only its goals, its tasks and the links between them."""
    elements = []
    for el in actor["elements"]:
        if el["kind"] not in SR_OVERVIEW_KINDS:
            continue
        kept = dict(el)
        links = [l for l in el.get("links", []) if l["type"] in SR_OVERVIEW_LINKS]
        if links:
            kept["links"] = links
        else:
            kept.pop("links", None)
        elements.append(kept)
    return dict(actor, elements=elements)


def actor_pages(sd, actors):
    """Each actor's own page, and the box its boundary takes on the canvas."""
    pages = {}
    for actor in actors:
        page = sr_page_model(actor, sd)
        cell = page["actors"][0]
        box = [cell["x"], cell["y"], cell["x"] + ACTOR_SIZE, cell["y"] + ACTOR_SIZE]
        for n in cell["nodes"]:
            d = page["display"][n["id"]]
            box[0] = min(box[0], n["x"])
            box[1] = min(box[1], n["y"])
            box[2] = max(box[2], n["x"] + d["width"])
            box[3] = max(box[3], n["y"] + d["height"])
        pages[actor["actor"]] = {"page": page, "box": box}
    return pages


def place_actor_pages(pages, order_deps, gap_x=SHELF_GAP_X, gap_y=SHELF_GAP_Y,
                      target_width=None):
    """The order of the actors on the canvas and the place of each boundary."""
    sizes = {aid: (p["box"][2] - p["box"][0] + 2.0 * BOUNDARY_PAD,
                   p["box"][3] - p["box"][1] + 2.0 * BOUNDARY_PAD)
             for aid, p in pages.items()}
    order, (places, _extent) = G.pack_order_search(
        sorted(pages), order_deps, sizes, gap_x=gap_x, gap_y=gap_y,
        target_width=target_width)
    boxes = {aid: (places[aid][0], places[aid][1],
                   places[aid][0] + sizes[aid][0], places[aid][1] + sizes[aid][1])
             for aid in pages}
    return order, places, boxes, sizes


def actor_cells(order, pages, places, sizes):
    """The actor cells and their nodes, and the links inside them."""
    model_actors, display, model_links = [], {}, []
    for aid in order:
        entry = pages[aid]
        page, box = entry["page"], entry["box"]
        place = places[aid]
        dx = place[0] + BOUNDARY_PAD - box[0]
        dy = place[1] + BOUNDARY_PAD - box[1]
        cell = dict(page["actors"][0])
        cell["x"] = round(cell["x"] + dx, 2)
        cell["y"] = round(cell["y"] + dy, 2)
        cell["nodes"] = [dict(n, x=round(n["x"] + dx, 2), y=round(n["y"] + dy, 2))
                         for n in page["actors"][0]["nodes"]]
        for n in cell["nodes"]:
            display[n["id"]] = page["display"][n["id"]]
        for link in page["links"]:
            moved = dict(link)
            extra = page["display"].get(link["id"], {})
            if extra.get("vertices"):
                display[link["id"]] = {"vertices": [
                    {"x": round(v["x"] + dx, 2), "y": round(v["y"] + dy, 2)}
                    for v in extra["vertices"]]}
            model_links.append(moved)
        model_actors.append(cell)
    return model_actors, display, model_links


def sr_overview_model(sd, sr, name, note):
    """One piStar model: every actor's goals and tasks, and the links between
    them, on one canvas - the activity view of the whole model.

    No qualities and no dependencies: what each actor is trying to achieve and
    what it does about it, which is what a reader needs first. `sr-all.json` is
    the same canvas with everything on it."""
    actors = [sr_overview_actor(a) for a in sr["actors"]]
    pages = actor_pages(sd, actors)
    # one page for a reader and for a slide, so it is laid out landscape rather
    # than in whatever shape crosses least
    area = sum((p["box"][2] - p["box"][0] + 2.0 * BOUNDARY_PAD) *
               (p["box"][3] - p["box"][1] + 2.0 * BOUNDARY_PAD)
               for p in pages.values())
    target = math.sqrt(area) * 1.6
    order, places, boxes, sizes = place_actor_pages(
        pages, sd["dependencies"], target_width=target)
    model_actors, display, model_links = actor_cells(order, pages, places, sizes)
    return assemble(actors=model_actors, dependencies=[], links=model_links,
                    display=display, name=name, note=note,
                    source="sr-hprtas.json")


def sr_all_model(sd, sr, name, note):
    """One piStar model: every actor's rationale on one canvas, with the
    dependencies between them drawn round the actors.

    This is the Strategic Rationale view of the whole model, which is what iStar
    2.0 draws a rationale as: actors, their intentional elements, the links
    between those elements, and the dependencies that cross a boundary. Each
    actor is laid out by the same code as its own page, so an element sits in
    the same place inside its boundary; what is new here is where the actor
    boundaries go, and where the six dependums go between them.

    The page has three parts, and each is computed rather than eyeballed: the
    order of the actors on the page (swaps, kept while they lower the number of
    dependency lines that cross), the shelf the boundaries sit on, and the place
    of each dependum on the line between the two it joins."""
    pages = actor_pages(sd, sr["actors"])
    order, places, actor_boxes, sizes = place_actor_pages(pages, sd["dependencies"])
    model_actors, display, model_links = actor_cells(order, pages, places, sizes)

    centers = {aid: ((b[0] + b[2]) / 2.0, (b[1] + b[3]) / 2.0)
               for aid, b in actor_boxes.items()}
    deps = sd["dependencies"]
    anchors, dep_sizes = G.dependum_anchors_outside(
        deps, actor_boxes, size_of=lambda d: node_size(d["dependum"], d["type"]))
    boxes = {}
    for d in deps:
        w, h = dep_sizes[d["id"]]
        cx, cy = anchors[d["id"]]
        boxes[d["id"]] = [cx - w / 2.0, cy - h / 2.0, cx + w / 2.0, cy + h / 2.0]
    circles = {aid: (cx, cy, 0.0) for aid, (cx, cy) in centers.items()}
    boxes = G.relax_dependums(boxes, anchors, circles, deps, DEP_GAP_X, DEP_GAP_Y,
                              obstacles=actor_boxes, rounds=700, push=22.0)

    model_deps = []
    for d in deps:
        box = boxes[d["id"]]
        w, h = box[2] - box[0], box[3] - box[1]
        custom = {"Evidence": d.get("evidence", "")}
        for key, label in (("rules", "Rules"), ("reqs", "Requirements"),
                           ("nfrs", "NFRs"), ("exceptions", "Exceptions")):
            if d.get(key):
                custom[label] = ", ".join(d[key])
        if d.get("note"):
            custom["Note"] = d["note"]
        model_deps.append({
            "id": d["id"],
            "text": d["dependum"],
            "type": "istar." + KIND_TO_TYPE[d["type"]],
            "x": round(box[0], 2), "y": round(box[1], 2),
            "source": d["from"],
            "target": d["to"],
            "customProperties": custom,
        })
        model_links.append({"id": d["id"] + "-depender", "type": "istar.DependencyLink",
                            "source": d["from"], "target": d["id"]})
        model_links.append({"id": d["id"] + "-dependee", "type": "istar.DependencyLink",
                            "source": d["id"], "target": d["to"]})
        display[d["id"]] = {"width": round(w, 2), "height": round(h, 2)}

    return assemble(actors=model_actors, dependencies=model_deps, links=model_links,
                    display=display, name=name, note=note, source="sr-hprtas.json")


def check_sr_all(model, label, problems):
    """A page that expands every actor: no two elements touch, no element sits
    inside another actor's boundary, and no dependum sits on either."""
    bounds = {}
    for actor in model["actors"]:
        box = [actor["x"], actor["y"], actor["x"] + ACTOR_SIZE, actor["y"] + ACTOR_SIZE]
        for n in actor["nodes"]:
            d = model["display"][n["id"]]
            box[0] = min(box[0], n["x"])
            box[1] = min(box[1], n["y"])
            box[2] = max(box[2], n["x"] + d["width"])
            box[3] = max(box[3], n["y"] + d["height"])
        bounds[actor["id"]] = (box[0] - BOUNDARY_PAD, box[1] - BOUNDARY_PAD,
                               box[2] + BOUNDARY_PAD, box[3] + BOUNDARY_PAD)
    nodes = []
    for actor in model["actors"]:
        for n in actor["nodes"]:
            d = model["display"][n["id"]]
            nodes.append((n["id"], actor["id"],
                          rect(n["x"], n["y"], d["width"], d["height"])))
    for i, (ida, owner, a) in enumerate(nodes):
        for idb, other, b in nodes[i + 1:]:
            if overlap(a, b, 0.0, 0.0):
                problems.append("%s: %s and %s touch" % (label, ida, idb))
        for aid, boundary in bounds.items():
            if aid != owner and overlap(a, boundary, 0.0, 0.0):
                problems.append("%s: %s sits inside the %s boundary" % (label, ida, aid))
    for d in model["dependencies"]:
        w = model["display"][d["id"]]["width"]
        h = model["display"][d["id"]]["height"]
        box = rect(d["x"], d["y"], w, h)
        for idn, owner, a in nodes:
            if overlap(box, a, 6.0, 6.0):
                problems.append("%s: dependum %s sits on %s" % (label, d["id"], idn))
        for aid, boundary in bounds.items():
            if aid in (d["source"], d["target"]):
                continue
            if overlap(box, boundary, 0.0, 0.0):
                problems.append("%s: dependum %s sits on the %s boundary"
                                % (label, d["id"], aid))
    return len(nodes) + len(model["dependencies"])


def actor_kind(actor, sd):
    """The actor's kind, looked up from the SD model's actor list."""
    for entry in sd["actors"]:
        if entry["id"] == actor["actor"]:
            return entry["kind"]
    return "role"


# ------------------------------------------------------------------- assembly
MARGIN = 60.0


def assemble(actors, dependencies, links, display, name, note, source):
    """Wrap the cells up as a piStar model.

    piStar's canvas starts at (0, 0) and clips anything drawn above or left of
    it, so the whole drawing is shifted into the positive quadrant first - the
    layout places dependums on rings around their actor and a ring reaches
    outside the first grid cell."""
    boxes = []
    for a in actors:
        boxes.append((a, "actor", ACTOR_SIZE, ACTOR_SIZE))
        for n in a["nodes"]:
            boxes.append((n, "node", display[n["id"]]["width"], display[n["id"]]["height"]))
    for d in dependencies:
        boxes.append((d, "dep", display[d["id"]]["width"], display[d["id"]]["height"]))

    min_x = min(cell["x"] for cell, _, _, _ in boxes)
    min_y = min(cell["y"] for cell, _, _, _ in boxes)
    dx = MARGIN - min_x if min_x < MARGIN else 0.0
    dy = MARGIN - min_y if min_y < MARGIN else 0.0
    for cell, _, _, _ in boxes:
        cell["x"] = round(cell["x"] + dx, 2)
        cell["y"] = round(cell["y"] + dy, 2)

    width = max(cell["x"] + w for cell, _, w, _ in boxes) + MARGIN
    height = max(cell["y"] + h for cell, _, _, h in boxes) + MARGIN

    return {
        "actors": actors,
        "orphans": [],
        "dependencies": dependencies,
        "links": links,
        "display": display,
        "tool": PISTAR_TOOL,
        "istar": "2.0",
        "saveDate": datetime.datetime.now(datetime.timezone.utc).strftime(
            "%a, %d %b %Y %H:%M:%S GMT"),
        "diagram": {
            "width": round(max(900.0, width), 2),
            "height": round(max(700.0, height), 2),
            "name": name,
            "customProperties": {"Source": "HPRTAS/models/socio-technical/" + source,
                                 "Note": note},
        },
    }


# --------------------------------------------------------------------- checks
def check_sd(model, label, problems):
    """No two dependums touch, and none sits on an actor."""
    dep_boxes = []
    for d in model["dependencies"]:
        w = model["display"][d["id"]]["width"]
        h = model["display"][d["id"]]["height"]
        dep_boxes.append((d["id"], rect(d["x"], d["y"], w, h)))
    for i, (ida, a) in enumerate(dep_boxes):
        for idb, b in dep_boxes[i + 1:]:
            if overlap(a, b, 0.0, 0.0):
                problems.append("%s: %s and %s overlap" % (label, ida, idb))
        for act in model["actors"]:
            cx, cy = act["x"] + ACTOR_SIZE / 2.0, act["y"] + ACTOR_SIZE / 2.0
            if circle_rect_overlap(a, cx, cy, ACTOR_SIZE / 2.0, 2.0):
                problems.append("%s: %s sits on actor %s" % (label, ida, act["id"]))
    for i, a in enumerate(model["actors"]):
        for b in model["actors"][i + 1:]:
            if math.hypot(a["x"] - b["x"], a["y"] - b["y"]) < ACTOR_SIZE + 8.0:
                problems.append("%s: actor %s and actor %s overlap" % (label, a["id"], b["id"]))
    return len(dep_boxes)


def check_sr(model, label, problems):
    """No two elements of a page touch, and none crosses the actor boundary."""
    boxes = []
    for a in model["actors"]:
        for n in a["nodes"]:
            w = model["display"][n["id"]]["width"]
            h = model["display"][n["id"]]["height"]
            boxes.append((n["id"], rect(n["x"], n["y"], w, h)))
    for i, (ida, a) in enumerate(boxes):
        for idb, b in boxes[i + 1:]:
            if overlap(a, b, 0.0, 0.0):
                problems.append("%s: %s and %s touch" % (label, ida, idb))
    for n in model["actors"][0]["nodes"]:
        if not (n["id"].startswith(model["actors"][0]["id"] + "-")):
            problems.append("%s: element %s is not named after its actor"
                            % (label, n["id"]))
    return len(boxes)


def check_canvas(model, label, problems):
    """Everything inside the declared canvas - piStar clips what is outside."""
    width, height = model["diagram"]["width"], model["diagram"]["height"]
    cells = [(a["id"], a["x"], a["y"], ACTOR_SIZE, ACTOR_SIZE) for a in model["actors"]]
    for a in model["actors"]:
        for n in a["nodes"]:
            cells.append((n["id"], n["x"], n["y"],
                          model["display"][n["id"]]["width"],
                          model["display"][n["id"]]["height"]))
    for d in model["dependencies"]:
        cells.append((d["id"], d["x"], d["y"],
                      model["display"][d["id"]]["width"],
                      model["display"][d["id"]]["height"]))
    for cid, x, y, w, h in cells:
        if x < 0 or y < 0 or x + w > width or y + h > height:
            problems.append("%s: %s is outside the canvas" % (label, cid))


def write(path, model):
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(model, handle, ensure_ascii=False, indent=1)
        handle.write("\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=REPO)
    parser.add_argument("--out", default=OUT)
    parser.add_argument("--check", action="store_true",
                       help="report layout problems and write nothing")
    args = parser.parse_args()

    sd = json.load(open(os.path.join(args.repo, "models", "socio-technical",
                                     "sd-hprtas.json"), encoding="utf-8"))
    sr = json.load(open(os.path.join(args.repo, "models", "socio-technical",
                                     "sr-hprtas.json"), encoding="utf-8"))

    problems, written, dep_count, el_count = [], 0, 0, 0
    files = []

    for view in sd["views"]:
        model = sd_view_model(sd, view.get("stages", []),
                              "HPRTAS SD - %s" % view["title"], view.get("note", ""),
                              hub=view.get("hub"))
        dep_count += check_sd(model, "sd/%s" % view["id"], problems)
        check_canvas(model, "sd/%s" % view["id"], problems)
        files.append(("sd-%s.json" % view["id"], model))

    all_stages = sorted({d["stage"] for d in sd["dependencies"] if d.get("stage")})
    model = sd_view_model(sd, all_stages, "HPRTAS SD - every dependency (the whole model)",
                          "Every actor and every dependency of the SD model, on one canvas.")
    dep_count += check_sd(model, "sd/all", problems)
    check_canvas(model, "sd/all", problems)
    files.append(("sd-all.json", model))

    for actor in sr["actors"]:
        model = sr_page_model(actor, sd)
        el_count += check_sr(model, "sr/%s" % actor["actor"], problems)
        check_canvas(model, "sr/%s" % actor["actor"], problems)
        files.append(("sr-%s.json" % actor["actor"], model))

    model = sr_all_model(sd, sr, "HPRTAS SR - every actor's rationale, and every dependency",
                         "The whole Strategic Rationale view on one canvas: every actor's "
                         "elements and links, with the dependencies drawn round them.")
    check_sr_all(model, "sr/all", problems)
    check_canvas(model, "sr/all", problems)
    files.append(("sr-all.json", model))

    model = sd_view_model(sd, [], "HPRTAS SD - the referral pathway",
                          "The whole SD model on one canvas: four actors and the six "
                          "dependencies between them.",
                          dep_ids=[d["id"] for d in sd["dependencies"]])
    dep_count += check_sd(model, "sd/overview", problems)
    check_canvas(model, "sd/overview", problems)
    files.append(("sd-overview.json", model))

    model = sr_overview_model(sd, sr, "HPRTAS SR - every actor's goals and tasks (overview)",
                              "Each actor's goals and the tasks it carries out about them, "
                              "with the links between them. The complete rationale is the "
                              "per-actor pages and the combined page.")
    el_count += check_sr_all(model, "sr/overview", problems)
    check_canvas(model, "sr/overview", problems)
    files.append(("sr-overview.json", model))

    print("%d SD dependums and %d SR elements checked" % (dep_count, el_count))
    if problems:
        print("\n%d layout problem(s):" % len(problems))
        for p in problems[:40]:
            print("  - %s" % p)
        return 1
    if args.check:
        print("no layout problems")
        return 0

    os.makedirs(args.out, exist_ok=True)
    for name, model in files:
        write(os.path.join(args.out, name), model)
        written += 1
    print("wrote %d piStar JSON file(s) to %s" % (written, args.out))

    if REWRITTEN:
        pages = sorted({r[0] for r in REWRITTEN})
        print("\n%d contribution link(s) on %d page(s) did not enter a quality and were written"
              "\nas means-end (OR-refinement) links, which is what iStar 2.0 and piStar allow a"
              "\ntask that supports a hard goal to be - see README.md in the output folder:"
              % (len(REWRITTEN), len(pages)))
        for actor_id, s, skind, t, tkind, kind in REWRITTEN:
            print("  %s: %s (%s) -%s-> %s (%s)" % (actor_id, s, skind, kind, t, tkind))
    return 0


if __name__ == "__main__":
    sys.exit(main())
