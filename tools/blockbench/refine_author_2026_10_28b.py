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

def hop_roll_dodge(data):
    a = ak.Anim.load(data, "hop_roll_dodge")
    no_twist(a)
    # The author's crouch (0-0.25 s) and landing (0.9-1.33 s) already stand on the floor; the take-off
    # is at 0.25 and the landing at 0.9, so the move keeps the timing of the hop the game knows.
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


def finisher_stab(data):
    a = ak.Anim.load(data, "finisher_stab")
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
