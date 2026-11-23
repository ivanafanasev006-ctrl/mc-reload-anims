"""The author, 2026-11-23 (videos and notes on 0.18.0): the night's moves made again.

Axe
    ax_slam      (1.3 s)  W + Space + G, livelier: down into a crouch, up off both feet (the knees pulled up, seen), the axe
                          over and behind the head at the top, down onto the ground at 0.75 s (AX_SLAM's hit), a deep
                          lunge with the axe in the ground, up again.
    ax_heavy     (2.7 s)  G held, slower and heavier, the whole body in it: each blow wound up with the hips and the
                          working (right) arm, a step into it, the follow-through: down along from the upper right
                          (0.6 s), level from the side (1.45 s), up along from the lower left (2.3 s).
Sword
    sw_charge    (3 s)    the run up in the thrust's hold (author: "the left hand laid over the hilt, the right bent at
                          the elbow along the body, the blade forward as if to prick someone with its point, the body a
                          little forward").
    sw_skewer    (0.8 s)  one run through: the blade and both hands pushed a little forward, as into a body, running on.
    sw_cut       (1.0 s)  the level cut pulling the blade out, a half turn: his back to it (0.3 s), it falls; he turns back.
    sw_kick      (3.8 s)  the finisher (Ctrl + W + Space + R, a mob under a fifth of its health): the leap to its head, all
                          stops (0.6 s), a sharp whole turn in the air (1.2-1.7 s) with the right leg drawn back, the
                          wind-up held in slow motion, the kick home at 2.9 s, the fall, a calm landing.
Crossbow, bow
    xb_hold      (1 s, looped)  the living shield held at the left like a shield (author: so as to see ahead), the
                          crossbow out in the right hand.
    xb_backslide (2.0 s)  from the slide's pose (Ctrl + W + C, then LMB) the upper body goes down onto the back, feet
                          first, the weapon on the chest; up again at the end if nobody fell on him.
    xb_belly     (1.5 s)  one falls onto him (0.3 s): the shot up into its belly (0.45 s), the kip-up.
    bw_shake     (0.9 s)  the bow's ultimate begins: he shakes this way and that, then gone.
    bw_aim, bw_jump, bw_fallback, bw_flip, bw_turn (0.5 s each): his phantoms at each enemy in turn: the archer's draw in
                          front; drawing in a jump from above; from its back, falling back and down, the legs forward as
                          if sitting; upside down, rolled over, shooting down from the sky; out of a whole turn.
    bw_brake     (1.8 s)  the real one back, skidding backwards: both hands on the bow held low, the body a little
                          forward, the knees bent so as not to fall.

    python3 tools/blockbench/refine_author_2026_10_29/round26.py   (writes refined/moves_2026-11-23.bbmodel)
"""
import os

import kit
import round9
import round25
from round25 import STAND, LOW, RUN_A, RUN_B, ITEM


def _make(name, length, keys, hits=(), item=True):
    """round25's, with the whole body keyed in every key (a key without it would hold the last one's turn)."""
    keys = [(k[0], {'body': (0.0, 0.0, 0.0), 'body@p': (0.0, 0.0, 0.0), **k[1]}) + tuple(k[2:]) for k in keys]
    return round25._make(name, length, keys, hits, item)

DST = os.path.join(kit.BB, 'refined', 'moves_2026-11-23.bbmodel')

#: the sword's point forward with the forearm (or the whole arm) level in front (the item turned 120 about its own axis; 90 left the point a third up)
POINT = {'right_item_sword': (0.0, 120.0, 0.0)}


# ------------------------------------------------------------------ the axe

