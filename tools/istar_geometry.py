# -*- coding: utf-8 -*-
"""The geometry the two i* drawings share.

`istar_render.py` draws the models into PDFs and PNGs and `convert_istar_to_pistar.py`
writes them in piStar's format; both have to place the same actors, the same
dependums and the same rationale elements, and a reader who opens one drawing
and then the other should find the same shape. So the placement lives here,
once, and each tool supplies only what it draws with - the size it gives an
element, the radius it gives an actor.

The shapes:
  - **SD**: a hub with a ring of actors round it. The actor most of a view's
    dependencies run through sits at the centre; the rest sit on one ring in
    the order that leaves the fewest lines crossing, and every dependum sits on
    the line between the two actors it joins, lanes deep where several
    dependencies run between the same pair.
  - **SR**: rows of elements by kind, each row ordered so the links stay short.

Everything here is deterministic: the same model always lays out the same way,
which is what lets a checker measure the drawing that is produced rather than a
second opinion about it.
"""
import math

ROW_KINDS = ("softgoal", "goal", "task", "resource")

RING_BASE_R = 420.0       # the ring the actors sit on, before dependums are placed
RING_GROWTH = 1.32        # how much wider the ring is tried when it is crowded
LANE_GAP = 30.0           # clear space between two dependums of the same pair
LINE_CLEAR = 16.0         # how far a dependum keeps off another dependency's line
LINE_STEP = 8.0           # the most one pass moves a box away from a line
RELAX_ROUNDS = 420


def seg_side(p, q, r):
    """Which side of the line p->q the point r is on: the sign of the cross
    product. Zero means the three points are in line."""
    return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])


def segments_cross(a, b, c, d):
    """True when the segments a-b and c-d cross at a point interior to both.

    Segments that share an endpoint do not count - two dependencies read from
    the same actor meet there - and neither does a shared line, which the lane
    offsets are what prevent."""
    s1, s2 = seg_side(a, b, c), seg_side(a, b, d)
    s3, s4 = seg_side(c, d, a), seg_side(c, d, b)
    return s1 * s2 < 0 and s3 * s4 < 0


def seg_hit_box(p, q, box, pad=0.0):
    """True when the segment p-q meets the box grown by `pad` (Liang-Barsky)."""
    x0, y0, x1, y1 = box[0] - pad, box[1] - pad, box[2] + pad, box[3] + pad
    dx, dy = q[0] - p[0], q[1] - p[1]
    t0, t1 = 0.0, 1.0
    for pnum, qnum in ((-dx, p[0] - x0), (dx, x1 - p[0]),
                       (-dy, p[1] - y0), (dy, y1 - p[1])):
        if pnum == 0.0:
            if qnum < 0.0:
                return False
            continue
        r = qnum / pnum
        if pnum < 0.0:
            if r > t1:
                return False
            if r > t0:
                t0 = r
        else:
            if r < t0:
                return False
            if r < t1:
                t1 = r
    return True


def seg_circle_hit(p, q, cx, cy, r, pad=0.0):
    """True when the segment p-q comes within r + pad of the circle's centre."""
    dx, dy = q[0] - p[0], q[1] - p[1]
    length2 = dx * dx + dy * dy
    if length2 == 0.0:
        return math.hypot(p[0] - cx, p[1] - cy) < r + pad
    t = max(0.0, min(1.0, ((cx - p[0]) * dx + (cy - p[1]) * dy) / length2))
    return math.hypot(p[0] + t * dx - cx, p[1] + t * dy - cy) < r + pad


def box_center(box):
    return ((box[0] + box[2]) / 2.0, (box[1] + box[3]) / 2.0)


def shift_box(box, dx, dy):
    return [box[0] + dx, box[1] + dy, box[2] + dx, box[3] + dy]


def rect(x, y, w, h):
    return (x, y, x + w, y + h)


def boxes_overlap(a, b, gap_x=0.0, gap_y=0.0):
    return not (a[2] + gap_x <= b[0] or b[2] + gap_x <= a[0] or
                a[3] + gap_y <= b[1] or b[3] + gap_y <= a[1])


def push_apart(a, b, gap_x, gap_y):
    """Move the two boxes the small amount that stops them overlapping."""
    acx, acy = box_center(a)
    bcx, bcy = box_center(b)
    dx = (a[2] - a[0] + b[2] - b[0]) / 2.0 + gap_x - abs(acx - bcx)
    dy = (a[3] - a[1] + b[3] - b[1]) / 2.0 + gap_y - abs(acy - bcy)
    if dx <= 0.0 or dy <= 0.0:
        return a, b
    if dx < dy:
        shift = dx / 2.0
        direction = 1.0 if acx <= bcx else -1.0
        a = (a[0] - direction * shift, a[1], a[2] - direction * shift, a[3])
        b = (b[0] + direction * shift, b[1], b[2] + direction * shift, b[3])
    else:
        shift = dy / 2.0
        direction = 1.0 if acy <= bcy else -1.0
        a = (a[0], a[1] - direction * shift, a[2], a[3] - direction * shift)
        b = (b[0], b[1] + direction * shift, b[2], b[3] + direction * shift)
    return a, b


