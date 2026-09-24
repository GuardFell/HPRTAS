# -*- coding: utf-8 -*-
"""Lay the HPRTAS models out again, without touching what they mean.

The diagram interchange in a `.bpmn` file is presentation only: the process,
its elements, its conditions and its forms are read from the model, and the
diagram is redrawn from them. This rewrites the `<bpmndi:BPMNDiagram>` block and
leaves every other byte of the file alone, so the diff of a re-layout is
geometry and nothing else.

What it lays out, following the W02 lectures:

  * one column per step, read left to right, and lanes kept as horizontal bands
    so the role that does the work is the row it sits in
  * an activity stays inside its lane ("tasks can't cross lanes"), while a
    sequence flow may cross a lane boundary
  * every flow is routed on a lattice, avoiding the activities and the flows
    already routed, which is what removes the crossings a hand-drawn diagram is
    full of
  * labels are placed below their element and beside their flow, then nudged
    until they collide with nothing

`layout_problems` in bpmn_render is the acceptance bar: the same module draws
the PNGs and the PDFs, so a model that reads clean here is the model that gets
exported.

Run: python tools\\relayout_hprtas_bpmn.py [model.bpmn ...]
     python tools\\relayout_hprtas_bpmn.py            (all of them)
"""
import heapq
import math
import os
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpmn_render as R                                            # noqa: E402

# the repository root: <repo>/tools/<script>.py, so the tools run from any checkout
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIRS = (os.path.join(REPO, "models", "operational"),
              os.path.join(REPO, "models", "strategic"))

BPMN = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
BPMNDI = "{http://www.omg.org/spec/BPMN/20100524/DI}"
DC = "{http://www.omg.org/spec/DD/20100524/DC}"
DI = "{http://www.omg.org/spec/DD/20100524/DI}"

# element sizes, in model units, and the vertical room a label needs
SIZE = {"task": (150.0, 80.0), "gateway": (50.0, 50.0), "event": (36.0, 36.0)}
LABEL_SIZE = R.LABEL_SIZE            # 17.0, a label under an element
FLOW_SIZE = R.FLOW_SIZE              # 14.0, a label beside a flow
LINE = R.LINE_SPACING

CELL = 10.0                          # the routing lattice
POOL_PAD_X = 60.0
POOL_PAD_Y = 46.0
POOL_HEADER = 30.0                   # the band the pool name sits in
LANE_HEADER = 32.0                   # the band a lane name sits in
LANE_PAD_BOTTOM = 18.0
NODE_GAP = 30.0                      # between two activities stacked in a lane
COL_PAD = 20.0
MIN_GAP = 100.0                      # between two columns
TRACK = 26.0                         # between two vertical runs in one gap
CORRIDOR_MIN = 34.0                  # a clear band at the foot of every lane
CORRIDOR_MAX = 130.0                 # past this a lane is mostly empty space
EXPANSION_LIMIT = 90000              # give up on a search that finds nothing
OUTSIDE = 150.0                      # between the hospital pool and the others

START_TAGS = ("startEvent",)
END_TAGS = ("endEvent",)


def local(tag):
    return tag.rsplit("}", 1)[-1]


def snap(value):
    return round(value / CELL) * CELL


# --------------------------------------------------------------------- reading
class Model(object):
    def __init__(self, path):
        self.path = path
        root = ET.parse(path).getroot()
        self.root = root
        self.process = root.find(BPMN + "process")
        self.process_id = self.process.get("id")
        collaboration = root.find(BPMN + "collaboration")
        self.collaboration_id = collaboration.get("id") if collaboration is not None else None

        self.nodes = {}
        for el in self.process:
            tag = local(el.tag)
            if tag in ("startEvent", "endEvent"):
                kind = "event"
            elif tag in ("userTask", "serviceTask", "manualTask", "businessRuleTask",
                         "sendTask", "receiveTask", "task", "callActivity", "subProcess"):
                kind = "task"
            elif tag.endswith("Gateway"):
                kind = "gateway"
            elif tag == "boundaryEvent":
                kind = "event"
            else:
                continue
            self.nodes[el.get("id")] = {
                "id": el.get("id"), "tag": tag, "kind": kind,
                "name": el.get("name") or "", "el": el,
                "host": el.get("attachedToRef")}

        self.flows = {}
        for el in self.process.iter(BPMN + "sequenceFlow"):
            condition = el.find(BPMN + "conditionExpression")
            self.flows[el.get("id")] = {
                "id": el.get("id"), "src": el.get("sourceRef"), "tgt": el.get("targetRef"),
                "name": el.get("name") or "",
                "condition": (condition.text or "").strip() if condition is not None else ""}

        self.lanes = []
        for lane in self.process.iter(BPMN + "lane"):
            self.lanes.append({"id": lane.get("id"), "name": lane.get("name") or "",
                               "members": [r.text for r in lane.findall(BPMN + "flowNodeRef")]})

        self.participants = []
        for el in root.iter(BPMN + "participant"):
            self.participants.append({"id": el.get("id"), "name": el.get("name") or "",
                                      "processRef": el.get("processRef")})

        self.messages = []
        for el in root.iter(BPMN + "messageFlow"):
            self.messages.append({"id": el.get("id"), "src": el.get("sourceRef"),
                                  "tgt": el.get("targetRef"), "name": el.get("name") or ""})

        self.lane_of = {}
        for lane in self.lanes:
            for member in lane["members"]:
                self.lane_of[member] = lane["id"]

        # the four steps of a flow are not all pointed the same way: a flow whose
        # target sits at or before its source is a repair path and has to be
        # routed back through the diagram
        self.non_boundary = [n for n in self.nodes.values()
                             if n["kind"] != "event" or n["tag"] != "boundaryEvent"]
        self.back_edges = set()

    def boundary_nodes(self):
        return [n for n in self.nodes.values() if n["tag"] == "boundaryEvent"]

    def host_of(self, node_id):
        return self.nodes[node_id]["host"] if node_id in self.nodes else None


