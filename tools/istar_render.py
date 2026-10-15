# -*- coding: utf-8 -*-
"""Draw the HPRTAS i* models - the Strategic Dependency view and the Strategic
Rationale view - from the JSON sources beside them, and write the tables that go
with the diagrams.

The models live in `models/socio-technical/` as data (`sd-hprtas.json`,
`sr-hprtas.json`); this module is what turns them into pictures. The layout is
computed here and is deterministic - the same model always draws the same way -
so `verify_hprtas_istar.py` can import these same functions and check the
geometry that is actually drawn, and a difference in the output means a
difference in the model rather than in the layout.

Notation (iStar 2.0, the shapes piStar and the i* literature use):

    actor      a circle with the name inside
    goal       a rounded rectangle
    task       a hexagon
    resource   a rectangle
    softgoal   a cloud

    In SD a dependum is drawn near the actor that depends, in the shape of its
    type, and the line leaves it for the actor depended on, arrow at that end, so
    the picture reads `A --[goal: text]--> B` as "A depends on B for text".

    In SR an actor boundary carries the elements, grouped by kind - soft goals at
    the top, resources at the bottom - and the links are
    means-end      a line with an arrowhead at the goal it serves
    decomposition  a line with a diamond at the parent, labelled AND or OR
    neededBy       a line with a small circle at the task end, from the resource
                   the task needs to the task that needs it
    contribution   a line with an arrowhead, labelled make / help / some+ /
                   unknown / some- / hurt / break

An element marked `gap` - a need the case states and this release does not meet -
is drawn dashed, carries no contribution link, and is labelled, so it reads as
unattached rather than as satisfied.

Usage:
    python tools\\istar_render.py               # every view and every actor
    python tools\\istar_render.py --pdf-only    # pages only, no PNG
"""
import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpmn_render as R  # noqa: E402
import istar_geometry as G  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(REPO, "models", "socio-technical")
OUT_DIR = os.path.join(REPO, "models", "exports")
SD_PATH = os.path.join(MODEL_DIR, "sd-hprtas.json")
SR_PATH = os.path.join(MODEL_DIR, "sr-hprtas.json")

SCALE = 1.55              # image pixels per model unit
ACTOR_FONT = 12.0         # the name inside an actor circle
NODE_FONT = 11.0          # the text inside an SR element
DEP_FONT = 10.0           # the text inside an SD dependum
LINK_FONT = 8.5           # a contribution or decomposition label
LEGEND_FONT = 9.0
PAD = 46.0                # model units of white space around a diagram
ROW_GAP = 96.0            # model units between two rows of the SR layout
HEAD = (34, 36, 42)
SOFT = (130, 134, 140)
BOUNDARY = (150, 154, 160)
GAP_INK = (176, 92, 92)
FILL = "white"
NEGATIVE = ("hurt", "break", "some-")
CONTRIBUTIONS = ("make", "help", "some+", "unknown", "some-", "hurt", "break", "equal")
GAP_LABEL = "not met in this release"


# --------------------------------------------------------------- text metrics
def fitted_text(text, font, max_width, lines_max, pad_x, pad_y):
    """Wrap `text` to at most `lines_max` lines and size a box to what it needs.

    Returns `(lines, width, height)` in model units. The box is sized to the
    text that will be drawn rather than to the wrap limit, so a short label gets
    a small box.
    """
    width = max_width
    lines = R.wrap(text, font, width)
    for _ in range(80):
        if len(lines) <= lines_max:
            break
        width += 12.0
        lines = R.wrap(text, font, width)
    widest = max(R.text_width(line, font) for line in lines)
    return lines, widest + pad_x * 2.0, len(lines) * font * R.LINE_SPACING + pad_y


def element_font(kind):
    return NODE_FONT - 0.5 if kind == "softgoal" else NODE_FONT


# ------------------------------------------------------------------- shapes
def _stroke(draw, pts, scale, dashed, color=HEAD):
    lw = max(1, int(round(1.4 * scale)))
    path = list(pts) + [pts[0]]
    if dashed:
        R._dashed(draw, path, scale, dash=7.0, gap=5.0)
    else:
        draw.line(path, fill=color, width=lw, joint="curve")