def circle_rect_overlap(box, cx, cy, r, pad=10.0):
    nx = min(max(cx, box[0]), box[2])
    ny = min(max(cy, box[1]), box[3])
    return math.hypot(cx - nx, cy - ny) < r + pad


# ------------------------------------------------------------------ SD layout
def ring_order_search(ring, hub_id, deps):
    """The order of `ring` round the hub that leaves the fewest lines crossing.

    The cost is read off unit positions - the hub at the origin, the ring on a
    circle of radius one - because crossings depend on the order and not on how
    wide the ring is drawn. Swaps are tried first, then reversals of a run of
    the ring, each kept while it lowers the count; the result is deterministic.
    """
    if len(ring) < 3:
        return list(ring)
    order = list(ring)

    def crossings(perm):
        n = len(perm)
        pos = {hub_id: (0.0, 0.0)}
        for i, aid in enumerate(perm):
            angle = 2.0 * math.pi * i / n
            pos[aid] = (math.cos(angle), math.sin(angle))
        segs = []
        for d in deps:
            a, b = pos[d["from"]], pos[d["to"]]
            segs.append((a, b, min(a[0], b[0]), min(a[1], b[1]),
                         max(a[0], b[0]), max(a[1], b[1])))
        total = 0
        for i in range(len(segs)):
            a, b, ax0, ay0, ax1, ay1 = segs[i]
            for j in range(i + 1, len(segs)):
                c, e, bx0, by0, bx1, by1 = segs[j]
                if ax1 < bx0 or bx1 < ax0 or ay1 < by0 or by1 < ay0:
                    continue
                if segments_cross(a, b, c, e):
                    total += 1
        return total

    best = crossings(order)
    improved = True
    while improved:
        improved = False
        for i in range(len(order)):
            for j in range(i + 1, len(order)):
                order[i], order[j] = order[j], order[i]
                count = crossings(order)
                if count < best:
                    best = count
                    improved = True
                else:
                    order[i], order[j] = order[j], order[i]
        for i in range(len(order)):
            for j in range(i + 1, len(order)):
                candidate = order[:i] + order[i:j + 1][::-1] + order[j + 1:]
                count = crossings(candidate)
                if count < best:
                    best = count
                    order = candidate
                    improved = True
    return order


def ring_positions(order, hub_id, radius):
    """Centres: the hub at the origin, the ring evenly spaced from the top."""
    n = max(1, len(order))
    centers = {hub_id: (0.0, 0.0)}
    for i, aid in enumerate(order):
        angle = 2.0 * math.pi * i / n - math.pi / 2.0
        centers[aid] = (radius * math.cos(angle), radius * math.sin(angle))
    return centers


def dependum_anchors(deps, centers, size_of, lane_gap=LANE_GAP):
    """Where each dependum wants to sit: the middle of its line, moved clear of
    its siblings along the same line.

    Several dependencies between the same two actors are drawn as several lines
    side by side, so each is offset along the perpendicular by the height of the
    tallest of them plus a gap. Without that the lines would be drawn on top of
    each other, which is the overlap this whole arrangement exists to avoid."""
    groups = {}
    for d in deps:
        key = tuple(sorted((d["from"], d["to"])))
        groups.setdefault(key, []).append(d)
    anchors, sizes = {}, {}
    for key in sorted(groups):
        group = sorted(groups[key], key=lambda d: d["id"])
        (ax, ay), (bx, by) = centers[key[0]], centers[key[1]]
        mx, my = (ax + bx) / 2.0, (ay + by) / 2.0
        dx, dy = bx - ax, by - ay
        length = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / length, dx / length
        measured = [size_of(d) for d in group]
        lane = max(h for _, h in measured) + lane_gap
        for i, d in enumerate(group):
            w, h = measured[i]
            offset = (i - (len(group) - 1) / 2.0) * lane
            sizes[d["id"]] = (w, h)
            anchors[d["id"]] = (mx + nx * offset, my + ny * offset)
    return anchors, sizes


