"""The author, 2026-11-20 (night): new moves for the axe, the sword, the crossbow and the bow (HeavyMoves in the mod).

Axe
    ax_slam     (1.0 s)  W + Space + G: up with the axe over the head in both hands, down onto the ground (AX_SLAM_HIT,
                         0.5 s): a wave round him, 7 blocks, throwing them up out of the ground.
    ax_heavy    (1.8 s)  G held: three heavy blows, each with a big slash: down along from the upper right (0.4 s), level
                         from the side (0.95 s), up along from the lower left (1.5 s).
    axc_skull, axc_kick, axc_spin, axc_upper (0.5-0.6 s): the axe's chain (Ctrl + W + Space + LMB): into the skull from
                         above, the kick away, the spinning blow, the uppercut.
Sword
    sw_charge   (3 s)    the run up: low, the blade forward at the hip, the strides.
    sw_skewer   (1 s)    a mob on the blade, still running, the left hand on the hilt behind.
    sw_fling    (0.6 s)  the blade flicked forward, the mob thrown off it (0.2 s).
    sw_knee     (1.6 s)  Ctrl + W + Space + R at a mob under a fifth of its health: the leap, the flying knee into its head
                         in slow motion (0.3-0.9 s, the knee home at 0.6 s), a whole turn in the air, a calm landing.
Crossbow
    xb_hold     (1 s, looped)  the living shield: the left hand on its shoulder, the crossbow out in the right hand.
    xb_backslide (2.4 s) Ctrl + W + C + LMB (the bow too): down on the back, feet first, the weapon on the chest aimed up,
                         the slide dying; up again at the end.
    xb_belly    (1.2 s)  a mob fallen onto him: the shot up into its belly (0.15 s), the kip-up onto his feet.
Bow
    bw_rain     (3.0 s)  the bow's ultimate: up into the air, three turns drawing and loosing, the hero's landing on one
                         knee (2.3 s), the arrows hanging in the air fly all at once (2.4 s).

The held item's place is keyed in the axe's and the sword's poses (an all-zero item is left out of the file and the game
holds it its own way).

    python3 tools/blockbench/refine_author_2026_10_29/round25.py   (writes refined/moves_2026-11-20.bbmodel)
"""
import os

import kit
import round9
from round9 import Feet, design, bake