def _rounded_path(box, radius, steps=4):
    x0, y0, x1, y1 = box
    pts = []
    for cx, cy, a0 in ((x1 - radius, y0 + radius, -math.pi / 2),
                       (x1 - radius, y1 - radius, 0.0),
                       (x0 + radius, y1 - radius, math.pi / 2),
                       (x0 + radius, y0 + radius, math.pi)):
        for k in range(steps + 1):
            a = a0 + (math.pi / 2) * k / steps
            pts.append((cx + radius * math.cos(a), cy + radius * math.sin(a)))
    return pts


def draw_goal(draw, box, scale, dashed=False):
    x0, y0, x1, y1 = box
    radius = min(9 * scale, (y1 - y0) / 2.0, (x1 - x0) / 2.0)
    draw.rounded_rectangle(box, radius=radius, fill=FILL)
    _stroke(draw, _rounded_path(box, radius), scale, dashed)


def draw_resource(draw, box, scale, dashed=False):
    draw.rectangle(box, fill=FILL)
    _stroke(draw, [(box[0], box[1]), (box[2], box[1]),
                   (box[2], box[3]), (box[0], box[3])], scale, dashed)


def draw_task(draw, box, scale, dashed=False):
    x0, y0, x1, y1 = box
    s = min((y1 - y0) / 2.0, (x1 - x0) / 4.0)
    cy = (y0 + y1) / 2.0
    pts = [(x0 + s, y0), (x1 - s, y0), (x1, cy), (x1 - s, y1), (x0 + s, y1), (x0, cy)]
    draw.polygon(pts, fill=FILL)
    _stroke(draw, pts, scale, dashed)


def cloud_shape(box, lobes=None):
    """A cloud silhouette: a scalloped top and bottom between two rounded ends.

    The lobes are a cosine ripple with the amplitude taken from the box height,
    not a union of circles: circles big enough to read on a wide, short label
    would make the cloud far taller than the box it has to sit in, and circles
    small enough to fit overlap until the outline is flat. The ripple gives
    lobes of a known depth, and one closed path, so the same shape can be
    dashed for a gap element."""
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    amp = h * 0.28
    if lobes is None:
        lobes = max(3, int(round(w / max(1.0, h * 1.05))))
    cap = min(amp * 0.95, w * 0.13)
    xs, xe = x0 + cap, x1 - cap
    mid, ry = (y0 + y1) / 2.0, (y1 - y0) / 2.0
    steps = max(48, lobes * 16)
    pts = []
    for k in range(steps + 1):                      # top, left to right
        t = k / float(steps)
        pts.append((xs + (xe - xs) * t,
                    y0 + amp * (1.0 - math.cos(2.0 * math.pi * lobes * t)) / 2.0))
    for k in range(9):                              # right end
        a = -math.pi / 2.0 + math.pi * k / 8.0
        pts.append((xe + cap * math.cos(a), mid + ry * math.sin(a)))
    for k in range(steps + 1):                      # bottom, right to left
        t = k / float(steps)
        pts.append((xe - (xe - xs) * t,
                    y1 - amp * (1.0 - math.cos(2.0 * math.pi * lobes * t)) / 2.0))
    for k in range(9):                              # left end
        a = math.pi / 2.0 + math.pi * k / 8.0
        pts.append((xs + cap * math.cos(a), mid + ry * math.sin(a)))
    return pts


def draw_softgoal(draw, box, scale, dashed=False):
    pts = cloud_shape(box)
    draw.polygon(pts, fill=FILL)
    _stroke(draw, pts, scale, dashed)


SHAPES = {"goal": draw_goal, "task": draw_task, "resource": draw_resource,
          "softgoal": draw_softgoal}


def draw_node(draw, kind, text, box, scale, origin, dashed=False):
    """Draw one element. `box` is in model units, `origin` the canvas corner."""
    px = ((box[0] - origin[0]) * scale, (box[1] - origin[1]) * scale,
          (box[2] - origin[0]) * scale, (box[3] - origin[1]) * scale)
    SHAPES[kind](draw, px, scale, dashed)
    font = element_font(kind)
    lines = R.wrap(text, font, (box[2] - box[0]) - text_pad(kind, box))
    cx, cy = (px[0] + px[2]) / 2.0, (px[1] + px[3]) / 2.0
    if kind == "softgoal":
        cy += 0.0
    _text_block(draw, lines, font, (cx, cy), scale)