def relax_dependums(boxes, anchors, circles, deps, gap_x, gap_y,
                    line_clear=LINE_CLEAR, line_step=LINE_STEP, rounds=RELAX_ROUNDS,
                    obstacles=None, push=12.0):
    """Settle the dependums: each is pulled to its anchor and pushed off the
    other lines, the actors and the other dependums, until it stops moving.
    `circles` maps an actor id to (cx, cy, r) - the shape an actor takes in an
    SD view. `obstacles`, when given, maps an actor id to the rectangle its
    boundary takes in a drawing that expands the actors, and is used in place of
    the circles. Deterministic - the same model always settles the same way."""
    ids = sorted(boxes)
    by_id = {d["id"]: d for d in deps}
    centers = {aid: (cx, cy) for aid, (cx, cy, _) in circles.items()}
    stranded = set()
    if obstacles is not None:
        for aid in ids:                                  # an anchor inside a boundary
            ax, ay = anchors[aid]                        # is no anchor at all
            for r in obstacles.values():
                if r[0] <= ax <= r[2] and r[1] <= ay <= r[3]:
                    stranded.add(aid)
                    break
    for _ in range(rounds):
        moved = False
        pulled = 0.0
        for aid in ids:                                  # pull towards the anchor
            if aid in stranded:
                continue
            box = boxes[aid]
            cx, cy = box_center(box)
            ax, ay = anchors[aid]
            box[0] += (ax - cx) * 0.06
            box[2] += (ax - cx) * 0.06
            box[1] += (ay - cy) * 0.06
            box[3] += (ay - cy) * 0.06
            pulled += abs(ax - cx) + abs(ay - cy)
        lines = []                                       # every line, as drawn
        for aid in ids:
            d = by_id[aid]
            c = box_center(boxes[aid])
            for p, q in ((centers[d["from"]], c), (c, centers[d["to"]])):
                lines.append((p[0], p[1], q[0], q[1], aid,
                              min(p[0], q[0]), min(p[1], q[1]),
                              max(p[0], q[0]), max(p[1], q[1])))
        for aid in ids:                                  # off the other lines
            box = boxes[aid]
            for (px, py, qx, qy, owner, lx0, ly0, lx1, ly1) in lines:
                if owner == aid:
                    continue
                if (lx1 + line_clear < box[0] or lx0 - line_clear > box[2] or
                        ly1 + line_clear < box[1] or ly0 - line_clear > box[3]):
                    continue
                p, q = (px, py), (qx, qy)
                if not seg_hit_box(p, q, box, line_clear):
                    continue
                dx, dy = q[0] - p[0], q[1] - p[1]
                length = math.hypot(dx, dy) or 1.0
                cx, cy = box_center(box)
                side = seg_side(p, q, (cx, cy))
                sign = 1.0 if side >= 0.0 else -1.0
                box[0] += sign * (-dy / length) * line_step
                box[2] += sign * (-dy / length) * line_step
                box[1] += sign * (dx / length) * line_step
                box[3] += sign * (dx / length) * line_step
                moved = True
        for aid in ids:                                  # off the actor circles
            d = by_id[aid]
            box = boxes[aid]
            if obstacles is not None:
                for actor_id in sorted(obstacles):
                    r = obstacles[actor_id]
                    if not boxes_overlap(box, r, 4.0, 4.0):
                        continue
                    dx = (r[2] - r[0] + box[2] - box[0]) / 2.0 + 16.0 -                         abs((r[0] + r[2]) / 2.0 - box_center(box)[0])
                    dy = (r[3] - r[1] + box[3] - box[1]) / 2.0 + 16.0 -                         abs((r[1] + r[3]) / 2.0 - box_center(box)[1])
                    if dx > 0 and dy > 0:
                        cx, cy = box_center(box)
                        ox, oy = (r[0] + r[2]) / 2.0, (r[1] + r[3]) / 2.0
                        if dx < dy:
                            step = min(push, dx)
                            boxes[aid] = shift_box(box, step if cx >= ox else -step, 0.0)
                        else:
                            step = min(push, dy)
                            boxes[aid] = shift_box(box, 0.0, step if cy >= oy else -step)
                        moved = True
                continue
            for actor_id in sorted(circles):
                if actor_id in (d["from"], d["to"]):
                    continue
                cx, cy, radius = circles[actor_id]
                if (box[2] < cx - radius - 10.0 or box[0] > cx + radius + 10.0 or
                        box[3] < cy - radius - 10.0 or box[1] > cy + radius + 10.0):
                    continue
                if not circle_rect_overlap(box, cx, cy, radius, 10.0):
                    continue
                bx, by = box_center(box)
                dx, dy = bx - cx, by - cy
                length = math.hypot(dx, dy) or 1.0
                nx = min(max(cx, box[0]), box[2])
                ny = min(max(cy, box[1]), box[3])
                reach = math.hypot(cx - nx, cy - ny)
                step = min(14.0, (radius + 12.0 - reach) + 1.0)
                if step > 0:
                    boxes[aid] = shift_box(box, dx / length * step, dy / length * step)
                    moved = True
        for i, aid in enumerate(ids):                    # off each other
            for other in ids[i + 1:]:
                if boxes_overlap(boxes[aid], boxes[other]):
                    a, b = push_apart(tuple(boxes[aid]), tuple(boxes[other]), gap_x, gap_y)
                    boxes[aid], boxes[other] = list(a), list(b)
                    moved = True
        if not moved and pulled < 0.5:
            break
    if obstacles is not None:
        # anything the pushes could not fit in the gap it was aiming for is moved
        # along the page, up or down, to the nearest place that is clear - the
        # band between two rows of boundaries is where it ends up
        for aid in ids:
            box = boxes[aid]
            if not any(boxes_overlap(box, r, 6.0, 6.0) for r in obstacles.values()):
                continue
            for distance in range(20, 1400, 20):
                for direction in (-1.0, 1.0):
                    candidate = shift_box(box, 0.0, direction * distance)
                    if not any(boxes_overlap(candidate, r, 6.0, 6.0)
                               for r in obstacles.values()):
                        boxes[aid] = candidate
                        break
                else:
                    continue
                break
    def settle():
        for _ in range(60):                              # pushes alone, so nothing
            moved = False                                # is left touching
            for i, aid in enumerate(ids):
                for other in ids[i + 1:]:
                    if boxes_overlap(boxes[aid], boxes[other]):
                        a, b = push_apart(tuple(boxes[aid]), tuple(boxes[other]), gap_x, gap_y)
                        boxes[aid], boxes[other] = list(a), list(b)
                        moved = True
            if obstacles is not None:
                for aid in ids:
                    box = boxes[aid]
                    for actor_id in sorted(obstacles):
                        r = obstacles[actor_id]
                        if not boxes_overlap(box, r, 6.0, 6.0):
                            continue
                        cx, cy = box_center(box)
                        ox, oy = (r[0] + r[2]) / 2.0, (r[1] + r[3]) / 2.0
                        dx = (r[2] - r[0] + box[2] - box[0]) / 2.0 + 18.0 - abs(ox - cx)
                        dy = (r[3] - r[1] + box[3] - box[1]) / 2.0 + 18.0 - abs(oy - cy)
                        if dx <= 0 or dy <= 0:
                            continue
                        if dx < dy:
                            step = min(push + 4.0, dx)
                            boxes[aid] = shift_box(box, step if cx >= ox else -step, 0.0)
                        else:
                            step = min(push + 4.0, dy)
                            boxes[aid] = shift_box(box, 0.0, step if cy >= oy else -step)
                        box = boxes[aid]
                        moved = True
            for aid in ids:
                d = by_id[aid]
                box = boxes[aid]
                for actor_id in sorted(circles):
                    if actor_id in (d["from"], d["to"]) and obstacles is not None:
                        continue
                    cx, cy, radius = circles[actor_id]
                    if circle_rect_overlap(box, cx, cy, radius, 2.0):
                        bx, by = box_center(box)
                        dx, dy = bx - cx, by - cy
                        length = math.hypot(dx, dy) or 1.0
                        boxes[aid] = shift_box(box, dx / length * 8.0, dy / length * 8.0)
                        moved = True
            if not moved:
                break

    def clear_spot(box):
        """Whether this box is clear of every boundary and every other dependum."""
        for r in obstacles.values():
            if boxes_overlap(box, r, 6.0, 6.0):
                return False
        for other in ids:
            if boxes[other] is box:
                continue
            if boxes_overlap(box, boxes[other], 2.0, 2.0):
                return False
        return True

    def unstick():
        """Anything the pushes could not fit where it was aiming is moved along
        the page, up or down, to the nearest place that is clear - the band
        between two rows of boundaries is where it ends up."""
        for aid in ids:
            box = boxes[aid]
            if not any(boxes_overlap(box, r, 6.0, 6.0) for r in obstacles.values()):
                continue
            for distance in range(20, 2400, 20):
                for direction in (-1.0, 1.0):
                    candidate = shift_box(box, 0.0, direction * distance)
                    if clear_spot(candidate):
                        boxes[aid] = candidate
                        break
                else:
                    continue
                break

    if obstacles is not None:
        for _ in range(3):
            settle()
            unstick()
    else:
        settle()
    return boxes