def ax_slam():
    crouch = {**LOW, 'body@p': (0.0, -3.2, 0.0), 'chest': (-18.0, 0.0, 0.0), 'head': (10.0, 0.0, 0.0),
              'right_arm': (24.0, 0.0, 14.0), 'right_forearm': (30.0, 0.0, 0.0), 'left_arm': (30.0, 0.0, -10.0), 'left_forearm': (40.0, 0.0, 0.0)}
    rise = {'body@p': (0.0, 0.0, 0.0), 'chest': (0.0, 0.0, 0.0), 'head': (-6.0, 0.0, 0.0),
            'right_arm': (130.0, 6.0, -4.0), 'right_forearm': (30.0, 0.0, 0.0), 'left_arm': (124.0, -10.0, 8.0), 'left_forearm': (36.0, 0.0, 0.0),
            'right_leg': (36.0, 0.0, 6.0), 'right_shin': (-60.0, 0.0, 0.0), 'left_leg': (20.0, 0.0, -6.0), 'left_shin': (-70.0, 0.0, 0.0)}
    # the top: the knees up to the chest, the axe far back over the head, the back arched
    top = {'body@p': (0.0, 0.6, 0.0), 'chest': (16.0, 0.0, 0.0), 'head': (-14.0, 0.0, 0.0),
           'right_arm': (196.0, 8.0, -6.0), 'right_forearm': (40.0, 0.0, 0.0), 'left_arm': (190.0, -14.0, 10.0), 'left_forearm': (48.0, 0.0, 0.0),
           'right_leg': (84.0, 0.0, 8.0), 'right_shin': (-110.0, 0.0, 0.0), 'left_leg': (70.0, 0.0, -8.0), 'left_shin': (-118.0, 0.0, 0.0)}
    hit = {**LOW, 'body@p': (0.0, -5.0, -1.4), 'chest': (-46.0, 0.0, 0.0), 'head': (32.0, 0.0, 0.0),
           'right_arm': (48.0, 10.0, 2.0), 'right_forearm': (4.0, 0.0, 0.0), 'left_arm': (52.0, -20.0, -2.0), 'left_forearm': (12.0, 0.0, 0.0),
           'right_leg': (60.0, 0.0, 10.0), 'right_shin': (-80.0, 0.0, 0.0), 'left_leg': (-30.0, 0.0, -10.0), 'left_shin': (-70.0, 0.0, 0.0)}
    shook = {**hit, 'body@p': (0.0, -5.3, -1.4), 'chest': (-48.0, 0.0, 0.0)}
    keys = [(0, {}), (0.15, crouch, 'out'), (0.3, rise, 'out'), (0.52, top, 'out'), (0.62, top), (0.75, hit, 'in'),
            (0.82, shook, 'linear'), (0.95, hit, 'linear'), (1.05, hit), (1.3, {}, 'inout')]
    return _make('ax_slam', 1.3, keys, (0.75,))


