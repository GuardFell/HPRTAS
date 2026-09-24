# -*- coding: utf-8 -*-
"""Export the HPRTAS BPMN models to PDF, one page per model.

`bpmn_render.py` draws the flat W02 tutorial diagrams: it knows tasks, events,
gateways and sequence flows, and nothing about pools, lanes or message flows.
The operational and strategic models here are collaborations, so this module
draws the whole diagram - the pools and lanes as containers, the message flows
that cross them, and the sequence flows inside them - straight from the diagram
interchange in the file.

Everything is drawn at the position the file records, including the label
boxes, so the output is what the model says rather than a re-layout. The page is
sized to the diagram and an element label is set to `TARGET_PT`, which is what
makes the export readable: a model this wide cannot be squeezed onto A4 without
the labels becoming unreadable.

Usage:
    python tools\\export_hprtas_bpmn_pdf.py            # every model, both PDF and PNG
    python tools\\export_hprtas_bpmn_pdf.py --pdf-only
"""
import argparse
import glob
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpmn_render as R  # noqa: E402

BPMN = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
BPMNDI = "{http://www.omg.org/spec/BPMN/20100524/DI}"
DC = "{http://www.omg.org/spec/DD/20100524/DC}"
DI = "{http://www.omg.org/spec/DD/20100524/DI}"

# the repository root: <repo>/tools/<script>.py, so the tools run from any checkout
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO, "models", "exports")

SCALE = 2.0               # image pixels per model unit
TARGET_PT = 10.0          # point size an element label is set at on the page
PAD = 20.0                # model units of white space around the diagram

NODE_TAGS = ("startEvent", "endEvent", "userTask", "serviceTask", "manualTask",
             "exclusiveGateway", "parallelGateway", "inclusiveGateway",
             "boundaryEvent", "intermediateCatchEvent", "intermediateThrowEvent",
             "callActivity", "subProcess")

CONTAINER_INK = (150, 154, 160)
LANE_FILL = (252, 252, 252)


def bounds(el):
    b = el.find(DC + "Bounds")
    if b is None:
        return None
    return (float(b.get("x")), float(b.get("y")),
            float(b.get("width")), float(b.get("height")))


def label_bounds(el):
    lb = el.find(BPMNDI + "BPMNLabel")
    if lb is None:
        return None
    return bounds(lb)


def _is_vertical(box):
    """True for a label box the model turns a quarter turn; see `_size_for`."""
    return box is not None and box[3] > box[2]


