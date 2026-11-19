"""The author, 2026-11-19: the time stop (TimeStop in the mod), its stance his samurai_mode (author_2026-11-16_ninja.bbmodel;
19.11: "the samurai stance from samurai_mode, a hundred per cent", at the start of the time stop).

"He takes a stance, looks about, 5+ enemies run at him, and he moves very fast, 0.5 s an enemy, a different blow at each:
from above along it with the sword, a thrust, from behind, into the belly, in turn, and ends where he began and with the
left hand yawns grandly, holding the sword in the right."

    ts_stance  (1.5 s)  (author, 19.11) the sword upright in both hands right in the middle before the face, its flat to
                        the front, the forehead bowed onto the blade; a breath in, held.
    6 blows    (0.35 s each, the hit at tick 3; 19.11: "1.5 times faster", "the blows and the look of the player varied"):
      ts_slash   standing, from over the right shoulder down across to the left hip;
      ts_thrust  a deep lunge, the arm and the blade straight out;
      ts_back    upright, wound to the right, swept level across its back;
      ts_belly   down on a knee, the blade rising from the right hip into the belly;
      ts_leap    in the air above it, both hands, straight down;
      ts_spin    a whole turn on the spot, the blade out level at arm's length.
                 Each starts in its own wind-up (the server puts the player at the mob between them: he appears in it)
                 and ends in its own follow-through, so no two look alike and none ends in a common pose.
    ts_brake   (1.8 s)  (19.11, instead of the yawn) back where he began, skidding to a stop as on a skateboard: side-on,
                        low, leaning back, the feet scraping, the arms out; up, the blade flicked down at his side.

His file is not changed.

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
    """(19.11, the second try: "the sword and the hands not quite in the middle; he should lean his head on the sword, the
    blade turned flat outwards, and as if hold his breath, then the teleports") Up into it: the sword upright in both hands
    right in the middle before the face, its flat to the front, the forehead bowed onto the blade; a breath in (the
    shoulders and the chest up), and held, still."""
    LEN = 1.5
    hold = {'right_arm': (10.0, 47.0, 0.0), 'right_forearm': (80.0, 0.0, 0.0), 'left_arm': (4.0, -47.0, 0.0),
            'left_forearm': (80.0, 0.0, 0.0), 'head': (-17.0, 0.0, 0.0), 'chest': (-12.0, 0.0, 0.0), 'body@p': (0.0, -0.3, 0.0)}
    breath = {**hold, 'chest': (-9.0, 0.0, 0.0), 'head': (-19.0, 0.0, 0.0), 'right_arm': (12.0, 47.0, 0.0), 'left_arm': (6.0, -47.0, 0.0),
              'body@p': (0.0, 0.1, 0.0)}
    keys = [(0, {}), (0.35, hold, 'inout'), (0.75, breath, 'inout'), (LEN, breath)]
    feet = Feet()
    return bake('ts_stance', LEN, design('ts_stance', LEN, _all(keys)), feet, level=False)


HIT, THRU, LEN = 0.15, 0.27, 0.35
#: The stances the blows are made from (the look of the player varied, author).
LOW = {'body@p': (0.0, -2.0, 0.0), 'chest': (-14.0, 0.0, 0.0), 'head': (12.0, 0.0, 0.0),
       'left_arm': (24.0, -10.0, -24.0), 'left_forearm': (30.0, 0.0, 0.0),
       'right_leg': (30.0, 0.0, 6.0), 'right_shin': (-40.0, 0.0, 0.0), 'left_leg': (-6.0, 0.0, -6.0), 'left_shin': (-34.0, 0.0, 0.0)}
UP = {'body@p': (0.0, -0.6, 0.0), 'chest': (-6.0, 0.0, 0.0), 'head': (6.0, 0.0, 0.0),
      'left_arm': (14.0, 0.0, -20.0), 'left_forearm': (24.0, 0.0, 0.0),
      'right_leg': (14.0, 0.0, 4.0), 'right_shin': (-16.0, 0.0, 0.0), 'left_leg': (-8.0, 0.0, -4.0), 'left_shin': (-14.0, 0.0, 0.0)}


def _all(keys):
    bones = {b: (0, 0, 0) for b in UPPER + LOWER}
    return [(k[0], {**bones, **k[1]}) + tuple(k[2:]) for k in keys]


def _blow(name, base, wind, hit, thru, feet=None, lift=None):
    """Its wind-up at once, the blade through it at HIT, the follow-through held to the end."""
    keys = [(0, {**base, **wind}), (HIT, {**base, **hit}, 'linear'), (THRU, {**base, **thru}, 'out'), (LEN, {**base, **thru})]
    if feet is None:
        feet = Feet()
        feet.start = {'right': (2.2, 2.0, -20.0), 'left': (-2.4, -3.0, 10.0)}
    return bake(name, LEN, design(name, LEN, _all(keys)), feet, extra=(HIT,), level=False, lift=lift)


def ts_slash():
    """Standing, from over the right shoulder down across to the left hip."""
    return _blow('ts_slash', UP,
                 {'right_arm': (165.0, 10.0, -20.0), 'right_forearm': (20.0, 0.0, 0.0), 'torso': (0.0, 24.0, 0.0), 'chest': (2.0, 0.0, 0.0),
                  'head': (4.0, -18.0, 0.0), 'body@p': (0.0, 0.0, 0.4)},
                 {'right_arm': (70.0, -30.0, 30.0), 'right_forearm': (8.0, 0.0, 0.0), 'torso': (0.0, -26.0, 0.0), 'chest': (-20.0, 0.0, 0.0),
                  'head': (14.0, 22.0, 0.0), 'body@p': (0.0, -1.6, -1.4)},
                 {'right_arm': (30.0, -40.0, 34.0), 'right_forearm': (10.0, 0.0, 0.0), 'torso': (0.0, -34.0, 0.0), 'chest': (-22.0, 0.0, 0.0),
                  'head': (14.0, 30.0, 0.0), 'body@p': (0.0, -1.8, -1.6)})


def ts_thrust():
    """A deep lunge, the blade drawn back level at the hip, then the arm and the blade straight out."""
    lunge = {**LOW, 'right_leg': (50.0, 0.0, 4.0), 'right_shin': (-50.0, 0.0, 0.0), 'left_leg': (-30.0, 0.0, -4.0), 'left_shin': (-10.0, 0.0, 0.0)}
    feet = Feet()
    feet.start = {'right': (2.2, 4.6, -10.0), 'left': (-2.4, -5.0, 14.0)}
    return _blow('ts_thrust', lunge,
                 {'right_arm': (40.0, 30.0, 26.0), 'right_forearm': (92.0, 0.0, 0.0), 'torso': (0.0, 30.0, 0.0), 'head': (8.0, -26.0, 0.0),
                  'right_item_sword': (0.0, -84.0, 0.0), 'body@p': (0.0, -2.4, 1.0)},
                 {'right_arm': (88.0, 2.0, 2.0), 'right_forearm': (0.0, 0.0, 0.0), 'torso': (0.0, -12.0, 0.0), 'chest': (-18.0, 0.0, 0.0),
                  'head': (14.0, 10.0, 0.0), 'right_item_sword': (0.0, -84.0, 0.0), 'body@p': (0.0, -3.2, -3.0),
                  'left_arm': (10.0, 0.0, -50.0), 'left_forearm': (10.0, 0.0, 0.0)},
                 {'right_arm': (84.0, 2.0, 2.0), 'right_forearm': (4.0, 0.0, 0.0), 'torso': (0.0, -10.0, 0.0), 'chest': (-16.0, 0.0, 0.0),
                  'head': (14.0, 10.0, 0.0), 'right_item_sword': (0.0, -84.0, 0.0), 'body@p': (0.0, -3.2, -2.8),
                  'left_arm': (14.0, 0.0, -60.0), 'left_forearm': (12.0, 0.0, 0.0)}, feet)


def ts_back():
    """Upright, wound to the right, the blade swept level across its back from right to left."""
    return _blow('ts_back', UP,
                 {'right_arm': (84.0, 70.0, 40.0), 'right_forearm': (20.0, 0.0, 0.0), 'torso': (0.0, 44.0, 0.0), 'head': (4.0, -38.0, 0.0),
                  'body@p': (0.0, -0.4, 0.6), 'left_arm': (40.0, 0.0, -30.0)},
                 {'right_arm': (86.0, -40.0, -6.0), 'right_forearm': (6.0, 0.0, 0.0), 'torso': (0.0, -30.0, 0.0), 'chest': (-10.0, 0.0, 0.0),
                  'head': (8.0, 26.0, 0.0), 'body@p': (0.0, -0.8, -1.0), 'left_arm': (20.0, 0.0, -50.0)},
                 {'right_arm': (80.0, -66.0, -10.0), 'right_forearm': (10.0, 0.0, 0.0), 'torso': (0.0, -44.0, 0.0), 'chest': (-8.0, 0.0, 0.0),
                  'head': (8.0, 36.0, 0.0), 'body@p': (0.0, -0.8, -1.0), 'left_arm': (16.0, 0.0, -56.0)})


def ts_belly():
    """Down on the left knee, the blade rising from the right hip across into the belly."""
    knee = {**LOW, 'body@p': (0.0, -5.4, 0.0), 'right_leg': (80.0, 0.0, 4.0), 'right_shin': (-84.0, 0.0, 0.0),
            'left_leg': (-4.0, 0.0, -4.0), 'left_shin': (-96.0, 0.0, 0.0)}
    feet = Feet().designed(-1.0, LEN + 1.0)
    return _blow('ts_belly', knee,
                 {'right_arm': (12.0, 40.0, 50.0), 'right_forearm': (30.0, 0.0, 0.0), 'torso': (0.0, 30.0, 0.0), 'head': (16.0, -24.0, 0.0)},
                 {'right_arm': (96.0, -28.0, -10.0), 'right_forearm': (10.0, 0.0, 0.0), 'torso': (0.0, -22.0, 0.0), 'chest': (-16.0, 0.0, 0.0),
                  'head': (6.0, 18.0, 0.0)},
                 {'right_arm': (124.0, -34.0, -12.0), 'right_forearm': (10.0, 0.0, 0.0), 'torso': (0.0, -28.0, 0.0), 'chest': (-8.0, 0.0, 0.0),
                  'head': (0.0, 22.0, 0.0)}, feet)


def ts_leap():
    """In the air above it, the knees drawn up, both hands high; down with it onto the ground, the blade straight down."""
    air = {'right_leg': (60.0, 0.0, 6.0), 'right_shin': (-90.0, 0.0, 0.0), 'left_leg': (40.0, 0.0, -6.0), 'left_shin': (-100.0, 0.0, 0.0)}
    land = {'right_leg': (40.0, 0.0, 6.0), 'right_shin': (-70.0, 0.0, 0.0), 'left_leg': (-10.0, 0.0, -6.0), 'left_shin': (-60.0, 0.0, 0.0)}
    feet = Feet().designed(-1.0, LEN + 1.0)
    return _blow('ts_leap', {},
                 {**air, 'right_arm': (170.0, 6.0, -6.0), 'right_forearm': (30.0, 0.0, 0.0), 'left_arm': (168.0, -10.0, 8.0),
                  'left_forearm': (40.0, 0.0, 0.0), 'chest': (8.0, 0.0, 0.0), 'head': (-10.0, 0.0, 0.0), 'body@p': (0.0, 6.0, 0.0)},
                 {**land, 'right_arm': (66.0, 14.0, 4.0), 'right_forearm': (14.0, 0.0, 0.0), 'left_arm': (66.0, -24.0, -4.0),
                  'left_forearm': (24.0, 0.0, 0.0), 'chest': (-24.0, 0.0, 0.0), 'head': (16.0, 0.0, 0.0), 'body@p': (0.0, -3.4, -1.0)},
                 {**land, 'right_arm': (40.0, 14.0, 4.0), 'right_forearm': (14.0, 0.0, 0.0), 'left_arm': (40.0, -24.0, -4.0),
                  'left_forearm': (24.0, 0.0, 0.0), 'chest': (-26.0, 0.0, 0.0), 'head': (18.0, 0.0, 0.0), 'body@p': (0.0, -3.8, -1.0)}, feet,
                 lift=lambda t: 10.0 * (1 - kit.smooth(min(1.0, t / HIT))))


def ts_spin():
    """A whole turn on the spot (to the left), the blade out level at arm's length, the left arm in to the chest."""
    out = {'right_arm': (86.0, 0.0, 70.0), 'right_forearm': (6.0, 0.0, 0.0), 'left_arm': (60.0, -30.0, 10.0), 'left_forearm': (90.0, 0.0, 0.0),
           'right_item_sword': (0.0, -60.0, 0.0), 'chest': (-8.0, 0.0, 0.0), 'head': (6.0, 0.0, 0.0), 'body@p': (0.0, -1.2, 0.0)}
    keys = [(0, {**UP, **out, 'body': (0.0, -60.0, 0.0)}), (HIT, {**UP, **out, 'body': (0.0, 150.0, 0.0)}, 'linear'),
            (THRU, {**UP, **out, 'body': (0.0, 300.0, 0.0), 'right_arm': (80.0, 0.0, 60.0)}, 'out'),
            (LEN, {**UP, **out, 'body': (0.0, 300.0, 0.0), 'right_arm': (80.0, 0.0, 60.0)})]
    feet = Feet().designed(-1.0, LEN + 1.0)
    return bake('ts_spin', LEN, design('ts_spin', LEN, _all(keys)), feet, extra=(HIT,), level=False)


def ts_brake():
    """(19.11: "at the end better that he teleports and brakes with his feet as on a skateboard, hard, instead of the yawn")
    He comes out of the last flicker already skidding (the server gives him the slide, TimeStop): the body turned side-on to
    the way he goes, low, leaning back against it, the feet wide and scraping, the left arm out for balance, the blade
    trailing low behind; the skid dies (0.55 s), he rises and turns back to the front, the blade flicked down at his side."""
    LEN = 1.8
    skid = {'body': (8.0, 72.0, 0.0), 'body@p': (0.0, -5.0, 0.0), 'chest': (6.0, 0.0, 0.0), 'head': (-4.0, -60.0, 0.0),
            'left_leg': (24.0, 0.0, -34.0), 'left_shin': (-18.0, 0.0, 0.0), 'right_leg': (-14.0, 0.0, 30.0), 'right_shin': (-78.0, 0.0, 0.0),
            'left_arm': (50.0, 0.0, -72.0), 'left_forearm': (14.0, 0.0, 0.0),
            'right_arm': (-34.0, 0.0, 40.0), 'right_forearm': (10.0, 0.0, 0.0)}
    shake = {**skid, 'body@p': (0.0, -5.3, 0.0), 'chest': (8.0, 0.0, 0.0), 'left_arm': (56.0, 0.0, -76.0)}
    stop = {**skid, 'body': (2.0, 66.0, 0.0), 'body@p': (0.0, -4.0, 0.0), 'chest': (-4.0, 0.0, 0.0), 'head': (4.0, -56.0, 0.0),
            'left_arm': (30.0, 0.0, -50.0)}
    rise = {'body': (0.0, 20.0, 0.0), 'body@p': (0.0, -0.8, 0.0), 'chest': (-2.0, 0.0, 0.0), 'head': (2.0, -18.0, 0.0),
            'left_leg': (4.0, 0.0, -10.0), 'left_shin': (-8.0, 0.0, 0.0), 'right_leg': (-4.0, 0.0, 10.0), 'right_shin': (-10.0, 0.0, 0.0),
            'left_arm': (8.0, 0.0, -14.0), 'left_forearm': (10.0, 0.0, 0.0), 'right_arm': (24.0, 0.0, 30.0), 'right_forearm': (20.0, 0.0, 0.0)}
    flick = {**rise, 'body': (0.0, 0.0, 0.0), 'body@p': (0.0, -0.2, 0.0), 'head': (6.0, 0.0, 0.0), 'left_leg': (0.0, 0.0, -4.0),
             'left_shin': (0.0, 0.0, 0.0), 'right_leg': (0.0, 0.0, 4.0), 'right_shin': (0.0, 0.0, 0.0),
             'right_arm': (6.0, 0.0, 22.0), 'right_forearm': (8.0, 0.0, 0.0)}
    keys = [(0, skid), (0.12, shake, 'linear'), (0.24, skid, 'linear'), (0.36, shake, 'linear'), (0.55, stop, 'out'),
            (0.95, rise, 'inout'), (1.25, flick, 'out'), (1.5, flick), (LEN, {}, 'inout')]
    feet = Feet().designed(-1.0, LEN + 1.0)
    return bake('ts_brake', LEN, design('ts_brake', LEN, _all(keys)), feet, level=False)


def made():
    a = [ts_stance(), ts_slash(), ts_thrust(), ts_back(), ts_belly(), ts_leap(), ts_spin(), ts_brake()]
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