def centers_of(circles):
    """The centres of a {id: (cx, cy, r)} mapping."""
    return {aid: (cx, cy) for aid, (cx, cy, _) in circles.items()}


def placement_cost(deps, circles, boxes):
    """What a placement looks like from outside: lines crossing, and lines
    running through a dependum or an actor circle that is not theirs."""
    centers = centers_of(circles)
    crossings = 0
    segments = []
    for d in deps:
        c = box_center(boxes[d["id"]])
        segments.append((centers[d["from"]], c, d["id"]))
        segments.append((c, centers[d["to"]], d["id"]))
    for i in range(len(segments)):
        p, q, di = segments[i]
        for j in range(i + 1, len(segments)):
            r, s, dj = segments[j]
            if di == dj:
                continue
            if segments_cross(p, q, r, s):
                crossings += 1
    penetrations = 0
    for d in deps:
        c = box_center(boxes[d["id"]])
        for p, q in ((centers[d["from"]], c), (c, centers[d["to"]])):
            for other in deps:
                if other["id"] == d["id"]:
                    continue
                if seg_hit_box(p, q, boxes[other["id"]], 0.0):
                    penetrations += 1
                    break
            for aid, (cx, cy, radius) in circles.items():
                if aid in (d["from"], d["to"]):
                    continue
                if seg_circle_hit(p, q, cx, cy, radius, 0.0):
                    penetrations += 1
                    break
    return crossings, penetrations