class Diagram(object):
    """Everything the drawing needs, read from one .bpmn file."""

    def __init__(self, path):
        import xml.etree.ElementTree as ET
        root = ET.parse(path).getroot()
        self.path = path
        self.pools = []        # (bounds, name, label bounds)
        self.lanes = []
        self.shapes = {}       # element id -> dict(kind, name, box, label)
        self.edges = []        # dict(kind, points, label box, text)

        names = {}
        for tag in ("participant", "lane") + NODE_TAGS:
            for el in root.iter(BPMN + tag):
                if el.get("id"):
                    names[el.get("id")] = (tag, el.get("name") or "")

        flows = {}
        for tag in ("sequenceFlow", "messageFlow", "association"):
            for el in root.iter(BPMN + tag):
                cond = el.find(BPMN + "conditionExpression")
                text = (cond.text or "").strip() if cond is not None else ""
                if text.startswith("="):
                    text = text[1:].strip()
                flows[el.get("id")] = (tag, el.get("name") or "", text)

        boxes, labels = {}, {}
        for shape in root.iter(BPMNDI + "BPMNShape"):
            eid = shape.get("bpmnElement")
            boxes[eid] = bounds(shape)
            lb = label_bounds(shape)
            if lb is not None:
                labels[eid] = lb
            if eid not in names or boxes[eid] is None:
                continue
            tag, name = names[eid]
            if tag == "participant":
                self.pools.append((boxes[eid], name, labels.get(eid)))
            elif tag == "lane":
                self.lanes.append((boxes[eid], name, labels.get(eid)))

        for eid, (tag, name) in names.items():
            if tag in ("participant", "lane") or eid not in boxes:
                continue
            self.shapes[eid] = {
                "kind": R.SHAPE_KINDS.get(tag, "task"),
                "name": name,
                "box": boxes[eid],
                "label": labels.get(eid),
            }

        for edge in root.iter(BPMNDI + "BPMNEdge"):
            eid = edge.get("bpmnElement")
            points = [(float(p.get("x")), float(p.get("y")))
                      for p in edge.findall(DI + "waypoint")]
            if not points:
                continue
            label = label_bounds(edge)
            if eid in flows:
                tag, name, cond = flows[eid]
                # the label box is sized for the flow's name; the condition is
                # longer than the box and would have to be set unreadably small
                text = name or cond
            else:
                tag, text = "association", ""
            self.edges.append({"kind": tag, "points": points, "label": label,
                               "text": text})

        # The label boxes in the file are sized for a particular font, and the
        # files do not all agree on which one. Taking the smallest size that fits
        # every stored box keeps each label inside the space the model gives it,
        # and gives one size per category rather than one per label.
        #
        # A pool drawn horizontally keeps its name in the narrow header on the
        # left, and the model records that label as a box taller than it is wide:
        # the name is turned a quarter turn. Measuring such a box as though the
        # text ran across it is what collapsed every container label to about
        # three model units - unreadable, and far smaller than the element
        # labels beside them. The orientation is respected here, and the label is
        # drawn turned to match.
        self.container_size = self._size_for(
            [(nm, lb, _is_vertical(lb)) for _, nm, lb in self.pools + self.lanes], 16.0)
        self.node_size = self._size_for(
            [(s["name"], s["label"], False) for s in self.shapes.values()], 13.0)
        self.flow_size = self._size_for(
            [(e["text"], e["label"], False) for e in self.edges], 11.0)

    @staticmethod
    def _size_for(entries, fallback):
        """Smallest font size that fits every label box, in model units.

        Entries are `(text, box, vertical)`. A vertical box holds a label the
        model turns a quarter turn, so its length runs down the box rather than
        across it and the two constraints swap.
        """
        sizes = []
        for text, lb, vertical in entries:
            if not text or not lb:
                continue
            width, height = lb[2], lb[3]
            if vertical:
                width, height = height, width
            by_height = height / R.LINE_SPACING
            per_unit = R.text_width(text, 1.0)
            by_width = width / per_unit if per_unit > 0 else by_height
            sizes.append(min(by_height, by_width))
        return min(sizes) if sizes else fallback

    def extent(self):
        xs, ys = [], []
        for box, _, lb in self.pools + self.lanes:
            xs += [box[0], box[0] + box[2]]
            ys += [box[1], box[1] + box[3]]
            if lb:
                xs += [lb[0], lb[0] + lb[2]]
                ys += [lb[1], lb[1] + lb[3]]
        for s in self.shapes.values():
            xs += [s["box"][0], s["box"][0] + s["box"][2]]
            ys += [s["box"][1], s["box"][1] + s["box"][3]]
            if s["label"]:
                xs += [s["label"][0], s["label"][0] + s["label"][2]]
                ys += [s["label"][1], s["label"][1] + s["label"][3]]
        for e in self.edges:
            for x, y in e["points"]:
                xs.append(x)
                ys.append(y)
            if e["label"]:
                xs += [e["label"][0], e["label"][0] + e["label"][2]]
                ys += [e["label"][1], e["label"][1] + e["label"][3]]
        return min(xs), min(ys), max(xs), max(ys)


def _paste_rotated_text(img, text, box, size_units, scale, origin, color=R.INK):
    """Set a label a quarter turn anticlockwise and centre it in its box.

    PIL draws no rotated text, so the label is set on a tile of its own - cut
    tight to the glyphs, so the empty margin does not push a narrow header out
    of shape - turned, and pasted. Reading bottom to top is the direction the
    modeller uses for the name in a horizontal pool's header.
    """
    x, y, w, h = box
    ox, oy = origin
    font = R._font(size_units * scale)
    probe = ImageDraw.Draw(Image.new("L", (1, 1)))
    l, t, r, b = probe.textbbox((0, 0), text, font=font)
    tile = Image.new("RGBA", (max(1, r - l), max(1, b - t)), (255, 255, 255, 0))
    ImageDraw.Draw(tile).text((-l, -t), text, font=font, fill=color)
    tile = tile.rotate(90, expand=True)
    cx = (x - ox) * scale + w * scale / 2.0
    cy = (y - oy) * scale + h * scale / 2.0
    img.paste(tile, (int(round(cx - tile.width / 2.0)),
                     int(round(cy - tile.height / 2.0))), tile)


def _open_arrow(draw, tip, previous, size, scale):
    """The hollow arrowhead a message flow ends with."""
    import math
    dx, dy = tip[0] - previous[0], tip[1] - previous[1]
    length = math.hypot(dx, dy) or 1.0
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    back = (tip[0] - ux * size, tip[1] - uy * size)
    half = size * 0.45
    draw.polygon([tip,
                  (back[0] + px * half, back[1] + py * half),
                  (back[0] - px * half, back[1] - py * half)],
                 fill="white", outline=R.FLOW_INK)
    draw.line([tip, (back[0] + px * half, back[1] + py * half)], fill=R.FLOW_INK)
    draw.line([tip, (back[0] - px * half, back[1] - py * half)], fill=R.FLOW_INK)


