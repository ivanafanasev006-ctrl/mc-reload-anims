"""The author's message of 2026-11-08, its second half: the crossbow's G, G held and the rolls.

"The crossbow's moves on G: a hop back and a shot of 3 arrows (spread) if the crossbow was loaded; if
not, one turn round himself and a shove. The damage a little more than the bow's LMB. G+ lets one load
the 'fan of arrows': one shot and a whole shotgun of arrows flies at once, as the bow's G+, only those fly
one after another and here all at once with one shot; an arrow loads in about 0.5 s, 8 at most, G held.
And instead of the Legolas stance: the right button held and R pressed at once, he starts hopping back
at once and shooting an arrow a roll; the rolls go left and right too, not only back and forward, by the
key held of WASD; the right button let go or another weapon in hand ends it. The examples are in the
file (refined/crossbow_2026-11-08.bbmodel, the chain on LMB): make them smoother and for Kinetrix's
animations, not vanilla's, so it does not look different."

The chain on LMB (round9.py) is smoothed there (SOFTEN) and its shot aimed as Kinetrix aims a loaded
crossbow (round9.AIM). The new ones, no draft, all starting and ending in Kinetrix's carry (CARRY) and
shooting from its aim (AIM, carried with the neck, round9._aim), smoothed the same:

    crossbow_hop_volley    G, loaded: down, off both feet backwards high, the crossbow up into the aim
                           in the air; the three arrows at the top of the hop (VO_SHOT: Crossbow
                           VOLLEY_SHOT), the recoil throwing the arms up and the body back; down into a
                           crouch, up.
    crossbow_spin_push     G, not loaded: wound up to the left, a whole turn clockwise with the crossbow
                           out at arm's length, sweeping round at the chest's height (SP_SWEEP: every
                           mob about him hit), then both hands shove it into the nearest mob and off it he
                           springs back, away from it (SP_SHOVE; author, after 0.9.1), a crouch, up.
    crossbow_fan           G held, the fan of arrows: into a braced stance, the crossbow at the aim; one
                           loading a FAN_EVERY (the free hand back to the string, an arrow laid, back to
                           the front: the game loops FAN_LOOP_FROM - FAN_LOOP_TO while G is held, an arrow
                           a loop); let go, up and the shot (FAN_SHOT): the recoil rocks the body back
                           and the right foot slides back, up into the carry.
    crossbow_roll_back     The rolls (the right button held, R): a roll that way, the crossbow pulled in to
    crossbow_roll_front    the chest with both hands, up onto one knee facing the look, the crossbow at
    crossbow_roll_left     the aim, the shot (RL_SHOT), the recoil; the next roll may start from RL_NEXT,
    crossbow_roll_right    else up into the carry. The back and the front roll are the author's own
                           (dodge_back, dodge_front, 2026-10-27), the side ones the author's roll of the
                           bow's flow (bow_flow_roll_left_shot, 2026-10-28; the right one mirrored).

    python3 tools/blockbench/refine_author_2026_10_29/round10.py   (writes the crossbow's file, all twelve)
"""
import math
import os

import numpy as np

import kit
import moves
import round8
import round9
from kit import L
from round9 import AIM, CARRY, Feet, LEGS, arm_to, design, bake, _aim, stepping

REF28 = os.path.join(kit.BB, 'refined', 'author_2026-10-28.bbmodel')
BONES = ('body', 'torso', 'chest', 'head', 'right_leg', 'right_shin', 'left_leg', 'left_shin')
SOFTEN = round9.SOFTEN


def _kick(p, k=1.0):
    """The recoil of a shot from the aim: the arms and the crossbow thrown up, the shoulders back."""
    q = dict(p)
    for b, d in (('right_arm', 11.0), ('left_arm', 10.0), ('right_forearm', 7.0)):
        x, y, z = q[b]
        q[b] = (x + d * k, y, z)
    cx, cy, cz = q.get('chest', (0, 0, 0))
    q['chest'] = (cx + 6.0 * k, cy, cz)
    return q


# --------------------------------------------------------------------------------------------
# 1. crossbow_hop_volley: G loaded, the hop back with three arrows at its top
# --------------------------------------------------------------------------------------------