def ax_heavy():
    base = {**STAND, 'body@p': (0.0, -1.2, 0.0)}
    step_l = {'left_leg': (40.0, 0.0, -8.0), 'left_shin': (-40.0, 0.0, 0.0), 'right_leg': (-24.0, 0.0, 8.0), 'right_shin': (-30.0, 0.0, 0.0)}
    step_r = {'right_leg': (40.0, 0.0, 8.0), 'right_shin': (-40.0, 0.0, 0.0), 'left_leg': (-24.0, 0.0, -8.0), 'left_shin': (-30.0, 0.0, 0.0)}
    # 1: down along from the upper right
    wind1 = {**base, **step_r, 'body': (0.0, 26.0, 0.0), 'torso': (0.0, 34.0, 0.0), 'chest': (12.0, 0.0, 0.0), 'head': (-6.0, -30.0, 0.0),
             'right_arm': (176.0, 30.0, -30.0), 'right_forearm': (54.0, 0.0, 0.0), 'left_arm': (150.0, -10.0, 14.0), 'left_forearm': (50.0, 0.0, 0.0)}
    hit1 = {**base, **step_l, 'body@p': (0.0, -3.0, -1.6), 'body': (0.0, -16.0, 0.0), 'torso': (0.0, -36.0, 0.0), 'chest': (-34.0, 0.0, 0.0),
            'head': (20.0, 26.0, 0.0), 'right_arm': (44.0, -36.0, 34.0), 'right_forearm': (4.0, 0.0, 0.0),
            'left_arm': (44.0, -44.0, 10.0), 'left_forearm': (14.0, 0.0, 0.0)}
    thru1 = {**hit1, 'torso': (0.0, -44.0, 0.0), 'chest': (-38.0, 0.0, 0.0), 'right_arm': (30.0, -40.0, 40.0)}
    # 2: level from the side
    wind2 = {**base, **step_r, 'body': (0.0, 30.0, 0.0), 'torso': (0.0, 60.0, 0.0), 'chest': (-6.0, 0.0, 0.0), 'head': (4.0, -56.0, 0.0),
             'right_arm': (88.0, 90.0, 44.0), 'right_forearm': (26.0, 0.0, 0.0), 'left_arm': (70.0, 60.0, 10.0), 'left_forearm': (50.0, 0.0, 0.0)}
    hit2 = {**base, **step_l, 'body@p': (0.0, -2.4, -1.2), 'body': (0.0, -24.0, 0.0), 'torso': (0.0, -50.0, 0.0), 'chest': (-12.0, 0.0, 0.0),
            'head': (8.0, 44.0, 0.0), 'right_arm': (88.0, -64.0, -8.0), 'right_forearm': (4.0, 0.0, 0.0),
            'left_arm': (80.0, -74.0, -8.0), 'left_forearm': (14.0, 0.0, 0.0)}
    thru2 = {**hit2, 'torso': (0.0, -62.0, 0.0), 'right_arm': (84.0, -80.0, -12.0)}
    # 3: up along from the lower left
    wind3 = {**base, **step_l, 'body@p': (0.0, -3.6, 0.0), 'body': (0.0, -20.0, 0.0), 'torso': (0.0, -40.0, 0.0), 'chest': (-30.0, 0.0, 0.0),
             'head': (22.0, 24.0, 0.0), 'right_arm': (-16.0, -34.0, -30.0), 'right_forearm': (24.0, 0.0, 0.0),
             'left_arm': (8.0, -44.0, -20.0), 'left_forearm': (34.0, 0.0, 0.0)}
    hit3 = {**base, **step_r, 'body@p': (0.0, 0.2, -1.0), 'body': (0.0, 20.0, 0.0), 'torso': (0.0, 38.0, 0.0), 'chest': (12.0, 0.0, 0.0),
            'head': (-12.0, -24.0, 0.0), 'right_arm': (156.0, 34.0, -22.0), 'right_forearm': (8.0, 0.0, 0.0),
            'left_arm': (140.0, 24.0, 0.0), 'left_forearm': (20.0, 0.0, 0.0)}
    thru3 = {**hit3, 'torso': (0.0, 44.0, 0.0), 'chest': (16.0, 0.0, 0.0), 'right_arm': (170.0, 34.0, -22.0)}
    keys = [(0, {}), (0.4, wind1, 'inout'), (0.6, hit1, 'in'), (0.72, thru1, 'out'), (0.9, thru1),
            (1.22, wind2, 'inout'), (1.45, hit2, 'in'), (1.57, thru2, 'out'), (1.75, thru2),
            (2.07, wind3, 'inout'), (2.3, hit3, 'in'), (2.42, thru3, 'out'), (2.55, thru3), (2.7, {}, 'inout')]
    return _make('ax_heavy', 2.7, keys, (0.6, 1.45, 2.3))


# ------------------------------------------------------------------ the sword

#: the thrust's hold: the right upper arm along the body, bent at the elbow, the blade out forward at the hip; the left
#: hand laid over the hilt; the body forward
THRUST = {'body@p': (0.0, -1.6, 0.0), 'chest': (-22.0, 0.0, 0.0), 'head': (18.0, 0.0, 0.0),
          'right_arm': (14.0, 0.0, 4.0), 'right_forearm': (78.0, 0.0, 0.0), **POINT,
          'left_arm': (40.0, -30.0, 30.0), 'left_forearm': (56.0, 0.0, 0.0)}
