"""The author, 2026-11-20: the axe's ultimate, "the axe's shadow" (AxeShadow in the mod), on the right button and G held.

"He makes one turn about himself and is gone; the creatures, taken aback, look about, frozen, puzzled; then he comes out
behind one of them, one blow from above (it falls); a second or so later at another's side, as a stealthy assassin, and so
on until all are dead; after each a lingering image of him, and each blow a wave in the air."

    as_spin    (0.7 s)  low, the axe out at arm's length, one whole turn on the spot (0.1-0.55 s), down into a crouch: gone.
    as_hidden  (1 s)    the crouch held (not seen: he is gone).
    as_chop    (0.6 s)  behind it, the axe high in both hands, down on it from above (AS_HIT, 0.25 s), the follow-through low.
    as_side    (0.6 s)  at its side, low, the axe drawn back level, swept across into it (0.25 s).
    as_hook    (0.6 s)  behind it, the axe low by the right knee, hooked up through it from below (0.25 s).

The axe's place is keyed in every pose (an all-zero item is left out of the file and the game holds it its own way).

    python3 tools/blockbench/refine_author_2026_10_29/round24.py   (writes refined/axe_shadow_2026-11-20.bbmodel)
"""
import os

import kit
import round9
from round9 import Feet, design, bake

DST = os.path.join(kit.BB, 'refined', 'axe_shadow_2026-11-20.bbmodel')
UPPER = ('torso', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm', 'right_item_sword')
LOWER = ('left_leg', 'left_shin', 'right_leg', 'right_shin')
ITEM = {'right_item_sword': (0.01, 0.0, 0.0)}
#: The low crouch he is gone in and comes out of.
CROUCH = {**ITEM, 'body@p': (0.0, -4.0, 0.0), 'chest': (-26.0, 0.0, 0.0), 'head': (20.0, 0.0, 0.0),
          'right_arm': (20.0, 0.0, 16.0), 'right_forearm': (30.0, 0.0, 0.0), 'left_arm': (30.0, 0.0, -20.0), 'left_forearm': (40.0, 0.0, 0.0),
          'right_leg': (50.0, 0.0, 8.0), 'right_shin': (-70.0, 0.0, 0.0), 'left_leg': (-10.0, 0.0, -8.0), 'left_shin': (-70.0, 0.0, 0.0)}
HIT, THRU, LEN = 0.25, 0.4, 0.6


def _all(keys):
    bones = {b: (0, 0, 0) for b in UPPER + LOWER}
    return [(k[0], {**bones, **ITEM, **k[1]}) + tuple(k[2:]) for k in keys]


def as_spin():
    """Low, the axe out at arm's length, one whole turn to the left on the spot, down into the crouch."""
    L = 0.7
    out = {**ITEM, 'body@p': (0.0, -1.6, 0.0), 'chest': (-10.0, 0.0, 0.0), 'head': (8.0, 0.0, 0.0),
           'right_arm': (84.0, 0.0, 72.0), 'right_forearm': (6.0, 0.0, 0.0), 'left_arm': (50.0, -20.0, -30.0), 'left_forearm': (60.0, 0.0, 0.0),
           'right_leg': (20.0, 0.0, 6.0), 'right_shin': (-30.0, 0.0, 0.0), 'left_leg': (-10.0, 0.0, -6.0), 'left_shin': (-24.0, 0.0, 0.0)}
    keys = [(0, {**ITEM}), (0.1, {**out, 'body': (0.0, 0.0, 0.0)}, 'out'), (0.55, {**out, 'body': (0.0, 360.0, 0.0)}, 'inout'),
            (L, {**CROUCH, 'body': (0.0, 360.0, 0.0)}, 'out')]
    feet = Feet().designed(-1.0, L + 1.0)
    return bake('as_spin', L, design('as_spin', L, _all(keys)), feet, level=False)


def as_hidden():
    L = 1.0
    keys = [(0, CROUCH), (L, CROUCH)]
    return bake('as_hidden', L, design('as_hidden', L, _all(keys)), Feet().designed(-1.0, L + 1.0), level=False)


def _strike(name, wind, hit, thru):
    keys = [(0, wind), (HIT, hit, 'linear'), (THRU, thru, 'out'), (LEN, thru)]
    return bake(name, LEN, design(name, LEN, _all(keys)), Feet().designed(-1.0, LEN + 1.0), extra=(HIT,), level=False)


def as_chop():
    """Behind it, the axe high in both hands; down on it from above; the follow-through low."""
    stand = {**ITEM, 'right_leg': (24.0, 0.0, 6.0), 'right_shin': (-20.0, 0.0, 0.0), 'left_leg': (-16.0, 0.0, -6.0), 'left_shin': (-16.0, 0.0, 0.0)}
    return _strike('as_chop',
                   {**stand, 'body@p': (0.0, -0.4, 0.4), 'chest': (8.0, 0.0, 0.0), 'head': (-4.0, 0.0, 0.0),
                    'right_arm': (172.0, 10.0, -10.0), 'right_forearm': (30.0, 0.0, 0.0), 'left_arm': (168.0, -16.0, 12.0), 'left_forearm': (40.0, 0.0, 0.0)},
                   {**stand, 'body@p': (0.0, -2.6, -1.4), 'chest': (-30.0, 0.0, 0.0), 'head': (24.0, 0.0, 0.0),
                    'right_arm': (70.0, 10.0, 4.0), 'right_forearm': (10.0, 0.0, 0.0), 'left_arm': (70.0, -22.0, -4.0), 'left_forearm': (24.0, 0.0, 0.0)},
                   {**stand, 'body@p': (0.0, -3.0, -1.6), 'chest': (-34.0, 0.0, 0.0), 'head': (26.0, 0.0, 0.0),
                    'right_arm': (40.0, 10.0, 4.0), 'right_forearm': (10.0, 0.0, 0.0), 'left_arm': (40.0, -22.0, -4.0), 'left_forearm': (24.0, 0.0, 0.0)})


def as_side():
    """At its side, low, the axe drawn back level; swept across into it."""
    low = {**ITEM, 'body@p': (0.0, -3.0, 0.0), 'right_leg': (40.0, 0.0, 10.0), 'right_shin': (-60.0, 0.0, 0.0),
           'left_leg': (-14.0, 0.0, -10.0), 'left_shin': (-50.0, 0.0, 0.0)}
    return _strike('as_side',
                   {**low, 'torso': (0.0, 50.0, 0.0), 'chest': (-12.0, 0.0, 0.0), 'head': (8.0, -44.0, 0.0),
                    'right_arm': (84.0, 76.0, 40.0), 'right_forearm': (16.0, 0.0, 0.0), 'left_arm': (40.0, 0.0, -36.0), 'left_forearm': (30.0, 0.0, 0.0)},
                   {**low, 'torso': (0.0, -20.0, 0.0), 'chest': (-16.0, 0.0, 0.0), 'head': (10.0, 18.0, 0.0),
                    'right_arm': (86.0, -36.0, -6.0), 'right_forearm': (6.0, 0.0, 0.0), 'left_arm': (20.0, 0.0, -56.0), 'left_forearm': (14.0, 0.0, 0.0)},
                   {**low, 'torso': (0.0, -40.0, 0.0), 'chest': (-14.0, 0.0, 0.0), 'head': (10.0, 34.0, 0.0),
                    'right_arm': (80.0, -66.0, -10.0), 'right_forearm': (10.0, 0.0, 0.0), 'left_arm': (16.0, 0.0, -60.0), 'left_forearm': (12.0, 0.0, 0.0)})


def as_hook():
    """Behind it, the axe low by the right knee; hooked up through it from below."""
    base = {**ITEM, 'body@p': (0.0, -2.0, 0.0), 'right_leg': (30.0, 0.0, 6.0), 'right_shin': (-44.0, 0.0, 0.0),
            'left_leg': (-12.0, 0.0, -6.0), 'left_shin': (-36.0, 0.0, 0.0)}
    return _strike('as_hook',
                   {**base, 'torso': (0.0, 30.0, 0.0), 'chest': (-24.0, 0.0, 0.0), 'head': (16.0, -20.0, 0.0),
                    'right_arm': (-20.0, 20.0, 30.0), 'right_forearm': (20.0, 0.0, 0.0), 'left_arm': (30.0, 0.0, -30.0), 'left_forearm': (40.0, 0.0, 0.0)},
                   {**base, 'torso': (0.0, -16.0, 0.0), 'chest': (-6.0, 0.0, 0.0), 'head': (2.0, 14.0, 0.0), 'body@p': (0.0, -1.0, -0.8),
                    'right_arm': (110.0, -20.0, -10.0), 'right_forearm': (10.0, 0.0, 0.0), 'left_arm': (20.0, 0.0, -50.0), 'left_forearm': (20.0, 0.0, 0.0)},
                   {**base, 'torso': (0.0, -24.0, 0.0), 'chest': (2.0, 0.0, 0.0), 'head': (-4.0, 20.0, 0.0), 'body@p': (0.0, -0.6, -1.0),
                    'right_arm': (150.0, -26.0, -14.0), 'right_forearm': (12.0, 0.0, 0.0), 'left_arm': (16.0, 0.0, -56.0), 'left_forearm': (16.0, 0.0, 0.0)})


def made():
    a = [as_spin(), as_hidden(), as_chop(), as_side(), as_hook()]
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
    model['name'] = 'axe_shadow_2026-11-20'
    refine.write_set(model, made(), DST)


if __name__ == '__main__':
    main()