# ------------------------------------------------------------------- layering
def assign_layers(model):
    """One column per step: how far along the flow a node sits."""
    ids = [n["id"] for n in model.non_boundary]
    host_of = {}
    for node in model.boundary_nodes():
        host_of[node["id"]] = node["host"]

    # a flow out of a boundary event starts where its host is
    def endpoints(flow):
        src = host_of.get(flow["src"], flow["src"])
        return src, flow["tgt"]

    # depth first, to tell a repair path from a forward one
    outgoing = {i: [] for i in ids}
    for flow in model.flows.values():
        src, tgt = endpoints(flow)
        if src in outgoing and tgt in outgoing:
            outgoing[src].append((flow["id"], tgt))
    starts = [i for i in ids if not any(edge[1] == i
                                       for edges in outgoing.values() for edge in edges)]
    on_stack, done = set(), set()
    stack = [(s, iter(outgoing[s])) for s in starts]
    on_stack.update(starts)
    while stack:
        node, children = stack[-1]
        advanced = False
        for fid, child in children:
            if child in on_stack:
                model.back_edges.add(fid)
            elif child not in done and child not in [s[0] for s in stack]:
                stack.append((child, iter(outgoing[child])))
                on_stack.add(child)
                advanced = True
                break
        if not advanced:
            stack.pop()
            on_stack.discard(node)
            done.add(node)

    layer = {i: 0 for i in ids}
    for _ in range(len(ids) + 1):
        changed = False
        for flow in model.flows.values():
            if flow["id"] in model.back_edges:
                continue
            src, tgt = endpoints(flow)
            if src in layer and tgt in layer and layer[tgt] < layer[src] + 1:
                layer[tgt] = layer[src] + 1
                changed = True
        if not changed:
            break
    for node in model.boundary_nodes():
        layer[node["id"]] = layer.get(node["host"], 0)
    return layer


# ------------------------------------------------------------------ placement
def label_lines(text, size, limit):
    return R.wrap(text, size, limit)


def label_height(text, size, limit):
    return len(label_lines(text, size, limit)) * size * LINE


def task_width(name):
    """Widen a task until its name needs no more than three lines."""
    for width in (150.0, 180.0, 210.0, 240.0):
        if len(R.wrap(name, LABEL_SIZE, width)) <= 3:
            return width
    return 240.0


def place(model, layer):
    """Where every activity, lane and pool goes. Returns the geometry."""
    lane_index = {lane["id"]: i for i, lane in enumerate(model.lanes)}
    columns = {}
    for node in model.non_boundary:
        columns.setdefault(layer[node["id"]], []).append(node)
    for column in columns.values():
        column.sort(key=lambda n: (lane_index.get(model.lane_of.get(n["id"]), 0), n["id"]))

    widths = {}
    for col, nodes in columns.items():
        widths[col] = max([SIZE[n["kind"]][0] if n["kind"] != "task"
                           else task_width(n["name"]) for n in nodes] + [60.0])

    # a gap has to be wide enough for the vertical runs that pass through it
    def needs_gap(flow):
        if flow["src"] not in layer or flow["tgt"] not in layer:
            return None
        src, tgt = layer[flow["src"]], layer[flow["tgt"]]
        if src == tgt:
            return None
        return (min(src, tgt), max(src, tgt))

    demand = {}
    for flow in model.flows.values():
        span = needs_gap(flow)
        if span is None:
            continue
        for col in range(span[0], span[1]):
            demand[col] = demand.get(col, 0) + 1

    ordered = sorted(columns)
    gap = {col: max(MIN_GAP, 50.0 + TRACK * demand.get(col, 0))
           for col in ordered[:-1]}
    x_of_col, x = {}, None
    for col in ordered:
        if x is None:
            x = 120.0
        x_of_col[col] = x
        x += widths[col] + gap.get(col, MIN_GAP)

    # vertical room: what the lane's own activities need, plus a corridor for
    # the flows that have to come back through it
    lane_heights, lane_shapes = {}, {}
    # a lane needs a clear band at its foot for every flow that travels along it:
    # a run that spans more than one column cannot stay in the gaps between the
    # activities, and the band is where it goes
    back_pressure = {}
    for flow in model.flows.values():
        src_lane = lane_index.get(model.lane_of.get(flow["src"]))
        tgt_lane = lane_index.get(model.lane_of.get(flow["tgt"]))
        if src_lane is None or tgt_lane is None:
            continue
        if src_lane == tgt_lane:
            span = abs(layer.get(flow["src"], 0) - layer.get(flow["tgt"], 0))
            if span:
                back_pressure[src_lane] = back_pressure.get(src_lane, 0) + 1
        else:
            for lane in range(min(src_lane, tgt_lane), max(src_lane, tgt_lane) + 1):
                back_pressure[lane] = back_pressure.get(lane, 0) + 1

    for index, lane in enumerate(model.lanes):
        members = [model.nodes[m] for m in lane["members"]
                   if m in model.nodes and model.nodes[m]["tag"] != "boundaryEvent"]
        per_column = {}
        for node in members:
            per_column.setdefault(layer[node["id"]], []).append(node)
        tallest = 0.0
        for col, nodes in per_column.items():
            height = 0.0
            for node in nodes:
                height += node_height(model, node) + NODE_GAP
            tallest = max(tallest, height - NODE_GAP)
        corridor = min(CORRIDOR_MAX,
                       max(CORRIDOR_MIN, 30.0 + 26.0 * back_pressure.get(index, 0)))
        lane_heights[lane["id"]] = LANE_HEADER + tallest + corridor + LANE_PAD_BOTTOM
        lane_shapes[lane["id"]] = per_column

    content_width = x - gap.get(ordered[-1], MIN_GAP)
    pool_width = content_width + POOL_PAD_X
    pool_top = 120.0
    lane_y, y = {}, pool_top + POOL_HEADER
    for lane in model.lanes:
        lane_y[lane["id"]] = y
        y += lane_heights[lane["id"]]
    pool_height = y - pool_top + POOL_PAD_Y

    # place the activities, and keep the columns aligned on the lattice
    geometry = {}
    for lane in model.lanes:
        top = lane_y[lane["id"]]
        height = lane_heights[lane["id"]]
        for col, nodes in lane_shapes[lane["id"]].items():
            block = sum(node_height(model, n) + NODE_GAP for n in nodes) - NODE_GAP
            cursor = top + LANE_HEADER + max(0.0, (height - LANE_HEADER - LANE_PAD_BOTTOM
                                                   - block) / 2.0)
            for node in nodes:
                width = (SIZE[node["kind"]][0] if node["kind"] != "task"
                         else task_width(node["name"]))
                x = snap(x_of_col[col] + (widths[col] - width) / 2.0)
                geometry[node["id"]] = {"x": x, "y": snap(cursor), "w": width,
                                        "h": SIZE[node["kind"]][1]}
                cursor += node_height(model, node) + NODE_GAP

    # a boundary event hangs off the foot of the activity it is attached to
    for node in model.boundary_nodes():
        host = geometry.get(node["host"])
        if host is None:
            continue
        siblings = [n for n in model.boundary_nodes() if n["host"] == node["host"]]
        index = siblings.index(node)
        step = host["w"] / (len(siblings) + 1)
        cx = host["x"] + step * (index + 1)
        geometry[node["id"]] = {"x": snap(cx - 18.0), "y": snap(host["y"] + host["h"] - 18.0),
                                "w": 36.0, "h": 36.0}

    return {"geometry": geometry, "lane_y": lane_y, "lane_heights": lane_heights,
            "pool": {"x": 120.0 - POOL_PAD_X + 40.0, "y": pool_top,
                     "w": pool_width, "h": pool_height},
            "content_right": 120.0 + content_width, "x_of_col": x_of_col,
            "lane_index": lane_index}