#: run through: both hands pushed forward as into a body
STUCK = {**THRUST, 'chest': (-18.0, 0.0, 0.0), 'right_arm': (58.0, 0.0, 4.0), 'right_forearm': (30.0, 0.0, 0.0),
         'left_arm': (62.0, -26.0, 24.0), 'left_forearm': (36.0, 0.0, 0.0)}


def sw_charge():
    keys = [(0, {**THRUST, **RUN_A})]
    for i in range(12):
        keys.append((0.25 * (i + 1), {**THRUST, **(RUN_B if i % 2 == 0 else RUN_A),
                                      'body@p': (0.0, -1.0 if i % 2 == 0 else -1.6, 0.0)}, 'inout'))
    return _make('sw_charge', 3.0, keys)


def sw_skewer():
    keys = [(0, {**THRUST, **RUN_A}), (0.1, {**STUCK, **RUN_B}, 'out')]
    for i in range(3):
        keys.append((0.3 + 0.2 * i, {**STUCK, **(RUN_A if i % 2 == 0 else RUN_B), 'body@p': (0.0, -1.0 if i % 2 == 0 else -1.5, 0.0)}, 'inout'))
    keys.append((0.8, {**STUCK, **RUN_A}, 'inout'))
    return _make('sw_skewer', 0.8, keys)


def sw_cut():
    """The blade out of it level, a half turn to the left: his back to it; it falls; he turns to it again."""
    out = {**LOW, 'body@p': (0.0, -2.6, 0.0), 'chest': (-10.0, 0.0, 0.0), 'head': (6.0, 0.0, 0.0),
           'right_arm': (86.0, 0.0, 74.0), 'right_forearm': (6.0, 0.0, 0.0), 'right_item_sword': (0.0, -60.0, 0.0),
           'left_arm': (60.0, -30.0, 10.0), 'left_forearm': (90.0, 0.0, 0.0)}
    done = {**out, 'right_arm': (40.0, 0.0, 50.0), 'right_forearm': (10.0, 0.0, 0.0), 'right_item_sword': (0.0, -20.0, 0.0),
            'left_arm': (20.0, 0.0, -20.0), 'left_forearm': (20.0, 0.0, 0.0), 'body@p': (0.0, -1.8, 0.0)}
    keys = [(0, {**STUCK, **RUN_A, 'body': (0.0, 0.0, 0.0)}), (0.25, {**out, 'body': (0.0, 180.0, 0.0)}, 'out'),
            (0.4, {**done, 'body': (0.0, 190.0, 0.0)}, 'out'), (0.7, {**done, 'body': (0.0, 180.0, 0.0)}),
            (1.0, {**ITEM, 'body': (0.0, 360.0, 0.0)}, 'inout')]
    return _make('sw_cut', 1.0, keys, (0.25,))


