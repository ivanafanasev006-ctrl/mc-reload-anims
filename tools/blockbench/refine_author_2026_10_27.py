"""Refined versions of the author's animations of 2026-10-27 (CLAUDE.md: the author's work is a
framework; keep the idea and the key poses, perfect the execution).

    python3 tools/blockbench/refine_author_2026_10_27.py

reads  tools/blockbench/animations/author_2026-10-27.bbmodel  (never changed)
writes tools/blockbench/refined/author_2026-10-27.bbmodel     (same model, refined animations)

Every change is a line below with its reason; docs/moves.md says what each move is for and which
key drives which part of the mechanic.
"""
import copy
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import animkit as ak  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "animations", "author_2026-10-27.bbmodel")
DST = os.path.join(HERE, "refined", "author_2026-10-27.bbmodel")

LOWER = ("right_forearm", "left_forearm", "right_shin", "left_shin")


def no_twist(a):
    """Elbows and knees only fold: a turn of the forearm or shin about its own length never
    reaches the game, so it is taken out here too (the bone keeps pointing where it pointed, and
    the pose in Blockbench matches the game)."""
    for bone in LOWER:
        for kf in a.keys.get(bone, {}).get("rotation", []):
            kf[1] = ak.hinge_only(kf[1])


def drop_opponent_noise(a):
    """The opponent is a reference only; nothing to change in its keys."""
    return a


def at_rest(a, t, bones=None, body_turn=(0.0, 0.0, 0.0)):
    """Keys every animated bone at rest at time t (the move ends exactly in the vanilla stance)."""
    for bone, chans in a.keys.items():
        if bones and bone not in bones:
            continue
        for ch in chans:
            v = list(body_turn) if (bone == "body" and ch == "rotation") else [0.0, 0.0, 0.0]
            a.key(bone, ch, t, v)


def copy_pose(a, src_t, dst_t, bones=None):
    """Keys at dst_t the exact pose of src_t (a seamless loop, a held stance without drift)."""
    for bone, chans in a.keys.items():
        if bones and bone not in bones:
            continue
        for ch in chans:
            if chans[ch]:
                a.key(bone, ch, dst_t, a.value(bone, ch, src_t))


# --------------------------------------------------------------------------------------------
# Dodges
# --------------------------------------------------------------------------------------------

def dodge_left(data):
    a = ak.Anim.load(data, "dodge_left")
    no_twist(a)
    # The crouch and the push sank the feet up to 1.2 px into the floor: the body's height is now
    # set every 0.05 s so the lowest foot stands exactly on it.
    a.ground(data, 0.0, 0.5)
    a.loop = "once"
    return a


def dodge_right(left):
    # The same dodge to the other side, mirrored from the refined dodge_left (left and right swap,
    # sideways turns and moves flip), so both sides are exactly alike.
    return left.mirrored("dodge_right")


def roll(data, name):
    a = ak.Anim.load(data, name)
    no_twist(a)
    # A roll keeps touching the floor: the author's heights let the body sink 3 px into it at
    # the start of the turn and float 2.6 px above it in the middle. Every 0.05 s the lowest point
    # (feet, then back or shoulders, then feet) now rests exactly on the floor.
    a.ground(data, 0.0, a.length)
    # It ends in the plain stance, so it plays once and hands the body back (hold kept the last
    # frame forever).
    a.loop = "once"
    return a


# --------------------------------------------------------------------------------------------
# Slide
# --------------------------------------------------------------------------------------------

