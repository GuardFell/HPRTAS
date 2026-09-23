# -*- coding: utf-8 -*-
"""Parse a BPMN 2.0 file and draw it to PNG, plus the geometry checks that go
with it.

Written for the W02 gateway tutorial: the answer diagrams have to be embedded in
a Word document, and Camunda Modeler cannot be driven head-less, so the diagram
is redrawn from the BPMN DI that `build_cook_meal_bpmn.py` writes.  Only the
constructs those five models use are supported - events, tasks, the three
gateway types, sequence flows, text annotations and associations.

Model units are Camunda Modeler's pixels: a task is 100x80, a gateway 50x50, an
event 36x36.  Labels are drawn from the bounds stored in the file, so what comes
out here matches what Modeler shows.

Shared by build_cook_meal_bpmn.py (draws the PNGs) and verify_cook_meal_bpmn.py
(checks the geometry), so both sides agree on what a collision is.
"""
import math
import os
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont

BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL"
BPMNDI = "http://www.omg.org/spec/BPMN/20100524/DI"
DC = "http://www.omg.org/spec/DD/20100524/DC"
DI = "http://www.omg.org/spec/DD/20100524/DI"
NS = {"bpmn": BPMN, "bpmndi": BPMNDI, "dc": DC, "di": DI}

ARIAL = r"C:\Windows\Fonts\arial.ttf"

SHAPE_KINDS = {
    "startEvent": "start",
    "endEvent": "end",
    "task": "task",
    "userTask": "task",
    "serviceTask": "task",
    "exclusiveGateway": "xor",
    "parallelGateway": "and",
    "inclusiveGateway": "or",
    "complexGateway": "complex",
    "textAnnotation": "annotation",
}
EDGE_KINDS = {"sequenceFlow": "flow", "association": "association"}

# Stroke and fill, i.e. the Camunda Modeler palette.
INK = (34, 36, 42)
FLOW_INK = (34, 36, 42)
GRAY = (130, 134, 140)

LABEL_SIZE = 17.0        # normal label, in model units
SMALL_SIZE = 15.0        # annotations
FLOW_SIZE = 14.0         # conditions on sequence flows
LINE_SPACING = 1.21

# ------------------------------------------------------------------ text metrics
_font_cache = {}


def _font(size_px):
    size_px = max(6, int(round(size_px)))
    if size_px not in _font_cache:
        _font_cache[size_px] = ImageFont.truetype(ARIAL, size_px)
    return _font_cache[size_px]


_measure_font = ImageFont.truetype(ARIAL, 200)


def text_width(text, size_units):
    """Width of `text` when set at *size_units* model units."""
    return _measure_font.getlength(text) * size_units / 200.0


def wrap(text, size_units, max_units):
    """Greedy word wrap; a single word longer than the box is left to overflow.

    The limit carries a little slack: label boxes are sized from these same
    measurements and stored rounded to one decimal in the BPMN, so a line that
    fits exactly would otherwise re-wrap when the file is read back and spill
    out of its one-line box.
    """
    limit = max_units * 1.02 + 0.5
    words = text.split()
    if not words:
        return [""]
    lines, current = [], words[0]
    for word in words[1:]:
        trial = current + " " + word
        if text_width(trial, size_units) <= limit:
            current = trial
        else:
            lines.append(current)
            current = word
    lines.append(current)
    return lines


def label_box(text, size_units, max_units, center_x, top_y, center_text=True):
    """Bounds of a label block: (x, y, w, h) in model units.

    Follows what Modeler stores in BPMNLabel: width is the text (capped at
    max_units, which is where it wraps) and height is one line per row.
    """
    lines = wrap(text, size_units, max_units)
    width = max(text_width(line, size_units) for line in lines)
    width = min(width, max_units)
    height = len(lines) * size_units * LINE_SPACING
    x = center_x - width / 2.0 if center_text else center_x
    return x, top_y, width, height