def node_height(model, node):
    """The activity plus the room its label needs underneath it."""
    name = node["name"]
    if not name:
        return SIZE[node["kind"]][1]
    width = SIZE[node["kind"]][0] if node["kind"] != "task" else task_width(name)
    limit = width if node["kind"] == "task" else max(width, 190.0)
    height = SIZE[node["kind"]][1]
    room = label_height(name, LABEL_SIZE, limit)
    if node["kind"] == "task":
        room = max(room, 20.0)
    extra = 0.0
    for boundary in model.boundary_nodes():
        if boundary["host"] == node["id"]:
            extra = max(extra, 22.0)      # the boundary event and its label sit below
    return height + room + extra + 4.0


# -------------------------------------------------------------------- routing
class Router(object):
    """Orthogonal routing on a lattice, avoiding what is already drawn."""

    def __init__(self, width, height):
        self.w = int(width / CELL) + 1
        self.h = int(height / CELL) + 1
        self.blocked = set()
        self.soft = set()
        self.used = {}
        self.region = None
        self.exempt = set()
        self.hard = False
        self.limit = None

    def cells_of(self, x, y, w, h, pad=CELL / 2.0):
        x0, y0 = int((x - pad) / CELL), int((y - pad) / CELL)
        x1, y1 = int((x + w + pad) / CELL), int((y + h + pad) / CELL)
        return [(gx, gy)
                for gx in range(max(0, x0), min(self.w - 1, x1) + 1)
                for gy in range(max(0, y0), min(self.h - 1, y1) + 1)]

    def block_rect(self, x, y, w, h, pad=CELL / 2.0):
        self.blocked.update(self.cells_of(x, y, w, h, pad))

    def unblock_rect(self, x, y, w, h, pad=CELL / 2.0):
        """Free the cells of an element that sits on top of another one.

        A boundary event is drawn on the foot of the activity it is attached to,
        so the activity's own rectangle covers it; without this the event would
        have nowhere to start.
        """
        for cell in self.cells_of(x, y, w, h, pad):
            self.blocked.discard(cell)

    def ring_rect(self, x, y, w, h):
        """The cells just outside a box, which a flow has to be able to reach."""
        return self.cells_of(x, y, w, h, pad=3 * CELL / 2.0)

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def soft_block_rect(self, x, y, w, h, pad=CELL / 2.0):
        """A box a flow may cross, but would rather not: a label."""
        self.soft.update(self.cells_of(x, y, w, h, pad))

    def cost(self, x, y):
        if (x, y) in self.blocked and (x, y) not in self.exempt:
            return None
        if self.hard and (x, y) in self.used:
            return None
        if self.region is not None and not self.region(x * CELL, y * CELL):
            return None
        base = 1.0
        if (x, y) in self.soft:
            base += 90.0                 # running through a label is untidy
        seen = self.used.get((x, y), 0)
        if seen:
            # a flow already goes through here: crossing it is a last resort, so
            # the penalty is far above the cost of going round
            base += 600.0 * seen
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) in self.used:
                base += 6.0              # and keep a lane between the lines
        return base

    def route(self, src_box, tgt_box, region=None, hard=True, limit=None):
        """Route from one box to another, entering and leaving on a border.

        With `hard` a cell another flow already uses cannot be entered at all, so
        the two do not cross; when the geometry leaves no such way the caller
        routes again with `hard=False` and the crossing costs instead of being
        forbidden.
        """
        self.region = region
        self.hard = hard
        self.limit = limit
        # a port sits on a border, and the cells around the two elements have to
        # stay reachable however tightly the elements are packed
        self.exempt = set()
        for box in (src_box, tgt_box):
            x, y, w, h = box
            for point, step in (((x + w, y + h / 2.0), (1, 0)), ((x, y + h / 2.0), (-1, 0)),
                                ((x + w / 2.0, y + h), (0, 1)), ((x + w / 2.0, y), (0, -1))):
                cell = (int(round(point[0] / CELL)), int(round(point[1] / CELL)))
                self.exempt.add(cell)
                self.exempt.add((cell[0] + step[0], cell[1] + step[1]))
        ports = []
        sx, sy, sw, sh = src_box
        tx, ty, tw, th = tgt_box
        for name, point, first_step in (
                ("right", (sx + sw, sy + sh / 2.0), (1, 0)),
                ("left", (sx, sy + sh / 2.0), (-1, 0)),
                ("bottom", (sx + sw / 2.0, sy + sh), (0, 1)),
                ("top", (sx + sw / 2.0, sy), (0, -1))):
            ports.append(("src", name, (int(round(point[0] / CELL)), int(round(point[1] / CELL))),
                          first_step))
        goals = []
        for name, point, _ in (
                ("left", (tx, ty + th / 2.0), None),
                ("right", (tx + tw, ty + th / 2.0), None),
                ("top", (tx + tw / 2.0, ty), None),
                ("bottom", (tx + tw / 2.0, ty + th), None)):
            goals.append((name, (int(round(point[0] / CELL)), int(round(point[1] / CELL)))))

        goal_cells = {cell: name for name, cell in goals}
        best = None
        for origin, name, cell, step in ports:
            if not self.inside(*cell):
                continue
            path = self._search(cell, step, goal_cells)
            if path is None:
                continue
            length = len(path)
            if best is None or length < best[0]:
                best = (length, path, name, cell)
        if best is None:
            return None
        _, path, src_name, _ = best
        return self._finish(path, src_box, tgt_box)

    def _search(self, start, first_step, goals):
        # state: (cell, direction) - the direction is how the flow arrived
        start_state = (start, first_step)
        queue = [(0.0, 0, start_state)]
        came = {start_state: None}
        cost_so_far = {start_state: 0.0}
        counter = 1
        explored = 0
        while queue:
            explored += 1
            if self.limit is not None and explored > self.limit:
                return None
            estimate, _, state = heapq.heappop(queue)
            cell, direction = state
            if cell in goals:
                path = [cell]
                while came[state] is not None:
                    state = came[state]
                    path.append(state[0])
                return list(reversed(path))
            gx, gy = cell
            for step in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if step == direction:
                    turn = 0.0
                elif step[0] == -direction[0] and step[1] == -direction[1]:
                    continue                       # no U-turn in place
                else:
                    turn = 14.0
                nx, ny = gx + step[0], gy + step[1]
                if not self.inside(nx, ny):
                    continue
                step_cost = self.cost(nx, ny)
                if step_cost is None:
                    continue
                new_state = ((nx, ny), step)
                total = cost_so_far[state] + step_cost + turn
                if new_state in cost_so_far and cost_so_far[new_state] <= total:
                    continue
                cost_so_far[new_state] = total
                came[new_state] = state
                # stay inside the region rather than hugging its edge
                guess = min(abs(nx - g[0]) + abs(ny - g[1]) for g in goals)
                heapq.heappush(queue, (total + guess * 1.0, counter, new_state))
                counter += 1
        return None

    def _finish(self, path, src_box, tgt_box):
        points = [(cell[0] * CELL, cell[1] * CELL) for cell in path]
        # the first and the last point belong on the border of their element, and
        # the segment that leaves or enters has to stay perpendicular to it
        sx, sy, sw, sh = src_box
        first = points[0]
        if first[0] <= sx:
            points[0] = (sx, first[1])
        elif first[0] >= sx + sw:
            points[0] = (sx + sw, first[1])
        elif first[1] <= sy:
            points[0] = (first[0], sy)
        else:
            points[0] = (first[0], sy + sh)
        if len(points) > 1:
            if points[0][0] in (sx, sx + sw):
                points[1] = (points[1][0], points[0][1])
            else:
                points[1] = (points[0][0], points[1][1])
        tx, ty, tw, th = tgt_box
        last = points[-1]
        if last[0] <= tx:
            points[-1] = (tx, last[1])
        elif last[0] >= tx + tw:
            points[-1] = (tx + tw, last[1])
        elif last[1] <= ty:
            points[-1] = (last[0], ty)
        else:
            points[-1] = (last[0], ty + th)
        if len(points) > 1:
            if points[-1][0] in (tx, tx + tw):
                points[-2] = (points[-2][0], points[-1][1])
            else:
                points[-2] = (points[-1][0], points[-2][1])
        # drop the points that add nothing: only the corners are waypoints
        clean = [points[0]]
        for point in points[1:]:
            if point == clean[-1]:
                continue
            clean.append(point)
        turns = [clean[0]]
        for previous, current, following in zip(clean, clean[1:], clean[2:]):
            if (previous[0] == current[0] == following[0]) or \
               (previous[1] == current[1] == following[1]):
                continue
            turns.append(current)
        turns.append(clean[-1])
        return turns

    def reserve(self, points):
        for p, q in zip(points, points[1:]):
            steps = int(max(abs(q[0] - p[0]), abs(q[1] - p[1])) / CELL) + 1
            for i in range(steps + 1):
                x = p[0] + (q[0] - p[0]) * i / float(steps)
                y = p[1] + (q[1] - p[1]) * i / float(steps)
                cell = (int(round(x / CELL)), int(round(y / CELL)))
                self.used[cell] = self.used.get(cell, 0) + 1