def sw_kick():
    leap = {'chest': (6.0, 0.0, 0.0), 'right_arm': (-30.0, 0.0, 30.0), 'right_forearm': (20.0, 0.0, 0.0),
            'left_arm': (60.0, 0.0, -40.0), 'left_forearm': (30.0, 0.0, 0.0),
            'right_leg': (30.0, 0.0, 4.0), 'right_shin': (-50.0, 0.0, 0.0), 'left_leg': (-20.0, 0.0, -4.0), 'left_shin': (-30.0, 0.0, 0.0)}
    hang = {'chest': (-8.0, 0.0, 0.0), 'head': (8.0, 0.0, 0.0), 'right_arm': (-40.0, 0.0, 40.0), 'right_forearm': (30.0, 0.0, 0.0),
            'left_arm': (70.0, 0.0, -50.0), 'left_forearm': (30.0, 0.0, 0.0),
            'right_leg': (70.0, 0.0, 6.0), 'right_shin': (-100.0, 0.0, 0.0), 'left_leg': (50.0, 0.0, -6.0), 'left_shin': (-110.0, 0.0, 0.0)}
    # the right leg drawn back and up for the kick, the body leaning away from it, the arms out
    wind = {'chest': (10.0, 0.0, 0.0), 'head': (-8.0, 0.0, 0.0), 'right_arm': (-30.0, 0.0, 70.0), 'right_forearm': (20.0, 0.0, 0.0),
            'left_arm': (60.0, 0.0, -70.0), 'left_forearm': (20.0, 0.0, 0.0),
            'right_leg': (-50.0, 0.0, 10.0), 'right_shin': (-110.0, 0.0, 0.0), 'left_leg': (30.0, 0.0, -6.0), 'left_shin': (-60.0, 0.0, 0.0)}
    deep = {**wind, 'chest': (16.0, 0.0, 0.0), 'right_leg': (-66.0, 0.0, 12.0), 'right_shin': (-118.0, 0.0, 0.0), 'right_arm': (-40.0, 0.0, 76.0)}
    kick = {'chest': (-14.0, 0.0, 0.0), 'head': (10.0, 0.0, 0.0), 'right_arm': (-50.0, 0.0, 50.0), 'right_forearm': (10.0, 0.0, 0.0),
            'left_arm': (40.0, 0.0, -60.0), 'left_forearm': (20.0, 0.0, 0.0),
            'right_leg': (96.0, 0.0, 2.0), 'right_shin': (-4.0, 0.0, 0.0), 'left_leg': (-20.0, 0.0, -6.0), 'left_shin': (-30.0, 0.0, 0.0)}
    tuck = {'chest': (-10.0, 0.0, 0.0), 'right_arm': (40.0, 0.0, 20.0), 'right_forearm': (60.0, 0.0, 0.0), 'left_arm': (40.0, 0.0, -20.0),
            'left_forearm': (60.0, 0.0, 0.0), 'right_leg': (60.0, 0.0, 6.0), 'right_shin': (-90.0, 0.0, 0.0), 'left_leg': (60.0, 0.0, -6.0),
            'left_shin': (-90.0, 0.0, 0.0)}
    land = {**LOW, 'body@p': (0.0, -2.6, 0.0), 'chest': (-14.0, 0.0, 0.0), 'right_arm': (20.0, 0.0, 30.0), 'right_forearm': (20.0, 0.0, 0.0),
            'left_arm': (20.0, 0.0, -30.0), 'left_forearm': (20.0, 0.0, 0.0)}
    t = lambda p, y: {**p, 'body': (0.0, y, 0.0)}
    keys = [(0, {}), (0.2, t(leap, 0), 'out'), (0.6, t(hang, 0), 'out'), (1.2, t({**hang, 'chest': (-12.0, 0.0, 0.0)}, 0), 'inout'),
            (1.7, t(wind, 360), 'out'), (2.75, t(deep, 360), 'inout'), (2.9, t(kick, 360), 'in'),
            (3.1, t(kick, 360)), (3.35, t(tuck, 360), 'out'), (3.55, t(land, 360), 'out'), (3.8, t({}, 360), 'inout')]
    return _make('sw_kick', 3.8, keys, (2.9,))


# ------------------------------------------------------------------ the crossbow and the bow

def xb_hold():
    """The mob held off at the left like a shield (the left arm out to the left front), the crossbow out in the right."""
    top = {'body@p': (0.0, -0.6, 0.0), 'chest': (-4.0, 0.0, 0.0), 'torso': (0.0, -8.0, 0.0),
           'left_arm': (76.0, 0.0, -34.0), 'left_forearm': (24.0, 0.0, 0.0),
           'right_arm': (86.0, 8.0, 6.0), 'right_forearm': (6.0, 0.0, 0.0), 'head': (4.0, 8.0, 0.0)}
    keys = [(0, {**STAND, **top}), (0.5, {**STAND, **top, 'chest': (-6.0, 0.0, 0.0)}, 'inout'), (1.0, {**STAND, **top}, 'inout')]
    return _make('xb_hold', 1.0, keys, item=False)