def slide(data):
    a = ak.Anim.load(data, "slide")
    no_twist(a)
    author = copy.deepcopy(a)

    # Getting up took 0.35 s straight from lying back to standing, with nothing in between: one
    # more key (1.38 s) where the trunk comes forward over the feet and the folded leg pushes,
    # and the rise is 0.1 s longer.
    a.retime(lambda t: t if t <= 1.2 else 1.2 + (t - 1.2) * (0.45 / 0.35))
    a.length = 1.7
    rise = {
        "body": dict(rotation=(12, 2, 1)),
        "chest": dict(rotation=(-16, 0, 0)),
        "head": dict(rotation=(-8, 0, 0)),
        "left_leg": dict(rotation=(48, 0, -10)),
        "left_shin": dict(rotation=(-72, 0, 0)),
        "right_leg": dict(rotation=(34, 0, 2)),
        "right_shin": dict(rotation=(-28, 0, 0)),
        "right_arm": dict(rotation=(-6, 0, 12)),
        "right_forearm": dict(rotation=(12, 0, 0)),
        "left_arm": dict(rotation=(38, -6, -38)),
        "left_forearm": dict(rotation=(22, 0, 0)),
    }
    for bone, chans in rise.items():
        for ch, v in chans.items():
            a.key(bone, ch, 1.38, v)

    # (The trailing right hand stays 3 px above the floor: with the trunk leaned back as the author
    # set it, the shoulder is 13 px up and the arm 10 px long; the pose is kept.)

    # Lying on the floor: the shin and heel were 2-3 px inside it. The body now rests on it, and
    # the author's small bumps of the slide (the ground rushing under it) ride on top, +-0.3 px.
    def bumps(t):
        if not 0.3 <= t <= 1.2:
            return 0.0
        y = author.value("body", "position", t)[1]
        trend = -9.0 + (t - 0.3) / 0.9 * (-8.6 + 9.0)
        return float(np.clip(y - trend, -0.35, 0.35)) + 0.35  # bumps lift off the floor, never into it
    a.ground(data, 0.0, a.length, extra_y=bumps)
    at_rest(a, 1.65)
    copy_pose(a, 1.65, 1.7)
    return a


# --------------------------------------------------------------------------------------------
# Blocks
# --------------------------------------------------------------------------------------------

BREATH_FROM = 0.4


def blade_point(m, name, t, item, frac, below=1.0, behind=0.4):
    import kinetrix_rig as k
    anim = next(x for x in m.animations if k.anim_name(x) == name)
    W = k.bb_pose(m, m.channels(anim), t)
    g = m.groups[m.by_name[item]]
    o = g["origin"]
    best = None
    for e in m.data["elements"]:
        if e["uuid"] not in g["children"]:
            continue
        lo, hi = np.array(e["from"], float), np.array(e["to"], float)
        for c in ((x, y, z) for x in (0, 1) for y in (0, 1) for z in (0, 1)):
            p = lo + (hi - lo) * np.array(c)
            d = np.linalg.norm(p - o)
            if best is None or d > best[0]:
                best = (d, p)
    grip = k.apply(W[item], np.array(o) - o)
    tip = k.apply(W[item], best[1] - o)
    p = grip + frac * (tip - grip)
    return p + np.array([0.0, -below, behind])


def guard(data, name, loop_from=BREATH_FROM):
    a = ak.Anim.load(data, name)
    no_twist(a)
    # Bent knees put the feet 0.8 px under the floor: the stance now stands on it.
    a.ground(data, 0.0, a.length)
    # The guard is held as long as the block is: after the raise it now breathes in a loop that
    # closes on itself (the last key is the pose of the first one), instead of freezing at 1.5 s.
    copy_pose(a, loop_from, a.length)
    a.markers = [loop_from]
    a.loop = "hold"
    return a


def cross_guard(data):
    a = guard(data, "cross_guard")
    # The free hand was meant to brace the flat of the blade but stayed 2.3 px under it: the left
    # fist now rests under the blade a little past its middle, at every key of the held guard.
    for t in (0.3, 0.4, 0.8, 1.15, 1.5):
        m = a.model(data)
        target = blade_point(m, a.name, t, "right_item_sword", 0.55)
        ak.reach(a, data, t, "left", target)
    copy_pose(a, BREATH_FROM, a.length)
    a.ground(data, 0.0, a.length)
    return a


def cross_guard_double(data):
    a = ak.Anim.load(data, "cross_guard_double")
    no_twist(a)
    # The right blade slowly turned flat and slid 4 px over the whole held guard, so the X came
    # apart: both swords now stay as the author set them at 0.4-0.6 s, where the cross is
    # symmetric.
    rs = a.value("right_item_sword", "rotation", 0.6)
    a.drop("right_item_sword")
    for t, v in ((0.0, (0, 0, 0)), (0.1, (10, -12, -18)), (0.2, (28, -32, -48)), (0.3, (37, -42, -63)),
                 (0.4, tuple(rs)), (a.length, tuple(rs))):
        a.key("right_item_sword", "rotation", t, v)
    a.key("right_item_sword", "position", 0.0, (0, 0, 0))
    a.key("right_item_sword", "position", a.length, (0, 0, 0))
    a.ground(data, 0.0, a.length)
    copy_pose(a, BREATH_FROM, a.length)
    a.markers = [BREATH_FROM]
    a.loop = "hold"
    return a


