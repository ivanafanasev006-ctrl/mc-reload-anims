"""Refined versions of the author's animations of 2026-10-28, second file (CLAUDE.md: the author's
work is a framework; keep the idea and the key poses, perfect the execution).

    python3 tools/blockbench/refine_author_2026_10_28b.py

reads  tools/blockbench/animations/author_2026-10-28b.bbmodel  (never changed)
writes tools/blockbench/refined/author_2026-10-28b.bbmodel     (same model, refined animations)

The set: hop_roll_dodge (the new vault over a mob at point blank), cross_guard_flat (the new block
with two swords), dash_strike (a lunge into the belly off a sprint) and finisher_stab (the finishing
stab). The grey second figure (bones *2) is the author's opponent for reference: it stays as it was
and never reaches the game. Every change is a line below with its reason; docs/moves.md says what
each move is for and which key drives which part of the mechanic.
"""
import copy
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import animkit as ak  # noqa: E402
import kinetrix_rig as kr  # noqa: E402
from refine_author_2026_10_27 import copy_pose, no_twist  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "animations", "author_2026-10-28b.bbmodel")
DST = os.path.join(HERE, "refined", "author_2026-10-28b.bbmodel")


def keyed_times(a, t0, t1, bones=None):
    """Times in [t0, t1] at which any (or one of these) bones has a key."""
    out = set()
    for bone, chans in a.keys.items():
        if bones and bone not in bones:
            continue
        for lst in chans.values():
            out |= {round(kf[0], 4) for kf in lst if t0 - 1e-4 <= kf[0] <= t1 + 1e-4}
    return sorted(out)


# --------------------------------------------------------------------------------------------
# Vault over a mob at point blank
# --------------------------------------------------------------------------------------------

#: Author, 2026-10-29 (second message): the vault must be quick, "as in the 28b file, 1 s or
#: 1.25", and flowing like parkour: run, press, over, run on without a stop. The PC session had
#: stretched the push off at the end 1.5 times (landing at 1.0 s, 1.43 s in all) for "the push is
#: there but hardly seen"; that stretch is taken back (the push is made stronger instead, below),
#: and the whole move is played 1.25 times faster than the author's 1.33 s: take-off at 0.2 s, the
#: push at 0.63, landing at 0.72, 1.07 s in all (Move.HOP_TAKEOFF, HOP_SHOVE, HOP_LAND).
FAST = 0.8


def hop_time(t):
    """Author's time of the move -> new time."""
    return t * FAST


def retime_extra(a, f):
    """The opponent's keys (kept as the author made them) moved in time with the player's."""
    for an in a.extra.values():
        for kf in an.get("keyframes", []):
            kf["time"] = round(f(float(kf["time"])), 4)


def extra_key(a, bone, ch, t, v):
    """Adds or replaces a key of one of the opponent's bones (the grey figure, bones *2)."""
    an = next(x for x in a.extra.values() if x.get("name") == bone)
    an["keyframes"] = [kf for kf in an["keyframes"] if not (kf["channel"] == ch and abs(float(kf["time"]) - t) < 0.02)]
    an["keyframes"].append({"channel": ch, "data_points": [{"x": v[0], "y": v[1], "z": v[2]}], "uuid": ak._uid(),
                            "time": round(t, 4), "color": -1, "interpolation": "linear"})


