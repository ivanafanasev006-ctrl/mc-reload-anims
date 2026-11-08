"""The author, after 0.9.1: the finishers of the bow and the crossbow.

"The bow's finisher: LMB at point blank, as with the sword; then he shoves the enemy and shoots it in the body and it
dies. The crossbow's finisher on LMB: he turns a whole turn, aims like a pistol with one hand and shoots a finishing
arrow into the body or the head. The finishers only when there is at least 1 arrow in the inventory."

No draft:

    bow_finisher        (1.0 s, the bow's file) both hands shove the mob back with the bow's grip and a step in (SHOVE:
                        Strikes.BOW_SHOVE, tick 4); the bow up and drawn as the author draws it (bow_barrage's draw,
                        2026-10-27), aimed down at the mob's body; the shot (SHOT: Strikes.BOW_KILL, tick 12), the string
                        hand flying open; the bow down.
    crossbow_finisher   (1.1 s, the crossbow's file) off the shoulder, a whole turn clockwise with the crossbow pulled in;
                        facing the mob the right arm goes straight out, the crossbow level at its head, the other hand
                        back at the hip (one hand, as a pistol); the shot (SHOT: Strikes.XBOW_KILL, tick 12), the recoil
                        throwing the arm up; onto the shoulder again.

    python3 tools/blockbench/refine_author_2026_10_29/round11.py   (writes both files)
"""
import numpy as np

import kit
import round8
import round9
from kit import L
from round9 import CARRY, Feet, arm_to, design, bake, stepping

# --------------------------------------------------------------------------------------------
# bow_finisher
# --------------------------------------------------------------------------------------------

