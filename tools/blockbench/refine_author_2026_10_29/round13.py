"""The author, after 0.9.1: the chain finisher.

"Several weak enemies about 5 blocks from each other: a double or a triple finisher: the sword in hand, Ctrl + W + Space +
LMB: he leaps onto the shoulders of the enemy from behind (under 50 % health) and drives the sword into its head, right in
the middle, as on landing with the sword; then, if there is another one under 50 % near, he leaps over onto it turning
180-360 degrees as its look and place need, and so on up to 5; when he cannot go on or none are left he does a back flip,
lands and walks on; the camera from behind as for Ctrl + W + Space + R."

The blade's poses are the author's own landing with the sword (plunge_fall: held point down overhead; plunge_land: driven
in), 2026-10-29. The mechanic carries the player (Chain), the animations stay where they are:

    chain_leap   (0.8 s) down, up off both feet, the sword up point down in both hands, the knees tucked; down astride the
                 mob's shoulders (LAND 0.45 s), the blade into its head (STAB 0.5 s: Chain.KILL, tick 10), held.
    chain_hop    (0.8 s) the blade out, off the shoulders, the same flight and stab onto the next.
    chain_flip   (0.9 s) the blade out, off backwards in a whole back flip, down on the feet (LAND 0.6 s), up.

    python3 tools/blockbench/refine_author_2026_10_29/round13.py
"""
import os

import kit
import moves
import round9
from kit import L
from round9 import Feet, design, bake

DST = os.path.join(kit.BB, 'refined', 'chain_2026-11-08.bbmodel')
REF29B = os.path.join(kit.BB, 'refined', 'author_2026-10-29b.bbmodel')
UPPER = ('chest', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm')


def _plunge(name, t):
    p = moves._sampled(REF29B, name, t)
    return {k: p[k] for k in UPPER if k in p}


HELD = _plunge('plunge_fall', 0.2)          # the sword up, point down, both hands
STAB = _plunge('plunge_land', 0.1)          # driven in
IN = _plunge('plunge_land', 0.3)            # in, held
ZERO = {b: (0, 0, 0) for b in ('body', 'torso', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm',
                               'right_leg', 'right_shin', 'left_leg', 'left_shin')}
CROUCH = {'body@p': (0, -3.0, 0), 'right_leg': (34, 0, 4), 'right_shin': (-62, 0, 0), 'left_leg': (34, 0, -4), 'left_shin': (-62, 0, 0)}
TUCK = {'body': (-6, 0, 0), 'right_leg': (50, 0, 6), 'right_shin': (-90, 0, 0), 'left_leg': (40, 0, -6), 'left_shin': (-80, 0, 0)}
#: Astride the shoulders: the knees out to either side of the head, low.
ASTRIDE = {'body@p': (0, -2.0, 0), 'body': (-4, 0, 0), 'right_leg': (28, 0, 28), 'right_shin': (-70, 0, 0),
           'left_leg': (28, 0, -28), 'left_shin': (-70, 0, 0)}
LAND, STAB_T, LEN = 0.45, 0.5, 0.8


def _flight(start, name):
    keys = [(0, start), (0.08, {**start, **CROUCH, **(HELD if start is not ZERO else {})}, 'inout'),
            (0.16, {**ZERO, **TUCK, **HELD}, 'out'), (0.38, {**ZERO, **TUCK, **HELD}),
            (LAND, {**ZERO, **ASTRIDE, **HELD}, 'in'), (STAB_T, {**ZERO, **ASTRIDE, 'body@p': (0, -3.0, 0), **STAB}, 'out'),
            (0.62, {**ZERO, **ASTRIDE, **IN}, 'inout'), (LEN, {**ZERO, **ASTRIDE, **IN})]
    a = bake(name, LEN, design(name, LEN, keys), Feet().designed(0.0, LEN + 1), extra=(LAND, STAB_T), level=False)
    return a


def chain_leap():
    """Down, up off both feet with the sword going up point down; astride the shoulders; the blade into the head."""
    return _flight(ZERO, 'chain_leap')


def chain_hop():
    """The blade out of the head, up off the shoulders, onto the next one's and into its head."""
    return _flight({**ZERO, **ASTRIDE, **IN}, 'chain_hop')


FL_OFF, FL_LAND, FL_LEN = 0.12, 0.6, 0.9


def chain_flip():
    """The blade out, off the shoulders backwards: a whole back flip, tucked, the sword held in; down onto the feet, up."""
    start = {**ZERO, **ASTRIDE, **IN}
    keys = [(0, start), (0.08, {**start, **HELD}, 'out'), (FL_OFF, {**ZERO, **CROUCH, **HELD}),
            (0.25, {**ZERO, **TUCK, **HELD, 'body': (90, 0, 0)}), (0.4, {**ZERO, **TUCK, **HELD, 'body': (230, 0, 0)}),
            (0.52, {**ZERO, **TUCK, **HELD, 'body': (330, 0, 0)}), (FL_LAND, {**ZERO, **CROUCH, **HELD, 'body': (360, 0, 0)}, 'in'),
            (0.75, {**ZERO, **CROUCH, 'body@p': (0, -1.5, 0), 'body': (360, 0, 0)}, 'inout'), (FL_LEN, {**ZERO, 'body': (360, 0, 0)}, 'inout')]
    return bake('chain_flip', FL_LEN, design('chain_flip', FL_LEN, keys), Feet().designed(0.0, FL_LEN + 1), extra=(FL_LAND,), level=False)


def made():
    a = [chain_leap(), chain_hop(), chain_flip()]
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
    model['name'] = 'chain_2026-11-08'
    refine.write_set(model, made(), DST)


if __name__ == '__main__':
    main()