DST = os.path.join(kit.BB, 'refined', 'moves_2026-11-20.bbmodel')
UPPER = ('torso', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm')
LOWER = ('left_leg', 'left_shin', 'right_leg', 'right_shin')
ITEM = {'right_item_sword': (0.01, 0.0, 0.0)}
STAND = {'right_leg': (14.0, 0.0, 6.0), 'right_shin': (-14.0, 0.0, 0.0), 'left_leg': (-10.0, 0.0, -6.0), 'left_shin': (-12.0, 0.0, 0.0)}
LOW = {'body@p': (0.0, -3.0, 0.0), 'right_leg': (44.0, 0.0, 8.0), 'right_shin': (-60.0, 0.0, 0.0), 'left_leg': (-16.0, 0.0, -8.0),
       'left_shin': (-50.0, 0.0, 0.0)}


def _all(keys, item=True):
    bones = {b: (0, 0, 0) for b in UPPER + LOWER}
    extra = ITEM if item else {}
    return [(k[0], {**bones, **extra, **k[1]}) + tuple(k[2:]) for k in keys]


def _make(name, length, keys, hits=(), item=True):
    return bake(name, length, design(name, length, _all(keys, item)), Feet().designed(-1.0, length + 1.0), extra=tuple(hits), level=False)


# ------------------------------------------------------------------ the axe

def ax_slam():
    up = {**STAND, 'body@p': (0.0, 0.0, 0.0), 'chest': (10.0, 0.0, 0.0), 'head': (-8.0, 0.0, 0.0),
          'right_arm': (176.0, 8.0, -6.0), 'right_forearm': (24.0, 0.0, 0.0), 'left_arm': (172.0, -14.0, 10.0), 'left_forearm': (34.0, 0.0, 0.0),
          'right_leg': (40.0, 0.0, 6.0), 'right_shin': (-70.0, 0.0, 0.0), 'left_leg': (30.0, 0.0, -6.0), 'left_shin': (-80.0, 0.0, 0.0)}
    hit = {**LOW, 'body@p': (0.0, -4.4, -1.0), 'chest': (-40.0, 0.0, 0.0), 'head': (30.0, 0.0, 0.0),
           'right_arm': (52.0, 10.0, 2.0), 'right_forearm': (6.0, 0.0, 0.0), 'left_arm': (54.0, -20.0, -2.0), 'left_forearm': (14.0, 0.0, 0.0)}
    crouch = {**LOW, 'body@p': (0.0, -2.0, 0.0), 'chest': (-12.0, 0.0, 0.0), 'right_arm': (40.0, 0.0, 10.0), 'left_arm': (30.0, 0.0, -10.0)}
    keys = [(0, {}), (0.12, crouch, 'out'), (0.3, up, 'out'), (0.42, up), (0.5, hit, 'linear'), (0.7, hit), (1.0, {}, 'inout')]
    return _make('ax_slam', 1.0, keys, (0.5,))


def ax_heavy():
    base = {**STAND, 'body@p': (0.0, -1.0, 0.0)}
    wind1 = {**base, 'torso': (0.0, 30.0, 0.0), 'chest': (6.0, 0.0, 0.0), 'right_arm': (160.0, 20.0, -24.0), 'right_forearm': (30.0, 0.0, 0.0),
             'left_arm': (150.0, -10.0, 10.0), 'left_forearm': (40.0, 0.0, 0.0)}
    hit1 = {**base, 'body@p': (0.0, -2.4, -1.2), 'torso': (0.0, -30.0, 0.0), 'chest': (-28.0, 0.0, 0.0), 'head': (16.0, 20.0, 0.0),
            'right_arm': (50.0, -30.0, 30.0), 'right_forearm': (8.0, 0.0, 0.0), 'left_arm': (50.0, -40.0, 10.0), 'left_forearm': (16.0, 0.0, 0.0)}
    wind2 = {**base, 'torso': (0.0, 56.0, 0.0), 'chest': (-8.0, 0.0, 0.0), 'head': (4.0, -40.0, 0.0), 'right_arm': (86.0, 80.0, 40.0),
             'right_forearm': (20.0, 0.0, 0.0), 'left_arm': (70.0, 50.0, 10.0), 'left_forearm': (40.0, 0.0, 0.0)}
    hit2 = {**base, 'body@p': (0.0, -2.0, -0.8), 'torso': (0.0, -44.0, 0.0), 'chest': (-12.0, 0.0, 0.0), 'head': (8.0, 34.0, 0.0),
            'right_arm': (86.0, -60.0, -8.0), 'right_forearm': (6.0, 0.0, 0.0), 'left_arm': (80.0, -70.0, -8.0), 'left_forearm': (14.0, 0.0, 0.0)}
    wind3 = {**base, 'body@p': (0.0, -3.0, 0.0), 'torso': (0.0, -36.0, 0.0), 'chest': (-26.0, 0.0, 0.0), 'head': (18.0, 20.0, 0.0),
             'right_arm': (-10.0, -30.0, -30.0), 'right_forearm': (20.0, 0.0, 0.0), 'left_arm': (10.0, -40.0, -20.0), 'left_forearm': (30.0, 0.0, 0.0)}
    hit3 = {**base, 'body@p': (0.0, -0.6, -0.8), 'torso': (0.0, 34.0, 0.0), 'chest': (8.0, 0.0, 0.0), 'head': (-8.0, -20.0, 0.0),
            'right_arm': (150.0, 30.0, -20.0), 'right_forearm': (10.0, 0.0, 0.0), 'left_arm': (140.0, 20.0, 0.0), 'left_forearm': (20.0, 0.0, 0.0)}
    keys = [(0, {}), (0.25, wind1, 'out'), (0.4, hit1, 'linear'), (0.55, hit1), (0.8, wind2, 'inout'), (0.95, hit2, 'linear'), (1.1, hit2),
            (1.35, wind3, 'inout'), (1.5, hit3, 'linear'), (1.65, hit3), (1.8, {}, 'inout')]
    return _make('ax_heavy', 1.8, keys, (0.4, 0.95, 1.5))


def _blow(name, length, wind, hit, thru, at=0.25):
    keys = [(0, wind), (at, hit, 'linear'), (at + 0.15, thru, 'out'), (length, thru)]
    return _make(name, length, keys, (at,))


def axc_skull():
    air = {'right_leg': (60.0, 0.0, 6.0), 'right_shin': (-90.0, 0.0, 0.0), 'left_leg': (40.0, 0.0, -6.0), 'left_shin': (-100.0, 0.0, 0.0)}
    return _blow('axc_skull', 0.6,
                 {**air, 'chest': (10.0, 0.0, 0.0), 'right_arm': (176.0, 6.0, -6.0), 'right_forearm': (30.0, 0.0, 0.0),
                  'left_arm': (170.0, -12.0, 10.0), 'left_forearm': (36.0, 0.0, 0.0)},
                 {**LOW, 'chest': (-36.0, 0.0, 0.0), 'head': (26.0, 0.0, 0.0), 'right_arm': (60.0, 8.0, 2.0), 'right_forearm': (6.0, 0.0, 0.0),
                  'left_arm': (60.0, -20.0, -2.0), 'left_forearm': (14.0, 0.0, 0.0)},
                 {**LOW, 'chest': (-38.0, 0.0, 0.0), 'head': (28.0, 0.0, 0.0), 'right_arm': (54.0, 8.0, 2.0), 'right_forearm': (6.0, 0.0, 0.0),
                  'left_arm': (54.0, -20.0, -2.0), 'left_forearm': (14.0, 0.0, 0.0)})


def axc_kick():
    chamber = {'right_leg': (80.0, 0.0, 2.0), 'right_shin': (-100.0, 0.0, 0.0), 'left_leg': (-6.0, 0.0, -4.0), 'left_shin': (-16.0, 0.0, 0.0)}
    kicked = {'right_leg': (90.0, 0.0, 2.0), 'right_shin': (-6.0, 0.0, 0.0), 'left_leg': (-8.0, 0.0, -4.0), 'left_shin': (-12.0, 0.0, 0.0)}
    arms = {'right_arm': (30.0, 0.0, 30.0), 'right_forearm': (30.0, 0.0, 0.0), 'left_arm': (30.0, 0.0, -40.0), 'left_forearm': (20.0, 0.0, 0.0)}
    return _blow('axc_kick', 0.5, {**chamber, **arms, 'chest': (4.0, 0.0, 0.0)},
                 {**kicked, **arms, 'chest': (16.0, 0.0, 0.0), 'head': (-8.0, 0.0, 0.0), 'body@p': (0.0, -0.4, 0.8)},
                 {**kicked, **arms, 'chest': (14.0, 0.0, 0.0), 'head': (-6.0, 0.0, 0.0), 'body@p': (0.0, -0.4, 0.8)}, at=0.2)


def axc_spin():
    out = {**LOW, 'body@p': (0.0, -1.6, 0.0), 'chest': (-8.0, 0.0, 0.0), 'right_arm': (86.0, 0.0, 72.0), 'right_forearm': (6.0, 0.0, 0.0),
           'left_arm': (50.0, -20.0, -30.0), 'left_forearm': (60.0, 0.0, 0.0)}
    keys = [(0, {**out, 'body': (0.0, -90.0, 0.0)}), (0.3, {**out, 'body': (0.0, 200.0, 0.0)}, 'linear'),
            (0.45, {**out, 'body': (0.0, 270.0, 0.0)}, 'out'), (0.6, {**out, 'body': (0.0, 270.0, 0.0)})]
    return _make('axc_spin', 0.6, keys, (0.3,))


def axc_upper():
    return _blow('axc_upper', 0.6,
                 {**LOW, 'chest': (-28.0, 0.0, 0.0), 'torso': (0.0, 26.0, 0.0), 'right_arm': (-24.0, 20.0, 30.0), 'right_forearm': (20.0, 0.0, 0.0),
                  'left_arm': (20.0, 0.0, -30.0), 'left_forearm': (30.0, 0.0, 0.0)},
                 {**STAND, 'body@p': (0.0, 0.4, -0.8), 'chest': (6.0, 0.0, 0.0), 'torso': (0.0, -16.0, 0.0), 'head': (-12.0, 10.0, 0.0),
                  'right_arm': (150.0, -20.0, -10.0), 'right_forearm': (8.0, 0.0, 0.0), 'left_arm': (30.0, 0.0, -50.0), 'left_forearm': (20.0, 0.0, 0.0)},
                 {**STAND, 'body@p': (0.0, 0.6, -0.8), 'chest': (10.0, 0.0, 0.0), 'torso': (0.0, -20.0, 0.0), 'head': (-16.0, 10.0, 0.0),
                  'right_arm': (176.0, -20.0, -10.0), 'right_forearm': (8.0, 0.0, 0.0), 'left_arm': (24.0, 0.0, -56.0), 'left_forearm': (16.0, 0.0, 0.0)})


# ------------------------------------------------------------------ the sword

RUN_A = {'right_leg': (50.0, 0.0, 4.0), 'right_shin': (-60.0, 0.0, 0.0), 'left_leg': (-30.0, 0.0, -4.0), 'left_shin': (-40.0, 0.0, 0.0)}
RUN_B = {'right_leg': (-30.0, 0.0, 4.0), 'right_shin': (-40.0, 0.0, 0.0), 'left_leg': (50.0, 0.0, -4.0), 'left_shin': (-60.0, 0.0, 0.0)}


def sw_charge():
    top = {'body@p': (0.0, -1.6, 0.0), 'chest': (-24.0, 0.0, 0.0), 'head': (18.0, 0.0, 0.0), 'right_arm': (60.0, 10.0, 6.0),
           'right_forearm': (30.0, 0.0, 0.0), 'right_item_sword': (0.0, -84.0, 0.0), 'left_arm': (-30.0, 0.0, -12.0), 'left_forearm': (40.0, 0.0, 0.0)}
    keys = [(0, {**top, **RUN_A})]
    for i in range(12):
        keys.append((0.25 * (i + 1), {**top, **(RUN_B if i % 2 == 0 else RUN_A), 'body@p': (0.0, -1.0 if i % 2 == 0 else -1.6, 0.0)}, 'inout'))
    return _make('sw_charge', 3.0, keys)


def sw_skewer():
    top = {'body@p': (0.0, -1.4, 0.0), 'chest': (-18.0, 0.0, 0.0), 'head': (12.0, 0.0, 0.0), 'right_arm': (84.0, 4.0, 2.0),
           'right_forearm': (4.0, 0.0, 0.0), 'right_item_sword': (0.0, -84.0, 0.0), 'left_arm': (70.0, -20.0, -4.0), 'left_forearm': (40.0, 0.0, 0.0)}
    keys = [(0, {**top, **RUN_A}), (0.25, {**top, **RUN_B}, 'inout'), (0.5, {**top, **RUN_A}, 'inout'), (0.75, {**top, **RUN_B}, 'inout'),
            (1.0, {**top, **RUN_A}, 'inout')]
    return _make('sw_skewer', 1.0, keys)


def sw_fling():
    held = {**STAND, 'body@p': (0.0, -1.4, 0.0), 'chest': (-18.0, 0.0, 0.0), 'right_arm': (84.0, 4.0, 2.0), 'right_forearm': (4.0, 0.0, 0.0),
            'right_item_sword': (0.0, -84.0, 0.0), 'left_arm': (70.0, -20.0, -4.0), 'left_forearm': (40.0, 0.0, 0.0)}
    flung = {**STAND, 'body@p': (0.0, -0.6, 0.4), 'chest': (6.0, 0.0, 0.0), 'head': (-4.0, 0.0, 0.0), 'right_arm': (140.0, 0.0, 10.0),
             'right_forearm': (10.0, 0.0, 0.0), 'right_item_sword': (0.0, -40.0, 0.0), 'left_arm': (20.0, 0.0, -30.0), 'left_forearm': (20.0, 0.0, 0.0)}
    keys = [(0, held), (0.1, {**held, 'right_arm': (70.0, 4.0, 2.0)}, 'out'), (0.2, flung, 'linear'), (0.35, flung), (0.6, {}, 'inout')]
    return _make('sw_fling', 0.6, keys, (0.2,))


def sw_knee():
    leap = {'body@p': (0.0, 0.0, 0.0), 'chest': (6.0, 0.0, 0.0), 'right_arm': (-30.0, 0.0, 30.0), 'right_forearm': (20.0, 0.0, 0.0),
            'left_arm': (60.0, 0.0, -40.0), 'left_forearm': (30.0, 0.0, 0.0),
            'right_leg': (20.0, 0.0, 4.0), 'right_shin': (-30.0, 0.0, 0.0), 'left_leg': (-20.0, 0.0, -4.0), 'left_shin': (-20.0, 0.0, 0.0)}
    knee = {'chest': (14.0, 0.0, 0.0), 'head': (-10.0, 0.0, 0.0), 'right_arm': (-50.0, 0.0, 40.0), 'right_forearm': (30.0, 0.0, 0.0),
            'left_arm': (80.0, 0.0, -60.0), 'left_forearm': (20.0, 0.0, 0.0),
            'right_leg': (100.0, 0.0, 4.0), 'right_shin': (-130.0, 0.0, 0.0), 'left_leg': (-30.0, 0.0, -4.0), 'left_shin': (-40.0, 0.0, 0.0)}
    home = {**knee, 'right_leg': (110.0, 0.0, 4.0), 'chest': (18.0, 0.0, 0.0), 'head': (-14.0, 0.0, 0.0)}
    tuck = {'chest': (-10.0, 0.0, 0.0), 'right_arm': (40.0, 0.0, 20.0), 'right_forearm': (60.0, 0.0, 0.0), 'left_arm': (40.0, 0.0, -20.0),
            'left_forearm': (60.0, 0.0, 0.0), 'right_leg': (60.0, 0.0, 6.0), 'right_shin': (-90.0, 0.0, 0.0), 'left_leg': (60.0, 0.0, -6.0),
            'left_shin': (-90.0, 0.0, 0.0)}
    land = {**LOW, 'body@p': (0.0, -2.6, 0.0), 'chest': (-14.0, 0.0, 0.0), 'right_arm': (20.0, 0.0, 30.0), 'right_forearm': (20.0, 0.0, 0.0),
            'left_arm': (20.0, 0.0, -30.0), 'left_forearm': (20.0, 0.0, 0.0)}
    keys = [(0, {}), (0.15, leap, 'out'), (0.3, knee, 'out'), (0.6, home, 'linear'), (0.9, knee, 'linear'),
            (1.05, {**tuck, 'body': (0.0, 0.0, 0.0)}, 'out'), (1.3, {**tuck, 'body': (0.0, 360.0, 0.0)}, 'inout'),
            (1.4, {**land, 'body': (0.0, 360.0, 0.0)}, 'out'), (1.6, {'body': (0.0, 360.0, 0.0)}, 'inout')]
    return _make('sw_knee', 1.6, keys, (0.6,))


# ------------------------------------------------------------------ the crossbow and the bow

def xb_hold():
    top = {'body@p': (0.0, -0.6, 0.0), 'chest': (-6.0, 0.0, 0.0), 'left_arm': (90.0, -6.0, 12.0), 'left_forearm': (30.0, 0.0, 0.0),
           'right_arm': (86.0, 10.0, 6.0), 'right_forearm': (6.0, 0.0, 0.0), 'head': (4.0, 0.0, 0.0)}
    keys = [(0, {**STAND, **top}), (0.5, {**STAND, **top, 'chest': (-8.0, 0.0, 0.0)}, 'inout'), (1.0, {**STAND, **top}, 'inout')]
    return _make('xb_hold', 1.0, keys, item=False)


#: lying on the back, feet first, the weapon on the chest aimed up
BACK = {'body': (-82.0, 0.0, 0.0), 'body@p': (0.0, -9.0, 0.0), 'head': (30.0, 0.0, 0.0), 'chest': (0.0, 0.0, 0.0),
        'right_arm': (150.0, 0.0, 10.0), 'right_forearm': (20.0, 0.0, 0.0), 'left_arm': (150.0, 0.0, -10.0), 'left_forearm': (30.0, 0.0, 0.0),
        'right_leg': (6.0, 0.0, 4.0), 'right_shin': (-4.0, 0.0, 0.0), 'left_leg': (16.0, 0.0, -4.0), 'left_shin': (-20.0, 0.0, 0.0)}


def xb_backslide():
    sit = {**LOW, 'body@p': (0.0, -6.0, 0.0), 'chest': (-30.0, 0.0, 0.0), 'right_arm': (60.0, 0.0, 20.0), 'left_arm': (60.0, 0.0, -20.0),
           'right_leg': (80.0, 0.0, 10.0), 'right_shin': (-100.0, 0.0, 0.0), 'left_leg': (80.0, 0.0, -10.0), 'left_shin': (-100.0, 0.0, 0.0)}
    keys = [(0, {}), (0.2, {**BACK, 'body': (-40.0, 0.0, 0.0), 'body@p': (0.0, -6.0, 0.0)}, 'out'), (0.35, BACK, 'out'), (1.7, BACK),
            (2.0, sit, 'inout'), (2.4, {}, 'inout')]
    return _make('xb_backslide', 2.4, keys, item=False)


def xb_belly():
    fired = {**BACK, 'right_arm': (160.0, 0.0, 10.0), 'left_arm': (160.0, 0.0, -10.0), 'head': (36.0, 0.0, 0.0)}
    kip = {'body': (-30.0, 0.0, 0.0), 'body@p': (0.0, -2.0, 0.0), 'chest': (20.0, 0.0, 0.0), 'right_arm': (-30.0, 0.0, 20.0),
           'left_arm': (-30.0, 0.0, -20.0), 'right_leg': (100.0, 0.0, 6.0), 'right_shin': (-120.0, 0.0, 0.0), 'left_leg': (100.0, 0.0, -6.0),
           'left_shin': (-120.0, 0.0, 0.0)}
    landed = {**LOW, 'body@p': (0.0, -2.4, 0.0), 'chest': (-14.0, 0.0, 0.0), 'right_arm': (30.0, 0.0, 20.0), 'left_arm': (30.0, 0.0, -20.0)}
    keys = [(0, BACK), (0.15, fired, 'linear'), (0.45, fired), (0.65, kip, 'out'), (0.85, landed, 'out'), (1.2, {}, 'inout')]
    return _make('xb_belly', 1.2, keys, (0.15,), item=False)


def bw_rain():
    draw = {'chest': (4.0, 0.0, 0.0), 'left_arm': (88.0, 4.0, 10.0), 'left_forearm': (4.0, 0.0, 0.0), 'right_arm': (90.0, 30.0, -10.0),
            'right_forearm': (110.0, 0.0, 0.0), 'head': (10.0, 0.0, 0.0)}
    tuck = {'right_leg': (50.0, 0.0, 6.0), 'right_shin': (-80.0, 0.0, 0.0), 'left_leg': (30.0, 0.0, -6.0), 'left_shin': (-90.0, 0.0, 0.0)}
    hero = {'body@p': (0.0, -6.0, 0.0), 'chest': (-20.0, 0.0, 0.0), 'head': (16.0, 0.0, 0.0),
            'right_leg': (80.0, 0.0, 4.0), 'right_shin': (-80.0, 0.0, 0.0), 'left_leg': (-6.0, 0.0, -4.0), 'left_shin': (-100.0, 0.0, 0.0),
            'right_arm': (-20.0, 0.0, 40.0), 'right_forearm': (10.0, 0.0, 0.0), 'left_arm': (40.0, 0.0, -30.0), 'left_forearm': (20.0, 0.0, 0.0)}
    keys = [(0, {}), (0.3, {**LOW, 'chest': (-16.0, 0.0, 0.0)}, 'out'), (0.45, {**draw, **tuck, 'body': (0.0, 0.0, 0.0)}, 'out')]
    for i in range(3):
        t0 = 0.45 + i * 0.5
        keys.append((t0 + 0.5, {**draw, **tuck, 'body': (0.0, 360.0 * (i + 1), 0.0)}, 'linear'))
    keys += [(2.3, {**hero, 'body': (0.0, 1080.0, 0.0)}, 'out'), (2.4, {**hero, 'body': (0.0, 1080.0, 0.0), 'right_arm': (-30.0, 0.0, 60.0)}),
             (2.75, {**hero, 'body': (0.0, 1080.0, 0.0)}), (3.0, {'body': (0.0, 1080.0, 0.0)}, 'inout')]
    return _make('bw_rain', 3.0, keys, (2.3, 2.4), item=False)


def made():
    a = [ax_slam(), ax_heavy(), axc_skull(), axc_kick(), axc_spin(), axc_upper(), sw_charge(), sw_skewer(), sw_fling(), sw_knee(),
         xb_hold(), xb_backslide(), xb_belly(), bw_rain()]
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
    model['name'] = 'moves_2026-11-20'
    refine.write_set(model, made(), DST)


if __name__ == '__main__':
    main()