#: Times (s): the shove (SHOVE: tick 4), drawn (DRAWN), aimed (AIM), the shot (SHOT: tick 12), the bow down (DOWN), LEN.
BF_SHOVE, BF_DRAWN, BF_AIM, BF_SHOT, BF_DOWN, BF_LEN = 0.18, 0.42, 0.55, 0.6, 0.82, 1.0
#: The author's draw (bow_barrage, the kneel with the bow drawn) and its release, the arms and the upper body.
DRAW_BONES = ('torso', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm')


def _drawn(t):
    p = round8._barrage(t)
    return {k: p[k] for k in DRAW_BONES if k in p}


def bow_finisher():
    """Both hands shove the mob away with the bow's grip, the left foot stepping in; the bow comes up and is drawn
    (the author's draw), the body leaning over it to aim down at the mob's middle; the string hand flies open (the
    shot), a breath, the bow down."""
    shove = {'body@p': (0, -1.0, -2.2), 'chest': (-10, 0, 0), 'head': (6, 0, 0),
             'right_arm': (82, -8, -6), 'right_forearm': (12, 0, 0), 'left_arm': (82, 8, 6), 'left_forearm': (12, 0, 0)}
    drawn = {**_drawn(0.5), 'body@p': (0, -0.8, -0.8)}
    aim = dict(drawn)
    cx, cy, cz = aim.get('chest', (0, 0, 0))
    aim['chest'] = (cx - 10, cy, cz)
    pull = {**aim, **{k: v for k, v in _drawn(0.55).items() if k in ('left_arm', 'left_forearm', 'right_forearm')}}
    loose = {**aim, **{k: v for k, v in _drawn(0.6).items() if k in ('left_arm', 'left_forearm', 'right_forearm')}}
    opened = {**aim, **{k: v for k, v in _drawn(0.65).items() if k in ('left_arm', 'left_forearm', 'right_forearm')}}
    down = L.mix(opened, {}, 0.55)
    down['body@p'] = (0, -0.4, -0.3)
    keys = [(0, {}), (BF_SHOVE, shove, 'out'), (0.28, shove), (BF_DRAWN, drawn, 'inout'), (BF_AIM, aim, 'inout'),
            (0.58, pull, 'inout'), (BF_SHOT, loose, 'out'), (0.66, opened, 'out'), (BF_DOWN, down, 'inout'), (BF_LEN, {}, 'inout')]
    feet = (Feet().step('left', 0.04, BF_SHOVE, -2.0, -5.0, 8, lift=1.6)
            .step('right', 0.3, 0.45, 2.6, 1.0, -20, lift=1.0)
            .step('left', BF_DOWN - 0.04, BF_LEN - 0.03, -1.9, 0.0, 0, lift=1.2)
            .step('right', BF_DOWN, BF_LEN, 1.9, 0.0, 0, lift=1.0))
    a = bake('bow_finisher', BF_LEN, design('bow_finisher', BF_LEN, [(k[0], {**{b: (0, 0, 0) for b in DRAW_BONES}, **k[1]}) + tuple(k[2:])
                                                                      for k in keys]),
             feet, extra=(BF_SHOVE, BF_SHOT), level=False)
    a.errors = ()
    return a


# --------------------------------------------------------------------------------------------
# crossbow_finisher
# --------------------------------------------------------------------------------------------

#: Times (s): off the shoulder and wound up (WIND), round (ROUND), the arm out (AIM), the shot (SHOT: tick 12), the
#: recoil (KICK), held (HOLD), the stance (LEN).
CF_WIND, CF_ROUND, CF_AIM, CF_SHOT, CF_KICK, CF_HOLD, CF_LEN = 0.1, 0.42, 0.52, 0.6, 0.66, 0.82, 1.1
CF_YAW = kit.keys((0.0, 0.0), (CF_WIND, 20.0), (0.2, -90.0), (0.3, -230.0), (CF_ROUND, -345.0), (CF_AIM, -360.0), (CF_LEN, -360.0))


def crossbow_finisher():
    """The crossbow off the shoulder into the chest, wound up to the left; a whole turn clockwise, the feet stepping
    round, the head spotting the mob; facing it, the right shoulder forward, the arm goes straight out and levels the
    crossbow at its head, one hand, the other back at the hip; the shot, the arm thrown up by the recoil and back; onto
    the shoulder."""
    wind = {**CARRY, 'body@p': (0, -0.8, 0), 'chest': (-4, 0, 0), 'torso': (0, 16, 0)}
    wind, e1 = arm_to(wind, 'right', [('rhand', round9.yawed(CF_YAW(CF_WIND), (2.0, 19.0, -5.5)), 1.0),
                                      ('xfront', round9.yawed(CF_YAW(CF_WIND), (-6.0, 22.0, -7.0)), 0.6)])
    tuck = dict(wind, torso=(0, -6, 0))
    # the arm straight out at the mob, no bend at the elbow (author, after 0.9.6: "the arm straight forward at the target,
    # not bent"); the prod lies level that way (its tips across, as the arm hangs)
    aim = {**CARRY, 'torso': (0, -18, 0), 'chest': (-2, 0, 0), 'head': (0, 18, 0), 'body@p': (0, -0.6, 0),
           'left_arm': (-12, 0, -18), 'left_forearm': (35, 0, 0), 'right_arm': (90.0, 14.0, 0.0), 'right_forearm': (0.0, 0.0, 0.0)}
    e2 = 0.0
    kick = dict(aim)
    kick['right_arm'] = (104.0, 14.0, 0.0)
    kick['head'] = (-5, 18, 0)
    keys = [(0, CARRY), (CF_WIND, wind, 'inout'), (0.3, tuck), (CF_ROUND, tuck), (CF_AIM, aim, 'out'), (CF_SHOT, aim),
            (CF_KICK, kick, 'out2'), (CF_HOLD, aim, 'inout'), (CF_LEN, CARRY, 'inout')]
    dsg = design('crossbow_finisher', CF_LEN, keys)

    def pose_at(t):
        p = kit.design(dsg, t)
        yaw = CF_YAW(t)
        if t < CF_AIM:
            p['head'] = (p['head'][0], round9.spotting(-(yaw + p['torso'][1]), -1), p['head'][2])
        return stepping(p, yaw, kit.span(t, CF_WIND, CF_WIND + 0.04) * (1 - kit.span(t, 0.36, CF_AIM)))

    feet = (Feet().designed(CF_WIND - 0.01, CF_AIM - 0.04)
            .step('right', CF_AIM - 0.04, CF_AIM + 0.04, 2.6, -3.5, -14, lift=1.0)
            .step('left', CF_AIM - 0.04, CF_AIM + 0.04, -2.2, 2.0, 16, lift=0.0)
            .step('right', CF_HOLD + 0.04, CF_LEN - 0.04, 1.9, 0.0, 0, lift=1.2)
            .step('left', CF_HOLD + 0.06, CF_LEN - 0.02, -1.9, 0.0, 0, lift=0.0))
    # the arm is set by hand (straight): not levelled again frame by frame, which would bend it
    a = bake('crossbow_finisher', CF_LEN, pose_at, feet, body=lambda t: (0.0, CF_YAW(t), 0.0), extra=(CF_SHOT,), level=False)
    a.errors = (e1, e2)
    return a


def bow_made():
    a = [bow_finisher()]
    for x in a:
        kit.soften(x, round9.SOFTEN, round9.LEGS)
    return a


def crossbow_made():
    a = [crossbow_finisher()]
    for x in a:
        kit.soften(x, round9.SOFTEN, round9.LEGS)
    return a


def crossbow_all():
    """Every animation of the crossbow's file: the chain (round9), G, the fan and the rolls (round10), the finisher."""
    import round10
    return round9.made() + round10.made() + crossbow_made()


def write_bow():
    import copy
    import importlib.util
    import json
    import os
    # this folder's refine.py (another refine module is first on the path)
    spec = importlib.util.spec_from_file_location('refine_29', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'refine.py'))
    refine = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(refine)
    with open(kit.SRC, encoding='utf-8') as f:
        mbow = json.load(f)
    mbow['name'] = 'bow_2026-10-31'
    refine.set_visible(mbow, 'right_item_sword', False)
    refine.set_visible(mbow, 'right_item_bow', True)
    refine.write_set(copy.deepcopy(mbow), [round8.bow_slide_shot()] + bow_made(), refine.DST_BOW, bow=True)


def main():
    write_bow()
    round9.write(crossbow_all())


if __name__ == '__main__':
    main()