# ------------------------------------------------------------------ parsing
class Model(object):
    """The bits of a BPMN file this project needs: shapes, edges and names."""

    def __init__(self, path):
        tree = ET.parse(path)
        root = tree.getroot()
        self.path = path
        self.process = root.find("bpmn:process", NS)
        self.process_id = self.process.get("id")
        self.process_name = self.process.get("name")

        self.shapes = {}          # id -> dict(kind, name, x, y, w, h, label, marker)
        for el in self.process:
            kind = SHAPE_KINDS.get(_local(el.tag))
            if kind is None or kind == "annotation":
                continue
            self.shapes[el.get("id")] = {
                "id": el.get("id"), "kind": kind, "name": el.get("name") or "",
                "default": el.get("default") or "",
                "outgoing": [f.text for f in el.findall("bpmn:outgoing", NS)],
                "incoming": [f.text for f in el.findall("bpmn:incoming", NS)],
            }

        self.flows = {}           # id -> dict(kind, src, tgt, condition, name, waypoints, label)
        for el in self.process:
            kind = EDGE_KINDS.get(_local(el.tag))
            if kind is None:
                continue
            cond = el.find("bpmn:conditionExpression", NS)
            self.flows[el.get("id")] = {
                "id": el.get("id"), "kind": kind,
                "src": el.get("sourceRef"), "tgt": el.get("targetRef"),
                "name": el.get("name") or "",
                "condition": (cond.text or "").strip() if cond is not None else "",
                "waypoints": [], "label": None,
            }

        self.annotations = {}     # id -> dict(text, x, y, w, h, assoc_to)

        for plane in root.iter("{%s}BPMNPlane" % BPMNDI):
            for shape in plane.findall("bpmndi:BPMNShape", NS):
                b = shape.find("dc:Bounds", NS)
                box = (float(b.get("x")), float(b.get("y")),
                       float(b.get("width")), float(b.get("height")))
                label = shape.find("bpmndi:BPMNLabel", NS)
                label_box = _bounds(label) if label is not None else None
                element = shape.get("bpmnElement")
                if element in self.shapes:
                    x, y, w, h = box
                    self.shapes[element].update(
                        x=x, y=y, w=w, h=h, label=label_box,
                        marker=shape.get("isMarkerVisible") == "true")
                else:
                    text = _annotation_text(self.process, element)
                    self.annotations[element] = {
                        "id": element, "text": text, "x": box[0], "y": box[1],
                        "w": box[2], "h": box[3], "label": label_box,
                        "target": None, "points": []}
            for edge in plane.findall("bpmndi:BPMNEdge", NS):
                element = edge.get("bpmnElement")
                points = [(float(p.get("x")), float(p.get("y")))
                          for p in edge.findall("di:waypoint", NS)]
                label = edge.find("bpmndi:BPMNLabel", NS)
                label_box = _bounds(label) if label is not None else None
                if element in self.flows:
                    self.flows[element]["waypoints"] = points
                    self.flows[element]["label"] = label_box
                elif element in self.annotations:
                    self.annotations[element]["points"] = points

        for ann in self.annotations.values():
            if ann["points"]:
                ann["target"] = _other_end(self.flows, ann["points"])
            # an annotation is associated with exactly one flow in this project
        for flow in self.flows.values():
            if flow["kind"] == "association":
                for ann in self.annotations.values():
                    if flow["src"] == ann["id"]:
                        ann["assoc_to"] = flow["tgt"]
                    elif flow["tgt"] == ann["id"]:
                        ann["assoc_to"] = flow["src"]
        self.associations = [f for f in self.flows.values() if f["kind"] == "association"]

    def bounds(self):
        """Bounding box of everything that has to be drawn, in model units."""
        xs, ys = [], []
        for s in list(self.shapes.values()) + list(self.annotations.values()):
            xs += [s["x"], s["x"] + s["w"]]
            ys += [s["y"], s["y"] + s["h"]]
            if s["label"]:
                lx, ly, lw, lh = s["label"]
                xs += [lx, lx + lw]
                ys += [ly, ly + lh]
        for f in self.flows.values():
            for x, y in f["waypoints"]:
                xs.append(x)
                ys.append(y)
            if f["label"]:
                lx, ly, lw, lh = f["label"]
                xs += [lx, lx + lw]
                ys += [ly, ly + lh]
        return min(xs), min(ys), max(xs), max(ys)