#: the game's slide at 0.6 s (the slide the back slide is made from)
SLIDE = {'body': (36.8, 6.0, 5.5), 'body@p': (0.0, -6.7, 0.0), 'chest': (-22.0, 0.0, -1.0), 'head': (-21.0, 0.0, -2.5),
         'left_arm': (88.5, -14.8, -67.0), 'left_forearm': (34.5, 2.5, -8.0), 'left_leg': (58.0, 0.0, -21.5), 'left_shin': (-80.5, 0.0, 0.0),
         'right_arm': (-48.5, -12.0, 19.5), 'right_forearm': (21.0, 0.0, 0.0), 'right_leg': (63.0, 0.0, 3.0), 'right_shin': (-34.5, 0.0, 0.0)}
#: lying on the back, feet first, the weapon on the chest aimed up (round25's)
BACK = {'body': (-82.0, 0.0, 0.0), 'body@p': (0.0, -9.0, 0.0), 'head': (30.0, 0.0, 0.0), 'chest': (0.0, 0.0, 0.0),
        'right_arm': (150.0, 0.0, 10.0), 'right_forearm': (20.0, 0.0, 0.0), 'left_arm': (150.0, 0.0, -10.0), 'left_forearm': (30.0, 0.0, 0.0),
        'right_leg': (6.0, 0.0, 4.0), 'right_shin': (-4.0, 0.0, 0.0), 'left_leg': (16.0, 0.0, -4.0), 'left_shin': (-20.0, 0.0, 0.0)}


def xb_backslide():
    sit = {**LOW, 'body@p': (0.0, -6.0, 0.0), 'chest': (-30.0, 0.0, 0.0), 'right_arm': (60.0, 0.0, 20.0), 'left_arm': (60.0, 0.0, -20.0),
           'right_leg': (80.0, 0.0, 10.0), 'right_shin': (-100.0, 0.0, 0.0), 'left_leg': (80.0, 0.0, -10.0), 'left_shin': (-100.0, 0.0, 0.0)}
    keys = [(0, SLIDE), (0.25, BACK, 'out'), (1.45, BACK), (1.7, sit, 'inout'), (2.0, {}, 'inout')]
    return _make('xb_backslide', 2.0, keys, item=False)


def xb_belly():
    aim = {**BACK, 'right_arm': (120.0, 0.0, 6.0), 'left_arm': (120.0, 0.0, -6.0), 'head': (40.0, 0.0, 0.0)}
    fired = {**aim, 'right_arm': (128.0, 0.0, 6.0), 'left_arm': (132.0, 0.0, -6.0), 'chest': (4.0, 0.0, 0.0)}
    kip = {'body': (-30.0, 0.0, 0.0), 'body@p': (0.0, -2.0, 0.0), 'chest': (20.0, 0.0, 0.0), 'right_arm': (-30.0, 0.0, 20.0),
           'left_arm': (-30.0, 0.0, -20.0), 'right_leg': (100.0, 0.0, 6.0), 'right_shin': (-120.0, 0.0, 0.0), 'left_leg': (100.0, 0.0, -6.0),
           'left_shin': (-120.0, 0.0, 0.0)}
    landed = {**LOW, 'body@p': (0.0, -2.4, 0.0), 'chest': (-14.0, 0.0, 0.0), 'right_arm': (30.0, 0.0, 20.0), 'left_arm': (30.0, 0.0, -20.0)}
    keys = [(0, BACK), (0.3, aim, 'out'), (0.45, fired, 'linear'), (0.7, fired), (0.95, kip, 'out'), (1.15, landed, 'out'), (1.5, {}, 'inout')]
    return _make('xb_belly', 1.5, keys, (0.45,), item=False)


DRAW = {'chest': (4.0, 0.0, 0.0), 'left_arm': (88.0, 4.0, 10.0), 'left_forearm': (4.0, 0.0, 0.0), 'right_arm': (90.0, 30.0, -10.0),
        'right_forearm': (110.0, 0.0, 0.0), 'head': (10.0, 0.0, 0.0)}
#: the string let go: the right hand back past the cheek
LOOSE = {**DRAW, 'right_arm': (92.0, 40.0, -16.0), 'right_forearm': (126.0, 0.0, 0.0)}
TUCK = {'right_leg': (60.0, 0.0, 6.0), 'right_shin': (-90.0, 0.0, 0.0), 'left_leg': (40.0, 0.0, -6.0), 'left_shin': (-100.0, 0.0, 0.0)}


