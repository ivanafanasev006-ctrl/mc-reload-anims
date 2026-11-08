"""The author, after 0.9.1: the sword and the axe thrown.

"Add the throw of the sword and the axe, say on RMB + LMB: hold RMB + LMB, let go of LMB and he throws it, about as a
trident; if it hits a mob it sticks in it; to get it out press LMB at point blank: he pulls it out with an animation, a
little damage as it comes out of the body, a little stun; ... also a finisher: pulling it out of the body he first pulls
it out, then drives it in under the ribs, right in the middle, sharply as in anger, and pulls it out (the sword); with
the axe he pulls it out and chops as wood is split, from above straight down."

No draft. The finishers after the pull are the author's own (finisher_stab, finisher_chop, the moves of 2026-10-30):
the game plays them right after the pull. One file, refined/throw_2026-11-08.bbmodel (the rig with the sword):

    weapon_throw_aim    (0.5 s) side-on, the left foot forward, the weapon drawn back over the right shoulder, the
                        elbow high, the left arm pointing the way; the game holds the end (AIM_LOOP_FROM - LEN) while
                        both buttons are down.
    weapon_throw        (0.65 s) from the aim: the hips and the shoulder whip round, the arm over the top, the weapon
                        leaves at 0.1 s (Thrown.THROW_RELEASE, tick 2), the arm on down across the body, the right foot
                        stepping through, the stance.
    weapon_pull         (0.7 s) a step in, the right hand on the hilt in the mob's chest (0.15 s), a yank back, the body
                        leaning away from it (0.3 s: Thrown.PULL_TICK, tick 6, the weapon in the hand again), the stance.

    python3 tools/blockbench/refine_author_2026_10_29/round12.py
"""
import os

import kit
import round9
from kit import L
from round9 import Feet, design, bake

DST = os.path.join(kit.BB, 'refined', 'throw_2026-11-08.bbmodel')

#: The aim: the shoulders turned a little to the right, the fist at the right shoulder with the elbow back and up, the
#: weapon over the shoulder pointing forward and up (as a javelin is held; the blade stands out of the fist across it).
AIM = {'body@p': (0, -1.2, 0.6), 'torso': (0, -30, 0), 'chest': (4, 0, 0), 'head': (0, 30, 0),
       'right_arm': (-105, 0, 25), 'right_forearm': (135, 0, 0), 'left_arm': (85, 0, -10), 'left_forearm': (8, 0, 0),
       'right_leg': (10, 0, 4), 'right_shin': (-18, 0, 0), 'left_leg': (-16, 0, -3), 'left_shin': (-8, 0, 0)}
AIM_LOOP_FROM, AIM_LEN = 0.25, 0.5


def weapon_throw_aim():
    """Into the aim: the left foot forward, the shoulders round to the right, the weapon back over the shoulder; a
    breath held (the loop's ends are the same)."""
    breath = {**AIM, 'right_arm': (-108, 0, 25), 'chest': (5, 0, 0)}
    keys = [(0, {}), (AIM_LOOP_FROM, AIM, 'out'), (0.38, breath, 'inout'), (AIM_LEN, AIM, 'inout')]
    feet = Feet().step('left', 0.04, 0.2, -2.2, -4.5, 20, lift=1.6).step('right', 0.06, 0.2, 2.2, 1.8, -50, lift=0.0)
    a = bake('weapon_throw_aim', AIM_LEN, design('throw_aim', AIM_LEN, [(k[0], {**{b: (0, 0, 0) for b in AIM if not b.endswith('@p')},
                                                                                **k[1]}) + tuple(k[2:]) for k in keys]),
             feet, extra=(AIM_LOOP_FROM,), level=False)
    a.protect = (AIM_LOOP_FROM, AIM_LEN)
    return a


#: Times (s): the arm over the top (OVER), the weapon gone (RELEASE: tick 2), on down across (ACROSS), the step through
#: (STEP), the stance (LEN).
TH_OVER, TH_RELEASE, TH_ACROSS, TH_STEP, TH_LEN = 0.05, 0.1, 0.2, 0.32, 0.65