def pick_hub(ids, deps, hub=None):
    """The actor the view's dependencies run through most often.

    A caller can name the hub instead - `hub` - which is what the workshop
    model's drawing does with the patient, the actor every dependency of that
    model runs from."""
    if len(ids) < 2:
        return ids[0]
    if hub is not None and hub in ids:
        return hub
    degree = {aid: 0 for aid in ids}
    for d in deps:
        degree[d["from"]] += 1
        degree[d["to"]] += 1
    return max(ids, key=lambda aid: (degree[aid], -ids.index(aid)))


def place_ring_view(ids, deps, size_of, circle_of, gap_x=44.0, gap_y=30.0, hub=None):
    """Place a view: hub, ring, dependums. `size_of(dependum dict)` gives the box
    an element is drawn in, `circle_of(actor id, radius hint)` is filled in by
    the caller once the centres are known - so it is passed in as a function of
    the centre. `hub` names the actor at the centre, where a caller wants a
    particular one there.

    Returns (order, centers, circles, boxes): the actor ids hub-first, the actor
    centres, the circles the caller will draw (same centres), and the dependum
    boxes as [x0, y0, x1, y1]."""
    hub = pick_hub(ids, deps, hub)
    ring = [aid for aid in ids if aid != hub]
    order = ring_order_search(ring, hub, deps) if ring else []
    best = None
    radius = RING_BASE_R
    for _ in range(4):
        centers = ring_positions(order, hub, radius)
        circles = {aid: (cx, cy, circle_of(aid)) for aid, (cx, cy) in centers.items()}
        anchors, sizes = dependum_anchors(deps, centers, size_of)
        boxes = {}
        for d in deps:
            w, h = sizes[d["id"]]
            cx, cy = anchors[d["id"]]
            boxes[d["id"]] = [cx - w / 2.0, cy - h / 2.0, cx + w / 2.0, cy + h / 2.0]
        boxes = relax_dependums(boxes, anchors, circles, deps, gap_x, gap_y)
        crossings, penetrations = placement_cost(deps, circles, boxes)
        drift = max((math.hypot(box_center(boxes[aid])[0] - anchors[aid][0],
                                box_center(boxes[aid])[1] - anchors[aid][1])
                     for aid in boxes), default=0.0)
        score = (penetrations, crossings, round(drift, 1))
        if best is None or score < best[0]:
            best = (score, centers, boxes, radius)
        if penetrations == 0 and drift < radius * 0.45:
            break
        radius *= RING_GROWTH
    return [hub] + order, best[1], best[2]


def view_dependencies(sd, view):
    """The dependencies a view draws.

    A view selects by the operational stage its dependencies cite, and - where
    it names no stage - by the actors it names. A view that names neither is
    drawn as the whole model."""
    stages = view.get("stages")
    if stages:
        return [d for d in sd["dependencies"] if d.get("stage") in stages]
    ids = view.get("actors")
    if ids:
        return [d for d in sd["dependencies"]
                if d["from"] in ids or d["to"] in ids]
    return list(sd["dependencies"])

