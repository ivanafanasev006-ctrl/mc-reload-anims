"""The author, 2026-11-19: the time stop (TimeStop in the mod), its stance his samurai_mode (author_2026-11-16_ninja.bbmodel;
19.11: "the samurai stance from samurai_mode, a hundred per cent", at the start of the time stop).

"He takes a stance, looks about, 5+ enemies run at him, and he moves very fast, 0.5 s an enemy, a different blow at each:
from above along it with the sword, a thrust, from behind, into the belly, in turn, and ends where he began and with the
left hand yawns grandly, holding the sword in the right."

    ts_stance  (1.5 s)  his samurai_mode as he made it (0.625 s: the body turned side-on, the blade drawn level at the
                        hip, low on bent knees), then held: the head looks about (the enemies freezing), a breath.
    ts_slash   (0.5 s)  from above down along it: the sword up over the right shoulder, down across to the left hip (hit
                        at tick 4).
    ts_thrust  (0.5 s)  the blade drawn back level, a lunge, the arm straight out (tick 4).
    ts_back    (0.5 s)  from behind it, across its back: wound to the right, the blade swept level right to left (tick 4).
    ts_belly   (0.5 s)  low, into the belly: down on the knees, the blade rising from the right hip across (tick 4).
    ts_yawn    (2 s)    up out of the crouch, the sword lowered in the right hand, the left hand to the mouth, the head
                        back in a long yawn, the hand down, at ease.

The blows start and end in the same low ready pose (the server puts the player at each mob between them; the flicker
covers the cut). His file is not changed.

    python3 tools/blockbench/refine_author_2026_10_29/round23.py   (writes refined/ninja_2026-11-16.bbmodel)
"""
import os

import kit
import kinetrix_rig as kr
import round9
from kit import L
from round9 import Feet, design, bake