def weapon_throw():
    """From the aim the hips and the shoulders whip round to the front, the arm comes over the top, the weapon leaves
    the hand ahead of the head, the arm goes on down across the body to the left hip, the body folding after it; the
    right foot steps through; up into the stance."""
    # the arm goes over the top all the way round (backwards, up, forwards, down): its angle runs on to a whole turn
    # (-360, the arm hanging again; MoveAnimator.Upright takes it back within half a turn as it blends out)
    over = {**AIM, 'torso': (0, -10, 0), 'head': (0, 10, 0), 'chest': (-4, 0, 0), 'right_arm': (-175, 0, 10),
            'right_forearm': (90, 0, 0), 'left_arm': (40, 0, -20), 'left_forearm': (40, 0, 0)}
    release = {**over, 'torso': (0, 12, 0), 'head': (0, -12, 0), 'chest': (-10, 0, 0), 'right_arm': (-235, 0, 0),
               'right_forearm': (15, 0, 0), 'left_arm': (10, 0, -25), 'left_forearm': (60, 0, 0), 'body@p': (0, -1.6, -0.8)}
    across = {**release, 'torso': (0, 28, 0), 'head': (0, -28, 0), 'chest': (-18, 0, 0), 'right_arm': (-295, 10, -25),
              'right_forearm': (20, 0, 0), 'left_arm': (-20, 0, -30), 'left_forearm': (40, 0, 0), 'body@p': (0, -2.2, -1.6)}
    step = {**across, 'torso': (0, 12, 0), 'head': (0, -12, 0), 'chest': (-8, 0, 0), 'right_arm': (-330, 0, -10),
            'right_forearm': (15, 0, 0), 'body@p': (0, -1.2, -1.0)}
    rest = {**{b: (0, 0, 0) for b in AIM}, 'right_arm': (-360, 0, 0)}
    keys = [(0, AIM), (TH_OVER, over, 'in'), (TH_RELEASE, release, 'linear'), (TH_ACROSS, across, 'out'),
            (TH_STEP, step, 'inout'), (TH_LEN, rest, 'inout')]
    feet = Feet()
    feet.start = {'right': (2.2, 1.8, -50), 'left': (-2.2, -4.5, 20)}     # where the aim left them
    feet.step('right', 0.14, TH_STEP, 1.6, -7.5, -10, lift=2.0).step('left', 0.16, TH_STEP, -2.0, -3.0, 6, lift=0.0)
    feet.step('left', 0.42, 0.58, -1.9, -5.5, 0, lift=1.2).step('right', 0.45, 0.62, 1.9, -5.5, 0, lift=0.0)
    a = bake('weapon_throw', TH_LEN, design('throw', TH_LEN, keys), feet, extra=(TH_RELEASE,), level=False)
    return a


#: Times (s): the hand on the hilt (GRAB), the yank (YANK: tick 6), the stance (LEN).
PL_GRAB, PL_YANK, PL_LEN = 0.15, 0.3, 0.7


def weapon_pull():
    """A step in with the left foot, the right hand out to the hilt in the mob's chest, the other on its shoulder;
    a yank back, the body leaning away, the weapon coming out; the stance."""
    grab = {'body@p': (0, -0.8, -1.6), 'chest': (-8, 0, 0), 'torso': (0, -12, 0), 'head': (4, 12, 0),
            'right_arm': (80, 5, 0), 'right_forearm': (14, 0, 0), 'left_arm': (70, -10, 10), 'left_forearm': (20, 0, 0)}
    yank = {**grab, 'body@p': (0, -1.2, 0.8), 'chest': (6, 0, 0), 'torso': (0, -24, 0), 'head': (0, 24, 0),
            'right_arm': (40, 20, 10), 'right_forearm': (70, 0, 0), 'left_arm': (55, -10, 10), 'left_forearm': (10, 0, 0)}
    keys = [(0, {}), (PL_GRAB, grab, 'out'), (0.2, grab), (PL_YANK, yank, 'out'), (0.45, yank, 'inout'), (PL_LEN, {}, 'inout')]
    bones = {b: (0, 0, 0) for b in ('torso', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm')}
    feet = (Feet().step('left', 0.02, PL_GRAB, -2.0, -5.0, 6, lift=1.6)
            .step('left', PL_YANK - 0.04, 0.45, -2.0, -1.0, 4, lift=1.0)
            .step('left', 0.5, 0.66, -1.9, 0.0, 0, lift=1.0))
    a = bake('weapon_pull', PL_LEN, design('pull', PL_LEN, [(k[0], {**bones, **k[1]}) + tuple(k[2:]) for k in keys]), feet,
             extra=(PL_YANK,), level=False)
    return a


def made():
    a = [weapon_throw_aim(), weapon_throw(), weapon_pull()]
    for x in a:
        kit.soften(x, round9.SOFTEN, round9.LEGS)
    # the head follows the look in the aim and the throw
    for x in a[:2]:
        x.tracks.pop(('head', 'rotation'), None)
    return a


def main():
    import importlib.util
    import json
    spec = importlib.util.spec_from_file_location('refine_29', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'refine.py'))
    refine = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(refine)
    with open(kit.SRC, encoding='utf-8') as f:
        model = json.load(f)
    model['name'] = 'throw_2026-11-08'
    refine.write_set(model, made(), DST)


if __name__ == '__main__':
    main()