# ------------------------------------------------------------------ SR layout
def row_order(actor, neighbours=None):
    """The elements of each row, ordered to keep the links short.

    Rows of elements joined by links above and below them are a small layering
    problem: sorting each row by the average position of what it is linked to
    in the rows next to it - sweeping down and up in turns, a few times - pulls
    linked elements towards each other and takes the crossings out of the
    middle of the page. Elements that nothing links stay where they are."""
    if neighbours is None:
        neighbours = {}
        for el in actor["elements"]:
            for link in el.get("links", []):
                neighbours.setdefault(el["id"], []).append(link["to"])
                neighbours.setdefault(link["to"], []).append(el["id"])
    rows = {kind: [el["id"] for el in actor["elements"] if el["kind"] == kind]
            for kind in ROW_KINDS}
    slot = {}

    def sync():
        for r, kind in enumerate(ROW_KINDS):
            for i, eid in enumerate(rows[kind]):
                slot[eid] = (r, i)

    sync()
    for sweep in range(8):
        kinds = ROW_KINDS[::(1 if sweep % 2 == 0 else -1)]
        for kind in kinds:
            keyed = []
            for eid in rows[kind]:
                near = [slot[n][1] for n in neighbours.get(eid, [])
                        if n in slot and slot[n][0] != slot[eid][0]]
                keyed.append((sum(near) / len(near) if near else slot[eid][1], eid))
            keyed.sort()
            rows[kind] = [eid for _, eid in keyed]
            sync()

    # A link inside one row - a task refined by its parts, a task that is one way
    # of doing another - reads best with its two ends beside each other, and the
    # crossing count the sweep works on is blind to that: the distance costs
    # nothing until it happens to bring two lines together. So each row is swept
    # once more and a linked pair is closed up. Whoever calls this runs its own
    # search over the result, which keeps the change only where it does no harm.
    for kind in ROW_KINDS:
        row = rows[kind]
        for _ in range(4):
            changed = False
            for eid in list(row):
                for other in neighbours.get(eid, []):
                    if other not in row or other == eid:
                        continue
                    before = row.index(eid) < row.index(other)
                    if abs(row.index(eid) - row.index(other)) == 1:
                        continue
                    row.remove(other)
                    at = row.index(eid) + (1 if before else 0)
                    row.insert(at, other)
                    changed = True
            if not changed:
                break
    return rows


# ---------------------------------------------------- packing many boundaries
def shelf_layout(order, sizes, target_width, gap_x, gap_y):
    """Place the boxes in rows, left to right and top to bottom, each row as
    tall as its tallest box: the arrangement a drawing of many actor
    boundaries wants, where a row of small actors wastes far less page than a
    grid whose every cell is as big as the biggest one."""
    places, x, y, row_h, widest = {}, 0.0, 0.0, 0.0, 0.0
    for aid in order:
        w, h = sizes[aid]
        if x > 0.0 and x + w > target_width:
            x, y, row_h = 0.0, y + row_h + gap_y, 0.0
        places[aid] = (x, y)
        x += w + gap_x
        widest = max(widest, x - gap_x)
        row_h = max(row_h, h)
    return places, (widest, y + row_h)


def pack_order_search(ids, deps, sizes, gap_x=90.0, gap_y=110.0, target_width=None):
    """The order of the boxes on the page that leaves the fewest dependency
    lines crossing, over a few page widths.

    Boxes that carry many dependencies want to sit near the boxes they talk to,
    and the arrangement is a shelf, so the order is what decides the page. Swaps
    are tried and kept while they lower the number of crossing lines, measured
    between the box centres the drawing puts the lines through, with the total
    length of those lines as the tie-break; the page width is settled by trying
    several and keeping the one that crosses least - and, where the count is the
    same, the one whose shape reads best, which is roughly landscape. A caller
    that wants a particular shape passes `target_width` and gets that one."""
    if len(ids) < 3:
        return list(ids), shelf_layout(list(ids), sizes, 4000.0, gap_x, gap_y)
    total = sum(sizes[i][0] * sizes[i][1] for i in ids)
    area_side = math.sqrt(total)
    candidates = ([target_width] if target_width else
                  [area_side * factor for factor in (0.85, 1.1, 1.35, 1.6, 1.9)])

    def layout(order, width):
        places, extent = shelf_layout(order, sizes, width, gap_x, gap_y)
        return {aid: (x + sizes[aid][0] / 2.0, y + sizes[aid][1] / 2.0)
                for aid, (x, y) in places.items()}, extent

    def measure(centers):
        """(crossing pairs, total line length) for a placement."""
        segs, length = [], 0.0
        for d in deps:
            a, b = centers[d["from"]], centers[d["to"]]
            length += math.hypot(b[0] - a[0], b[1] - a[1])
            segs.append((a, b, min(a[0], b[0]), min(a[1], b[1]),
                         max(a[0], b[0]), max(a[1], b[1])))
        count = 0
        for i in range(len(segs)):
            a, b, ax0, ay0, ax1, ay1 = segs[i]
            for j in range(i + 1, len(segs)):
                c, e, bx0, by0, bx1, by1 = segs[j]
                if ax1 < bx0 or bx1 < ax0 or ay1 < by0 or by1 < ay0:
                    continue
                if segments_cross(a, b, c, e):
                    count += 1
        return count, length

    tried = []
    for width in candidates:
        order = sorted(ids)
        centers, extent = layout(order, width)
        best_score = measure(centers)
        improved = True
        while improved:
            improved = False
            for i in range(len(order)):
                for j in range(i + 1, len(order)):
                    order[i], order[j] = order[j], order[i]
                    centers, extent = layout(order, width)
                    score = measure(centers)
                    if score < best_score:
                        best_score = score
                        improved = True
                    else:
                        order[i], order[j] = order[j], order[i]
        aspect = extent[0] / max(extent[1], 1.0)
        tried.append((best_score[0], abs(aspect - 1.5), best_score[1], list(order), width))

    # the page shape is worth a little: among the widths whose crossing count is
    # within a few of the best, the one that reads best wins - a landscape page
    # rather than a strip
    fewest = min(t[0] for t in tried)
    tolerance = max(4.0, fewest * 0.15)
    best = min((t for t in tried if t[0] <= fewest + tolerance),
               key=lambda t: (t[1], t[2]))
    places, extent = shelf_layout(best[3], sizes, best[4], gap_x, gap_y)
    return best[3], (places, extent)