def _aimed(pose, pitch):
    """The draw pose aimed {pitch} degrees down (both arms lower, the head with them)."""
    return {**pose, 'left_arm': (pose['left_arm'][0] - pitch, *pose['left_arm'][1:]),
            'right_arm': (pose['right_arm'][0] - pitch, *pose['right_arm'][1:]), 'head': (pose['head'][0] + pitch * 0.6, 0.0, 0.0)}


def bw_shake():
    keys = [(0, {})]
    jolts = [(0.9, 0.0, 6.0), (-1.0, 0.4, -8.0), (0.6, -0.8, 10.0), (-0.7, 0.6, -5.0), (1.1, 0.2, 7.0), (-0.9, -0.5, -9.0),
             (0.5, 0.9, 4.0), (-1.2, -0.2, -6.0), (0.8, -0.6, 8.0), (-0.6, 0.7, -7.0), (1.0, -0.3, 5.0), (-0.8, 0.4, -10.0),
             (0.7, 0.8, 9.0), (-1.1, -0.6, -4.0), (0.4, 0.3, 6.0)]
    for i, (x, z, yaw) in enumerate(jolts):
        f = 0.4 + 0.6 * i / len(jolts)
        keys.append((0.05 * (i + 1), {'body@p': (x * f, -0.6 * f, z * f), 'body': (0.0, yaw * f, x * 4 * f), 'chest': (-8.0 * f, 0.0, 0.0),
                                      'right_arm': (30.0 * f, 0.0, 20.0), 'left_arm': (30.0 * f, 0.0, -20.0),
                                      'right_leg': (20.0 * f, 0.0, 6.0), 'right_shin': (-30.0 * f, 0.0, 0.0),
                                      'left_leg': (20.0 * f, 0.0, -6.0), 'left_shin': (-30.0 * f, 0.0, 0.0)}, 'linear'))
    keys.append((0.9, {**LOW, 'body@p': (0.0, -3.0, 0.0), 'chest': (-20.0, 0.0, 0.0)}, 'out'))
    return _make('bw_shake', 0.9, keys, item=False)


def _phantom(name, before, drawn, loosed):
    keys = [(0, before), (0.25, drawn, 'out'), (0.3, drawn), (0.33, loosed, 'linear'), (0.5, loosed)]
    return _make(name, 0.5, keys, (0.3,), item=False)


def bw_aim():
    feet = {**STAND, 'body@p': (0.0, -0.8, 0.0)}
    return _phantom('bw_aim', {**feet, 'left_arm': (40.0, 0.0, 0.0)}, {**feet, **DRAW, 'torso': (0.0, -10.0, 0.0)},
                    {**feet, **LOOSE, 'torso': (0.0, -10.0, 0.0)})


def bw_jump():
    lean = {'body': (-14.0, 0.0, 0.0), **TUCK}
    return _phantom('bw_jump', {**lean, 'left_arm': (60.0, 0.0, 0.0)}, {**lean, **_aimed(DRAW, 40)}, {**lean, **_aimed(LOOSE, 40)})


def bw_fallback():
    # falling back and down from its back, the legs forward as if sitting, the body leaning back
    sit = {'body': (-40.0, 0.0, 0.0), 'right_leg': (96.0, 0.0, 6.0), 'right_shin': (-30.0, 0.0, 0.0), 'left_leg': (84.0, 0.0, -6.0),
           'left_shin': (-44.0, 0.0, 0.0)}
    return _phantom('bw_fallback', {**sit, 'body': (-20.0, 0.0, 0.0), 'left_arm': (60.0, 0.0, 0.0)},
                    {**sit, **_aimed(DRAW, -20), 'head': (-4.0, 0.0, 0.0)}, {**sit, **_aimed(LOOSE, -20), 'head': (-4.0, 0.0, 0.0)})


