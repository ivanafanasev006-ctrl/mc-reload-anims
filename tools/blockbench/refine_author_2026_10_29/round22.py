"""The author, 2026-11-19: the sword block's answers (GuardAnswers in the mod; perks).

"The RMB block: if the enemy has under 25 % health he catches the hand of the enemy striking with its right hand on his
sword in 0.5 s and at once twists it behind its back in 1 s and kicks it, a shove; then the enemy stumbles, falls on its
face to the ground and dies, and the player stays where he was. When he just blocks a blow with the sword he kicks it
forward about 2 blocks with an animation. On a perfect block with the sword he catches the blow in the middle of the blade
and throws it far, 5 blocks, not with the foot: first the body goes back, as kinetic energy goes in a blow, then he shoves
forward with the body and the same sword with all his strength, 2 hearts. If 2 or more enemies are near or one shoots: on a
perfect block he takes the enemy's hand, turns and takes it in a hold, all fast, 1-2 s, as if he ran it through from
behind on the sword, the other hand holding it by the left shoulder; either it dies by itself or on RMB again he kicks it
off and it flies forward like a shot."

No draft; the poses start and end in the sword's guard (cross_guard, author_2026-10-27: the stance of the block), the stab
of the hold is the sword's finisher's (finisher_stab, author_2026-10-30, at 0.3 s). Times are the mod's (GuardAnswers):

    guard_kick     (0.65 s)  the right knee up, the foot out into it (KICK_HIT, tick 5), back, down, the guard again.
    guard_shove    (0.85 s)  the blade takes it (the body gives back, the left hand on the flat), then a step in and body
                             and blade drive it off (SHOVE_PUSH, tick 9), the follow-through, the guard.
    guard_finish   (2.3 s)   the left hand on its wrist (0.12 s), held (FIN_CATCH, tick 10), the arm twisted down and round
                             behind its back, the body over it (to 1.4 s), the kick into its back (FIN_KICK, tick 30), the
                             foot down, standing where he stood as it falls (FIN_KILL, tick 42).
    guard_grab     (1.1 s)   the wrist taken, pulled across as the shoulders turn (GRAB_TURN, tick 6), the hand onto its
                             left shoulder, the blade into its back (GRAB_STAB, tick 14), into the hold.
    hostage_hold   (1 s, loop)  the hold: the arms and the body above the hips only (the legs walk the vanilla way).
    hostage_kick   (0.75 s)  the blade drawn back out, the knee up, the foot into its back (THROW_HIT, tick 5), down.

    python3 tools/blockbench/refine_author_2026_10_29/round22.py   (writes refined/guard_2026-11-19.bbmodel)
"""
import os

import kit
import round9
from kit import L
from round9 import Feet, design, bake

DST = os.path.join(kit.BB, 'refined', 'guard_2026-11-19.bbmodel')

#: The guard (cross_guard at 0.3 s): the blade across in front, the left hand on its back.
GUARD = {'body@p': (0.0, -0.6, 0.0), 'chest': (-7.0, 0.0, 0.0), 'head': (4.0, 0.0, 0.0),
         'left_arm': (77.2, -7.9, -19.8), 'left_forearm': (45.4, 1.8, -4.3),
         'right_arm': (62.0, 30.0, 41.5), 'right_forearm': (24.0, 0.0, 0.0),
         'right_item_sword': (0.0, 0.0, -79.0), 'right_item_sword@p': (-1.0, 0.0, 0.0),
         'left_leg': (-5.9, 16.4, -6.2), 'left_shin': (-26.5, 0.0, 0.0), 'right_leg': (19.8, -12.2, 12.8), 'right_shin': (-27.0, 0.0, 0.0)}
#: The stab of the sword's finisher (finisher_stab at 0.3 s): the blade forward level into the body, the shoulders turned.
STAB = {'right_arm': (71.1, 1.6, 1.5), 'right_forearm': (17.6, 0.0, 0.0), 'right_item_sword': (0.0, -84.0, 0.0),
        'right_item_sword@p': (0.0, -6.0, 5.4), 'torso': (0.0, 15.4, 0.0), 'chest': (-10.7, 0.0, 0.0), 'head': (4.3, -8.7, 0.0)}