#: Times (s): down (DIP), off (OFF: Move.PUSH_TICK), the shot at the top (SHOT: Crossbow.VOLLEY_SHOT, tick 6),
#: the recoil (KICK), the feet down (LAND), the deepest (DEEP), up (UP), the stance (LEN).
VO_DIP, VO_OFF, VO_SHOT, VO_KICK, VO_LAND, VO_DEEP, VO_UP, VO_LEN = 0.07, 0.1, 0.3, 0.36, 0.5, 0.58, 0.85, 1.05
VO_LIFT = kit.keys((0.0, 0.0), (VO_OFF, 0.0), (0.28, 7.0, 'out'), (VO_LAND, 0.0, 'in'), (VO_LEN, 0.0))


def crossbow_hop_volley():
    """Down a little, then off both feet backwards, higher than the chain's hop, the knees coming up; the
    crossbow comes up into both hands at Kinetrix's aim on the way; at the top the three arrows, the recoil
    throwing the arms up and the body back in the air; down onto the feet, the left in front, into a crouch,
    still aiming; up into the carry."""
    dip = {**CARRY, 'body@p': (0, -2.2, -0.6), 'chest': (-8, 0, 0), 'right_leg': (16, 0, 2), 'right_shin': (-30, 0, 0),
           'left_leg': (16, 0, -2), 'left_shin': (-30, 0, 0)}
    air = {**AIM, 'body': (-4, 0, 0), 'chest': (-4, 0, 0), 'right_leg': (-30, 0, 4), 'right_shin': (-46, 0, 0),
           'left_leg': (28, 0, -3), 'left_shin': (-70, 0, 0)}
    air, e1 = _aim(air)
    shot = {**air, 'body': (-2, 0, 0)}
    shot, e2 = _aim(shot)
    kick = {**_kick(shot), 'body': (6, 0, 0), 'right_leg': (-24, 0, 4), 'left_leg': (34, 0, -3)}
    land = {**AIM, 'body': (-3, 0, 0), 'body@p': (0, -4.5, 0), 'chest': (-6, 0, 0)}
    land, e3 = _aim(land)
    deep = {**land, 'body@p': (0, -6.0, 0), 'chest': (-5, 0, 0)}
    deep, e4 = _aim(deep)
    up = L.mix(deep, CARRY, 0.55)
    up['body@p'] = (0, -2.4, 0)
    keys = [(0, CARRY), (VO_DIP, dip, 'inout'), (VO_OFF, dip), (0.22, air, 'out'), (VO_SHOT, shot, 'inout'),
            (VO_KICK, kick, 'out2'), (VO_LAND, land, 'inout'), (VO_DEEP, deep, 'out'), (0.68, deep),
            (VO_UP, up, 'inout'), (VO_LEN, CARRY, 'inout')]
    feet = (Feet().off(VO_OFF, VO_LAND).step('left', VO_OFF, VO_LAND, -2.4, -4.5, 4, lift=0.0)
            .step('right', VO_OFF, VO_LAND, 2.3, 4.6, -14, lift=0.0)
            .step('right', VO_UP - 0.1, VO_UP + 0.04, 1.9, 0.0, 0, lift=1.4)
            .step('left', VO_UP, VO_LEN - 0.03, -1.9, 0.0, 0, lift=1.2))
    a = bake('crossbow_hop_volley', VO_LEN, design('hop_volley', VO_LEN, keys), feet, lift=VO_LIFT, extra=(VO_SHOT,))
    a.errors = (e1, e2, e3, e4)
    return a


# --------------------------------------------------------------------------------------------
# 2. crossbow_spin_push: G not loaded, a whole turn with the crossbow out, then a shove
# --------------------------------------------------------------------------------------------

#: Times (s): wound up (WIND), the crossbow sweeping through the back half (SWEEP: Crossbow.SPIN_SWEEP, tick 5),
#: round to the front (ROUND), both hands shoving off the nearest mob (SHOVE: Crossbow.SPIN_SHOVE, tick 9), off the
#: floor backwards (OFF: the push from tick 10), the top of the hop (TOP), the feet down (LAND), up (UP), the stance (LEN).
SP_WIND, SP_SWEEP, SP_ROUND, SP_SHOVE, SP_OFF, SP_TOP, SP_LAND, SP_UP, SP_LEN = 0.1, 0.25, 0.4, 0.46, 0.5, 0.6, 0.75, 0.98, 1.15
#: The body's turn (degrees, clockwise is -): wound up to the left, a whole turn round, facing the mob at the shove.
SPIN_YAW = kit.keys((0.0, 0.0), (SP_WIND, 22.0), (0.18, -60.0), (SP_SWEEP, -160.0), (0.33, -265.0), (SP_ROUND, -330.0),
                    (SP_SHOVE, -358.0), (0.52, -360.0), (SP_LEN, -360.0))