def text_pad(kind, box):
    """How much width of a box its shape takes before the text can start.

    A hexagon loses its pointy ends and a cloud loses its bumps, and text set
    into either runs through the outline when it is measured against the whole
    box. `box` is in model units, and so is the result."""
    w, h = box[2] - box[0], box[3] - box[1]
    if kind == "task":
        return 2.0 * min(h / 2.0, w / 4.0) + 12.0
    if kind == "softgoal":
        return w * 0.03 + 12.0
    return 16.0


def draw_actor_node(draw, text, cx, cy, scale, origin, font_size=ACTOR_FONT):
    """An i* actor: a circle with the name inside. Returns its model-unit box."""
    radius = actor_radius(text, font_size)
    px, py, rp = (cx - origin[0]) * scale, (cy - origin[1]) * scale, radius * scale
    draw.ellipse((px - rp, py - rp, px + rp, py + rp), fill=FILL, outline=HEAD,
                 width=max(1, int(round(1.5 * scale))))
    _text_block(draw, R.wrap(text, font_size, radius * 1.42), font_size, (px, py), scale)
    return (cx - radius, cy - radius, cx + radius, cy + radius)


def actor_radius(text, font_size=ACTOR_FONT):
    """A circle wide enough for the wrapped name, with room for two lines."""
    width = R.text_width(text, font_size)
    lines = max(1, int(math.ceil(width / 140.0)))
    return max(48.0, (width / lines) * 0.60 + 16.0)


def _text_block(draw, lines, size, centre, scale, color=HEAD):
    font = R._font(size * scale)
    line_h = size * R.LINE_SPACING * scale
    cx, cy = centre
    top = cy - len(lines) * line_h / 2.0
    for i, line in enumerate(lines):
        draw.text((cx, top + i * line_h), line, font=font, fill=color, anchor="ma")


# ------------------------------------------------------------------ geometry
def rect_of(cx, cy, w, h):
    return (cx - w / 2.0, cy - h / 2.0, cx + w / 2.0, cy + h / 2.0)


def overlaps(a, b, gap=0.0):
    return not (a[2] + gap <= b[0] or b[2] + gap <= a[0] or
                a[3] + gap <= b[1] or b[3] + gap <= a[1])


def clip_rect_to_rect(inner, target):
    """Where the line from the centre of `inner` to the centre of `target`
    leaves `inner`, so a link stops at a border and not in the middle."""
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


def clip_ellipse_to_rect(ell, target):
    """The same, for an actor drawn as a circle."""
    ecx, ecy = (ell[0] + ell[2]) / 2.0, (ell[1] + ell[3]) / 2.0
    rx, ry = (ell[2] - ell[0]) / 2.0, (ell[3] - ell[1]) / 2.0
    dx = (target[0] + target[2]) / 2.0 - ecx
    dy = (target[1] + target[3]) / 2.0 - ecy
    if dx == 0 and dy == 0:
        return ecx, ecy
    s = 1.0 / math.sqrt((dx / rx) ** 2 + (dy / ry) ** 2)
    return ecx + dx * s, ecy + dy * s


# ---------------------------------------------------------------- SD layout
def sd_layout(sd, view):
    """Place the actors of `view` on a ring round the hub, and every dependum on
    the line between the two actors it joins.

    The shape is the one the piStar files carry - the shared placement in
    `istar_geometry.py`, given this renderer's own sizes: the text here is set
    at the renderer's fonts and the dependums are labelled `type: text`, so the
    boxes are not the same rectangles piStar draws, but an actor sits where the
    other drawing puts it and its dependums sit on the same lines."""
    deps = G.view_dependencies(sd, view)
    used = []
    for d in deps:
        for side in ("from", "to"):
            if d[side] not in used:
                used.append(d[side])
    actors = [a for a in sd["actors"] if a["id"] in used]
    radius_of = {a["id"]: actor_radius(a["name"]) for a in actors}

    def size_of(d):
        _, w, h = fitted_text("%s: %s" % (d["type"], d["dependum"]), DEP_FONT, 210.0,
                              4, 18.0, 38.0 if d["type"] == "softgoal" else 12.0)
        d["_w"], d["_h"] = w, h
        return (w, h)

    order, centers, boxes = G.place_ring_view(
        [a["id"] for a in actors], deps, size_of=size_of,
        circle_of=lambda aid: radius_of[aid], gap_x=34.0, gap_y=26.0,
        hub=view.get("hub"))

    rank = {aid: i for i, aid in enumerate(order)}
    actors.sort(key=lambda a: rank[a["id"]])
    bodies = {aid: (cx, cy, radius_of[aid]) for aid, (cx, cy) in centers.items()}
    for d in deps:
        d["_box"] = tuple(boxes[d["id"]])
    return actors, bodies, deps