def hop_roll_dodge(data):
    a = ak.Anim.load(data, "hop_roll_dodge")
    no_twist(a)
    # Author, 2026-10-29: the hand takes the mob by the head and presses its head and body down
    # under the player, then the roll over its back as it was. The left hand went to the mob's
    # back at shoulder height; it now reaches up onto the head (0.17-0.25 s, elbow bent as it takes
    # the weight) and presses down through the take-off and the start of the roll, the arm
    # straightening as it pushes (0.25-0.5 s). In Blockbench the grey mob stoops earlier and
    # deeper under the hand, its head pushed down (in the game it stoops by Hop.lean).
    for t, arm, fore in ((0.17, (100, -5, -24), (16, -1, 5)), (0.21, (112, -4, -24), (24, -1, 5)),
                         (0.25, (110, -3, -24), (20, -1, 5)), (0.29, (101, -3, -24), (12, -1, 4)),
                         (0.33, (90, -3, -24), (6, -1, 4)), (0.38, (78, -3, -24), (4, -1, 4)),
                         (0.42, (66, -3, -24), (5, -1, 4)), (0.46, (55, -3, -24), (8, -1, 4)),
                         (0.5, (46, -3, -24), (14, -1, 4))):
        a.key("left_arm", "rotation", t, arm)
        a.key("left_forearm", "rotation", t, fore)
    extra_key(a, "chest2", "rotation", 0.25, (-28, 0, 0))
    extra_key(a, "chest2", "rotation", 0.33, (-58, 0, 0))
    extra_key(a, "chest2", "rotation", 0.47, (-76, 0, 0))
    extra_key(a, "head2", "rotation", 0.25, (-10, 0, 0))
    extra_key(a, "head2", "rotation", 0.33, (-34, 0, 0))
    extra_key(a, "head2", "rotation", 0.47, (-30, 0, 0))
    # The push off, stronger: the left leg kicks further back and nearly straight (it shoves), the
    # left hand pushes back with it; the right leg reaches down for the landing and the right arm
    # goes out for balance. The strongest moment (0.79 s) is held a little (to 0.84), then the leg
    # comes back under the body for the landing at 0.9 s. Then the whole move 1.25 times faster
    # (FAST): the hop is a quick vault in a run, not a stunt to watch.
    peak = 0.79
    kick = {"left_leg": (-70, 1, -12), "left_shin": (-22, 0, 0), "left_arm": (-40, 20, -14),
            "left_forearm": (6, 0, 5), "right_arm": (60, -46, 46), "right_leg": (30, -12, 30),
            "right_shin": (-30, 0, 0)}
    for bone, v in kick.items():
        a.key(bone, "rotation", peak, v)
        a.key(bone, "rotation", 0.84, np.asarray(v) * 0.92)
    a.key("left_leg", "rotation", 0.875, (-36, 1, -10))
    a.key("left_shin", "rotation", 0.875, (-48, 0, 0))
    a.retime(hop_time)
    retime_extra(a, hop_time)
    a.length = hop_time(a.length)
    # The author's crouch (now 0-0.2 s) and landing (now 0.72-1.07 s) already stand on the floor; the
    # take-off is at 0.2 and the landing at 0.72 (Move.HOP_TAKEOFF, HOP_LAND in the mod).
    # In the air the model rose up to 1.1 blocks inside the player's own hitbox: the hitbox stayed on
    # the ground under the mob being vaulted (mobs push it, blows aimed at the player hit the floor).
    # The jump now lifts the player itself, as high as the mob needs (a pig less than a zombie,
    # docs/moves.md), and in the air the lowest point of the rolling body rides on the bottom of the
    # hitbox: wherever the jump carries the player, the roll clears what it carried them over. The
    # roll itself (the author's turn over the mob's back, arms and legs) is unchanged.
    a.ground(data, 0.0, a.length)
    a.loop = "once"
    return a


# --------------------------------------------------------------------------------------------
# Block with two swords, flat
# --------------------------------------------------------------------------------------------

FLAT_LOOP = 0.25


def cross_guard_flat(data):
    a = ak.Anim.load(data, "cross_guard_flat")
    no_twist(a)
    # The X itself is the author's and is right: both blades mirror each other, and the left one
    # crosses 1 px in front of the right, exactly a blade's thickness, so they do not cut into each
    # other. The bent knees put the feet 0.6-1.3 px under the floor: the stance now stands on it,
    # and the front foot, which then hung 0.6 px over the floor, folds its knee a little more to
    # stand on it too.
    a.ground(data, 0.0, a.length)
    for t in keyed_times(a, 0.04, a.length, bones=("right_shin",)):
        ak.fold_to(a, data, t, "right_shin", None, 0.0, -80, 0)
    a.ground(data, 0.0, a.length)
    # Held as long as the block is: it breathes from the marker at 0.25 s and the last frame is that
    # frame exactly, so the loop closes without a jump.
    copy_pose(a, FLAT_LOOP, a.length)
    a.markers = [FLAT_LOOP]
    a.loop = "hold"
    return a


# --------------------------------------------------------------------------------------------
# Dash strike
# --------------------------------------------------------------------------------------------