SPIN_LIFT = kit.keys((0.0, 0.0), (SP_OFF, 0.0), (SP_TOP, 5.0, 'out'), (SP_LAND, 0.0, 'in'), (SP_LEN, 0.0))


def _out(pose, yaw, reach=1.0):
    """The right arm out at the shoulder's height, almost straight, the crossbow going on beyond the fist
    (sweeping round); the free hand in at the chest."""
    y = yaw
    q, e1 = arm_to(pose, 'right', [('rhand', round9.yawed(y, (4.0 + 11.0 * reach, 21.5, -3.0)), 1.0),
                                   ('xfront', round9.yawed(y, (8.0 + 15.0 * reach, 22.5, -5.0)), 0.6)],
                   {('right_forearm', 0): (0.0, 25.0)})
    q, e2 = arm_to(q, 'left', [('lhand', round9.yawed(y, (-3.0, 18.0, -5.0)), 1.0)])
    return q, max(e1, e2)


def crossbow_spin_push():
    """Wound up to the left, the crossbow off the shoulder across the chest; the hips lead a whole turn to the right,
    the arm opening out at the shoulder's height, the crossbow at arm's length sweeping round (every mob about him
    hit); coming round to the mob the free hand takes the crossbow and both arms shove it into the mob, and off it
    the player springs back (author, after 0.9.1: "not just a turn round himself but a push off the nearest enemy,
    away from it"), the knees up, down into a crouch, up with the crossbow onto the shoulder again."""
    wind = {**CARRY, 'body@p': (-0.4, -1.0, 0.0), 'chest': (-4, 0, 0), 'torso': (0, 18, 0),
            'right_leg': (-4, 0, 3), 'right_shin': (-10, 0, 0), 'left_leg': (2, 0, -2), 'left_shin': (-10, 0, 0)}
    y = SPIN_YAW(SP_WIND)
    wind['body'] = (0, y, 0)
    wind, e1 = arm_to(wind, 'right', [('rhand', round9.yawed(y, (-2.0, 20.0, -5.5)), 1.0),
                                      ('xfront', round9.yawed(y, (-10.0, 22.0, -4.5)), 0.6)])
    wind, e2 = arm_to(wind, 'left', [('lhand', round9.yawed(y, (-4.0, 16.5, -5.5)), 1.0)])
    y = SPIN_YAW(0.18)
    open_ = {**wind, 'torso': (0, 8, 0), 'body': (0, y, 0), 'body@p': (0, -1.4, 0)}
    open_, e3 = _out(open_, y, 0.6)
    y = SPIN_YAW(SP_SWEEP)
    sweep = {**open_, 'torso': (0, -6, 0), 'body': (0, y, 0), 'body@p': (0, -1.8, 0), 'chest': (-2, 0, 0)}
    sweep, e4 = _out(sweep, y, 1.0)
    y = SPIN_YAW(0.33)
    late = {**sweep, 'torso': (0, -14, 0), 'body': (0, y, 0)}
    late, e5 = _out(late, y, 0.85)
    # round to the front: the arm folds in, the free hand on the crossbow, both drawn back to the chest
    y = SPIN_YAW(SP_ROUND)
    gather = {**late, 'torso': (0, -8, 0), 'body': (0, y, 0), 'body@p': (0, -1.8, 0.6), 'chest': (-2, 0, 0)}
    gather, e6 = arm_to(gather, 'right', [('rhand', round9.yawed(y, (3.5, 18.0, -3.5)), 1.0),
                                          ('xfront', round9.yawed(y, (2.0, 19.0, -12.0)), 0.6)])
    gather, e7 = arm_to(gather, 'left', [('lhand', kit.pt(kit.fk(gather), 'xfore') + np.array((-0.6, -1.4, 0.0)), 1.0)])
    # the shove: both arms out, the crossbow flat on the mob's chest, the body leaning into it, about to spring off
    shove = {**gather, 'torso': (0, 2, 0), 'body': (0, 0, 0), 'body@p': (0, -2.4, -1.6), 'chest': (-6, 0, 0), 'head': (4, 0, 0),
             'right_leg': (22, 0, 3), 'right_shin': (-36, 0, 0), 'left_leg': (8, 0, -3), 'left_shin': (-26, 0, 0)}
    shove, e8 = arm_to(shove, 'right', [('rhand', (2.5, 19.0, -11.0), 1.0), ('xfront', (1.5, 19.5, -19.5), 0.6)],
                       {('right_forearm', 0): (0.0, 35.0)})
    shove, e9 = arm_to(shove, 'left', [('lhand', kit.pt(kit.fk(shove), 'xfore') + np.array((-0.6, -1.4, 0.0)), 1.0)])
    # off it backwards: the arms drawing the crossbow back in, the knees up
    air = {**shove, 'body': (-6, 0, 0), 'body@p': (0, 0, 0), 'chest': (-2, 0, 0), 'head': (2, 0, 0),
           'right_leg': (-26, 0, 4), 'right_shin': (-50, 0, 0), 'left_leg': (26, 0, -3), 'left_shin': (-70, 0, 0)}
    air, e10 = arm_to(air, 'right', [('rhand', (4.0, 17.0, -6.0), 1.0), ('xfront', (3.0, 18.5, -15.0), 0.6)])
    air, e11 = arm_to(air, 'left', [('lhand', kit.pt(kit.fk(air), 'xfore') + np.array((-0.6, -1.4, 0.0)), 1.0)])
    land = {**air, 'body': (-3, 0, 0), 'body@p': (0, -5.0, 0), 'chest': (-6, 0, 0), 'head': (0, 0, 0)}
    up = L.mix(land, CARRY, 0.5)
    up['body@p'] = (0, -2.0, 0)
    for p in (wind, open_, sweep, late, gather):
        p.pop('body', None)
    keys = [(0, CARRY), (SP_WIND, wind, 'inout'), (0.18, open_), (SP_SWEEP, sweep), (0.33, late), (SP_ROUND, gather),
            (SP_SHOVE, shove, 'out'), (SP_TOP, air, 'out'), (SP_LAND, land, 'in'), (0.85, land),
            (SP_UP, up, 'inout'), (SP_LEN, CARRY, 'inout')]
    dsg = design('spin_push', SP_LEN, keys)
    tilt = kit.keys(*[(k[0], k[1].get('body', (0, 0, 0))[0]) for k in keys])

    def pose_at(t):
        p = kit.design(dsg, t)
        yaw = SPIN_YAW(t)
        if t < SP_ROUND:
            p['head'] = (p['head'][0], round9.spotting(-(yaw + p['torso'][1]), -1), p['head'][2])
        p['body'] = (float(tilt(t)), yaw, 0.0)
        return stepping(p, yaw, kit.span(t, SP_WIND, SP_WIND + 0.04) * (1 - kit.span(t, 0.36, SP_SHOVE - 0.02)))

    feet = (Feet().designed(SP_WIND - 0.01, SP_SHOVE - 0.06)
            .step('right', SP_SHOVE - 0.06, SP_SHOVE, 2.2, -4.0, -8, lift=1.2)
            .step('left', SP_SHOVE - 0.06, SP_SHOVE, -1.9, 0.8, 10, lift=0.0)
            .off(SP_OFF, SP_LAND).step('left', SP_OFF, SP_LAND, -2.4, -3.5, 4, lift=0.0)
            .step('right', SP_OFF, SP_LAND, 2.3, 3.8, -14, lift=0.0)
            .step('right', SP_UP - 0.1, SP_UP + 0.04, 1.9, 0.0, 0, lift=1.2)
            .step('left', SP_UP, SP_LEN - 0.03, -1.9, 0.0, 0, lift=1.2))
    a = bake('crossbow_spin_push', SP_LEN, pose_at, feet, lift=SPIN_LIFT, extra=(SP_SWEEP, SP_SHOVE))
    a.errors = (e1, e2, e3, e4, e5, e6, e7, e8, e9, e10, e11)
    return a