# ------------------------------------------------------------------ SR layout
def row_order(actor, neighbours=None):
    """The elements of each row, ordered to keep the links short.

    Rows of elements joined by links above and below them are a small layering
    problem: sorting each row by the average position of what it is linked to
    in the rows next to it - sweeping down and up in turns, a few times - pulls
    linked elements towards each other and takes the crossings out of the
    middle of the page. Elements that nothing links stay where they are."""
    if neighbours is None:
        neighbours = {}
        for el in actor["elements"]:
            for link in el.get("links", []):
                neighbours.setdefault(el["id"], []).append(link["to"])
                neighbours.setdefault(link["to"], []).append(el["id"])
    rows = {kind: [el["id"] for el in actor["elements"] if el["kind"] == kind]
            for kind in ROW_KINDS}
    slot = {}

    def sync():
        for r, kind in enumerate(ROW_KINDS):
            for i, eid in enumerate(rows[kind]):
                slot[eid] = (r, i)

    sync()
    for sweep in range(8):
        kinds = ROW_KINDS[::(1 if sweep % 2 == 0 else -1)]
        for kind in kinds:
            keyed = []
            for eid in rows[kind]:
                near = [slot[n][1] for n in neighbours.get(eid, [])
                        if n in slot and slot[n][0] != slot[eid][0]]
                keyed.append((sum(near) / len(near) if near else slot[eid][1], eid))
            keyed.sort()
            rows[kind] = [eid for _, eid in keyed]
            sync()

    # A link inside one row - a task refined by its parts, a task that is one way
    # of doing another - reads best with its two ends beside each other, and the
    # crossing count the sweep works on is blind to that: the distance costs
    # nothing until it happens to bring two lines together. So each row is swept
    # once more and a linked pair is closed up. Whoever calls this runs its own
    # search over the result, which keeps the change only where it does no harm.
    for kind in ROW_KINDS:
        row = rows[kind]
        for _ in range(4):
            changed = False
            for eid in list(row):
                for other in neighbours.get(eid, []):
                    if other not in row or other == eid:
                        continue
                    before = row.index(eid) < row.index(other)
                    if abs(row.index(eid) - row.index(other)) == 1:
                        continue
                    row.remove(other)
                    at = row.index(eid) + (1 if before else 0)
                    row.insert(at, other)
                    changed = True
            if not changed:
                break
    return rows


# ---------------------------------------------------- packing many boundaries
def shelf_layout(order, sizes, target_width, gap_x, gap_y):
    """Place the boxes in rows, left to right and top to bottom, each row as
    tall as its tallest box: the arrangement a drawing of many actor
    boundaries wants, where a row of small actors wastes far less page than a
    grid whose every cell is as big as the biggest one."""
    places, x, y, row_h, widest = {}, 0.0, 0.0, 0.0, 0.0
    for aid in order:
        w, h = sizes[aid]
        if x > 0.0 and x + w > target_width:
            x, y, row_h = 0.0, y + row_h + gap_y, 0.0
        places[aid] = (x, y)
        x += w + gap_x
        widest = max(widest, x - gap_x)
        row_h = max(row_h, h)
    return places, (widest, y + row_h)