LUNGE = (0.42, 0.98)


def dash_strike(data):
    a = ak.Anim.load(data, "dash_strike")
    no_twist(a)
    # Running in (0-0.4 s) the body floated up to 1.8 px over the floor for the whole run-up, only
    # touching it once: the foot under the body now carries it at every moment.
    a.ground(data, 0.0, LUNGE[0])
    # The lunge (0.45-0.9 s): the back foot went 2.4 px into the floor and the front one 0.6 px. The
    # front foot now stands on it, and the back leg is lifted a few degrees until its toes rest on
    # the floor behind (the author's long lunge, the back knee near the floor, is kept).
    for t in keyed_times(a, *LUNGE):
        low = ak.bone_low(a.model(data), a.name, t, "right_shin")
        y = a.value("body", "position", t)
        a.key("body", "position", t, (y[0], y[1] - low, y[2]))
        x = a.value("left_leg", "rotation", t)[0]
        ak.fold_to(a, data, t, "left_leg", None, 0.0, x - 30, x + 30, measure="left_shin")
    a.ground(data, LUNGE[0], LUNGE[1], mode="above")
    # Getting up from the lunge (0.95-1.375 s) sank the back foot up to 1.7 px: on the floor too.
    a.ground(data, LUNGE[1], a.length)
    a.loop = "once"
    return a


# --------------------------------------------------------------------------------------------
# Finisher
# --------------------------------------------------------------------------------------------

def shoulder_grip(data, a, t):
    """Where the free (left) hand takes hold of the opponent at time t: the top of the shoulder in
    front of it (its right one, on the player's left), a little in front of the shoulder's face."""
    m = a.model(data)
    anim = next(x for x in m.animations if kr.anim_name(x) == a.name)
    W = kr.bb_pose(m, m.channels(anim), t)
    g = m.groups[m.by_name["right_arm2"]]
    o = g["origin"]
    # The opponent's arm cube spans x 24..28, y 12..24, z -2..2 at rest; it faces the player (+z
    # after its half turn), so its front is local -z. The fist closes over the top front corner.
    return kr.apply(W["right_arm2"], np.array([o[0] + 1.0, 23.0, -2.8]) - o)


def deepen(a, bone, ch, delta, t0, t1, t2, t3):
    """Adds `delta` to a channel, fully from t1 to t2, eased in from t0 and out by t3 (keys at
    those four times and every key already between them)."""
    def w(t):
        if t <= t0 or t >= t3:
            return 0.0
        if t < t1:
            f = (t - t0) / (t1 - t0)
        elif t > t2:
            f = (t3 - t) / (t3 - t2)
        else:
            return 1.0
        return f * f * (3 - 2 * f)
    times = sorted({t0, t1, t2, t3} | {round(kf[0], 4) for kf in a.channel(bone, ch) if t0 < kf[0] < t3})
    vals = [(t, a.value(bone, ch, t)) for t in times]
    for t, v in vals:
        a.key(bone, ch, t, v + np.asarray(delta, float) * w(t))


#: The finisher's thrust (author, 2026-10-29: "the arm a little further, the body more forward,
#: more real"): eased in as the blade goes in (0.29-0.38 s), held while it is in, eased out as it
#: comes out (0.83-0.96 s). The stance under it deepens from the moment it is set (0.21 s).
STAB_IN, STAB_OUT = (0.29, 0.38), (0.83, 0.96)
STANCE_IN, STANCE_OUT = (0.21, 0.38), (0.88, 1.04)
FINISH_REACH = (
    # the arm goes out nearly straight, a little higher, at the belly
    ("right_arm", "rotation", (14, -6, 0), STAB_IN, STAB_OUT),
    ("right_forearm", "rotation", (-11, 0, 0), STAB_IN, STAB_OUT),
    # the right shoulder comes forward behind the blade, the chest leans into it, the head with it
    ("torso", "rotation", (0, 11, 0), STAB_IN, STAB_OUT),
    ("chest", "rotation", (-13, 0, 0), STAB_IN, STAB_OUT),
    ("head", "rotation", (9, -6, 0), STAB_IN, STAB_OUT),
    # the weight goes onto the front foot: the body forward, the front knee bends, the back leg
    # reaches further back
    ("body", "position", (0, 0, -2.2), STANCE_IN, STANCE_OUT),
    ("left_leg", "rotation", (16, 0, 0), STANCE_IN, STANCE_OUT),
    ("left_shin", "rotation", (-24, 0, 0), STANCE_IN, STANCE_OUT),
    ("right_leg", "rotation", (-12, 0, 0), STANCE_IN, STANCE_OUT),
)