def route_one(router, flow, geometry, make_region, pool_region, strict):
    """One flow, trying the lanes it has business with before the whole pool."""
    src, tgt = geometry[flow["src"]], geometry[flow["tgt"]]
    src_box = (src["x"], src["y"], src["w"], src["h"])
    tgt_box = (tgt["x"], tgt["y"], tgt["w"], tgt["h"])
    points = router.route(src_box, tgt_box, region=make_region(flow["src"], flow["tgt"]),
                          hard=strict, limit=EXPANSION_LIMIT if strict else None)
    if points is None and strict:
        points = router.route(src_box, tgt_box, region=pool_region, hard=True,
                              limit=EXPANSION_LIMIT)
    if points is None:
        points = router.route(src_box, tgt_box, region=pool_region, hard=False)
    return points


def shape_crossings(routes, geometry, flows, boundary_of):
    """Flows whose line would cut across an activity it does not belong to."""
    offenders = set()
    for fid, points in routes.items():
        flow = flows[fid]
        for p, q in zip(points, points[1:]):
            for nid, box in geometry.items():
                if nid in (flow["src"], flow["tgt"]):
                    continue
                if boundary_of.get(flow["src"]) == nid or boundary_of.get(flow["tgt"]) == nid:
                    continue          # the activity a boundary event hangs off
                if R._rect_hits_segment((box["x"], box["y"], box["w"], box["h"]), p, q, inner=1.0):
                    offenders.add(fid)
                    break
    return offenders