def pack_order_search(ids, deps, sizes, gap_x=90.0, gap_y=110.0, target_width=None):
    """The order of the boxes on the page that leaves the fewest dependency
    lines crossing, over a few page widths.

    Boxes that carry many dependencies want to sit near the boxes they talk to,
    and the arrangement is a shelf, so the order is what decides the page. Swaps
    are tried and kept while they lower the count of crossing lines, measured
    between the box centres the drawing puts the lines through; the page width
    is a second, coarser choice, settled by trying a few and keeping the one
    that crosses least."""
    if len(ids) < 3:
        return list(ids), shelf_layout(list(ids), sizes, 4000.0, gap_x, gap_y)
    total = sum(sizes[i][0] * sizes[i][1] for i in ids)
    area_side = math.sqrt(total)
    candidates = ([target_width] if target_width else
                  [area_side * factor for factor in (0.85, 1.1, 1.35, 1.6, 1.9)])

    def layout(order, width):
        places, extent = shelf_layout(order, sizes, width, gap_x, gap_y)
        return {aid: (x + sizes[aid][0] / 2.0, y + sizes[aid][1] / 2.0)
                for aid, (x, y) in places.items()}, extent

    def crossings(centers):
        """Crossing lines first - a crossing is what a reader trips over - and
        the total length of the lines as the tie-break, so the actors that talk
        to each other most end up next to each other rather than merely in an
        order that happens not to cross."""
        segs = []
        length = 0.0
        for d in deps:
            a, b = centers[d["from"]], centers[d["to"]]
            length += math.hypot(b[0] - a[0], b[1] - a[1])
            segs.append((a, b, min(a[0], b[0]), min(a[1], b[1]),
                         max(a[0], b[0]), max(a[1], b[1])))
        count = 0
        for i in range(len(segs)):
            a, b, ax0, ay0, ax1, ay1 = segs[i]
            for j in range(i + 1, len(segs)):
                c, e, bx0, by0, bx1, by1 = segs[j]
                if ax1 < bx0 or bx1 < ax0 or ay1 < by0 or by1 < ay0:
                    continue
                if segments_cross(a, b, c, e):
                    count += 1
        return count + round(length / 20000.0, 1)

    best = None
    for width in candidates:
        order = sorted(ids)
        centers, extent = layout(order, width)
        best_count = crossings(centers)
        improved = True
        while improved:
            improved = False
            for i in range(len(order)):
                for j in range(i + 1, len(order)):
                    order[i], order[j] = order[j], order[i]
                    centers, extent = layout(order, width)
                    count = crossings(centers)
                    if count < best_count:
                        best_count = count
                        improved = True
                    else:
                        order[i], order[j] = order[j], order[i]
        # a page that is roughly landscape reads better than a long strip, so the
        # aspect ratio settles the tie the crossing count leaves
        aspect = extent[0] / max(extent[1], 1.0)
        score = (best_count, round(abs(aspect - 1.5) * 40.0),
                 round(extent[0] * extent[1] / 1e6))
        if best is None or score < best[0]:
            best = (score, list(order), width)
    places, extent = shelf_layout(best[1], sizes, best[2], gap_x, gap_y)
    return best[1], (places, extent)


def dependum_anchors_outside(deps, boxes, size_of, lane_gap=LANE_GAP, margin=44.0):
    """Where each dependum wants to sit when the actors are boundaries rather
    than circles: just outside the depender's boundary, on the line towards the
    actor it is owed by.

    In an SD view the actors are circles and the middle of the line is free, so
    that is where a dependum goes. Here the actors are boxes that fill the page,
    and the middle of a line between two of them is as likely as not to be
    inside a third: a dependum parked near its own boundary sits in the gap the
    page leaves between the boxes, which is the only room there is. Several
    dependencies between the same pair keep the lanes they would have had on the
    line, so they do not land on top of each other."""
    groups = {}
    for d in deps:
        key = tuple(sorted((d["from"], d["to"])))
        groups.setdefault(key, []).append(d)
    anchors, sizes = {}, {}
    for key in sorted(groups):
        group = sorted(groups[key], key=lambda d: d["id"])
        measured = [size_of(d) for d in group]
        lane = max(h for _, h in measured) + lane_gap
        for i, d in enumerate(group):
            w, h = measured[i]
            source = boxes[d["from"]]
            target = boxes[d["to"]]
            cx, cy = box_center(source)
            tx, ty = box_center(target)
            dx, dy = tx - cx, ty - cy
            length = math.hypot(dx, dy) or 1.0
            ux, uy = dx / length, dy / length
            half_w = (source[2] - source[0]) / 2.0
            half_h = (source[3] - source[1]) / 2.0
            scale = min(half_w / abs(ux) if ux else float("inf"),
                        half_h / abs(uy) if uy else float("inf"))
            reach = abs(w / 2.0 * ux) + abs(h / 2.0 * uy)
            offset = (i - (len(group) - 1) / 2.0) * lane
            px, py = -uy, ux
            sizes[d["id"]] = (w, h)
            anchors[d["id"]] = (cx + ux * (scale + reach + margin) + px * offset,
                                cy + uy * (scale + reach + margin) + py * offset)
    return anchors, sizes