# --------------------------------------------------------------------------------------------
# 3. crossbow_fan: G held, the fan of arrows
# --------------------------------------------------------------------------------------------

#: Times (s): braced (BRACE); the loading looped from LOOP_FROM to LOOP_TO (an arrow each: Crossbow.FAN_EVERY,
#: 10 ticks); the crossbow up a hair (RAISE), the shot (SHOT: Crossbow.FAN_SHOT, 2 ticks into the ending), the
#: recoil (KICK), the slide back (BACK), up (UP), the stance (LEN).
FAN_BRACE, FAN_LOOP_FROM, FAN_LOOP_TO = 0.25, 0.3, 0.8
FAN_RAISE, FAN_SHOT, FAN_KICK, FAN_BACK, FAN_UP, FAN_LEN = 0.86, 0.9, 0.95, 1.08, 1.3, 1.5


def crossbow_fan():
    """Into a braced stance (the left foot in front, the knees bent, the weight low), the crossbow at the aim;
    each loading the free hand goes back along the stock to the string, lays an arrow and comes back to the
    front, the shoulders pumping with it; let go, the crossbow up a hair and the shot: the recoil rocks the
    body back, the arms thrown up, the right foot sliding half a step back; up into the carry."""
    brace = {**AIM, 'body@p': (0, -2.6, 0.3), 'body': (-3, 0, 0), 'chest': (-4, 0, 0),
             'right_leg': (14, 0, 3), 'right_shin': (-24, 0, 0), 'left_leg': (14, 0, -3), 'left_shin': (-24, 0, 0)}
    brace, e1 = _aim(brace)
    # the free hand at the string: back along the stock behind the prod, a little over it
    reach = dict(brace, chest=(-6, 0, 0))
    reach, e2 = _aim(reach)
    string = kit.pt(kit.fk(reach), 'xmid') + np.array((-1.2, 1.8, 0.0))
    reach, e3 = arm_to(reach, 'left', [('lhand', string, 1.0)])
    lay = dict(reach)
    lay, e4 = arm_to(lay, 'left', [('lhand', string + np.array((0.0, 0.4, 1.4)), 1.0)])
    raise_ = dict(brace, body=(-2, 0, 0), chest=(-2, 0, 0))
    raise_, e5 = _aim(raise_)
    kick = {**_kick(raise_, 1.3), 'body': (5, 0, 0), 'body@p': (0, -2.4, 1.2), 'head': (-6, 0, 0)}
    back = {**_kick(raise_, 0.5), 'body': (2, 0, 0), 'body@p': (0, -2.2, 1.6)}
    up = L.mix(back, CARRY, 0.55)
    up['body@p'] = (0, -1.0, 0.8)
    keys = [(0, CARRY), (FAN_BRACE, brace, 'inout'), (FAN_LOOP_FROM, brace), (0.44, reach, 'inout'), (0.52, lay, 'inout'),
            (0.68, brace, 'inout'), (FAN_LOOP_TO, brace), (FAN_RAISE, raise_, 'inout'), (FAN_SHOT, raise_),
            (FAN_KICK, kick, 'out2'), (FAN_BACK, back, 'inout'), (FAN_UP, up, 'inout'), (FAN_LEN, CARRY, 'inout')]
    feet = (Feet().step('left', 0.05, FAN_BRACE, -2.4, -3.2, 6, lift=1.4).step('right', 0.08, FAN_BRACE, 2.4, 1.6, -18, lift=0.0)
            .step('right', FAN_KICK - 0.02, FAN_BACK, 2.6, 4.2, -20, lift=0.0)
            .step('left', FAN_UP - 0.06, 1.42, -1.9, 0.0, 0, lift=1.2)
            .step('right', FAN_UP - 0.02, 1.46, 1.9, 0.0, 0, lift=1.2))
    a = bake('crossbow_fan', FAN_LEN, design('fan', FAN_LEN, keys), feet, extra=(FAN_LOOP_FROM, FAN_LOOP_TO, FAN_SHOT))
    a.errors = (e1, e2, e3, e4, e5)
    # the loop seams: the same pose at both ends of the loading
    a.protect = (FAN_LOOP_FROM, FAN_LOOP_TO, FAN_SHOT)
    return a