def _local(tag):
    return tag.rsplit("}", 1)[-1]


def _bounds(label):
    b = label.find("dc:Bounds", NS)
    if b is None:
        return None
    return (float(b.get("x")), float(b.get("y")),
            float(b.get("width")), float(b.get("height")))


def _annotation_text(process, element_id):
    for el in process.iter("{%s}textAnnotation" % BPMN):
        if el.get("id") == element_id:
            return (el.findtext("bpmn:text", default="", namespaces=NS) or "").strip()
    return ""


def _other_end(flows, points):
    for f in flows.values():
        if f["kind"] != "association":
            continue
        if points and (points[0] == tuple(f["waypoints"][0]) or
                       points[-1] == tuple(f["waypoints"][-1])):
            return f["src"]
    return None


# ------------------------------------------------------------------ drawing
def _arrow(draw, tip, previous, size, scale):
    """White-filled triangle, the sequence flow marker Camunda draws."""
    tx, ty = tip
    px, py = previous
    angle = math.atan2(ty - py, tx - px)
    length = size * scale
    spread = math.radians(20)
    left = (tx - length * math.cos(angle - spread), ty - length * math.sin(angle - spread))
    right = (tx - length * math.cos(angle + spread), ty - length * math.sin(angle + spread))
    draw.polygon([tip, left, right], fill="white", outline=INK, width=max(1, int(scale)))
    # re-draw the two flanks so the stroke matches the flow
    draw.line([left, tip, right], fill=INK, width=max(1, int(round(1.4 * scale))))


def _draw_label(draw, text, box, size_units, scale, origin, align="center", color=INK):
    """Draw `text` inside a model-unit box; `origin` is the canvas offset."""
    x, y, w, h = box
    ox, oy = origin
    lines = wrap(text, size_units, w)
    font = _font(size_units * scale)
    line_h = size_units * LINE_SPACING * scale
    total = len(lines) * line_h
    top = (y - oy) * scale + (h * scale - total) / 2.0
    left = (x - ox) * scale
    for i, line in enumerate(lines):
        if align == "center":
            draw.text((left + w * scale / 2.0, top + i * line_h),
                      line, font=font, fill=color, anchor="ma")
        else:
            draw.text((left, top + i * line_h), line, font=font, fill=color,
                      anchor="la")