def conflicting_flows(routes):
    """The flows that cross another one: the ones worth routing again."""
    ids = sorted(routes)
    offenders = set()
    for i, fid in enumerate(ids):
        for gid in ids[i + 1:]:
            if any(R._segments_cross(p, q, r, t)
                   for p, q in zip(routes[fid], routes[fid][1:])
                   for r, t in zip(routes[gid], routes[gid][1:])):
                offenders.add(fid)
                offenders.add(gid)
    return offenders


def rip_up_and_reroute(router, routes, routable, geometry, layer, make_region, verbose):
    """Lift the flows that cross and lay them again around the ones that do not.

    Routing each flow once, in a fixed order, boxes the later ones in: a flow
    that has to get past a line laid before it may find no way but across. Lifting
    it and routing it again with the same reservations in place usually finds the
    way it could not see the first time.
    """
    by_id = {flow["id"]: flow for flow in routable}
    for _ in range(3):
        offenders = conflicting_flows(routes)
        if not offenders:
            break
        improved = False
        for fid in sorted(offenders):
            flow = by_id.get(fid)
            if flow is None or len(routes) < 2:
                continue
            keep = routes[fid]
            del routes[fid]
            router.used.clear()
            for other in routes.values():
                router.reserve(other)
            src, tgt = geometry[flow["src"]], geometry[flow["tgt"]]
            src_box = (src["x"], src["y"], src["w"], src["h"])
            tgt_box = (tgt["x"], tgt["y"], tgt["w"], tgt["h"])
            points = router.route(src_box, tgt_box, region=make_region(flow["src"], flow["tgt"]),
                                  hard=True, limit=EXPANSION_LIMIT)
            if points is None:
                points = router.route(src_box, tgt_box, region=pool_region, hard=True,
                                      limit=EXPANSION_LIMIT)
            if points is None:
                routes[fid] = keep
                router.used.clear()
                for other in routes.values():
                    router.reserve(other)
                continue
            routes[fid] = points
            router.reserve(points)
            improved = True
        if not improved:
            break
    router.used.clear()
    for other in routes.values():
        router.reserve(other)
    return routes


def count_crossings(routes):
    """Pairs of flows whose lines cross, by the same test the verifier uses."""
    ids = sorted(routes)
    crossings = 0
    for i, fid in enumerate(ids):
        for gid in ids[i + 1:]:
            hit = False
            for p, q in zip(routes[fid], routes[fid][1:]):
                for r, t in zip(routes[gid], routes[gid][1:]):
                    if R._segments_cross(p, q, r, t):
                        hit = True
                        break
                if hit:
                    break
            if hit:
                crossings += 1
    return crossings


# --------------------------------------------------------------------- labels
def overlaps(a, b, gap=0.0):
    return not (a[0] + a[2] + gap <= b[0] or b[0] + b[2] + gap <= a[0] or
                a[1] + a[3] + gap <= b[1] or b[1] + b[3] + gap <= a[1])


class LabelPlacer(object):
    """Places labels, and refuses to let them collide."""

    def __init__(self, width, height):
        self.boxes = []
        self.width = width
        self.height = height

    def free(self, box):
        if box[0] < 0 or box[1] < 0 or box[0] + box[2] > self.width or box[1] + box[3] > self.height:
            return False
        return not any(overlaps(box, other, 1.0) for other in self.boxes)

    def take(self, box):
        self.boxes.append(box)
        return box

    def find(self, text, size, limit, candidates):
        """The first candidate box that is free, or the widest one if none is."""
        for box in candidates:
            if self.free(box):
                return self.take(box)
        fallback = candidates[0]
        return self.take(fallback)


# ---------------------------------------------------------------------- writer
def bounds_xml(x, y, w, h, indent):
    return ('%s<dc:Bounds x="%d" y="%d" width="%d" height="%d" />'
            % (indent, round(x), round(y), round(w), round(h)))