def sd_extent(bodies, deps):
    xs, ys = [], []
    for cx, cy, rad in bodies.values():
        xs += [cx - rad, cx + rad]
        ys += [cy - rad, cy + rad]
    for d in deps:
        xs += [d["_box"][0], d["_box"][2]]
        ys += [d["_box"][1], d["_box"][3]]
    return min(xs), min(ys), max(xs), max(ys)


def render_sd(sd, view):
    actors, bodies, deps = sd_layout(sd, view)
    x0, y0, x1, y1 = sd_extent(bodies, deps)
    x0, y0, x1, y1 = x0 - PAD, y0 - PAD, x1 + PAD, y1 + PAD
    legend_h = 205.0
    origin = (x0, y0)
    width = int(round((x1 - x0) * SCALE))
    height = int(round((y1 - y0 + legend_h) * SCALE))
    img = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(img)
    off = lambda p: ((p[0] - x0) * SCALE, (p[1] - y0) * SCALE)
    lw = max(1, int(round(1.3 * SCALE)))

    for d in deps:
        b = d["_box"]
        fcx, fcy, frad = bodies[d["from"]]
        tcx, tcy, trad = bodies[d["to"]]
        frect = rect_of(fcx, fcy, frad * 2.0, frad * 2.0)
        trect = rect_of(tcx, tcy, trad * 2.0, trad * 2.0)
        draw.line([off(clip_ellipse_to_rect(frect, b)), off(clip_rect_to_rect(b, frect))],
                  fill=SOFT, width=lw)
        p3 = off(clip_rect_to_rect(b, trect))
        p4 = off(clip_ellipse_to_rect(trect, b))
        draw.line([p3, p4], fill=SOFT, width=lw)
        R._arrow(draw, p4, p3, 8.0, SCALE)

    for a in actors:
        cx, cy, _ = bodies[a["id"]]
        draw_actor_node(draw, a["name"], cx, cy, SCALE, origin)
    for d in deps:
        draw_node(draw, d["type"], "%s: %s" % (d["type"], d["dependum"]),
                  d["_box"], SCALE, origin)

    _draw_sd_legend(draw, x0, y1 + 40.0, SCALE, origin)
    return img, width, height


def _draw_sd_legend(draw, x, y, scale, origin):
    """One band below the diagram: the five shapes, and what a line means."""
    draw_actor_node(draw, "actor", x + 90.0, y + 40.0, scale, origin, font_size=9.0)
    cx = x + 230.0
    for kind in ("goal", "task", "resource", "softgoal"):
        draw_node(draw, kind, kind, (cx, y + 6.0, cx + 176.0, y + 74.0), scale, origin)
        cx += 216.0
    _text(draw, "Each line runs from the actor that has the need, through the dependum, "
                "to the actor it depends on (arrow at that end).", x + 20.0, y + 96.0,
          scale, origin)
    _text(draw, "dependum types: a goal is left to the dependee to achieve; a task is "
                "something the dependee does; a resource is something it supplies; "
                "a softgoal is a need met to a satisfactory degree.",
          x + 20.0, y + 120.0, scale, origin)


def _text(draw, text, x, y, scale, origin, color=SOFT, font=LEGEND_FONT):
    draw.text(((x - origin[0]) * scale, (y - origin[1]) * scale), text,
              font=R._font(font * scale), fill=color, anchor="la")


# ---------------------------------------------------------------- SR layout
ROW_ORDER = ("softgoal", "goal", "task", "resource")


def actor_header(actor):
    """How much of the page the actor's own circle and its one-line intent need."""
    return actor_radius(actor["name"], 13.0) * 2.0 + 96.0