# --------------------------------------------------------------------------------------------
# Hop over a low attack
# --------------------------------------------------------------------------------------------

def hop(data):
    a = ak.Anim.load(data, "hop")
    no_twist(a)
    # Every key was linear, so each limb moved like a robot from pose to pose: limbs now ease in
    # and out (smooth). The body's own spin stays linear while it is in the air (a body turns at
    # a steady speed there), from the take-off at 0.75 s to the landing at 1.75 s.
    a.interp("catmullrom")
    a.interp("linear", bones=["body"], channels=("rotation",), t0=0.75, t1=1.5)
    # The landing straightened up from the deepest crouch in 0.25 s: 0.1 s more to absorb it.
    a.retime(lambda t: t if t <= 2.0 else 2.0 + (t - 2.0) * 1.4)
    a.length = 2.35
    # It ended 2.4 deg tilted (roll 357.6), 1 px sunk, with arms and legs a few degrees off: the
    # last frame is now exactly the plain stance (roll 360 = upright), so it hands over cleanly.
    at_rest(a, 2.35, body_turn=(0.0, 0.0, 360.0))
    # Feet on the floor before take-off and after landing (the crouch had them 1.8 px under it);
    # in the air nothing may go under the floor either.
    a.ground(data, 0.0, 0.75)
    a.ground(data, 0.75, 1.8, mode="above")
    a.ground(data, 1.8, 2.35)
    a.loop = "once"
    return a


# --------------------------------------------------------------------------------------------
# Bow
# --------------------------------------------------------------------------------------------

def use_bow(a):
    """The bow is what these moves hold: key the bow group (the converter and the self-test then
    hand a bow), and drop the zero keys on the sword that made it look like a sword move."""
    a.drop("right_item_sword")
    a.key("right_item_bow", "rotation", 0.0, (0, 0, 0))
    a.key("right_item_bow", "rotation", a.length, (0, 0, 0))


def anchor(m, name, t):
    """Where the drawing hand anchors at full draw: under the jaw, a little to its left."""
    return ak.point(m, name, t, "head", (-1.5, 24.5, -4.6))


def bow_hand(m, name, t):
    return ak.point(m, name, t, "right_forearm", ak.HAND["right"][2])


def draw_to(a, data, t, where):
    """Puts the drawing (left) hand on the arrow line: where = 0 at the bow hand, 1 at the anchor,
    more than 1 behind it (the follow-through of a release)."""
    m = a.model(data)
    b, an = bow_hand(m, a.name, t), anchor(m, a.name, t)
    ak.reach(a, data, t, "left", b + where * (an - b))


def bow_barrage(data):
    a = ak.Anim.load(data, "bow_barrage")
    no_twist(a)
    use_bow(a)
    # The body slid 14 px (0.9 block) back inside the model, so at the end the figure stood away
    # from its own hitbox: the slide back is the mechanic's push now (docs/moves.md), the model
    # spins and kneels in place.
    for kf in a.keys["body"]["position"]:
        kf[1][2] = 0.0
    # Kneeling: the knee went 4.5 px into the floor. Now the knee rests on it, the front foot's sole
    # (0.8 px up) is on it too, and the back shin folds until the toes touch it.
    a.ground(data, 0.0, a.length)  # the author's back knee is the lowest point: on the floor
    for t in (0.5, 0.71):  # kneeling (at 0.38 the body is still coming down)
        # front foot: its sole 0.8 px above the floor -> on it (the front thigh tips down a little)
        x = a.value("right_leg", "rotation", t)[0]
        ak.fold_to(a, data, t, "right_leg", None, 0.0, x - 25, x + 25, measure="right_shin")
        # back shin: folded on until the toes touch the floor behind the knee
        ak.fold_to(a, data, t, "left_shin", None, 0.0, -150, 0)
    a.ground(data, 0.0, a.length)
    # The volley: the drawing hand swung sideways (the forearm folded about z) instead of pulling
    # along the arrow. Now it pulls along the arrow to the jaw, lets go (a short follow-through
    # behind the jaw) and goes back for the next arrow, twice per 0.5 s as the author timed it:
    # full draw at 0.5, 0.75, 1.0; release just after; back at 0.625, 0.875. A short, fast draw:
    # half way to the bow is as far as the left arm reaches across the body (10.4 px).
    for t, where in ((0.375, 0.5), (0.5, 1.0), (0.54, 1.25), (0.625, 0.5), (0.75, 1.0), (0.79, 1.25), (0.875, 0.5)):
        draw_to(a, data, t, where)
    # The volley repeats while the button is held: the last frame is the first full draw (0.5)
    # and a marker there loops 0.5-1.0.
    copy_pose(a, 0.5, 1.0)
    a.markers = [0.5]
    a.loop = "hold"
    a.ground(data, 0.0, a.length)
    return a