def write_diagram(model, geometry, routes, lane_boxes, pool_box, other_pools, labels,
                  message_routes, plane_id):
    hospital = next((p for p in model.participants if p["processRef"] == model.process_id),
                    {"id": "Participant_Hospital", "name": "Hospital"})
    lines = ['  <bpmndi:BPMNDiagram id="BPMNDiagram_1">',
             '    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="%s">' % plane_id]
    px, py, pw, ph = pool_box["x"], pool_box["y"], pool_box["w"], pool_box["h"]
    lines.append('      <bpmndi:BPMNShape id="%s_di" bpmnElement="%s" isHorizontal="true">'
                 % (hospital["id"], hospital["id"]))
    lines.append(bounds_xml(px, py, pw, ph, "        "))
    # A horizontal pool carries its name at the foot of the left header, turned
    # on its side, so the box is narrow and as tall as the name is long. The
    # name is what sets the height, which is why the box is placed by
    # subtracting that height from the foot - placing it by a fixed offset
    # instead put the name outside the pool, below its bottom edge, where the
    # export drew it hanging under the border.
    lines.append("        <bpmndi:BPMNLabel>")
    name_h = min(140.0, R.text_width(hospital["name"], LABEL_SIZE))
    lines.append(bounds_xml(px + 6, py + ph - name_h - 6, 20.0, name_h, "          "))
    lines.append("        </bpmndi:BPMNLabel>")
    lines.append("      </bpmndi:BPMNShape>")

    for lane in model.lanes:
        x, y, w, h = lane_boxes[lane["id"]]
        lines.append('      <bpmndi:BPMNShape id="%s_di" bpmnElement="%s" isHorizontal="true">'
                     % (lane["id"], lane["id"]))
        lines.append(bounds_xml(x, y, w, h, "        "))
        lines.append("        <bpmndi:BPMNLabel>")
        lines.append(bounds_xml(x + 6, y + 6, min(240.0, R.text_width(lane["name"], LABEL_SIZE)),
                                20.0, "          "))
        lines.append("        </bpmndi:BPMNLabel>")
        lines.append("      </bpmndi:BPMNShape>")

    for pool in other_pools:
        lines.append('      <bpmndi:BPMNShape id="%s_di" bpmnElement="%s" isHorizontal="true">'
                     % (pool["id"], pool["id"]))
        lines.append(bounds_xml(pool["x"], pool["y"], pool["w"], pool["h"], "        "))
        lines.append("      </bpmndi:BPMNShape>")

    def shape_order(node):
        """Lanes top to bottom, then the elements in the order they appear."""
        lane = model.lane_of.get(node["id"])
        index = [l["id"] for l in model.lanes].index(lane) if lane in \
            [l["id"] for l in model.lanes] else len(model.lanes)
        return (index, node["id"])

    for node in sorted(model.nodes.values(), key=shape_order):
        box = geometry.get(node["id"])
        if box is None:
            continue
        marker = ' isMarkerVisible="true"' if node["kind"] == "gateway" else ""
        lines.append('      <bpmndi:BPMNShape id="%s_di" bpmnElement="%s"%s>'
                     % (node["id"], node["id"], marker))
        lines.append(bounds_xml(box["x"], box["y"], box["w"], box["h"], "        "))
        label = labels.get(node["id"])
        if label:
            lines.append("        <bpmndi:BPMNLabel>")
            lines.append(bounds_xml(label[0], label[1], label[2], label[3], "          "))
            lines.append("        </bpmndi:BPMNLabel>")
        lines.append("      </bpmndi:BPMNShape>")

    for flow in model.flows.values():
        points = routes.get(flow["id"])
        if not points:
            continue
        lines.append('      <bpmndi:BPMNEdge id="%s_di" bpmnElement="%s">' % (flow["id"], flow["id"]))
        for x, y in points:
            lines.append('        <di:waypoint x="%d" y="%d" />' % (round(x), round(y)))
        if flow["id"] in labels:
            label = labels[flow["id"]]
            lines.append("        <bpmndi:BPMNLabel>")
            lines.append(bounds_xml(label[0], label[1], label[2], label[3], "          "))
            lines.append("        </bpmndi:BPMNLabel>")
        lines.append("      </bpmndi:BPMNEdge>")

    for message in model.messages:
        points = message_routes.get(message["id"])
        if not points:
            continue
        lines.append('      <bpmndi:BPMNEdge id="%s_di" bpmnElement="%s">'
                     % (message["id"], message["id"]))
        for x, y in points:
            lines.append('        <di:waypoint x="%d" y="%d" />' % (round(x), round(y)))
        lines.append("      </bpmndi:BPMNEdge>")

    lines.append("    </bpmndi:BPMNPlane>")
    lines.append("  </bpmndi:BPMNDiagram>")
    return "\n".join(lines) + "\n"


def replace_diagram(text, diagram):
    start = text.find("<bpmndi:BPMNDiagram")
    end = text.rfind("</bpmndi:BPMNDiagram>")
    if start < 0 or end < 0:
        raise SystemExit("no diagram interchange to replace")
    end += len("</bpmndi:BPMNDiagram>")
    return text[:start] + diagram.rstrip("\n") + text[end:]