def sr_layout(actor):
    """Rows by kind - soft goals at the top, resources at the bottom, which is
    the order the i* literature draws a rationale in - each row centred over the
    one below. Returns {id: (cx, cy, w, h)} and the bottom edge."""
    ordered = G.row_order(actor)
    by_id = {el["id"]: el for el in actor["elements"]}
    rows = {k: [by_id[eid] for eid in ordered[k]] for k in ROW_ORDER}
    sized, base_y = {}, actor_header(actor)
    for kind in ROW_ORDER:
        row = rows[kind]
        if not row:
            continue
        pad_x, pad_y = (44.0, 46.0) if kind == "softgoal" else (16.0, 12.0)
        measures, heights = {}, 0.0
        for el in row:
            _, w, h = fitted_text(el["name"], element_font(kind), 260.0, 4, pad_x, pad_y)
            measures[el["id"]] = (w, h)
            heights = max(heights, h)
        gap = 30.0
        total = sum(measures[el["id"]][0] for el in row) + gap * (len(row) - 1)
        x = -total / 2.0
        for el in row:
            w, h = measures[el["id"]]
            sized[el["id"]] = (x + w / 2.0, base_y + h / 2.0, w, h)
            x += w + gap
        base_y += heights + ROW_GAP
    return sized, base_y


def render_sr(actor):
    sized, _ = sr_layout(actor)
    xs, ys = [], []
    for cx, cy, w, h in sized.values():
        xs += [cx - w / 2.0, cx + w / 2.0]
        ys += [cy - h / 2.0, cy + h / 2.0]
    half = max(max(abs(v) for v in xs) + 130.0, 400.0)
    bn = 14.0
    x0, x1 = -half, half
    y0, y1 = 0.0, max(ys) + 76.0
    # the legend wraps to the page - its first line is long enough to run past a
    # narrow page - and the band below the boundary has to be as tall as the
    # lines the two of them need, or the last line is drawn off the canvas
    limit = R.text_width("x" * 200, LEGEND_FONT) * SCALE
    wrap_at = 200.0 * ((x1 - x0) * SCALE - 48.0) / limit / SCALE
    legend = (R.wrap(
        "means-end:  a line to the goal it serves.    decomposition (AND/OR):  the "
        "parts of a task.    neededBy:  a resource the task needs (circle at the task)."
        "    contribution:  make / help / some+ / unknown / some- / hurt / break.",
        LEGEND_FONT, wrap_at) + R.wrap(
        "A dashed element labelled '%s' is a need the case states and this release "
        "does not satisfy: it carries no contribution link." % GAP_LABEL,
        LEGEND_FONT, wrap_at))
    legend_h = 30.0 + 22.0 * len(legend)
    width = int(round((x1 - x0) * SCALE))
    height = int(round((y1 - y0 + legend_h) * SCALE))
    img = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(img)
    origin = (x0, y0)
    off = lambda p: ((p[0] - x0) * SCALE, (p[1] - y0) * SCALE)
    lw = max(1, int(round(1.4 * SCALE)))

    draw.rounded_rectangle([off((x0 + bn, y0 + bn)), off((x1 - bn, y1 - bn))],
                           radius=18 * SCALE, fill="white", outline=BOUNDARY,
                           width=max(1, int(round(1.7 * SCALE))))

    # the boxes the link labels have to keep off, in image pixels - the same
    # units as the link ends they are placed against
    occupied = [(off((b[0], b[1]))[0], off((b[0], b[1]))[1],
                 off((b[2], b[3]))[0], off((b[2], b[3]))[1])
                for b in (rect_of(cx, cy, w, h) for cx, cy, w, h in sized.values())]
    chips = []
    for el in actor["elements"]:
        cx, cy, w, h = sized[el["id"]]
        a = rect_of(cx, cy, w, h)
        for link in el.get("links", []):
            if link["to"] not in sized:
                continue
            tx, ty, tw, th = sized[link["to"]]
            b = rect_of(tx, ty, tw, th)
            p1, p2 = off(clip_rect_to_rect(a, b)), off(clip_rect_to_rect(b, a))
            kind = link["type"]
            label = None
            if kind in CONTRIBUTIONS:
                draw.line([p1, p2], fill=SOFT, width=lw)
                R._arrow(draw, p2, p1, 7.5, SCALE)
                label = (kind, GAP_INK if kind in NEGATIVE else SOFT)
            else:
                draw.line([p1, p2], fill=HEAD, width=lw)
                if kind == "means-end":
                    R._arrow(draw, p2, p1, 8.5, SCALE)
                elif kind == "decomposition":
                    _diamond(draw, p1, p2, SCALE)
                    label = ((link.get("mode") or "and").upper(), HEAD)
                elif kind == "neededby":
                    _circle_marker(draw, p2, SCALE)
            if label:
                chips.append((label[0], _free_spot(p1, p2, occupied), label[1]))

    for el in actor["elements"]:
        cx, cy, w, h = sized[el["id"]]
        draw_node(draw, el["kind"], el["name"], rect_of(cx, cy, w, h), SCALE, origin,
                  dashed=bool(el.get("gap")))
    for text, mid, color in chips:               # after the nodes, so nothing hides them
        _chip(draw, text, mid, SCALE, color)
    for el in actor["elements"]:
        if not el.get("gap"):
            continue
        cx, cy, w, h = sized[el["id"]]
        px, py = off((cx - w / 2.0, cy - h / 2.0))
        draw.text((px, py - 3 * SCALE), GAP_LABEL, font=R._font(LEGEND_FONT * SCALE),
                  fill=GAP_INK, anchor="ls")

    rad = actor_radius(actor["name"], 13.0)
    draw_actor_node(draw, actor["name"], x0 + bn + 30.0 + rad, y0 + bn + 30.0 + rad,
                    SCALE, origin, font_size=13.0)
    _text(draw, actor["intent"], x0 + bn + 40.0, y0 + bn + 62.0 + rad * 2.0, SCALE,
          origin, color=HEAD, font=11.0)
    for i, line in enumerate(legend):
        _text(draw, line, x0 + 24.0, y1 + 6.0 + i * 22.0, SCALE, origin)
    return img, width, height