def render(path, png_path, scale=2.5, pad=14):
    """Draw the model to `png_path`; returns (width, height) in pixels."""
    model = Model(path)
    x0, y0, x1, y1 = model.bounds()
    x0 -= pad
    y0 -= pad
    x1 += pad
    y1 += pad
    width = int(round((x1 - x0) * scale))
    height = int(round((y1 - y0) * scale))

    img = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(img)
    off = lambda p: ((p[0] - x0) * scale, (p[1] - y0) * scale)

    # --- flows and associations first, so shapes sit on top of the line ends
    for flow in model.flows.values():
        if not flow["waypoints"]:
            continue
        pts = [off(p) for p in flow["waypoints"]]
        if flow["kind"] == "association":
            _dashed(draw, pts, scale)
        else:
            draw.line(pts, fill=FLOW_INK, width=max(1, int(round(1.4 * scale))),
                      joint="curve")
            if len(pts) >= 2:
                _arrow(draw, pts[-1], pts[-2], 9.0, scale)

    # --- shapes
    for s in model.shapes.values():
        kind = s["kind"]
        x, y = off((s["x"], s["y"]))
        w, h = s["w"] * scale, s["h"] * scale
        box = (x, y, x + w, y + h)
        if kind == "task":
            draw.rounded_rectangle(box, radius=10 * scale, fill="white", outline=INK,
                                   width=max(1, int(round(1.5 * scale))))
        elif kind == "start":
            _ring(draw, box, 1.5 * scale, 0)
        elif kind == "end":
            _ring(draw, box, 3.4 * scale, 0)
        elif kind in ("xor", "and", "or", "complex"):
            cx, cy, r = x + w / 2.0, y + h / 2.0, w / 2.0
            draw.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)],
                         fill="white", outline=INK, width=max(1, int(round(1.5 * scale))))
            _gateway_marker(draw, kind, cx, cy, r, scale)

    # --- text annotations: the bracket marker plus the text
    for ann in model.annotations.values():
        x, y, w, h = ann["x"], ann["y"], ann["w"], ann["h"]
        tick = min(8.0, h / 3.0)
        lw = max(1, int(round(1.3 * scale)))
        draw.line([off((x, y)), off((x, y + h))], fill=GRAY, width=lw)
        draw.line([off((x, y)), off((x + tick, y))], fill=GRAY, width=lw)
        draw.line([off((x, y + h)), off((x + tick, y + h))], fill=GRAY, width=lw)
        box = (x + tick + 3, y + 2, w - tick - 5, h - 4)
        _draw_label(draw, ann["text"], box, SMALL_SIZE, scale, (x0, y0),
                    align="left", color=GRAY)

    # --- labels
    for s in model.shapes.values():
        if not s["name"] or not s["label"]:
            continue
        _draw_label(draw, s["name"], s["label"], LABEL_SIZE, scale, (x0, y0))
    for flow in model.flows.values():
        text = flow["condition"] or flow["name"]
        if text and flow["label"]:
            _draw_label(draw, text, flow["label"], FLOW_SIZE, scale, (x0, y0),
                        color=GRAY)

    os.makedirs(os.path.dirname(png_path), exist_ok=True)
    img.save(png_path)
    return width, height


def _ring(draw, box, width, inner):
    draw.ellipse(box, fill="white", outline=INK, width=max(1, int(round(width))))
    if inner:
        x0, y0, x1, y1 = box
        draw.ellipse((x0 + inner, y0 + inner, x1 - inner, y1 - inner), outline=INK,
                     width=max(1, int(round(width))))


def _gateway_marker(draw, kind, cx, cy, r, scale):
    lw = max(1, int(round(1.5 * scale)))
    m = r * 0.45
    if kind == "xor":
        draw.line([(cx - m, cy - m), (cx + m, cy + m)], fill=INK, width=lw)
        draw.line([(cx - m, cy + m), (cx + m, cy - m)], fill=INK, width=lw)
    elif kind == "and":
        draw.line([(cx - m, cy), (cx + m, cy)], fill=INK, width=lw)
        draw.line([(cx, cy - m), (cx, cy + m)], fill=INK, width=lw)
    elif kind == "or":
        draw.ellipse((cx - m, cy - m, cx + m, cy + m), outline=INK, width=lw)
    elif kind == "complex":
        draw.line([(cx - m, cy), (cx + m, cy)], fill=INK, width=lw)
        draw.line([(cx, cy - m), (cx, cy + m)], fill=INK, width=lw)


def _dashed(draw, pts, scale, dash=5.0, gap=4.0):
    """Association line: dotted, as Camunda draws it."""
    lw = max(1, int(round(1.3 * scale)))
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        length = math.hypot(bx - ax, by - ay)
        if length == 0:
            continue
        ux, uy = (bx - ax) / length, (by - ay) / length
        pos = 0.0
        while pos < length:
            end = min(pos + dash * scale, length)
            draw.line([(ax + ux * pos, ay + uy * pos), (ax + ux * end, ay + uy * end)],
                      fill=GRAY, width=lw)
            pos += (dash + gap) * scale