UPPER = ('torso', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm', 'right_item_sword')
LEGS_OF = {'right': ('right_leg', 'right_shin'), 'left': ('left_leg', 'left_shin')}
#: The kicking leg: the knee up (chambered) and out (the foot into it, nearly straight), the body leaning back over it.
CHAMBER = {'right_leg': (78.0, 0.0, 2.0), 'right_shin': (-100.0, 0.0, 0.0)}
KICKED = {'right_leg': (88.0, 0.0, 2.0), 'right_shin': (-8.0, 0.0, 0.0)}


class OneFoot(Feet):
    """Feet, one of them free (kicking: the leg as designed) while the other stays planted."""

    def __init__(self):
        super().__init__()
        self.start = {'right': (2.6, 1.2, -14.0), 'left': (-2.4, -1.4, 16.0)}     # the guard's stance
        self.loose = []

    def kick(self, side, t0, t1):
        self.loose.append((side, t0, t1))
        return self

    def at(self, t, side):
        if any(s == side and t0 < t < t1 for s, t0, t1 in self.loose):
            return None
        return super().at(t, side)


def _all(keys, extra=()):
    """Every bone the moves use keyed in every pose (unkeyed ones at rest)."""
    bones = {b: (0, 0, 0) for b in UPPER + ('left_leg', 'left_shin', 'right_leg', 'right_shin') + tuple(extra)}
    return [(k[0], {**bones, **k[1]}) + tuple(k[2:]) for k in keys]


def guard_kick():
    """From the guard the right knee comes up, the foot snaps out into the mob (tick 5), the body leaning back over the
    standing leg, the blade kept across; back to the knee, down, the guard."""
    T_UP, T_HIT, T_BACK, T_DOWN, LEN = 0.12, 0.25, 0.36, 0.5, 0.65
    up = {**GUARD, **CHAMBER, 'chest': (2.0, 0.0, 0.0), 'body@p': (0.0, -0.3, 0.6)}
    hit = {**GUARD, **KICKED, 'chest': (12.0, 0.0, 0.0), 'head': (-6.0, 0.0, 0.0), 'body@p': (0.0, -0.5, 1.2)}
    back = {**up}
    keys = [(0, GUARD), (T_UP, up, 'out'), (T_HIT, hit, 'out'), (T_BACK, back, 'inout'), (T_DOWN, GUARD, 'inout'), (LEN, GUARD)]
    feet = OneFoot().kick('right', 0.02, T_DOWN - 0.02)
    return bake('guard_kick', LEN, design('guard_kick', LEN, _all(keys)), feet, extra=(T_HIT,), level=False)


def guard_shove():
    """The blow taken mid-blade: the body gives back with it, the left hand on the flat; then the left foot steps in and
    body and blade drive it off together (tick 9), arms out; the follow-through, the guard."""
    T_TAKE, T_COIL, T_PUSH, T_THRU, LEN = 0.1, 0.3, 0.45, 0.6, 0.85
    take = {**GUARD, 'body@p': (0.0, -0.9, 1.6), 'chest': (6.0, 0.0, 0.0), 'head': (-2.0, 0.0, 0.0)}
    coil = {**GUARD, 'body@p': (0.0, -1.5, 2.4), 'chest': (10.0, 0.0, 0.0), 'head': (-4.0, 0.0, 0.0), 'torso': (0.0, 10.0, 0.0),
            'right_arm': (55.0, 34.0, 44.0), 'right_forearm': (40.0, 0.0, 0.0),
            'left_arm': (68.0, -18.0, -16.0), 'left_forearm': (62.0, 0.0, 0.0)}
    push = {**GUARD, 'body@p': (0.0, -1.0, -3.0), 'chest': (-16.0, 0.0, 0.0), 'head': (10.0, 0.0, 0.0), 'torso': (0.0, -6.0, 0.0),
            'right_arm': (84.0, 14.0, 20.0), 'right_forearm': (6.0, 0.0, 0.0),
            'left_arm': (86.0, -8.0, -12.0), 'left_forearm': (14.0, 0.0, 0.0)}
    thru = {**push, 'body@p': (0.0, -0.9, -2.6), 'chest': (-12.0, 0.0, 0.0)}
    keys = [(0, GUARD), (T_TAKE, take, 'out'), (T_COIL, coil, 'inout'), (T_PUSH, push, 'out'), (T_THRU, thru, 'inout'),
            (LEN, GUARD, 'inout')]
    feet = OneFoot().step('left', 0.32, T_PUSH, -2.4, -5.4, 10, lift=1.4).step('left', T_THRU + 0.04, LEN - 0.04, -2.4, -1.4, 16, lift=1.0)
    return bake('guard_shove', LEN, design('guard_shove', LEN, _all(keys)), feet, extra=(T_PUSH,), level=False)


def guard_finish():
    """The left hand closes on the striking wrist over the blade (0.12 s) and holds it (to 0.5 s); the shoulders turn and
    the body goes over it as the arm is twisted down and round behind its back, the blade's point at its neck (to 1.4 s);
    up, the knee, the kick into its back (1.5 s), the foot down; standing where he stood, the blade down at the side as
    it falls and dies."""
    T_GRAB, T_HOLD, T_TWIST, T_OVER, T_UP, T_HIT, T_BACK, T_DOWN, LEN = 0.12, 0.5, 1.0, 1.25, 1.36, 1.5, 1.62, 1.82, 2.3
    grab = {**GUARD, 'left_arm': (86.0, -10.0, 8.0), 'left_forearm': (12.0, 0.0, 0.0), 'body@p': (0.0, -0.8, 0.4)}
    hold = {**grab, 'left_arm': (72.0, -6.0, 4.0), 'left_forearm': (40.0, 0.0, 0.0), 'chest': (-4.0, 0.0, 0.0)}
    twist = {**GUARD, 'torso': (0.0, -22.0, 0.0), 'head': (12.0, 18.0, 0.0), 'chest': (-14.0, 0.0, 0.0), 'body@p': (0.0, -1.4, -1.6),
             'left_arm': (46.0, 30.0, -8.0), 'left_forearm': (78.0, 0.0, 0.0),
             'right_arm': (78.0, 22.0, 30.0), 'right_forearm': (28.0, 0.0, 0.0), 'right_item_sword': (0.0, 0.0, -60.0)}
    over = {**twist, 'torso': (0.0, -28.0, 0.0), 'head': (16.0, 24.0, 0.0), 'chest': (-18.0, 0.0, 0.0), 'body@p': (0.0, -1.8, -2.0),
            'left_arm': (30.0, 34.0, -4.0), 'left_forearm': (88.0, 0.0, 0.0)}
    up = {**GUARD, **CHAMBER, 'chest': (2.0, 0.0, 0.0), 'body@p': (0.0, -0.4, 0.2),
          'left_arm': (40.0, -10.0, -30.0), 'left_forearm': (30.0, 0.0, 0.0)}
    hit = {**up, **KICKED, 'chest': (14.0, 0.0, 0.0), 'head': (-6.0, 0.0, 0.0), 'body@p': (0.0, -0.5, 0.8),
           'left_arm': (30.0, -10.0, -48.0), 'left_forearm': (20.0, 0.0, 0.0)}
    back = {**up}
    rest = {'right_arm': (12.0, 0.0, 6.0), 'right_forearm': (14.0, 0.0, 0.0), 'left_arm': (6.0, 0.0, -6.0), 'left_forearm': (12.0, 0.0, 0.0),
            'head': (14.0, 0.0, 0.0), 'chest': (-3.0, 0.0, 0.0), 'body@p': (0.0, -0.2, 0.0)}
    keys = [(0, GUARD), (T_GRAB, grab, 'out'), (T_HOLD, hold, 'inout'), (T_TWIST, twist, 'inout'), (T_OVER, over, 'inout'),
            (T_UP, up, 'inout'), (T_HIT, hit, 'out'), (T_BACK, back, 'inout'), (T_DOWN, rest, 'inout'), (2.05, rest), (LEN, {}, 'inout')]
    feet = (OneFoot().step('left', 0.55, 0.95, -2.6, -3.6, 30, lift=1.2)
            .step('left', 1.2, 1.34, -2.2, -0.6, 10, lift=1.0)
            .kick('right', 1.3, T_DOWN - 0.02)
            .step('left', 1.95, 2.2, -1.9, 0.0, 0, lift=1.0).step('right', T_DOWN, 2.1, 1.9, 0.0, 0, lift=0.0))
    return bake('guard_finish', LEN, design('guard_finish', LEN, _all(keys)), feet, extra=(T_HIT,), level=False)


#: The hold (the living shield): the blade in its back level forward, the left hand on its left shoulder, the body behind it.
HOLD = {**STAB, 'left_arm': (92.0, -4.0, 14.0), 'left_forearm': (34.0, 0.0, 0.0), 'body@p': (0.0, -0.5, -0.6)}


def guard_grab():
    """The wrist taken (0.12 s) and pulled across as the shoulders turn to the left (tick 6), swinging it round in front;
    the left hand goes onto its left shoulder, the blade into its back (tick 14); into the hold."""
    T_GRAB, T_TURN, T_SET, T_STAB, LEN = 0.12, 0.3, 0.5, 0.7, 1.1
    grab = {**GUARD, 'left_arm': (86.0, -10.0, 8.0), 'left_forearm': (12.0, 0.0, 0.0), 'body@p': (0.0, -0.8, 0.4)}
    turn = {**GUARD, 'torso': (0.0, 34.0, 0.0), 'head': (0.0, -20.0, 0.0), 'chest': (-6.0, 0.0, 0.0), 'body@p': (0.0, -1.0, 0.2),
            'left_arm': (64.0, 36.0, -18.0), 'left_forearm': (30.0, 0.0, 0.0)}
    drawn = {**HOLD, 'right_arm': (40.0, 14.0, 10.0), 'right_forearm': (60.0, 0.0, 0.0), 'torso': (0.0, 22.0, 0.0),
             'right_item_sword@p': (0.0, -6.0, 5.4)}
    stab = {**HOLD, 'body@p': (0.0, -0.6, -1.2), 'chest': (-12.0, 0.0, 0.0)}
    keys = [(0, GUARD), (T_GRAB, grab, 'out'), (T_TURN, turn, 'inout'), (T_SET, drawn, 'inout'), (T_STAB, stab, 'out'), (LEN, HOLD, 'inout')]
    feet = (OneFoot().step('right', 0.18, 0.42, 2.4, 1.8, -30, lift=1.2)
            .step('left', 0.4, 0.62, -1.9, -1.2, 6, lift=1.0).step('right', 0.62, 0.86, 1.9, 0.6, 0, lift=1.0))
    return bake('guard_grab', LEN, design('guard_grab', LEN, _all(keys)), feet, extra=(T_STAB,), level=False)


def hostage_hold():
    """The hold, a breath in a second, the arms and the body above the hips only."""
    LEN = 1.0
    breath = {**HOLD, 'chest': (-12.5, 0.0, 0.0), 'left_arm': (93.0, -4.0, 14.0)}
    keys = [(0, HOLD), (0.5, breath, 'inout'), (LEN, HOLD, 'inout')]
    d = design('hostage_hold', LEN, _all(keys))
    a = L.Anim('hostage_hold', LEN, 'loop')
    for t in kit.frames(0, LEN):
        p = kit.design(d, t)
        a.key(t, {b: v for b, v in p.items() if b.split('@')[0] in UPPER})
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a


def hostage_kick():
    """The blade drawn back out of it, the knee up; the foot into its back (tick 5), the left hand letting go; down."""
    T_UP, T_HIT, T_BACK, T_DOWN, LEN = 0.12, 0.25, 0.38, 0.52, 0.75
    up = {**HOLD, **CHAMBER, 'right_arm': (36.0, 16.0, 12.0), 'right_forearm': (64.0, 0.0, 0.0), 'chest': (0.0, 0.0, 0.0),
          'torso': (0.0, 6.0, 0.0), 'body@p': (0.0, -0.3, 0.4), 'right_item_sword': (0.0, -40.0, 0.0), 'right_item_sword@p': (0.0, 0.0, 0.0)}
    hit = {**up, **KICKED, 'chest': (12.0, 0.0, 0.0), 'head': (-6.0, 0.0, 0.0), 'body@p': (0.0, -0.5, 1.0),
           'left_arm': (70.0, -10.0, -10.0), 'left_forearm': (6.0, 0.0, 0.0)}
    keys = [(0, HOLD), (T_UP, up, 'out'), (T_HIT, hit, 'out'), (T_BACK, up, 'inout'), (T_DOWN, {**up, 'right_leg': (0, 0, 0), 'right_shin': (0, 0, 0)}, 'inout'),
            (LEN, {}, 'inout')]
    feet = OneFoot()
    feet.start = dict(round9.HOME)
    feet.kick('right', 0.02, T_DOWN - 0.02)
    return bake('hostage_kick', LEN, design('hostage_kick', LEN, _all(keys)), feet, extra=(T_HIT,), level=False)


def made():
    a = [guard_kick(), guard_shove(), guard_finish(), guard_grab(), hostage_hold(), hostage_kick()]
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
    model['name'] = 'guard_2026-11-19'
    refine.write_set(model, made(), DST)


if __name__ == '__main__':
    main()