def _free_spot(p1, p2, boxes):
    """A point on the link that no element box is sitting on.

    The middle of a link is the natural place for a link's label, but in a row
    of elements two neighbours are often joined past a third, so the label lands
    on it. The link is walked from end to end and the middle of the longest
    stretch clear of every box is taken - falling back to the middle of the line
    when it is over a box the whole way - so a label sits beside an element
    rather than across its text."""
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    length = math.hypot(dx, dy) or 1.0
    px, py = -dy / length, dx / length

    def clear(x, y):
        for b in boxes:
            if (b[0] - 7 * SCALE <= x <= b[2] + 7 * SCALE and
                    b[1] - 6 * SCALE <= y <= b[3] + 6 * SCALE):
                return False
        return True

    steps = 96
    runs, start = [], None
    for i in range(steps + 1):
        t = i / float(steps)
        if clear(p1[0] + dx * t, p1[1] + dy * t):
            if start is None:
                start = t
        elif start is not None:
            runs.append((start, t))
            start = None
    if start is not None:
        runs.append((start, 1.0))
    if not runs:                                     # over a box the whole way
        return ((p1[0] + p2[0]) / 2.0, (p1[1] + p2[1]) / 2.0)
    # the longest clear stretch wins, and the middle of the line breaks a tie so
    # a label stays where a reader looks for it when the link is clear through
    # its middle
    run = max(runs, key=lambda r: (r[1] - r[0], -abs((r[0] + r[1]) / 2.0 - 0.5)))
    t = (run[0] + run[1]) / 2.0
    if run[1] - run[0] < 0.16:                       # too short to hold a chip
        for offset in (0.0, 16.0, -16.0, 30.0, -30.0):
            x = p1[0] + dx * t + px * offset
            y = p1[1] + dy * t + py * offset
            if clear(x, y):
                return (x, y)
    return (p1[0] + dx * t, p1[1] + dy * t)


def _chip(draw, text, centre, scale, color=SOFT):
    if not text:
        return
    w = R.text_width(text, LINK_FONT) * scale
    h = LINK_FONT * R.LINE_SPACING * scale
    x, y = centre
    draw.rectangle([x - w / 2.0 - 3 * scale, y - h / 2.0 - 1.5 * scale,
                    x + w / 2.0 + 3 * scale, y + h / 2.0 + 1.5 * scale], fill="white")
    draw.text((x, y), text, font=R._font(LINK_FONT * scale), fill=color, anchor="mm")


def _circle_marker(draw, point, scale):
    """The marker a neededBy link carries at its task end: piStar draws a small
    circle there, so the two drawings of the same model read alike."""
    r = 3.4 * scale
    draw.ellipse((point[0] - r, point[1] - r, point[0] + r, point[1] + r), fill=HEAD)