# ------------------------------------------------------------------ checks
def ink_report(png_path):
    """Ink bounding box of a rendered PNG: (width, height, left, top, right, bottom).

    A drawing that is translated wrongly, or that overflows its canvas, shows up
    as zero - or negative - margins, so the build script asserts on them.
    """
    from PIL import Image
    img = Image.open(png_path).convert("L")
    width, height = img.size
    pixels = img.load()
    xs, ys = [], []
    for y in range(0, height, 2):
        for x in range(0, width, 2):
            if pixels[x, y] < 250:
                xs.append(x)
                ys.append(y)
    if not xs:
        return width, height, None, None, None, None
    return width, height, min(xs), min(ys), width - 1 - max(xs), height - 1 - max(ys)


def _segments_cross(a, b, c, d, tol=1e-6):
    """True when segment ab and cd cross away from a shared endpoint."""
    def orient(p, q, r):
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])

    def on(p, q, r):
        return (min(p[0], r[0]) - tol <= q[0] <= max(p[0], r[0]) + tol and
                min(p[1], r[1]) - tol <= q[1] <= max(p[1], r[1]) + tol)

    for p in (a, b):
        for q in (c, d):
            if abs(p[0] - q[0]) < 1.0 and abs(p[1] - q[1]) < 1.0:
                return False                      # share an endpoint
    o1, o2 = orient(a, b, c), orient(a, b, d)
    o3, o4 = orient(c, d, a), orient(c, d, b)
    if o1 * o2 < -tol and o3 * o4 < -tol:
        return True
    if abs(o1) < tol and on(a, c, b):
        return True
    if abs(o2) < tol and on(a, d, b):
        return True
    if abs(o3) < tol and on(c, a, d):
        return True
    if abs(o4) < tol and on(c, b, d):
        return True
    return False


def _rect_hits_segment(rect, p, q, inner=1.0):
    x, y, w, h = rect
    x0, y0, x1, y1 = x + inner, y + inner, x + w - inner, y + h - inner
    if x1 <= x0 or y1 <= y0:
        return False
    edges = [((x0, y0), (x1, y0)), ((x1, y0), (x1, y1)),
             ((x1, y1), (x0, y1)), ((x0, y1), (x0, y0))]
    for a, b in edges:
        if _segments_cross(a, b, p, q):
            return True
    # fully inside
    return x0 <= p[0] <= x1 and y0 <= p[1] <= y1


def _rects_overlap(a, b, gap=0.0):
    return not (a[0] + a[2] + gap <= b[0] or b[0] + b[2] + gap <= a[0] or
                a[1] + a[3] + gap <= b[1] or b[1] + b[3] + gap <= a[1])