# --------------------------------------------------------------------------------------------
# 4. the rolls with a shot each
# --------------------------------------------------------------------------------------------

#: Times (s): the roll over (ROLLED: the body round, the author's roll pressed into it), up on the knee facing
#: the look (KNEEL), the shot (SHOT: Crossbow.ROLL_SHOT, tick 13), the recoil (KICK), the next roll may start
#: (NEXT: Crossbow.ROLL_NEXT, tick 15), up (UP), the stance (LEN).
RL_ROLLED, RL_KNEEL, RL_SHOT, RL_KICK, RL_NEXT, RL_UP, RL_LEN = 0.5, 0.6, 0.65, 0.7, 0.75, 0.9, 1.05
#: Each source roll, the part of it pressed into 0 - RL_ROLLED (s of its own time).
ROLLS = {'back': (moves.REF27, 'dodge_back', 0.6), 'front': (moves.REF27, 'dodge_front', 0.6),
         'left': (REF28, 'bow_flow_roll_left_shot', 0.62)}


def _tuck():
    """The crossbow pulled in to the chest in a roll: the fist under the chin, the crossbow across the chest
    to the left, the free hand on its front (solved upright; the arms ride on the chest through the roll)."""
    q, e1 = arm_to(dict(CARRY, torso=(0, -10, 0)), 'right', [('rhand', (3.0, 19.0, -5.0), 1.0), ('xfront', (-4.5, 21.0, -6.5), 0.6)])
    q, e2 = arm_to(q, 'left', [('lhand', kit.pt(kit.fk(q), 'xfore') + np.array((0.0, -1.6, -0.4)), 1.0)])
    return {k: q[k] for k in ('torso', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm')}, max(e1, e2)


def _kneel():
    """Down on the left knee facing the look (the bow's kneel, bow_barrage at 0.5 s: the author's), the
    shoulders up, the crossbow at the aim."""
    k = round8._barrage(0.5)
    q = {**AIM, **{b: k[b] for b in ('right_leg', 'right_shin', 'left_leg', 'left_shin')}, 'body': (0, 0, 0), 'chest': (-4, 0, 0)}
    q = kit.grounded(q, bones=LEGS)
    return _aim(q)


def crossbow_roll(side):
    """A roll that way (the author's), the crossbow pulled in to the chest with both hands; out of it up onto
    the left knee facing the look, the crossbow at the aim, the shot, the recoil; up into the carry (if no
    roll comes next)."""
    mirror = side == 'right'
    path, src, upto = ROLLS['left' if mirror else side]
    tuck, e1 = _tuck()
    kneel, e2 = _kneel()
    kick = _kick(kneel)
    up = L.mix(kneel, CARRY, 0.5)
    up['body@p'] = (0, kneel['body@p'][1] * 0.45, 0)
    after = design('roll_after', RL_LEN, [(RL_KNEEL, kneel), (RL_SHOT, kneel), (RL_KICK, kick, 'out2'), (RL_NEXT + 0.03, kneel, 'inout'),
                                          (RL_UP, up, 'inout'), (RL_LEN, CARRY, 'inout')])
    sign = 1.0 if side == 'back' else -1.0     # the roll's own turn: back +, front and the sides -

    def rolled(t):
        p = moves._sampled(path, src, upto * min(t, RL_ROLLED) / RL_ROLLED, bones=BONES)
        p.pop('body@p', None)
        if mirror:
            p = kit.mirror_pose(p)
        if side in ('left', 'right'):
            # the author's side roll ends side-on, the head turned to its target: turned back to face the look as it
            # comes up, the head with the body (tucked as in the roll, then up)
            bx, by, bz = p['body']
            p['body'] = (bx, by * (1 - kit.span(t, 0.36, RL_KNEEL)), bz)
            if t > 0.3:
                p['head'] = (-45.0 * (1 - kit.span(t, 0.3, RL_KNEEL)), 0.0, 0.0)
        return p

    def pose_at(t):
        arms = L.mix(CARRY, tuck, kit.span(t, 0.0, 0.08)) if t < 0.08 else dict(tuck)
        p = {**CARRY, **rolled(t), **{k: arms[k] for k in ('torso', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm')}}
        if t <= RL_ROLLED - 0.1:
            return p
        k = kit.design(after, max(t, RL_KNEEL))
        f = kit.span(t, RL_ROLLED - 0.1, RL_KNEEL)
        q = L.mix(p, k, f)
        bx, by, bz = p['body']
        # the roll's turn goes on round to a whole one, not back the short way
        q['body'] = (bx + (sign * 360.0 - bx) * f if abs(bx) > 90 else bx * (1 - f), by * (1 - f), bz * (1 - f))
        return q

    name = 'crossbow_roll_' + side
    a = bake(name, RL_LEN, pose_at, Feet().designed(0.0, RL_KNEEL), extra=(RL_SHOT,), level=False)
    a.errors = (e1, e2)
    return a


def made():
    a = [crossbow_hop_volley(), crossbow_spin_push(), crossbow_fan()] + [crossbow_roll(s) for s in ('back', 'front', 'left', 'right')]
    for x in a:
        kit.soften(x, SOFTEN, LEGS)
    # the head follows the look where the body does not turn round (the hop, the fan)
    for x in (a[0], a[2]):
        x.tracks.pop(('head', 'rotation'), None)
    return a


def main():
    import round11
    for a in made():
        print(a.name, 'arm misses', ' '.join(f'{e:.1f}' for e in a.errors), f'feet {a.feet_miss:.1f}')
    round9.write(round11.crossbow_all())


if __name__ == '__main__':
    main()