def relayout(path, verbose=True):
    model = Model(path)
    layer = assign_layers(model)
    layout = place(model, layer)
    geometry = layout["geometry"]
    pool = layout["pool"]

    canvas_w = pool["x"] + pool["w"] + 3 * OUTSIDE
    canvas_h = pool["y"] + pool["h"] + OUTSIDE

    # sequence flow stays inside its pool, and inside the lanes it has business
    # with: confining the search to those bands is what keeps the routing quick
    lane_top = layout["lane_y"]
    lane_height = layout["lane_heights"]
    lane_order = [lane["id"] for lane in model.lanes]

    def band_of(node_id):
        lane = model.lane_of.get(node_id)
        return lane_order.index(lane) if lane in lane_order else None

    def pool_region(x, y):
        return (pool["x"] + CELL <= x <= pool["x"] + pool["w"] - CELL and
                pool["y"] + CELL <= y <= pool["y"] + pool["h"] - CELL)

    def make_region(src_id, tgt_id):
        low, high = band_of(src_id), band_of(tgt_id)
        if low is None or high is None:
            top, bottom = pool["y"], pool["y"] + pool["h"]
        else:
            first, last = min(low, high), max(low, high)
            top = lane_top[lane_order[max(0, first - 1)]]
            last_lane = lane_order[min(len(lane_order) - 1, last + 1)]
            bottom = lane_top[last_lane] + lane_height[last_lane]

        def region(x, y):
            return (pool["x"] + CELL <= x <= pool["x"] + pool["w"] - CELL and
                    top + CELL <= y <= bottom - CELL and
                    pool["y"] + CELL <= y <= pool["y"] + pool["h"] - CELL)
        return region

    placer = LabelPlacer(canvas_w, canvas_h)
    labels = {}

    # element labels first: they are what a reader needs most
    for node in model.nodes.values():
        box = geometry.get(node["id"])
        if box is None or not node["name"]:
            continue
        width = box["w"] if node["kind"] != "task" else box["w"] + 30.0
        limit = width if node["kind"] == "task" else max(width, 190.0)
        lines = R.wrap(node["name"], LABEL_SIZE, limit)
        text_w = max(R.text_width(line, LABEL_SIZE) for line in lines)
        height = len(lines) * LABEL_SIZE * LINE
        top = box["y"] + box["h"] + 4.0
        boundary_below = [n for n in model.boundary_nodes() if n["host"] == node["id"]]
        if boundary_below:
            rows = max(len(R.wrap(b["name"], LABEL_SIZE, 200.0)) for b in boundary_below
                       if b["name"]) if any(b["name"] for b in boundary_below) else 1
            top += rows * LABEL_SIZE * LINE + 4.0
        cx = box["x"] + box["w"] / 2.0
        candidates = [(cx - text_w / 2.0, top, text_w, height)]
        candidates += [(box["x"], top + 6.0, text_w, height),
                       (box["x"] + box["w"] - text_w, top + 6.0, text_w, height)]
        labels[node["id"]] = placer.find(node["name"], LABEL_SIZE, limit, candidates)

    # the boundary events' own labels, in the row between the host and its label
    for node in model.boundary_nodes():
        box = geometry.get(node["id"])
        if box is None or not node["name"]:
            continue
        host = geometry.get(node["host"], box)
        siblings = [n for n in model.boundary_nodes() if n["host"] == node["host"]]
        row = siblings.index(node)
        lines = R.wrap(node["name"], LABEL_SIZE, 200.0)
        text_w = max(R.text_width(line, LABEL_SIZE) for line in lines)
        height = len(lines) * LABEL_SIZE * LINE
        cx = box["x"] + box["w"] / 2.0
        candidates = []
        # a host with several boundary events stacks their labels rather than
        # letting them collide under the same activity, and a label that still
        # collides with a neighbour's moves down a row rather than on top of it
        for step in range(len(siblings) + 3):
            top = host["y"] + host["h"] + 2.0 + step * (height + 4.0)
            candidates += [(cx - text_w / 2.0, top, text_w, height),
                           (cx - text_w - 24.0, top, text_w, height),
                           (cx + 24.0, top, text_w, height)]
        labels[node["id"]] = placer.find(node["name"], LABEL_SIZE, 200.0, candidates)

    def arms():
        """A router with the activities and the labels of this layout on it."""
        fresh = Router(canvas_w, canvas_h)
        for node in model.nodes.values():
            box = geometry.get(node["id"])
            if box is not None:
                fresh.block_rect(box["x"], box["y"], box["w"], box["h"])
        for node in model.boundary_nodes():
            box = geometry.get(node["id"])
            if box is not None:
                fresh.unblock_rect(box["x"], box["y"], box["w"], box["h"])
        for label in placer.boxes:
            fresh.soft_block_rect(*label)
        return fresh

    # then the flows. The order they are routed in decides how many crossings
    # survive, and no single order is best for every model, so each is tried and
    # the one with the fewest crossings wins.
    routable = [f for f in model.flows.values()
                if f["src"] in geometry and f["tgt"] in geometry]

    by_id = {flow["id"]: flow for flow in model.flows.values()}
    boundary_of = {node["id"]: node["host"] for node in model.boundary_nodes()}

    def span(flow):
        return abs(layer.get(flow["src"], 0) - layer.get(flow["tgt"], 0))

    def left_of(flow):
        return geometry[flow["src"]]["x"]

    orders = [("longest first", lambda f: -span(f)),
              ("left to right", lambda f: left_of(f))]
    if "--fast" in sys.argv:
        # one order, one pass: for a diagram this wide, trying every combination
        # costs far more than the crossings it saves
        orders = orders[:1]
    best = None
    for name, key in orders:
        for strict in (True, False):
            router = arms()
            routes = {}
            for flow in sorted(routable, key=key):
                points = route_one(router, flow, geometry, make_region, pool_region, strict)
                if points is None:
                    if verbose:
                        print("      no route for %s (%s -> %s)"
                              % (flow["id"], flow["src"], flow["tgt"]))
                    continue
                routes[flow["id"]] = points
                router.reserve(points)
            offenders = shape_crossings(routes, geometry, by_id, boundary_of)
            if offenders:
                # a line through an activity is a fault, not a tidiness problem:
                # the flow that does it is one the earlier ones boxed in, so it
                # is routed first this time
                ahead = [f for f in sorted(routable, key=key) if f["id"] in offenders]
                rest = [f for f in sorted(routable, key=key) if f["id"] not in offenders]
                router = arms()
                routes = {}
                for flow in ahead + rest:
                    points = route_one(router, flow, geometry, make_region, pool_region, strict)
                    if points is None:
                        continue
                    routes[flow["id"]] = points
                    router.reserve(points)
            crossings = count_crossings(routes)
            if verbose:
                print("      %-16s %-6s %2d crossings, %d of %d routed"
                      % (name, "strict" if strict else "shared", crossings,
                         len(routes), len(routable)))
            if best is None or (crossings, -len(routes)) < (best[0], -len(best[1])):
                best = (crossings, routes)
    routes = best[1]

    # flow labels beside their flow, near the branch they belong to
    for flow in model.flows.values():
        text = flow["name"] or flow["condition"]
        points = routes.get(flow["id"])
        if not text or not points:
            continue
        lines = R.wrap(text, FLOW_SIZE, 220.0)
        text_w = max(R.text_width(line, FLOW_SIZE) for line in lines)
        height = len(lines) * FLOW_SIZE * LINE
        candidates = []
        for index in (1, 2):
            if len(points) <= index:
                continue
            p, q = points[index - 1], points[index]
            if p[1] == q[1]:                       # horizontal
                mid = (p[0] + q[0]) / 2.0
                candidates.append((mid - text_w / 2.0, p[1] - height - 6.0, text_w, height))
                candidates.append((mid - text_w / 2.0, p[1] + 6.0, text_w, height))
            else:                                   # vertical
                mid = (p[1] + q[1]) / 2.0
                candidates.append((p[0] + 8.0, mid - height / 2.0, text_w, height))
                candidates.append((p[0] - text_w - 8.0, mid - height / 2.0, text_w, height))
            # and further out from the line, so a label has somewhere to go when
            # an element label is sitting where it wants to be
            for shift in (10.0, 22.0, 34.0):
                if p[1] == q[1]:
                    mid = (p[0] + q[0]) / 2.0
                    candidates.append((mid - text_w / 2.0, p[1] - height - 6.0 - shift, text_w, height))
                    candidates.append((mid - text_w / 2.0, p[1] + 6.0 + shift, text_w, height))
                    candidates.append((p[0] + 12.0, p[1] - height - 6.0 - shift, text_w, height))
                else:
                    mid = (p[1] + q[1]) / 2.0
                    candidates.append((p[0] + 8.0 + shift, mid - height / 2.0, text_w, height))
                    candidates.append((p[0] - text_w - 8.0 - shift, mid - height / 2.0, text_w, height))
        if not candidates:
            continue
        labels[flow["id"]] = placer.find(text, FLOW_SIZE, 220.0, candidates)

    # the black box pools, to the right of the hospital, each level with the
    # activity it exchanges messages with
    others = [p for p in model.participants if p["processRef"] is None]
    partners = {}
    for message in model.messages:
        for end, other in ((message["src"], message["tgt"]), (message["tgt"], message["src"])):
            if end in geometry and other in {p["id"] for p in others}:
                box = geometry[end]
                partners.setdefault(other, []).append(box["y"] + box["h"] / 2.0)
    other_pools = []
    right = pool["x"] + pool["w"] + OUTSIDE
    placed = []
    for pool_el in others:
        centre = sum(partners.get(pool_el["id"], [pool["y"] + 40.0])) / \
            len(partners.get(pool_el["id"], [1]))
        y = centre - 30.0
        while any(abs(y - o["y"]) < 40.0 for o in placed):
            y += 40.0
        row = {"id": pool_el["id"], "name": pool_el["name"], "x": right, "y": y, "w": 330.0, "h": 60.0}
        placed.append(row)
        other_pools.append(row)

    # message flows: they leave the hospital pool, so they may cross its border
    message_routes = {}
    message_router = Router(right + 330.0 + OUTSIDE, canvas_h)
    for node in model.nodes.values():
        box = geometry.get(node["id"])
        if box is not None:
            message_router.block_rect(box["x"], box["y"], box["w"], box["h"])
    for pool_row in other_pools:
        message_router.block_rect(pool_row["x"], pool_row["y"], pool_row["w"], pool_row["h"])

    for message in model.messages:
        box_of_pool = {p["id"]: p for p in other_pools}
        src = geometry.get(message["src"]) or box_of_pool.get(message["src"])
        tgt = geometry.get(message["tgt"]) or box_of_pool.get(message["tgt"])
        if src is None or tgt is None:
            continue
        points = message_router.route((src["x"], src["y"], src["w"], src["h"]),
                                      (tgt["x"], tgt["y"], tgt["w"], tgt["h"]))
        if points is None:
            continue
        message_routes[message["id"]] = points
        message_router.reserve(points)

    lane_boxes = {}
    for lane in model.lanes:
        lane_boxes[lane["id"]] = (pool["x"], layout["lane_y"][lane["id"]], pool["w"],
                                  layout["lane_heights"][lane["id"]])

    plane_id = model.collaboration_id or model.process_id
    diagram = write_diagram(model, geometry, routes, lane_boxes, pool, other_pools, labels,
                            message_routes, plane_id)
    text = open(path, encoding="utf-8", newline="").read()
    newline = "\r\n" if "\r\n" in text else "\n"
    diagram = diagram.replace("\n", newline)
    updated = replace_diagram(text, diagram)
    with open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(updated)
    return len(routes), len(model.flows)


def main():
    targets = sys.argv[1:]
    if not targets:
        for directory in MODEL_DIRS:
            for name in sorted(os.listdir(directory)):
                if name.endswith(".bpmn"):
                    targets.append(os.path.join(directory, name))
    for path in targets:
        if "--fast" in sys.argv and path.endswith(".bpmn") is False:
            continue
        routed, total = relayout(path)
        print("%-58s %d of %d flows routed" % (os.path.basename(path), routed, total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