def layout_problems(model_path, annotation_pad=2.0):
    """Return a list of human-readable geometry problems, empty when clean."""
    m = Model(model_path)
    problems = []

    boxes = {s["id"]: (s["x"], s["y"], s["w"], s["h"]) for s in m.shapes.values()}
    for ann in m.annotations.values():
        boxes[ann["id"]] = (ann["x"], ann["y"], ann["w"], ann["h"])

    ids = list(boxes)
    for i, a in enumerate(ids):
        for b in ids[i + 1:]:
            if _rects_overlap(boxes[a], boxes[b], gap=annotation_pad):
                problems.append("shapes overlap: %s / %s" % (a, b))

    flows = [f for f in m.flows.values() if f["kind"] == "flow"]
    # flows must start and end on the border of their node
    for f in flows:
        if not f["waypoints"]:
            problems.append("flow without waypoints: %s" % f["id"])
            continue
        for end, node in ((f["waypoints"][0], f["src"]), (f["waypoints"][-1], f["tgt"])):
            box = boxes.get(node)
            if box is None:
                problems.append("flow %s refers to unknown node %s" % (f["id"], node))
                continue
            x, y, w, h = box
            on_border = (
                abs(end[0] - x) < 1.5 or abs(end[0] - (x + w)) < 1.5 or
                abs(end[1] - y) < 1.5 or abs(end[1] - (y + h)) < 1.5)
            inside = x - 1.5 <= end[0] <= x + w + 1.5 and y - 1.5 <= end[1] <= y + h + 1.5
            if not (inside and on_border):
                problems.append("flow %s does not touch %s at %s" % (f["id"], node, end))

    # flows must not cut through other shapes
    for f in flows:
        for p, q in zip(f["waypoints"], f["waypoints"][1:]):
            for node, box in boxes.items():
                if node in (f["src"], f["tgt"]):
                    # allow the first/last stub only
                    if _rect_hits_segment(box, p, q, inner=3.0):
                        if not (node in (f["src"], f["tgt"]) and
                                (tuple(p) in (tuple(f["waypoints"][0]), tuple(f["waypoints"][-1])) or
                                 tuple(q) in (tuple(f["waypoints"][0]), tuple(f["waypoints"][-1])))):
                            problems.append("flow %s crosses shape %s" % (f["id"], node))
                    continue
                if _rect_hits_segment(box, p, q, inner=0.5):
                    problems.append("flow %s crosses shape %s" % (f["id"], node))

    # flows must not cross each other
    for i, f in enumerate(flows):
        for g in flows[i + 1:]:
            for p, q in zip(f["waypoints"], f["waypoints"][1:]):
                for r, s in zip(g["waypoints"], g["waypoints"][1:]):
                    if _segments_cross(p, q, r, s):
                        problems.append("flows cross: %s / %s" % (f["id"], g["id"]))

    # associations must not cross flows, and labels must not sit on other shapes
    for assoc in m.associations:
        for p, q in zip(assoc["waypoints"], assoc["waypoints"][1:]):
            for f in flows:
                for r, s in zip(f["waypoints"], f["waypoints"][1:]):
                    if _segments_cross(p, q, r, s):
                        problems.append("association %s crosses flow %s" % (assoc["id"], f["id"]))
    for f in flows:
        if f["label"]:
            for node, box in boxes.items():
                if node in (f["src"], f["tgt"]):
                    continue
                if _rects_overlap(f["label"], box):
                    problems.append("label of %s overlaps shape %s" % (f["id"], node))
    for s in m.shapes.values():
        if s["label"]:
            for node, box in boxes.items():
                if node == s["id"]:
                    continue
                if _rects_overlap(s["label"], box):
                    problems.append("label of %s overlaps shape %s" % (s["id"], node))

    # labels must not sit on each other either: a task name and a flow
    # condition that collide are unreadable in print
    labels = [(s["id"], s["label"]) for s in m.shapes.values() if s["label"]]
    labels += [("flow " + f["id"], f["label"]) for f in flows if f["label"]]
    for i, (name_a, box_a) in enumerate(labels):
        for name_b, box_b in labels[i + 1:]:
            if _rects_overlap(box_a, box_b):
                problems.append("labels overlap: %s / %s" % (name_a, name_b))

    # and every label must fit inside the box the BPMN gives it: a label that
    # needs more lines than there is height for is drawn outside its shape
    def fits(text, box, size):
        lines = wrap(text, size, box[2])
        return len(lines) * size * LINE_SPACING <= box[3] + 0.5

    for s in m.shapes.values():
        if s["name"] and s["label"] and not fits(s["name"], s["label"], LABEL_SIZE):
            problems.append("label of %s does not fit its box %s" % (s["id"], s["label"]))
    for f in flows:
        text = f["condition"] or f["name"]
        if text and f["label"] and not fits(text, f["label"], FLOW_SIZE):
            problems.append("label of flow %s does not fit its box" % f["id"])
    for ann in m.annotations.values():
        inner = (ann["x"] + 11, ann["y"] + 2, max(ann["w"] - 13, 1), ann["h"] - 4)
        if not fits(ann["text"], inner, SMALL_SIZE):
            problems.append("annotation %s does not fit its box" % ann["id"])
    return problems