def _diamond(draw, p1, p2, scale):
    dx, dy = p2[0] - p1[0], p2[1] - p1[1]
    length = math.hypot(dx, dy) or 1.0
    ux, uy = dx / length, dy / length
    size = 8.5 * scale
    cx, cy = p1[0] + ux * size * 0.9, p1[1] + uy * size * 0.9
    draw.polygon([(cx, cy - size * 0.55), (cx + size * 0.55, cy),
                  (cx, cy + size * 0.55), (cx - size * 0.55, cy)], fill=HEAD, outline=HEAD)


# ------------------------------------------------------------------- tables
def write_tables(sd, sr):
    lines = ["# The models as tables", "",
             "> **Generated** by `python tools/istar_render.py` from `sd-hprtas.json` and",
             "> `sr-hprtas.json` - do not edit by hand. These tables are the content of the",
             "> diagrams; the prose that explains the modelling choices is in",
             "> `modelling-choices.md`.", "",
             "Baseline: %s" % sd["baseline"], ""]
    rationale = {}
    for actor in sr["actors"]:
        for el in actor["elements"]:
            for dep in el.get("deps", []):
                rationale.setdefault(dep, []).append("%s `%s`" % (actor["actor"], el["id"]))
    lines += ["## 1. The Strategic Dependency model", "",
              "`A -> B` reads *A depends on B for the dependum*. The `Rationale` column names the",
              "SR elements that account for the dependency. Every dependency here is one the",
              "workshop goal model states; the interface it leaves unstated, and why, is recorded",
              "in `modelling-choices.md` section 3.", "",
              "| # | Depender | Dependee | Type | Dependum | Rationale |",
              "|---|---|---|---|---|---|"]
    for d in sd["dependencies"]:
        lines.append("| %s | %s | %s | %s | %s | %s |" % (
            d["id"], d["from"], d["to"], d["type"], d["dependum"],
            "<br>".join(rationale.get(d["id"], ["-"]))))
    lines += ["", "## 2. The Strategic Rationale model", "",
              "One page per actor. `Accounts for` names the SD dependencies the element carries;",
              "`Links` the rationale links leaving it.", ""]
    for actor in sr["actors"]:
        lines += ["### %s `%s`" % (actor["name"], actor["actor"]), "",
                  "*%s*" % actor["intent"], "",
                  "| Kind | Id | Element | Accounts for | Links | Gap |",
                  "|---|---|---|---|---|---|"]
        for el in actor["elements"]:
            links = ", ".join("%s -> %s%s" % (l["type"], l["to"],
                                              " (%s)" % l["mode"] if l.get("mode") else "")
                              for l in el.get("links", []))
            lines.append("| %s | %s | %s | %s | %s | %s |" % (
                el["kind"], el["id"], el["name"],
                ", ".join(el.get("deps", [])) or "-", links or "-",
                ("yes" if el.get("gap") else "-")))
        lines.append("")
    path = os.path.join(MODEL_DIR, "tables.md")
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")
    return path


# --------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pdf-only", action="store_true")
    args = ap.parse_args()

    sd = json.load(open(SD_PATH, encoding="utf-8"))
    sr = json.load(open(SR_PATH, encoding="utf-8"))
    os.makedirs(OUT_DIR, exist_ok=True)

    pages = []
    for view in sd["views"]:
        img, w, h = render_sd(sd, view)
        name = "istar-sd-%s" % view["id"]
        if not args.pdf_only:
            img.save(os.path.join(OUT_DIR, name + ".png"), optimize=True)
        pages.append(img)
        print("%-32s %5d x %5d px" % (name, w, h))
    pages[0].save(os.path.join(OUT_DIR, "istar-sd-hprtas.pdf"), "PDF",
                  save_all=True, append_images=pages[1:], resolution=SCALE * 72.0)

    pages = []
    for actor in sr["actors"]:
        img, w, h = render_sr(actor)
        name = "istar-sr-%s" % actor["actor"]
        if not args.pdf_only:
            img.save(os.path.join(OUT_DIR, name + ".png"), optimize=True)
        pages.append(img)
        print("%-32s %5d x %5d px" % (name, w, h))
    pages[0].save(os.path.join(OUT_DIR, "istar-sr-hprtas.pdf"), "PDF",
                  save_all=True, append_images=pages[1:], resolution=SCALE * 72.0)

    print("tables -> %s" % write_tables(sd, sr))


if __name__ == "__main__":
    main()