def bw_flip():
    # rolled over head down (a roll, so still facing it), drawing down from the sky (down is "up" to him now)
    flip = {'body': (0.0, 0.0, 180.0), 'body@p': (0.0, 12.0, 0.0), 'right_leg': (10.0, 0.0, 10.0), 'left_leg': (-10.0, 0.0, -10.0),
            'right_shin': (-20.0, 0.0, 0.0), 'left_shin': (-10.0, 0.0, 0.0)}
    keys = [(0, {**TUCK, 'body': (0.0, 0.0, 0.0), 'left_arm': (60.0, 0.0, 0.0)}), (0.2, {**flip, **_aimed(DRAW, -40)}, 'out'),
            (0.3, {**flip, **_aimed(DRAW, -40)}), (0.33, {**flip, **_aimed(LOOSE, -40)}, 'linear'), (0.5, {**flip, **_aimed(LOOSE, -40)})]
    return _make('bw_flip', 0.5, keys, (0.3,), item=False)


def bw_turn():
    low = {**LOW, 'body@p': (0.0, -2.0, 0.0)}
    keys = [(0, {**low, 'body': (0.0, 0.0, 0.0), 'left_arm': (70.0, 0.0, -40.0)}), (0.2, {**low, **DRAW, 'body': (0.0, 360.0, 0.0)}, 'out'),
            (0.3, {**low, **DRAW, 'body': (0.0, 360.0, 0.0)}), (0.33, {**low, **LOOSE, 'body': (0.0, 360.0, 0.0)}, 'linear'),
            (0.5, {**low, **LOOSE, 'body': (0.0, 360.0, 0.0)})]
    return _make('bw_turn', 0.5, keys, (0.3,), item=False)


def bw_brake():
    """Skidding backwards: the body a little forward, the knees bent and wide, both hands on the bow held low in front."""
    skid = {'body@p': (0.0, -3.6, 0.0), 'chest': (-16.0, 0.0, 0.0), 'head': (12.0, 0.0, 0.0),
            'right_leg': (30.0, 0.0, 14.0), 'right_shin': (-56.0, 0.0, 0.0), 'left_leg': (16.0, 0.0, -14.0), 'left_shin': (-60.0, 0.0, 0.0),
            'right_arm': (24.0, -20.0, -16.0), 'right_forearm': (30.0, 0.0, 0.0), 'left_arm': (26.0, 20.0, 16.0), 'left_forearm': (30.0, 0.0, 0.0)}
    shake = {**skid, 'body@p': (0.0, -3.9, 0.0), 'chest': (-18.0, 0.0, 0.0)}
    rise = {**STAND, 'body@p': (0.0, -0.6, 0.0), 'chest': (-4.0, 0.0, 0.0), 'right_arm': (10.0, -10.0, -8.0), 'left_arm': (12.0, 10.0, 8.0),
            'right_forearm': (16.0, 0.0, 0.0), 'left_forearm': (16.0, 0.0, 0.0)}
    keys = [(0, skid), (0.12, shake, 'linear'), (0.24, skid, 'linear'), (0.36, shake, 'linear'), (0.6, skid, 'out'),
            (1.1, skid), (1.45, rise, 'inout'), (1.8, {}, 'inout')]
    return _make('bw_brake', 1.8, keys, item=False)


def made():
    a = [ax_slam(), ax_heavy(), sw_charge(), sw_skewer(), sw_cut(), sw_kick(), xb_hold(), xb_backslide(), xb_belly(),
         bw_shake(), bw_aim(), bw_jump(), bw_fallback(), bw_flip(), bw_turn(), bw_brake()]
    for x in a:
        kit.soften(x, round9.SOFTEN, round9.LEGS)
    return a


def main():
    import importlib.util
    import json
    spec = importlib.util.spec_from_file_location('refine_29', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'refine.py'))
    refine = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(refine)
    with open(kit.SRC, encoding='utf-8') as f:
        model = json.load(f)
    model['name'] = 'moves_2026-11-23'
    refine.write_set(model, made(), DST)


if __name__ == '__main__':
    main()