#: Author, 2026-10-29, second message: "more lunge forward, and the arm reaching further, straight
#: forward". On top of FINISH_REACH: the front (left) foot steps further out and its knee bends
#: deep, the back leg stretches back, the body goes 3 px more forward (0.37 block in all, as far as
#: the stance lets it without the back foot leaving the floor); the sword arm points straight ahead
#: (it came in from the side, 22 degrees across the body) and level with the shoulder, the elbow
#: straight, the shoulder behind it, and the blade turns in the fist to go on along the arm (it
#: stood up from the fist, so the arm reaching out lifted the point to the mob's chest).
FINISH_LUNGE = (
    ("body", "position", (0, 0, -3.0), STANCE_IN, STANCE_OUT),
    ("left_leg", "rotation", (16, 0, 0), STANCE_IN, STANCE_OUT),
    ("left_shin", "rotation", (-34, 0, 0), STANCE_IN, STANCE_OUT),
    ("right_leg", "rotation", (-14, 0, 0), STANCE_IN, STANCE_OUT),
    ("right_arm", "rotation", (-8, -14, -6), STAB_IN, STAB_OUT),
    ("right_forearm", "rotation", (-3, 0, 0), STAB_IN, STAB_OUT),
    ("right_item_sword", "rotation", (50, 0, 0), STAB_IN, STAB_OUT),
    ("torso", "rotation", (0, 6, 0), STAB_IN, STAB_OUT),
    ("chest", "rotation", (-6, 0, 0), STAB_IN, STAB_OUT),
)


def finisher_stab(data):
    a = ak.Anim.load(data, "finisher_stab")
    no_twist(a)
    for bone, ch, d, (t0, t1), (t2, t3) in FINISH_REACH + FINISH_LUNGE:
        deepen(a, bone, ch, d, t0, t1, t2, t3)
    no_twist(a)
    # Both feet were up to 0.65 px in the floor through the whole stab: the stance stands on it,
    # and the back foot, which then hung 0.45 px over it, folds its knee a little to stand too.
    a.ground(data, 0.0, a.length)
    for t in keyed_times(a, 0.04, a.length - 0.04, bones=("left_shin",)):
        ak.fold_to(a, data, t, "left_shin", None, 0.0, -80, 0)
    a.ground(data, 0.0, a.length)
    # The free hand hung at the side the whole time, so the stab had nothing to push against. Now
    # it takes the opponent by the shoulder as the sword is drawn back (0.25 s), holds them on the
    # blade while it is in (it follows their shoulder as they fold over it), and lets go as the
    # blade comes out (0.96 s), before they fall.
    for t in (0.25, 0.375, 0.46, 0.83):
        ak.reach(a, data, t, "left", shoulder_grip(data, a, t))
    grip = a.value("left_arm", "rotation", 0.83)
    a.key("left_arm", "rotation", 0.96, grip * 0.55)
    a.key("left_forearm", "rotation", 0.96, a.value("left_forearm", "rotation", 0.83) * 0.7)
    a.key("left_arm", "rotation", 0.125, a.value("left_arm", "rotation", 0.25) * 0.45)
    a.key("left_forearm", "rotation", 0.125, a.value("left_forearm", "rotation", 0.25) * 0.6)
    for bone in ("left_arm", "left_forearm"):
        a.key(bone, "rotation", 0.0, (0, 0, 0))
        a.key(bone, "rotation", a.length, (0, 0, 0))
    a.ground(data, 0.0, a.length)
    a.loop = "once"
    return a


# --------------------------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------------------------

def build(names=None):
    src = ak.load_model(SRC)
    out = copy.deepcopy(src)
    made = [hop_roll_dodge(src), cross_guard_flat(src), dash_strike(src), finisher_stab(src)]
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