SRC = os.path.join(kit.BB, 'animations', 'author_2026-11-16_ninja.bbmodel')
DST = os.path.join(kit.BB, 'refined', 'ninja_2026-11-16.bbmodel')
UPPER = ('torso', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm', 'right_item_sword')
LOWER = ('left_leg', 'left_shin', 'right_leg', 'right_shin')

_MODEL = kr.Model(SRC)
_SAMURAI = _MODEL.channels(next(a for a in _MODEL.animations if a['name'] == 'samurai_mode'))
SAMURAI_LEN = 0.625


def samurai(t):
    """His samurai_mode at t (held after its end), every bone of it, Blockbench values."""
    out = {}
    for bone, chans in _SAMURAI.items():
        if bone in ('left_item_sword', 'right_item_bow'):
            continue
        for c, keys in chans.items():
            v = kr.sample(keys, min(t, SAMURAI_LEN))
            out[bone + ('@p' if c == 'position' else '')] = tuple(float(x) for x in v)
    return out


def ts_stance():
    """His stance, then held: the head turning to look about (left, right, back to the front), a breath."""
    LEN = 1.5
    hold = samurai(SAMURAI_LEN)
    hx, hy, hz = hold['head']
    look = kit.keys((SAMURAI_LEN, hy), (0.85, hy - 35), (1.1, hy + 20), (1.35, hy), (LEN, hy))

    def pose_at(t):
        p = samurai(t)
        if t > SAMURAI_LEN:
            p['head'] = (hx, look(t), hz)
            bx, by, bz = p['body@p']
            p['body@p'] = (bx, by - 0.25 * kit.span(t, SAMURAI_LEN, 1.0) * (1 - kit.span(t, 1.0, LEN)), bz)
        return p

    feet = Feet().designed(-1.0, LEN + 1.0)
    a = bake('ts_stance', LEN, pose_at, feet, level=False)
    return a


#: The low ready pose the blows start and end in: crouched, the blade low at the right hip, the left hand out.
READY = {'body@p': (0.0, -2.0, 0.0), 'chest': (-14.0, 0.0, 0.0), 'head': (12.0, 0.0, 0.0),
         'right_arm': (30.0, 20.0, 22.0), 'right_forearm': (40.0, 0.0, 0.0), 'left_arm': (24.0, -10.0, -24.0), 'left_forearm': (30.0, 0.0, 0.0),
         'right_leg': (30.0, 0.0, 6.0), 'right_shin': (-40.0, 0.0, 0.0), 'left_leg': (-6.0, 0.0, -6.0), 'left_shin': (-34.0, 0.0, 0.0)}
WIND, HIT, THRU, LEN = 0.08, 0.2, 0.32, 0.5


def _all(keys):
    bones = {b: (0, 0, 0) for b in UPPER + LOWER}
    return [(k[0], {**bones, **k[1]}) + tuple(k[2:]) for k in keys]


def _blow(name, wind, hit, thru):
    keys = [(0, READY), (WIND, {**READY, **wind}, 'out'), (HIT, {**READY, **hit}, 'linear'), (THRU, {**READY, **thru}, 'out'),
            (LEN, READY, 'inout')]
    feet = (Feet().step('left', 0.0, 0.01, -2.4, -3.0, 10, lift=0.0).step('right', 0.0, 0.01, 2.2, 2.0, -20, lift=0.0))
    feet.start = {'right': (2.2, 2.0, -20.0), 'left': (-2.4, -3.0, 10.0)}
    return bake(name, LEN, design(name, LEN, _all(keys)), feet, extra=(HIT,), level=False)


def ts_slash():
    """Up over the right shoulder, down across to the left hip."""
    return _blow('ts_slash',
                 {'right_arm': (165.0, 10.0, -20.0), 'right_forearm': (20.0, 0.0, 0.0), 'torso': (0.0, 22.0, 0.0), 'chest': (-4.0, 0.0, 0.0),
                  'head': (4.0, -18.0, 0.0), 'body@p': (0.0, -1.0, 0.4)},
                 {'right_arm': (70.0, -30.0, 30.0), 'right_forearm': (8.0, 0.0, 0.0), 'torso': (0.0, -26.0, 0.0), 'chest': (-22.0, 0.0, 0.0),
                  'head': (16.0, 22.0, 0.0), 'body@p': (0.0, -2.6, -1.4)},
                 {'right_arm': (34.0, -40.0, 34.0), 'right_forearm': (10.0, 0.0, 0.0), 'torso': (0.0, -32.0, 0.0), 'chest': (-20.0, 0.0, 0.0),
                  'head': (16.0, 28.0, 0.0), 'body@p': (0.0, -2.6, -1.6)})


def ts_thrust():
    """The blade drawn back level at the hip, a lunge, the arm and the blade straight out."""
    return _blow('ts_thrust',
                 {'right_arm': (40.0, 30.0, 26.0), 'right_forearm': (92.0, 0.0, 0.0), 'torso': (0.0, 30.0, 0.0), 'head': (8.0, -26.0, 0.0),
                  'right_item_sword': (0.0, -84.0, 0.0), 'body@p': (0.0, -2.2, 1.0)},
                 {'right_arm': (88.0, 2.0, 2.0), 'right_forearm': (0.0, 0.0, 0.0), 'torso': (0.0, -12.0, 0.0), 'chest': (-18.0, 0.0, 0.0),
                  'head': (14.0, 10.0, 0.0), 'right_item_sword': (0.0, -84.0, 0.0), 'body@p': (0.0, -2.8, -3.2),
                  'left_arm': (10.0, 0.0, -50.0), 'left_forearm': (10.0, 0.0, 0.0)},
                 {'right_arm': (84.0, 2.0, 2.0), 'right_forearm': (4.0, 0.0, 0.0), 'torso': (0.0, -10.0, 0.0), 'chest': (-16.0, 0.0, 0.0),
                  'head': (14.0, 10.0, 0.0), 'right_item_sword': (0.0, -84.0, 0.0), 'body@p': (0.0, -2.8, -3.0),
                  'left_arm': (14.0, 0.0, -46.0), 'left_forearm': (12.0, 0.0, 0.0)})


def ts_back():
    """Wound to the right, the blade swept level across its back from right to left."""
    return _blow('ts_back',
                 {'right_arm': (84.0, 70.0, 40.0), 'right_forearm': (20.0, 0.0, 0.0), 'torso': (0.0, 40.0, 0.0), 'head': (6.0, -36.0, 0.0),
                  'body@p': (0.0, -1.6, 0.6)},
                 {'right_arm': (86.0, -40.0, -6.0), 'right_forearm': (6.0, 0.0, 0.0), 'torso': (0.0, -30.0, 0.0), 'chest': (-16.0, 0.0, 0.0),
                  'head': (12.0, 26.0, 0.0), 'body@p': (0.0, -2.4, -1.2)},
                 {'right_arm': (80.0, -62.0, -10.0), 'right_forearm': (10.0, 0.0, 0.0), 'torso': (0.0, -40.0, 0.0), 'chest': (-14.0, 0.0, 0.0),
                  'head': (12.0, 34.0, 0.0), 'body@p': (0.0, -2.4, -1.2)})


def ts_belly():
    """Down on the knees, the blade rising from the right hip across into the belly."""
    return _blow('ts_belly',
                 {'right_arm': (12.0, 40.0, 50.0), 'right_forearm': (30.0, 0.0, 0.0), 'torso': (0.0, 30.0, 0.0), 'head': (16.0, -24.0, 0.0),
                  'body@p': (0.0, -3.4, 0.4)},
                 {'right_arm': (96.0, -28.0, -10.0), 'right_forearm': (10.0, 0.0, 0.0), 'torso': (0.0, -22.0, 0.0), 'chest': (-20.0, 0.0, 0.0),
                  'head': (6.0, 18.0, 0.0), 'body@p': (0.0, -3.6, -1.6)},
                 {'right_arm': (118.0, -34.0, -12.0), 'right_forearm': (10.0, 0.0, 0.0), 'torso': (0.0, -28.0, 0.0), 'chest': (-14.0, 0.0, 0.0),
                  'head': (2.0, 22.0, 0.0), 'body@p': (0.0, -3.2, -1.6)})


def ts_yawn():
    """Up out of the crouch, the sword lowered in the right hand; the left hand to the mouth, the head back in a long yawn
    (0.4-1.3 s), the hand down, at ease."""
    LEN = 2.0
    up = {'right_arm': (14.0, 0.0, 8.0), 'right_forearm': (16.0, 0.0, 0.0), 'left_arm': (6.0, 0.0, -6.0), 'left_forearm': (10.0, 0.0, 0.0),
          'head': (4.0, 0.0, 0.0), 'body@p': (0.0, -0.2, 0.0)}
    mouth = {**up, 'left_arm': (112.0, 34.0, -6.0), 'left_forearm': (118.0, 0.0, 0.0), 'head': (-24.0, 0.0, 6.0), 'chest': (10.0, 0.0, 0.0),
             'torso': (0.0, 6.0, 0.0), 'body@p': (0.0, 0.2, 0.4)}
    deep = {**mouth, 'head': (-30.0, 4.0, 8.0), 'chest': (12.0, 0.0, 0.0), 'right_arm': (18.0, 0.0, 14.0)}
    keys = [(0, READY), (0.3, up, 'inout'), (0.5, mouth, 'inout'), (1.05, deep, 'inout'), (1.35, mouth, 'inout'), (1.6, up, 'inout'),
            (LEN, {}, 'inout')]
    feet = Feet()
    feet.start = {'right': (2.2, 2.0, -20.0), 'left': (-2.4, -3.0, 10.0)}
    feet.step('right', 0.1, 0.35, 1.9, 0.0, 0, lift=1.0).step('left', 0.2, 0.45, -1.9, 0.0, 0, lift=1.0)
    return bake('ts_yawn', LEN, design('ts_yawn', LEN, _all(keys)), feet, level=False)


def made():
    a = [ts_stance(), ts_slash(), ts_thrust(), ts_back(), ts_belly(), ts_yawn()]
    for x in a:
        kit.soften(x, round9.SOFTEN, round9.LEGS)
    return a


def main():
    import importlib.util
    import json
    spec = importlib.util.spec_from_file_location('refine_29', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'refine.py'))
    refine = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(refine)
    with open(SRC, encoding='utf-8') as f:
        model = json.load(f)
    model['name'] = 'ninja_2026-11-16'
    refine.write_set(model, made(), DST)


if __name__ == '__main__':
    main()