def bow_walk_around(data):
    a = ak.Anim.load(data, "bow_walk_around")
    no_twist(a)
    use_bow(a)
    # The spins turned the whole body on the spot with both feet glued to the floor: now the feet
    # step through each turn (one lifts while the other carries the weight), as one turns on foot.
    base_r = a.value("right_leg", "rotation", 0.25)
    base_l = a.value("left_leg", "rotation", 0.25)
    shin = a.value("right_shin", "rotation", 0.25)
    for t0 in (0.0, 1.0):
        for i, t in enumerate((0.12, 0.25, 0.37, 0.5, 0.62)):
            lift_right = i % 2 == 0
            if t0 + t < 0.25:  # the author's first turn of the legs into the stance stays
                continue
            a.key("right_leg", "rotation", t0 + t, base_r + (np.array([22, 0, 0]) if lift_right else 0))
            a.key("right_shin", "rotation", t0 + t, shin + (np.array([-30, 0, 0]) if lift_right else 0))
            a.key("left_leg", "rotation", t0 + t, base_l + (np.array([22, 0, 0]) if not lift_right else 0))
            a.key("left_shin", "rotation", t0 + t, shin + (np.array([-30, 0, 0]) if not lift_right else 0))
        a.key("right_leg", "rotation", t0 + 0.75, base_r)
        a.key("left_leg", "rotation", t0 + 0.75, base_l)
        a.key("right_shin", "rotation", t0 + 0.75, shin)
        a.key("left_shin", "rotation", t0 + 0.75, shin)
    a.ground(data, 0.0, a.length)
    # The full draw stopped 6-7 px short of the face: at 0.875 and 1.875 the hand is now at the jaw,
    # at 0.75 / 1.75 two thirds of the way there, and on the release (1.0, 2.0) it follows through
    # past the jaw instead of going back toward the bow.
    for t, where in ((0.75, 0.7), (0.875, 1.0), (1.0, 1.25), (1.75, 0.7), (1.875, 1.0), (2.0, 1.25)):
        draw_to(a, data, t, where)
    a.ground(data, 0.0, a.length)
    return a


# --------------------------------------------------------------------------------------------
# Crouch and leap ("celebrate": to be confirmed by the author)
# --------------------------------------------------------------------------------------------

def celebrate(data):
    a = ak.Anim.load(data, "celebrate")
    no_twist(a)
    # The crouch put the feet 2.5 px under the floor: it now stands on it.
    a.ground(data, 0.0, 2.42)
    # The leap went from the deepest crouch to full stretch in 0.08 s (under two game ticks, so in
    # the game it would just appear): 0.1 s more, still explosive.
    a.retime(lambda t: t if t <= 2.42 else 2.42 + (t - 2.42) * (0.18 / 0.08))
    a.length = 2.6
    return a


# --------------------------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------------------------

def build(names=None):
    src = ak.load_model(SRC)
    out = copy.deepcopy(src)
    made = []
    left = dodge_left(src)
    made += [left, dodge_right(left)]
    made += [roll(src, "dodge_back"), roll(src, "dodge_front")]
    made.append(slide(src))
    made += [cross_guard(src), cross_guard_double(src)]
    made += [hop(src), bow_barrage(src), bow_walk_around(src), celebrate(src)]
    if names:
        made = [a for a in made if a.name in names]
    out["animations"] = []
    for a in made:
        a.store(out)
    os.makedirs(os.path.dirname(DST), exist_ok=True)
    ak.save_model(out, DST)
    print("wrote", DST, ":", ", ".join(a.name for a in made))


if __name__ == "__main__":
    build(sys.argv[1:] or None)
