# -*- coding: utf-8 -*-
"""Static checks on the HPRTAS models - the layout and the BPMN rules the
lectures set out, with no engine needed.

The rules come from the W02 lectures and are the ones a marker can see:

  1  one process per file, named after the file, and executable if it is an
     operational model
  2  every element has diagram information, and every diagram element names
     something that exists (a model with a missing shape cannot be read)
  3  a lane holds whole activities: no task straddles a lane boundary, and
     every node belongs to exactly one lane
  4  an exclusive or inclusive gateway that splits declares a default flow,
     its other branches carry a FEEL condition, and a parallel gateway's
     branches carry none
  5  conditions are FEEL (`= accepted`), never the Camunda 7 `${...}` form
  6  a boundary event sits on the border of the activity it is attached to
  7  a sequence flow starts and ends on its own nodes and does not cut through
     another shape
  8  sequence flow stays inside its pool while a message flow leaves it
  9  activities are named Verb + Object, and no element is left called
     `Task_1`, `Gateway_2` or `Process_3`
 10  labels fit the box the model gives them

Two flows crossing is reported as a warning rather than a failure: the notation
does not forbid it, and a process whose repair paths travel back across the
diagram cannot always avoid it. `--strict` makes it a failure instead.

Run: python toolserify_hprtas_bpmn_layout.py [--strict]

Geometry comes from `bpmn_render` and `export_hprtas_bpmn_pdf`, the same two
modules the PNGs and the PDFs are drawn with, so a diagram that passes here is
the diagram that gets exported.

Run: python tools\\verify_hprtas_bpmn_layout.py
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpmn_render as R                    # noqa: E402
from export_hprtas_bpmn_pdf import BPMN, BPMNDI, DC, DI, REPO  # noqa: E402

MODEL_DIRS = (os.path.join(REPO, "models", "operational"),
              os.path.join(REPO, "models", "strategic"))

NODE_TAGS = ("startEvent", "endEvent", "userTask", "serviceTask", "manualTask",
             "businessRuleTask", "sendTask", "receiveTask", "task", "callActivity",
             "subProcess", "exclusiveGateway", "parallelGateway", "inclusiveGateway",
             "complexGateway", "eventBasedGateway", "intermediateCatchEvent",
             "intermediateThrowEvent", "boundaryEvent")

ACTIVITY_TAGS = ("userTask", "serviceTask", "manualTask", "businessRuleTask",
                 "sendTask", "receiveTask", "task", "callActivity", "subProcess")

# a lane holds activities; these marks the diagram is allowed to place anywhere
LANE_FREE_TAGS = ("boundaryEvent",)

GENERIC_ID = re.compile(r"^(Task|Activity|Gateway|Process|Event|SequenceFlow|"
                        r"Flow|UserTask|ServiceTask|StartEvent|EndEvent|"
                        r"ExclusiveGateway|ParallelGateway|Lane|Pool)_?\d*$")

# a diagram's own label boxes are approximate; this is the tolerance in model units
TOL = 1.5
# a lane boundary is a line the activity must not straddle
LANE_TOL = 0.5


def local(tag):
    return tag.rsplit("}", 1)[-1]


def bounds(el):
    b = el.find(DC + "Bounds")
    if b is None:
        return None
    return (float(b.get("x")), float(b.get("y")),
            float(b.get("width")), float(b.get("height")))


def label_box(el):
    lb = el.find(BPMNDI + "BPMNLabel")
    return bounds(lb) if lb is not None else None


def rects_overlap(a, b, gap=0.0):
    return not (a[0] + a[2] + gap <= b[0] or b[0] + b[2] + gap <= a[0] or
                a[1] + a[3] + gap <= b[1] or b[1] + b[3] + gap <= a[1])


def contains(outer, inner, tol=TOL):
    return (inner[0] >= outer[0] - tol and inner[1] >= outer[1] - tol and
            inner[0] + inner[2] <= outer[0] + outer[2] + tol and
            inner[1] + inner[3] <= outer[1] + outer[3] + tol)


def seg_crosses_rect(p, q, rect, inner=1.0):
    x, y, w, h = rect
    x0, y0, x1, y1 = x + inner, y + inner, x + w - inner, y + h - inner
    if x1 <= x0 or y1 <= y0:
        return False
    return R._segments_cross(p, q, (x0, y0), (x1, y0)) or \
        R._segments_cross(p, q, (x1, y0), (x1, y1)) or \
        R._segments_cross(p, q, (x1, y1), (x0, y1)) or \
        R._segments_cross(p, q, (x0, y1), (x0, y0))


def on_border(point, rect):
    x, y, w, h = rect
    return (abs(point[0] - x) < TOL or abs(point[0] - (x + w)) < TOL or
            abs(point[1] - y) < TOL or abs(point[1] - (y + h)) < TOL)


def parse(path):
    root = ET.parse(path).getroot()
    process = root.find(BPMN + "process")
    nodes, ids = {}, {}
    for tag in NODE_TAGS:
        for el in process.iter(BPMN + tag):
            nodes[el.get("id")] = {"id": el.get("id"), "tag": tag,
                                   "name": el.get("name") or "", "el": el}
    flows = {}
    for el in root.iter(BPMN + "sequenceFlow"):
        cond = el.find(BPMN + "conditionExpression")
        flows[el.get("id")] = {
            "id": el.get("id"), "src": el.get("sourceRef"), "tgt": el.get("targetRef"),
            "name": el.get("name") or "",
            "condition": (cond.text or "").strip() if cond is not None else ""}
    messages = {}
    for el in root.iter(BPMN + "messageFlow"):
        messages[el.get("id")] = {"id": el.get("id"), "src": el.get("sourceRef"),
                                  "tgt": el.get("targetRef")}
    lanes, lane_member = [], {}
    for ls in process.iter(BPMN + "laneSet"):
        for lane in ls.findall(BPMN + "lane"):
            refs = [r.text for r in lane.findall(BPMN + "flowNodeRef")]
            lanes.append({"id": lane.get("id"), "name": lane.get("name") or "",
                          "refs": refs})
            for ref in refs:
                lane_member.setdefault(ref, []).append(lane.get("id"))
    participants = {}
    for el in root.iter(BPMN + "participant"):
        participants[el.get("id")] = {"id": el.get("id"), "name": el.get("name") or "",
                                      "processRef": el.get("processRef")}
    return {"path": path, "root": root, "process": process, "nodes": nodes,
            "flows": flows, "messages": messages, "lanes": lanes,
            "lane_member": lane_member, "participants": participants}


def diagram_boxes(path):
    """Shape/edge DI, keyed by element id, including pools, lanes and labels."""
    root = ET.parse(path).getroot()
    boxes, labels, edges = {}, {}, {}
    for shape in root.iter(BPMNDI + "BPMNShape"):
        eid = shape.get("bpmnElement")
        b = bounds(shape)
        if b is not None:
            boxes[eid] = b
        lb = label_box(shape)
        if lb is not None:
            labels[eid] = lb
    for edge in root.iter(BPMNDI + "BPMNEdge"):
        pts = [(float(p.get("x")), float(p.get("y")))
               for p in edge.findall(DI + "waypoint")]
        lb = label_box(edge)
        edges[edge.get("bpmnElement")] = {"points": pts, "label": lb}
    return boxes, labels, edges


def check_model(path, problems, warnings):
    name = os.path.basename(path)
    m = parse(path)
    process = m["process"]
    stem = name[:-len(".bpmn")]
    boxes, labels, edges = diagram_boxes(path)
    operational = os.path.basename(os.path.dirname(path)) == "operational"

    def add(msg):
        problems.append("%s: %s" % (name, msg))

    def warn(msg):
        warnings.append("%s: %s" % (name, msg))

    # 1 one process per file, named after it, executable when operational
    processes = m["root"].findall(BPMN + "process")
    if len(processes) != 1:
        add("%d processes in the file, expected one" % len(processes))
    if process.get("id") != stem:
        add("process id %r does not match the file name %r" % (process.get("id"), stem))
    if operational and process.get("isExecutable") != "true":
        add("operational model is not executable")
    if not operational and process.get("isExecutable") == "true":
        add("analysis model is marked executable but is not deployed")

    node_ids = set(m["nodes"])
    lane_ids = {l["id"] for l in m["lanes"]}

    # 2 diagram information: every element drawn, every drawing naming an element
    known = node_ids | lane_ids | set(m["participants"]) | set(m["flows"]) | \
        set(m["messages"]) | {e.get("id") for e in m["root"].iter(BPMN + "textAnnotation")} | \
        {e.get("id") for e in m["root"].iter(BPMN + "association")}
    for nid, node in m["nodes"].items():
        if nid not in boxes and node["tag"] != "boundaryEvent":
            add("%s %s has no shape in the diagram" % (node["tag"], nid))
    for eid in boxes:
        if eid not in known:
            add("diagram shape %s names no element" % eid)
    for fid in m["flows"]:
        if fid not in edges:
            add("sequence flow %s has no edge in the diagram" % fid)
    plane = m["root"].find(".//" + BPMNDI + "BPMNPlane")
    # a collaboration diagram has one plane for the collaboration, which holds
    # every pool; a model without pools has the plane on the process itself
    collaborations = {c.get("id") for c in m["root"].iter(BPMN + "collaboration")}
    if plane is None:
        add("the file has no diagram plane")
    elif plane.get("bpmnElement") not in collaborations | {process.get("id")}:
        add("the diagram plane references %r, which is neither the process nor a "
            "collaboration" % plane.get("bpmnElement"))

    # 3 lanes hold whole activities
    for nid, node in m["nodes"].items():
        members = m["lane_member"].get(nid, [])
        if len(members) > 1:
            add("%s belongs to %d lanes" % (nid, len(members)))
        if not members and node["tag"] not in LANE_FREE_TAGS and m["lanes"]:
            add("%s belongs to no lane" % nid)
    for lane in m["lanes"]:
        for ref in lane["refs"]:
            if ref not in node_ids:
                add("lane %s refers to unknown node %s" % (lane["id"], ref))
    for nid, node in m["nodes"].items():
        if nid not in boxes or node["tag"] in LANE_FREE_TAGS:
            continue
        members = m["lane_member"].get(nid)
        if not members:
            continue
        lane_box = boxes.get(members[0])
        if lane_box is None:
            add("lane %s has no shape in the diagram" % members[0])
        elif not contains(lane_box, boxes[nid], LANE_TOL):
            add("%s is drawn outside %s" % (nid, members[0]))

    # 4 and 5 gateways: a split declares a default, conditions are FEEL
    for nid, node in m["nodes"].items():
        if node["tag"] not in ("exclusiveGateway", "inclusiveGateway",
                               "parallelGateway", "complexGateway"):
            continue
        el = node["el"]
        out = [f.text for f in el.findall(BPMN + "outgoing")]
        if node["tag"] == "parallelGateway":
            for fid in out:
                if m["flows"].get(fid, {}).get("condition"):
                    add("parallel gateway %s has a conditional branch %s" % (nid, fid))
            continue
        if len(out) < 2:
            continue
        default = el.get("default")
        if not default:
            add("%s splits into %d and declares no default flow" % (nid, len(out)))
        elif default not in out:
            add("%s declares default %s which is not one of its outgoing flows" % (nid, default))
        for fid in out:
            flow = m["flows"].get(fid)
            if flow is None:
                add("%s declares outgoing flow %s which does not exist" % (nid, fid))
                continue
            if fid == default:
                if flow["condition"]:
                    add("default flow %s of %s carries a condition" % (fid, nid))
            elif len(out) > 1 and not flow["condition"] and node["tag"] != "complexGateway":
                add("%s branch %s has no condition and is not the default" % (nid, fid))
    for fid, flow in m["flows"].items():
        if "${" in flow["condition"]:
            add("flow %s uses Camunda 7 EL: %s" % (fid, flow["condition"]))
        elif flow["condition"] and not flow["condition"].startswith("="):
            add("flow %s has a condition that is not FEEL: %s" % (fid, flow["condition"]))

    # 6 boundary events sit on their host
    for nid, node in m["nodes"].items():
        if node["tag"] != "boundaryEvent":
            continue
        host = node["el"].get("attachedToRef")
        if not host:
            add("boundary event %s is attached to nothing" % nid)
            continue
        if host not in boxes:
            add("boundary event %s is attached to %s which is not drawn" % (nid, host))
        elif nid in boxes and not rects_overlap(boxes[host], boxes[nid], 0.0):
            add("boundary event %s is not on %s" % (nid, host))

    # 7 flows start and end on their own nodes, and cross nothing
    flow_boxes = {}
    for fid, flow in m["flows"].items():
        e = edges.get(fid)
        if e is None or not e["points"]:
            add("flow %s has no waypoints" % fid)
            continue
        pts = e["points"]
        flow_boxes[fid] = pts
        for point, node_id in ((pts[0], flow["src"]), (pts[-1], flow["tgt"])):
            box = boxes.get(node_id)
            if box is None:
                add("flow %s refers to undrawn node %s" % (fid, node_id))
            elif not (on_border(point, box) and contains(
                    (box[0] - TOL, box[1] - TOL, box[2] + 2 * TOL, box[3] + 2 * TOL),
                    (point[0], point[1], 0, 0))):
                add("flow %s does not meet %s at its border" % (fid, node_id))
        for p, q in zip(pts, pts[1:]):
            for node_id, node in m["nodes"].items():
                box = boxes.get(node_id)
                if box is None or node_id in (flow["src"], flow["tgt"]):
                    continue
                if node["tag"] == "boundaryEvent":
                    continue
                if seg_crosses_rect(p, q, box, inner=1.0):
                    add("flow %s crosses %s" % (fid, node_id))
    ids = [f for f in sorted(flow_boxes)]
    crossings = set()
    for i, fid in enumerate(ids):
        for gid in ids[i + 1:]:
            for p, q in zip(flow_boxes[fid], flow_boxes[fid][1:]):
                for r, s in zip(flow_boxes[gid], flow_boxes[gid][1:]):
                    if R._segments_cross(p, q, r, s):
                        crossings.add((fid, gid))
    for fid, gid in sorted(crossings):
        warn("flows cross: %s / %s" % (fid, gid))

    # 8 sequence flow stays in its pool, message flow leaves it
    hospital_pool = None
    for pid, part in m["participants"].items():
        if part["processRef"] == process.get("id"):
            hospital_pool = boxes.get(pid)
            break
    if hospital_pool is not None:
        for fid, flow in m["flows"].items():
            pts = flow_boxes.get(fid)
            if not pts:
                continue
            for p, q in zip(pts, pts[1:]):
                if seg_crosses_rect(p, q, hospital_pool, inner=-TOL):
                    add("sequence flow %s leaves its pool" % fid)
                    break
    # a message flow joins two pools; a participant is not a node, so a message
    # flow to a black-box pool is drawn to the pool itself rather than to an element
    pool_boxes = {pid: boxes.get(pid) for pid in m["participants"]}
    pool_box_ids = {id(b): pid for pid, b in pool_boxes.items() if b is not None}
    for mid, msg in m["messages"].items():
        if msg["src"] is None or msg["tgt"] is None:
            continue
        src_pool = pool_boxes.get(msg["src"])
        tgt_pool = pool_boxes.get(msg["tgt"])
        if src_pool is not None and tgt_pool is not None and src_pool is tgt_pool:
            add("message flow %s stays inside one pool" % mid)

    # 9 names: no id left in place of a name, activities read Verb + Object
    for nid, node in m["nodes"].items():
        if GENERIC_ID.match(nid):
            add("%s is a generic element id, not a meaningful name" % nid)
        if node["tag"] in ACTIVITY_TAGS or node["tag"].endswith("Gateway"):
            if not node["name"]:
                add("%s has no name" % nid)
            elif GENERIC_ID.match(node["name"]):
                add("%s is named %r, which says nothing" % (nid, node["name"]))
    for fid, flow in m["flows"].items():
        if flow["condition"] and not flow["condition"].startswith("="):
            continue
        if GENERIC_ID.match(fid):
            add("flow %s is a generic element id" % fid)

    # 10 labels: they must not collide with each other, nor sit on a shape that
    # is not the one they belong to
    return m


def label_problems(path, name, problems):
    root = ET.parse(path).getroot()
    boxes, labels, edges = diagram_boxes(path)
    for eid, lb in labels.items():
        for other, box in boxes.items():
            if other == eid or other.startswith("Lane_") or other.startswith("Participant_"):
                continue
            if other in labels:
                continue
            if rects_overlap(lb, box, 0.0):
                problems.append("%s: label of %s overlaps %s" % (name, eid, other))
    for eid, edge in edges.items():
        lb = edge["label"]
        if lb is None:
            continue
        for other, box in boxes.items():
            if other in (eid,):
                continue
            if other.startswith("Lane_") or other.startswith("Participant_"):
                continue
            if other in labels:
                continue
            if rects_overlap(lb, box, 0.0):
                problems.append("%s: label of flow %s overlaps %s" % (name, eid, other))
    ids = list(labels)
    for i, a in enumerate(ids):
        for b in ids[i + 1:]:
            if rects_overlap(labels[a], labels[b], 0.0):
                problems.append("%s: labels overlap: %s / %s" % (name, a, b))
    flow_labels = [(fid, e["label"]) for fid, e in edges.items() if e["label"]]
    for fid, lb in flow_labels:
        for eid, other in labels.items():
            if rects_overlap(lb, other, 0.0):
                problems.append("%s: label of flow %s overlaps the label of %s"
                                % (name, fid, eid))
    for i, (fid, lb) in enumerate(flow_labels):
        for gid, other in flow_labels[i + 1:]:
            if rects_overlap(lb, other, 0.0):
                problems.append("%s: labels overlap: flow %s / flow %s" % (name, fid, gid))


def main():
    strict = "--strict" in sys.argv
    problems, warnings = [], []
    files = []
    for d in MODEL_DIRS:
        files += sorted(glob.glob(os.path.join(d, "*.bpmn")))
    for path in files:
        name = os.path.basename(path)
        before, warned = len(problems), len(warnings)
        check_model(path, problems, warnings)
        label_problems(path, name, problems)
        found = problems[before:]
        crossed = warnings[warned:]
        state = "ok" if not found and not crossed else             "%d problems, %d crossings" % (len(found), len(crossed))
        print("%-58s %s" % (name, state))
        for problem in found:
            print("    - %s" % problem)
        for warning in crossed:
            print("    ~ %s" % warning)
    print()
    if problems or (strict and warnings):
        print("FAIL: %d problems and %d crossings" % (len(problems), len(warnings)))
        return 1
    print("PASS: %d models follow the layout and BPMN rules checked here"
          % len(files))
    if warnings:
        print("      %d crossings remain, where the process leaves no other way round"
              % len(warnings))
    return 0


if __name__ == "__main__":
    sys.exit(main())