def render(diagram, png_path, scale=SCALE, pad=PAD):
    x0, y0, x1, y1 = diagram.extent()
    x0 -= pad
    y0 -= pad
    x1 += pad
    y1 += pad
    width = int(round((x1 - x0) * scale))
    height = int(round((y1 - y0) * scale))

    img = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(img)
    off = lambda p: ((p[0] - x0) * scale, (p[1] - y0) * scale)
    origin = (x0, y0)

    def box_of(b):
        x, y = off((b[0], b[1]))
        return (x, y, x + b[2] * scale, y + b[3] * scale)

    lw = max(1, int(round(1.4 * scale)))

    # --- 1. pools and lanes, behind everything else
    for box, _, _ in diagram.pools:
        draw.rectangle(box_of(box), fill="white", outline=CONTAINER_INK, width=lw)
    for box, _, _ in diagram.lanes:
        draw.rectangle(box_of(box), fill=LANE_FILL, outline=CONTAINER_INK, width=lw)

    # --- 2. flows
    for e in diagram.edges:
        pts = [off(p) for p in e["points"]]
        if e["kind"] == "sequenceFlow":
            draw.line(pts, fill=R.FLOW_INK, width=max(1, int(round(1.4 * scale))),
                      joint="curve")
            if len(pts) >= 2:
                R._arrow(draw, pts[-1], pts[-2], 9.0, scale)
        elif e["kind"] == "messageFlow":
            R._dashed(draw, pts, scale, dash=7.0, gap=5.0)
            if len(pts) >= 2:
                _open_arrow(draw, pts[-1], pts[-2], 11.0 * scale, scale)
        else:
            R._dashed(draw, pts, scale)

    # --- 3. nodes
    for s in diagram.shapes.values():
        kind = s["kind"]
        x, y = off((s["box"][0], s["box"][1]))
        w, h = s["box"][2] * scale, s["box"][3] * scale
        box = (x, y, x + w, y + h)
        if kind == "task":
            draw.rounded_rectangle(box, radius=10 * scale, fill="white",
                                   outline=R.INK, width=max(1, int(round(1.5 * scale))))
        elif kind == "start":
            R._ring(draw, box, 1.5 * scale, 0)
        elif kind == "end":
            R._ring(draw, box, 3.4 * scale, 0)
        elif kind in ("xor", "and", "or", "complex"):
            cx, cy, r = x + w / 2.0, y + h / 2.0, w / 2.0
            draw.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)],
                         fill="white", outline=R.INK, width=max(1, int(round(1.5 * scale))))
            R._gateway_marker(draw, kind, cx, cy, r, scale)

    # --- 4. labels: containers first, then nodes, then flows
    for box, name, lb in diagram.pools:
        if not name:
            continue
        if _is_vertical(lb):
            _paste_rotated_text(img, name, lb, diagram.container_size, scale, origin)
        else:
            R._draw_label(draw, name, lb or box, diagram.container_size, scale,
                          origin, align="left" if lb else "center")
    for box, name, lb in diagram.lanes:
        if not name:
            continue
        if _is_vertical(lb):
            _paste_rotated_text(img, name, lb, diagram.container_size, scale, origin)
        else:
            R._draw_label(draw, name, lb or box, diagram.container_size, scale,
                          origin, align="left" if lb else "center")
    for s in diagram.shapes.values():
        if s["name"]:
            # a task whose label has no stored box carries its name centred in
            # the task itself, which is how the modeller draws it
            R._draw_label(draw, s["name"], s["label"] or s["box"],
                          diagram.node_size, scale, origin)
    for e in diagram.edges:
        if e["text"] and e["label"]:
            R._draw_label(draw, e["text"], e["label"], diagram.flow_size, scale,
                          origin, color=R.GRAY)

    os.makedirs(os.path.dirname(png_path), exist_ok=True)
    img.save(png_path)
    return img, width, height


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pdf-only", action="store_true")
    args = ap.parse_args()

    models = sorted(glob.glob(os.path.join(REPO, "models", "operational", "*.bpmn"))) + \
        sorted(glob.glob(os.path.join(REPO, "models", "strategic", "*.bpmn")))

    for path in models:
        name = os.path.basename(path)[:-5]
        diagram = Diagram(path)
        png = os.path.join(OUT_DIR, name + ".png")
        img, w, h = render(diagram, png)
        # the page is sized to the diagram, with an element label set at 10 pt
        dpi = SCALE * diagram.node_size * 72.0 / TARGET_PT
        pdf = os.path.join(OUT_DIR, name + ".pdf")
        img.save(pdf, "PDF", resolution=dpi)
        print("%-52s %5d x %5d px -> %5.1f x %5.1f in at %5.1f dpi, label %.1f units"
              % (name, w, h, w / dpi, h / dpi, dpi, diagram.node_size))
        if args.pdf_only:
            os.remove(png)


if __name__ == "__main__":
    main()
