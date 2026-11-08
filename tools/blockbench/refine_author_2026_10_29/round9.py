"""The author's message of 2026-11-08: the crossbow's attacks on the left button.

"First the crossbow: it needs attacks on LMB, different ones. The first is a push forward with one
hand, then a turn clockwise and a blow with the crossbow like a slap with a run-up, after that a turn
the other way and a blow with a swing onto the spine, the arm really going down forward and down, and
the last LMB is a hop back into a crouch and a shot if the crossbow is loaded; if it is not, just a
dash back, but with its own animation too. Make the animations as good as can be: take examples from
Assassin's Creed or from open 3D models and animations, or as you like."

No draft: made from the description, in the manner of a fighting game's chain (each blow wound up,
struck through the target and followed through, the weight under it; the next one starting from where
the last one left the body). Five animations, one file (refined/crossbow_2026-11-08.bbmodel):

    crossbow_thrust      LMB 1: the push. The weight drops back a hair, then the right arm drives the
                         crossbow straight out from the hip, its front first (the game holds the
                         crossbow along the forearm, prod beyond the fist), the left foot stepping in,
                         the right shoulder through, the free hand pulled back to the chest.
    crossbow_slap        LMB 2: a whole turn clockwise, a spinning backhand: the arm folds across the
                         chest, the hips lead the turn, the back to the mob, the head spots it first,
                         the arm whips out and the crossbow's flat sweeps through the face from the
                         player's left to the right as they come round; the follow-through wraps on.
    crossbow_spine       LMB 3: a whole turn the other way, the crossbow going up overhead in it; facing
                         the mob again, a breath at the top, then the arm comes down forward and down
                         onto the back of a mob bent over by the slap, the body folding over the blow,
                         the right foot stepping into it.
    crossbow_leap_shot   LMB 4, loaded: a crouch, a hop back, the crossbow up in both hands in the air,
                         landing into a deep crouch, the shot (the recoil), up.
    crossbow_dash_back   LMB 4, not loaded: a low dash back along the ground, the free hand brushing
                         the floor as it brakes, up.

The model does not travel: the mechanic pushes the player in the hop and the dash (Move.XBOW_SHOT,
XBOW_DASH); every frame the lowest point of the legs is put on the floor. Each starts and ends in the
pose Kinetrix holds a crossbow in (CombatModule, the carry: the trigger hand at the hip, the other under
the front), so the chain flows and Kinetrix takes over without a jump. The crossbow is not turned in
the fist: the game holds it as vanilla does, along the forearm (author, 2026-10-28, of the bow: it does
not twirl in the hand). The grey figure is the mob, at the reach the blows are made at; it never reaches
the game.

    python3 tools/blockbench/refine_author_2026_10_29/round9.py   (writes only this file; refine.py too)
"""
import json
import math
import os
import uuid

import numpy as np

import kit
import round5
from kit import L, kr

DST = os.path.join(kit.BB, 'refined', 'crossbow_2026-11-08.bbmodel')

#: The crossbow in the right hand: vanilla's item/crossbow.json, thirdperson_righthand (rotation,
#: translation, scale) and the sprite's diagonal turn the rig uses for every item.
XBOW = 'right_item_crossbow'
LEGS = round5.LEGS

# --------------------------------------------------------------------------------------------
# the crossbow: where its parts are on the forearm (it is not turned in the fist)
# --------------------------------------------------------------------------------------------

_FR = kr.item_frame('right', *kr.ITEMS[XBOW]['display'])
_XO = np.array(_FR['origin'], float)
_XR = kr.rot_zyx(*np.radians(_FR['rotation']))
_XS = _FR['scale']


def _sprite(sx, sy, sz=0.0):
    """A point of the crossbow's sprite (sprite-local pixels: +x towards the butt, +y along the prod,
    sz off its face) where it is on the rig at rest."""
    return tuple(_XO + _XR @ (np.array((-sx, -sy, sz)) * _XS))


#: The prod's middle (the front, what the push lands with), the butt, the prod's tips, the stock just
#: behind the prod (where the free hand holds it) and the flat of the prod (what the slap lands with).
for _n, _p in {'xfront': _sprite(-7.8, 0.0), 'xbutt': _sprite(9.2, 0.6), 'xtip_a': _sprite(-1.1, 8.1),
               'xtip_b': _sprite(-1.1, -8.1), 'xfore': _sprite(-3.5, 0.0), 'xmid': _sprite(1.0, 0.0)}.items():
    kit.POINTS[_n] = ('right_forearm', _p)

# --------------------------------------------------------------------------------------------
# the stance: Kinetrix's crossbow carry (CombatModule: the trigger hand at the hip, the other hand
# forward under the front, the shoulders turned a little behind it), as the rig's keys; fitted to
# Kinetrix's own angles to 0.0 px (its arms, playerAnimator's convention, through kinetrix_rig)
# --------------------------------------------------------------------------------------------

CARRY = {'torso': (0.0, -22.9, 0.0), 'right_arm': (61.5, 56.0, 37.0), 'right_forearm': (48.1, 0.0, 0.0),
         'left_arm': (62.7, 0.7, 24.6), 'left_forearm': (5.7, 0.0, 0.0)}

#: The mob: in front, at the reach of the blows (its middle this far, px), facing the player.
MOB_AT = (0.0, 0.0, -27.0)
#: The soles' middles at rest (x, z) and where the toes point (degrees about the vertical, + to the left).
HOME = {'right': (1.9, 0.0, 0.0), 'left': (-1.9, 0.0, 0.0)}
#: The mob standing ready, its own frame: the grey figure (a zombie, a skeleton) facing the player.
MOB_STAND = {'chest': (-4, 0, 0), 'right_arm': (16, 0, 6), 'right_forearm': (22, 0, 0), 'left_arm': (16, 0, -6),
             'left_forearm': (22, 0, 0), 'right_leg': (6, 0, 2), 'right_shin': (-10, 0, 0), 'left_leg': (6, 0, -2),
             'left_shin': (-10, 0, 0)}


def yawed(yaw, v, at=(0.0, 0.0, 0.0)):
    """A point given in the frame of a body turned by `yaw` (degrees, + to the left) about the
    vertical through `at`, in world px."""
    return tuple(np.asarray(at, float) + kit.rot_of((0.0, yaw, 0.0)) @ np.asarray(v, float))


def design(name, length, keys):
    """An animlib design (key poses with eases, every bone keyed) from [(t, pose[, ease])]."""
    d = L.Anim(name + '_design', length)
    for k in keys:
        t, pose = k[0], k[1]
        d.key(t, pose, k[2] if len(k) > 2 else 'auto', whole=True)
    return d


def arm_to(pose, side, targets, limits=None, reg=(0.01, 0.03, 0.01, 0.01)):
    """The arm of `side` solved so its points reach the targets [(point, world xyz, weight)], its
    designed angles preferred a little (the turn about its own length more). Returns (pose, miss px)."""
    b = dict(kit.arm_bounds(side))
    b.update(limits or {})
    q, err, _ = kit.solve(pose, [(n, np.asarray(x, float), w) for n, x, w in targets], kit.arm_vary(side), b, reg=list(reg))
    return q, err


class Feet:
    """Where the soles are: planted, or lifted and carried to their next place (a step: lifted in a half
    sine, moved and turned with smoothstep); none while the player is off the floor (the legs as designed)."""

    def __init__(self):
        self.start = dict(HOME)
        self.steps = {'right': [], 'left': []}
        self.air = []
        self.free = []

    def step(self, side, t0, t1, x, z, yaw=0.0, lift=1.5):
        self.steps[side].append((t0, t1, (x, z, yaw), lift))
        return self

    def off(self, t0, t1):
        """Both feet off the floor from t0 to t1 (a hop): the legs as designed."""
        self.air.append((t0, t1))
        return self

    def designed(self, t0, t1):
        """The legs as designed from t0 to t1 (a turn whose feet step round under the body)."""
        self.free.append((t0, t1))
        return self

    def at(self, t, side):
        if any(t0 < t < t1 for t0, t1 in self.air + self.free):
            return None
        x, z, yaw = self.start[side]
        lift = 0.0
        for t0, t1, (x1, z1, yaw1), h in self.steps[side]:
            if t >= t1:
                x, z, yaw = x1, z1, yaw1
                continue
            if t > t0:
                f = kit.smooth((t - t0) / (t1 - t0))
                x, z, yaw = x + (x1 - x) * f, z + (z1 - z) * f, yaw + (yaw1 - yaw) * f
                lift = h * math.sin(math.pi * (t - t0) / (t1 - t0))
            break
        return (x, lift, z), yaw


#: The thigh's turn about its own length and its swing out to the side are kept natural.
LEG_LIMITS = {('right_leg', 1): (-70.0, 70.0), ('left_leg', 1): (-70.0, 70.0),
              ('right_leg', 2): (-40.0, 40.0), ('left_leg', 2): (-40.0, 40.0)}


def legs_on(pose, feet, t, prev):
    """The legs solved so each planted sole is where `feet` says, flat, its toes the way it says
    (warm-started from the last frame). Returns (pose, worst miss px)."""
    q = dict(pose)
    miss = 0.0
    for side in ('right', 'left'):
        at = feet.at(t, side)
        if at is None:
            prev.pop(side, None)
            continue
        (x, y, z), yaw = at
        s = side[0]
        pos = np.array((x, y, z))
        fwd = kit.rot_of((0.0, yaw, 0.0)) @ np.array((0.0, 0.0, -2.0))
        tg = [(s + 'foot', pos, 1.0), (s + 'toe', pos + fwd, 0.6), (s + 'heel', pos - fwd, 0.6)]
        b = dict(kit.leg_bounds(side))
        b.update({k: v for k, v in LEG_LIMITS.items() if k[0] == side + '_leg'})
        q, err, x_ = kit.solve(q, tg, kit.leg_vary(side), b, reg=[0.004, 0.01, 0.004, 0.004], start=prev.get(side))
        prev[side] = x_
        miss = max(miss, err)
    return q, miss


def stooped(pose, lean):
    """The mob's own pose leant forward from the feet by `lean` degrees (as StoopFx leans it in the game)."""
    return kit.turned_about(dict(pose), kit.rot_of((-lean, 0.0, 0.0)), 'lfoot')


def mob_at(mob, t):
    """The mob's keys (bones *2) at t: its own pose on the floor, put in front of the player (where the
    mob function says, else at MOB_AT)."""
    return kit.as_partner(kit.grounded(mob(t)), getattr(mob, 'place', MOB_AT))


def mob_pt(mob, t, name, offset=(0.0, 0.0, 0.0)):
    """A point of the mob as it is at t (world px), moved by offset."""
    return kit.pt(kit.fk(mob_at(mob, t)), name + '2') + np.asarray(offset, float)


def mob_design(length, keys):
    d = design('mob', length, keys)
    return lambda t: kit.design(d, t)


def bake(name, length, design_, feet, mob=None, body=None, lift=None, extra=()):
    """The animation: every frame of the design (an animlib design, or the pose as a function of t; body:
    the whole body's turn at t, overriding the design's), the planted legs solved onto the feet, the
    lowest point of the legs on the floor (or `lift` px over it), the mob beside it."""
    a = L.Anim(name, length, 'once')
    prev, worst, last, last2 = {}, 0.0, None, None
    for t in sorted(set(kit.frames(0, length)) | set(extra)):
        p = design_(t) if callable(design_) else kit.design(design_, t)
        if body is not None:
            p['body'] = tuple(body(t))
        p, miss = legs_on(p, feet, t, prev)
        worst = max(worst, miss)
        p = kit.grounded(p, clearance=lift(t) if lift else 0.0, bones=LEGS)
        p['body'] = tuple(kit.continuous(last, p['body']))
        last = p['body']
        if mob is not None:
            p.update(mob_at(mob, t))
            p['body2'] = tuple(kit.continuous(last2, p['body2']))
            last2 = p['body2']
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    a.feet_miss = worst
    return a


def stepping(p, yaw, inspin, step=24.0):
    """The feet stepping round under a turning body (as round6's turns): a step a half turn."""
    phase = math.radians(yaw) * 2
    r, l = max(0.0, math.sin(phase)) * inspin, max(0.0, -math.sin(phase)) * inspin
    for side, k in (('right', r), ('left', l)):
        leg, shin = p.get(side + '_leg', (0, 0, 0)), p.get(side + '_shin', (0, 0, 0))
        p[side + '_leg'] = (leg[0] + step * k, leg[1], leg[2])
        p[side + '_shin'] = (shin[0] - 1.6 * step * k, shin[1], shin[2])
    return p


def spotting(turned, sign):
    """The head's turn on the chest in a whole turn (degrees, + to the left), spotting the mob as a
    fighter does: it stays on the mob as long as the neck allows, whips round ahead of the body and
    finds it again first. turned: how far round the shoulders are (0-360, the way of the turn);
    sign: -1 turning clockwise, +1 the other way."""
    s = turned
    if s <= 70:
        h = s
    elif s <= 150:
        h = 70.0
    elif s <= 215:
        h = 70.0 - 140.0 * kit.smooth((s - 150) / 65.0)
    elif s <= 290:
        h = -70.0
    else:
        h = s - 360.0
    return -sign * h


# --------------------------------------------------------------------------------------------
# 1. crossbow_thrust: the push
# --------------------------------------------------------------------------------------------

#: Times (s): the weight back and the fist at the hip (LOAD), the crossbow's front on the mob's chest
#: (HIT: Crossbow.THRUST_HIT, tick 4), the arm locked out (FOLLOW), back halfway (BACK), the stance (LEN).
TH_LOAD, TH_HIT, TH_FOLLOW, TH_BACK, TH_LEN = 0.1, 0.2, 0.27, 0.36, 0.55


def _mob_thrust():
    hit = {**MOB_STAND, 'body@p': (0, 0, 3.0), 'body': (7, 0, 0), 'chest': (8, 0, 0), 'head': (14, 0, 0),
           'right_arm': (34, 0, 16), 'left_arm': (34, 0, -16)}
    after = {**MOB_STAND, 'body@p': (0, 0, 3.2), 'body': (2, 0, 0)}
    return mob_design(TH_LEN, [(0, MOB_STAND), (TH_HIT, MOB_STAND), (TH_HIT + 0.1, hit, 'out'), (TH_LEN, after, 'inout')])


def crossbow_thrust():
    """The weight drops back a hair and the fist comes back to the hip, the crossbow levelled at the
    mob, the free hand off it, up as a guard; then the push: the left foot steps in, the hips and the
    right shoulder drive through, the arm shoots straight out and the crossbow's front lands on the
    mob's chest (0.2 s), the free hand pulled back to the chest; the arm locks out, then the foot
    steps back and the carry comes back."""
    mob = _mob_thrust()
    load = {**CARRY, 'body@p': (0, -0.8, 0.8), 'torso': (0, -34, 0), 'chest': (-2, 0, 0), 'head': (2, 34, 0),
            'right_arm': (-10, 20, 15), 'right_forearm': (95, 0, 0), 'left_arm': (55, -15, -5), 'left_forearm': (75, 0, 0)}
    load, e1 = arm_to(load, 'right', [('rhand', (5.4, 14.5, 0.5), 1.0), ('xfront', (3.8, 15.5, -10.5), 0.6)])
    load, e2 = arm_to(load, 'left', [('lhand', (-2.0, 19.5, -7.5), 1.0)])
    hit = {'body@p': (0, -1.2, -2.8), 'body': (-3, 0, 0), 'torso': (0, 26, 0), 'chest': (-8, 0, 0), 'head': (8, -26, 0),
           'right_arm': (88, 5, 0), 'right_forearm': (5, 0, 0), 'left_arm': (35, 10, -10), 'left_forearm': (110, 0, 0)}
    front = mob_pt(mob, TH_HIT, 'chestfront', (0.6, 0.0, 0.4))
    straight = {('right_forearm', 0): (0.0, 14.0)}
    hit, e3 = arm_to(hit, 'right', [('xfront', front, 1.0), ('xbutt', front + np.array((3.0, -0.6, 14.5)), 0.4)], straight)
    hit, e4 = arm_to(hit, 'left', [('lhand', (-3.0, 18.0, -6.5), 1.0)])
    follow = {**hit, 'body@p': (0, -1.3, -3.0), 'torso': (0, 29, 0), 'chest': (-10, 0, 0), 'head': (10, -29, 0)}
    follow, e5 = arm_to(follow, 'right', [('xfront', front + np.array((0.0, -0.2, -1.4)), 1.0),
                                          ('xbutt', front + np.array((3.0, -0.6, 13.1)), 0.4)], straight)
    # back: the fist drawn in to the hip again, the crossbow still on the mob, the free hand coming to it
    back = {**L.mix(follow, CARRY, 0.5), 'body@p': (0, -0.9, -1.2), 'body': (-1, 0, 0), 'torso': (0, 2, 0), 'chest': (-5, 0, 0),
            'head': (4, -2, 0), 'right_arm': load['right_arm'], 'right_forearm': load['right_forearm']}
    back, e6 = arm_to(back, 'right', [('rhand', (4.8, 16.0, -3.0), 1.0), ('xfront', (3.0, 17.5, -14.0), 0.6)])
    back, e7 = arm_to(back, 'left', [('lhand', kit.pt(kit.fk(back), 'xfore') + np.array((-0.8, -1.6, 0.0)), 0.7)])
    keys = [(0, CARRY), (TH_LOAD, load, 'inout'), (TH_HIT, hit, 'hit'), (TH_FOLLOW, follow, 'out'),
            (TH_BACK, back, 'inout'), (TH_LEN, CARRY, 'inout')]
    feet = (Feet().step('left', 0.08, 0.19, -2.6, -6.5, 4, lift=1.8).step('right', 0.1, 0.2, 1.9, 0.0, -26, lift=0.0)
            .step('left', 0.37, 0.5, -1.9, 0.0, 0, lift=1.2).step('right', 0.37, 0.5, 1.9, 0.0, 0, lift=0.0))
    a = bake('crossbow_thrust', TH_LEN, design('thrust', TH_LEN, keys), feet, mob=mob, extra=(TH_HIT,))
    a.errors = (e1, e2, e3, e4, e5, e6, e7)
    return a


# --------------------------------------------------------------------------------------------
# 2. crossbow_slap: a whole turn clockwise, a spinning backhand
# --------------------------------------------------------------------------------------------

#: Times (s): wound up the other way (WIND), the crossbow's flat on the mob's face (CONTACT:
#: Crossbow.SLAP_HIT, tick 7), swept on past it (FOLLOW), the stance (LEN).
SL_WIND, SL_CONTACT, SL_FOLLOW, SL_LEN = 0.09, 0.35, 0.44, 0.75
#: The body's turn (degrees, clockwise is -): wound up 14 to the left, round fast, through the mob's
#: face at 292 (side-on, the right shoulder to it), slowing to a whole turn.
SLAP_YAW = kit.keys((0.0, 0.0), (SL_WIND, 14.0), (0.19, -80.0), (0.26, -175.0), (0.31, -245.0), (SL_CONTACT, -292.0),
                    (SL_FOLLOW, -336.0), (0.54, -355.0), (0.66, -360.0), (SL_LEN, -360.0))
#: The shoulders' turn on the hips (torso): coiled to the left in the wind-up, lagging the hips in the
#: turn, whipping through into the blow.
SLAP_TORSO = kit.keys((0.0, CARRY['torso'][1]), (SL_WIND, 16.0), (0.19, 24.0), (0.26, 18.0), (0.31, 8.0),
                      (SL_CONTACT, -12.0), (SL_FOLLOW, -20.0), (0.54, -20.0), (SL_LEN, CARRY['torso'][1]))


def _mob_slap():
    struck = {**MOB_STAND, 'body': (0, -16, 0), 'head': (-6, -38, 10), 'chest': (-8, 0, 6), 'right_arm': (10, 0, 22),
              'left_arm': (24, 0, -10)}
    bent = {**MOB_STAND, 'head': (-24, -14, 0), 'chest': (-20, 0, 0), 'right_arm': (6, 0, 10), 'left_arm': (6, 0, -10),
            'right_leg': (14, 0, 2), 'right_shin': (-22, 0, 0), 'left_leg': (14, 0, -2), 'left_shin': (-22, 0, 0)}
    d = design('mob', SL_LEN, [(0, MOB_STAND), (SL_CONTACT, MOB_STAND), (SL_CONTACT + 0.07, struck, 'out'),
                               (0.6, bent, 'inout'), (SL_LEN, bent)])
    lean = kit.keys((0.0, 0.0), (SL_CONTACT + 0.07, 0.0), (0.6, 18.0), (SL_LEN, 20.0))
    return lambda t: stooped(kit.design(d, t), lean(t))


def crossbow_slap():
    """Wound up the other way, the weight onto the left foot, the arm folded across the chest (the
    crossbow by the left shoulder); the hips lead the turn round to the right, the shoulders after, the
    head staying on the mob, then whipping round ahead to find it again; with the back to the mob the
    arm unfolds out to the right, lagging, and whips through: the crossbow's flat sweeps through the
    mob's face from the player's left to the right, side-on, the right shoulder to it, the feet wide;
    the free arm flung back against it. It sweeps on, the turn slows to a whole one, the carry comes back."""
    mob = _mob_slap()
    wind = {**CARRY, 'body@p': (-0.6, -0.8, 0.0), 'chest': (-4, 0, 0), 'right_forearm': (100, 0, 0),
            'left_arm': (40, 0, 10), 'left_forearm': (95, 0, 0),
            'right_leg': (-6, 0, 3), 'right_shin': (-8, 0, 0), 'left_leg': (2, 0, -2), 'left_shin': (-10, 0, 0)}
    wind['torso'] = (0, SLAP_TORSO(SL_WIND), 0)
    y = SLAP_YAW(SL_WIND)
    wind['body'] = (0, y, 0)
    wind, e1 = arm_to(wind, 'right', [('rhand', yawed(y, (-2.5, 21.0, -5.0)), 1.0), ('xfront', yawed(y, (-10.0, 24.0, -4.0)), 0.6)])
    wind, e2 = arm_to(wind, 'left', [('lhand', yawed(y, (-4.5, 16.0, -5.5)), 1.0)])
    turn = {**wind, 'body@p': (0, -1.0, 0.0), 'torso': (0, SLAP_TORSO(0.19), 0)}
    # the arm unfolds low, in front of the chest, like a door opening: the fist across to the right, the
    # crossbow turning from the left through the front (it went up over the head between the two)
    y = SLAP_YAW(0.225)
    unfold = {**turn, 'torso': (0, SLAP_TORSO(0.225), 0), 'body': (0, y, 0)}
    unfold, e10 = arm_to(unfold, 'right', [('rhand', yawed(y, (3.5, 21.0, -7.5)), 1.0), ('xfront', yawed(y, (4.5, 22.5, -18.5)), 0.6)])
    y = SLAP_YAW(0.26)
    open_ = {**unfold, 'torso': (0, SLAP_TORSO(0.26), 0), 'body': (0, y, 0), 'left_arm': (50, 0, 20), 'left_forearm': (100, 0, 0)}
    open_, e3 = arm_to(open_, 'right', [('rhand', yawed(y, (10.0, 22.0, -5.0)), 1.0), ('xfront', yawed(y, (16.0, 24.5, -8.0)), 0.6)])
    y = SLAP_YAW(0.31)
    out = {**open_, 'torso': (0, SLAP_TORSO(0.31), 0), 'body': (0, y, 0), 'body@p': (0, -1.4, 0.0)}
    out, e4 = arm_to(out, 'right', [('rhand', yawed(y, (13.0, 24.5, -4.0)), 1.0), ('xfront', yawed(y, (19.5, 27.0, -6.5)), 0.6)])
    y = SLAP_YAW(SL_CONTACT)
    face = mob_pt(mob, SL_CONTACT, 'head', (-2.0, -0.5, 3.6))
    hit = {**out, 'torso': (0, SLAP_TORSO(SL_CONTACT), 0), 'body': (0, y, 0), 'body@p': (0, -1.8, 0.0),
           'right_leg': (-4, 0, 16), 'right_shin': (-14, 0, 0), 'left_leg': (4, 0, -14), 'left_shin': (-12, 0, 0)}
    hit, e5 = arm_to(hit, 'right', [('xfront', face, 1.0), ('rhand', face + np.array((3.0, -2.5, 6.5)), 0.5)])
    hit, e6 = arm_to(hit, 'left', [('lhand', yawed(y, (-12.0, 17.0, 4.5)), 1.0)])
    y = SLAP_YAW(SL_FOLLOW)
    follow = {**hit, 'torso': (0, SLAP_TORSO(SL_FOLLOW), 0), 'body': (0, y, 0)}
    follow, e7 = arm_to(follow, 'right', [('xfront', yawed(y, (15.5, 25.0, 6.0)), 1.0), ('rhand', yawed(y, (11.0, 23.0, 1.0)), 0.5)])
    follow, e8 = arm_to(follow, 'left', [('lhand', yawed(y, (-10.0, 16.5, 3.0)), 1.0)])
    y = SLAP_YAW(0.54)
    settle = {**follow, 'torso': (0, SLAP_TORSO(0.54), 0), 'body': (0, y, 0), 'body@p': (0, -1.0, 0.0),
              'right_leg': (-2, 0, 6), 'right_shin': (-8, 0, 0), 'left_leg': (2, 0, -5), 'left_shin': (-8, 0, 0)}
    settle, e9 = arm_to(settle, 'right', [('rhand', yawed(y, (6.5, 16.5, -3.5)), 1.0), ('xfront', yawed(y, (3.0, 19.0, -12.5)), 0.6)])
    for p in (wind, turn, unfold, open_, out, hit, follow, settle):
        p.pop('body', None)
    keys = [(0, CARRY), (SL_WIND, wind, 'inout'), (0.19, turn), (0.225, unfold), (0.26, open_), (0.31, out), (SL_CONTACT, hit),
            (SL_FOLLOW, follow, 'out'), (0.54, settle, 'inout'), (SL_LEN, CARRY, 'inout')]
    dsg = design('slap', SL_LEN, keys)

    def pose_at(t):
        # the head spots the mob; the feet step round under the turn
        p = kit.design(dsg, t)
        yaw = SLAP_YAW(t)
        p['head'] = (p['head'][0], spotting(-(yaw + p['torso'][1]), -1), p['head'][2])
        return stepping(p, yaw, kit.span(t, SL_WIND, SL_WIND + 0.04) * (1 - kit.span(t, 0.29, SL_CONTACT)))

    feet = Feet().designed(SL_WIND - 0.01, SL_LEN + 1)
    a = bake('crossbow_slap', SL_LEN, pose_at, feet, mob, body=lambda t: (0.0, SLAP_YAW(t), 0.0), extra=(SL_CONTACT,))
    a.errors = (e1, e2, e3, e4, e5, e6, e7, e8, e9, e10)
    return a


# --------------------------------------------------------------------------------------------
# 3. crossbow_spine: a whole turn the other way, the blow from above onto the spine
# --------------------------------------------------------------------------------------------

#: Times (s): a breath of wind-up clockwise (WIND), round to the left with the crossbow going up, facing
#: the mob again with it cocked behind the head (COCK, a last inch to HOLD), the crossbow's front down on
#: the mob's spine (HIT: Crossbow.SPINE_HIT, tick 11), on down (FOLLOW), up (UP), the stance (LEN).
SP_WIND, SP_COCK, SP_HOLD, SP_HIT, SP_FOLLOW, SP_UP, SP_LEN = 0.06, 0.42, 0.47, 0.55, 0.62, 0.76, 0.95
SPINE_YAW = kit.keys((0.0, 0.0), (SP_WIND, -10.0), (0.17, 80.0), (0.25, 180.0), (0.33, 285.0), (SP_COCK, 355.0),
                     (SP_HOLD, 360.0), (SP_LEN, 360.0))
SPINE_TORSO = kit.keys((0.0, CARRY['torso'][1]), (SP_WIND, -30.0), (0.17, -12.0), (0.25, -4.0), (0.33, -8.0),
                       (SP_COCK, -20.0), (SP_HOLD, -24.0), (SP_HIT, 18.0), (SP_FOLLOW, 22.0), (SP_UP, 0.0),
                       (SP_LEN, CARRY['torso'][1]))
#: How far the mob is bent over (degrees from its feet): by the slap, then driven down by the blow.
#: The slap has knocked the mob back a little: it stands further than for the push.
SPINE_MOB_AT = (0.0, 0.0, -32.0)
SPINE_LEAN = kit.keys((0.0, 20.0), (SP_HIT, 20.0), (SP_HIT + 0.07, 38.0, 'out'), (0.8, 34.0), (SP_LEN, 28.0))


def _mob_spine():
    bent = {**MOB_STAND, 'head': (-24, -10, 0), 'chest': (-20, 0, 0), 'right_arm': (6, 0, 10), 'left_arm': (6, 0, -10),
            'right_leg': (14, 0, 2), 'right_shin': (-22, 0, 0), 'left_leg': (14, 0, -2), 'left_shin': (-22, 0, 0)}
    down = {**bent, 'head': (-34, 0, 0), 'chest': (-26, 0, 0), 'right_arm': (-14, 0, 16), 'left_arm': (-14, 0, -16),
            'right_leg': (30, 0, 3), 'right_shin': (-50, 0, 0), 'left_leg': (30, 0, -3), 'left_shin': (-50, 0, 0)}
    d = design('mob', SP_LEN, [(0, bent), (SP_HIT, bent), (SP_HIT + 0.07, down, 'out'), (SP_LEN, down, 'inout')])

    def mob(t):
        return stooped(kit.design(d, t), SPINE_LEAN(t))
    mob.place = SPINE_MOB_AT
    return mob


def crossbow_spine():
    """A breath of wind-up the other way, then round to the left, the hips first, the crossbow going up
    out to the side and over: facing the mob again the arm is cocked high, the crossbow hanging down
    behind the head, the body leaning back over the left foot, the free hand reaching out at the mob; a
    last inch, then the blow: the right foot steps in, the body folds forward over it, the shoulders
    square and the arm comes down forward and down, the crossbow's front onto the spine of the mob bent
    over by the slap, the free hand pulled back to the hip; on down a little, then up into the carry."""
    mob = _mob_spine()
    y = SPINE_YAW(SP_WIND)
    wind = {**CARRY, 'body@p': (0.4, -0.8, 0.0), 'torso': (0, SPINE_TORSO(SP_WIND), 0), 'body': (0, y, 0)}
    y = SPINE_YAW(0.17)
    rise = {**wind, 'torso': (0, SPINE_TORSO(0.17), 0), 'body': (0, y, 0), 'body@p': (0, -1.0, 0.0),
            'right_arm': (20, 0, 100), 'right_forearm': (30, 0, 0), 'left_arm': (20, 0, -70), 'left_forearm': (20, 0, 0)}
    rise, e1 = arm_to(rise, 'right', [('rhand', yawed(y, (13.0, 25.0, 1.0)), 1.0), ('xfront', yawed(y, (18.0, 31.0, 3.0)), 0.6)])
    rise, e2 = arm_to(rise, 'left', [('lhand', yawed(y, (-13.0, 19.0, 1.0)), 1.0)])
    y = SPINE_YAW(0.25)
    over = {**rise, 'torso': (0, SPINE_TORSO(0.25), 0), 'body': (0, y, 0), 'right_arm': (170, 0, 20), 'right_forearm': (40, 0, 0)}
    over, e3 = arm_to(over, 'right', [('rhand', yawed(y, (7.0, 33.0, 0.5)), 1.0), ('xfront', yawed(y, (8.0, 40.0, 3.0)), 0.6)])
    y = SPINE_YAW(SP_COCK)
    cock = {**over, 'torso': (0, SPINE_TORSO(SP_COCK), 0), 'body': (4, y, 0), 'chest': (10, 0, 0), 'body@p': (0, -1.0, 1.4),
            'right_arm': (175, 0, -5), 'right_forearm': (100, 0, 0), 'left_arm': (80, -10, -10), 'left_forearm': (10, 0, 0)}
    cock, e4 = arm_to(cock, 'right', [('rhand', yawed(y, (4.5, 32.0, 2.5)), 1.0), ('xfront', yawed(y, (3.0, 24.0, 8.5)), 0.6)],
                      limits={('right_arm', 1): (-60.0, 60.0)})
    cock, e5 = arm_to(cock, 'left', [('lhand', yawed(y, (-4.0, 23.0, -9.5)), 1.0)])
    hold = {**cock, 'torso': (0, SPINE_TORSO(SP_HOLD), 0), 'chest': (12, 0, 0), 'body@p': (0, -1.1, 1.6)}
    hold, e6 = arm_to(hold, 'right', [('rhand', yawed(y, (4.5, 32.4, 3.2)), 1.0), ('xfront', yawed(y, (3.0, 24.0, 9.5)), 0.6)],
                      limits={('right_arm', 1): (-60.0, 60.0)})
    back = mob_pt(mob, SP_HIT, 'back', (0.0, 1.2, 0.0))
    hit = {**hold, 'torso': (0, SPINE_TORSO(SP_HIT), 0), 'body': (-5, 360, 0), 'chest': (-20, 0, 0), 'body@p': (0, -2.4, -3.0),
           'right_arm': (40, 0, 0), 'right_forearm': (15, 0, 0), 'left_arm': (-30, 0, -15), 'left_forearm': (35, 0, 0)}
    hit, e7 = arm_to(hit, 'right', [('xfront', back, 1.0), ('rhand', back + np.array((1.5, 4.5, 9.0)), 0.4)],
                     {('right_forearm', 0): (0.0, 30.0)})
    hit, e8 = arm_to(hit, 'left', [('lhand', (-6.0, 11.0, 2.0), 1.0)])
    follow = {**hit, 'torso': (0, SPINE_TORSO(SP_FOLLOW), 0), 'body': (-7, 360, 0), 'chest': (-25, 0, 0), 'body@p': (0, -2.9, -3.3)}
    # on down with the mob as the blow drives it lower, the arm pointing forward and down
    down = mob_pt(mob, SP_FOLLOW, 'back', (0.0, 0.6, 0.6))
    follow, e9 = arm_to(follow, 'right', [('xfront', down, 1.0), ('rhand', down + np.array((1.5, 6.5, 8.0)), 0.4)],
                        {('right_forearm', 0): (0.0, 30.0)})
    up = L.mix(follow, {**CARRY, 'body': (0, 360, 0)}, 0.6)
    up['body@p'] = (0, -1.4, -1.4)
    # the whole body's lean (its turn is SPINE_YAW): taken out of the keys, eased on its own
    poses = [(0.0, CARRY), (SP_WIND, wind, 'inout'), (0.17, rise), (0.25, over), (SP_COCK, cock, 'out'),
             (SP_HOLD, hold, 'linear'), (SP_HIT, hit, 'in'), (SP_FOLLOW, follow, 'out'), (SP_UP, up, 'inout'),
             (SP_LEN, CARRY, 'inout')]
    tilt = kit.keys(*[(k[0], (k[1].get('body', (0, 0, 0))[0], 0.0, k[1].get('body', (0, 0, 0))[2])) for k in poses])
    keys = [(k[0], {kk: vv for kk, vv in k[1].items() if kk != 'body'}) + tuple(k[2:]) for k in poses]
    dsg = design('spine', SP_LEN, keys)

    def pose_at(t):
        p = kit.design(dsg, t)
        yaw = SPINE_YAW(t)
        p['head'] = (p['head'][0], spotting(yaw + p['torso'][1], 1), p['head'][2])
        return stepping(p, yaw, kit.span(t, SP_WIND, SP_WIND + 0.04) * (1 - kit.span(t, 0.36, SP_COCK)))

    def body(t):
        tx, _, tz = tilt(t)
        return (tx, SPINE_YAW(t), tz)

    # the turn steps round under the body; then planted, the right foot steps into the blow and back
    feet = (Feet().designed(SP_WIND - 0.01, SP_COCK)
            .step('right', SP_HOLD - 0.01, SP_HIT, 2.3, -6.5, -10, lift=1.6)
            .step('left', SP_HOLD, SP_HIT, -1.9, 0.6, 18, lift=0.0)
            .step('right', SP_UP - 0.02, 0.88, 1.9, 0.0, 0, lift=1.2)
            .step('left', SP_UP - 0.02, 0.88, -1.9, 0.0, 0, lift=0.0))
    a = bake('crossbow_spine', SP_LEN, pose_at, feet, mob, body=body, extra=(SP_HIT,))
    a.errors = (e1, e2, e3, e4, e5, e6, e7, e8, e9)
    return a


# --------------------------------------------------------------------------------------------
# 4. crossbow_leap_shot: the hop back into a crouch and the shot (loaded)
# --------------------------------------------------------------------------------------------

#: Times (s): down into the take-off (DIP), off the floor (OFF: the push starts, Move.PUSH_TICK), the top
#: of the hop (TOP), the feet down (LAND), the deepest of the crouch (DEEP), the shot (SHOT:
#: Crossbow.LEAP_SHOT, tick 11), the recoil (KICK), still aiming (AIM), up (UP), the stance (LEN).
LS_DIP, LS_OFF, LS_TOP, LS_LAND, LS_DEEP, LS_SHOT, LS_KICK, LS_AIM, LS_UP, LS_LEN = (
    0.07, 0.1, 0.22, 0.36, 0.44, 0.55, 0.6, 0.76, 1.0, 1.15)
#: How high the hop lifts the body (px over the floor; the mechanic carries the player back on the ground).
LEAP_LIFT = kit.keys((0.0, 0.0), (LS_OFF, 0.0), (LS_TOP, 4.5), (LS_LAND, 0.0), (LS_LEN, 0.0))


#: The hop and the dash leave the mob where the blow on its spine left it, getting up; the hop's bolt
#: hits it (a jolt at the shot).
LEAP_MOB_AT = (0.0, 0.0, -36.0)


def _mob_after(length, struck=None):
    bent = {**MOB_STAND, 'head': (-20, 0, 0), 'chest': (-18, 0, 0), 'right_leg': (14, 0, 2), 'right_shin': (-22, 0, 0),
            'left_leg': (14, 0, -2), 'left_shin': (-22, 0, 0)}
    keys = [(0, bent), (length, MOB_STAND, 'inout')]
    lean = kit.keys((0.0, 26.0), (length * 0.8, 0.0), (length, 0.0))
    if struck is not None:
        jolt = {**bent, 'body@p': (0, 0, 1.5), 'head': (6, 0, 0), 'chest': (0, 0, 0), 'right_arm': (40, 0, 20), 'left_arm': (40, 0, -20)}
        keys = [(0, bent), (struck, bent), (struck + 0.06, jolt, 'out'), (length, {**MOB_STAND, 'body@p': (0, 0, 1.5)}, 'inout')]
        lean = kit.keys((0.0, 26.0), (struck, 12.0), (struck + 0.06, -6.0, 'out'), (length, 0.0))
    d = design('mob', length, keys)

    def mob(t):
        return stooped(kit.design(d, t), lean(t))
    mob.place = LEAP_MOB_AT
    return mob


#: Kinetrix's crossbow aimed (CombatModule with a loaded crossbow: up a little from the carry, level and
#: ready) as the rig's keys, fitted to its arms to 0.0 px as CARRY is (author, 2026-11-08: "for Kinetrix's
#: animations, not vanilla's, so it does not look different"): every shot of the crossbow's moves is
#: aimed so, not at the eyes as vanilla's loaded crossbow.
AIM = {**CARRY, 'right_arm': (66.0, 53.2, 27.3), 'right_forearm': (44.1, 0.0, 0.0), 'left_arm': (74.8, -12.6, -11.5),
       'left_forearm': (5.7, 0.0, 0.0)}
_AIMED = {}


def _aim(pose, low=0.0):
    """Both hands on the crossbow as Kinetrix aims it (AIM), carried with the neck (down in a crouch, up in
    a hop, forward in a lean): where it is at the chest standing, level, the front ahead. `low` is not used
    (it was the drop of an aim at the eyes)."""
    if not _AIMED:
        _AIMED.update(kit.at(AIM, 'xfront', 'xbutt', 'lhand', 'neck'))
    q = {**pose, **{k: AIM[k] for k in ('right_arm', 'right_forearm', 'left_arm', 'left_forearm')}}
    d = kit.pt(kit.fk(q), 'neck') - _AIMED['neck']
    q, e1 = arm_to(q, 'right', [('xfront', _AIMED['xfront'] + d, 1.0), ('xbutt', _AIMED['xbutt'] + d, 0.7)])
    q, e2 = arm_to(q, 'left', [('lhand', _AIMED['lhand'] + d, 1.0)])
    return q, max(e1, e2)


def crossbow_leap_shot():
    """Down a little and off both feet backwards, the crossbow coming up into both hands in the air, the
    legs reaching back for the floor; down into a deep crouch, the left foot in front, the right knee
    low, the crossbow level at the mob; the shot (the recoil kicks the arms and the shoulders up and
    back), a breath still aiming, then up into the carry."""
    dip = {**CARRY, 'body@p': (0, -2.0, -0.6), 'chest': (-8, 0, 0), 'right_leg': (14, 0, 2), 'right_shin': (-26, 0, 0),
           'left_leg': (14, 0, -2), 'left_shin': (-26, 0, 0)}
    air = {**CARRY, 'body': (-6, 0, 0), 'chest': (-8, 0, 0), 'torso': (0, -10, 0), 'right_leg': (-18, 0, 4), 'right_shin': (-30, 0, 0),
           'left_leg': (10, 0, -3), 'left_shin': (-40, 0, 0)}
    air, e1 = _aim(air, 2.0)
    land = {**air, 'body': (-4, 0, 0), 'body@p': (0, -4.0, 0), 'chest': (-8, 0, 0)}
    land, e2 = _aim(land, 5.5)
    deep = {**land, 'body': (-3, 0, 0), 'body@p': (0, -6.0, 0), 'chest': (-6, 0, 0)}
    deep, e3 = _aim(deep, 7.0)
    shot = {**deep, 'body@p': (0, -5.6, 0)}
    shot, e4 = _aim(shot, 6.6)
    kick = {**shot, 'chest': (0, 0, 0), 'body': (-1, 0, 0), 'right_arm': tuple(np.add(shot['right_arm'], (9, 0, 0))),
            'left_arm': tuple(np.add(shot['left_arm'], (9, 0, 0))), 'right_forearm': tuple(np.add(shot['right_forearm'], (6, 0, 0)))}
    aim = dict(shot)
    up = L.mix(aim, CARRY, 0.55)
    up['body@p'] = (0, -2.5, 0)
    up['body'] = (-1, 0, 0)
    keys = [(0, CARRY), (LS_DIP, dip, 'inout'), (LS_OFF, dip), (LS_TOP, air, 'out'), (LS_LAND, land, 'in'),
            (LS_DEEP, deep, 'out'), (LS_SHOT, shot, 'inout'), (LS_KICK, kick, 'out2'), (LS_AIM, aim, 'inout'),
            (LS_UP, up, 'inout'), (LS_LEN, CARRY, 'inout')]
    feet = (Feet().off(LS_OFF, LS_LAND).step('left', LS_OFF, LS_LAND, -2.4, -4.0, 4, lift=0.0)
            .step('right', LS_OFF, LS_LAND, 2.2, 4.2, -14, lift=0.0)
            .step('right', LS_UP - 0.1, LS_UP + 0.04, 1.9, 0.0, 0, lift=1.4)
            .step('left', LS_UP, LS_LEN - 0.03, -1.9, 0.0, 0, lift=1.2))
    a = bake('crossbow_leap_shot', LS_LEN, design('leap_shot', LS_LEN, keys), feet, mob=_mob_after(LS_LEN, LS_SHOT + 0.04),
             lift=LEAP_LIFT, extra=(LS_SHOT,))
    a.errors = (e1, e2, e3, e4)
    return a


# --------------------------------------------------------------------------------------------
# 5. crossbow_dash_back: a low dash back (not loaded)
# --------------------------------------------------------------------------------------------

#: Times (s): down and forward (DIP), pushed back (OFF: Move.PUSH_TICK) low over the floor, the left
#: hand brushing it as it brakes (BRUSH to BRAKE), up (UP), the stance (LEN).
DB_DIP, DB_OFF, DB_LOW, DB_BRUSH, DB_BRAKE, DB_UP, DB_LEN = 0.06, 0.1, 0.2, 0.3, 0.42, 0.56, 0.72


def crossbow_dash_back():
    """Down and forward over the knees, then pushed back low along the floor: the left foot reaches
    back, the right knee bent in front, the body leaning over it to keep the eyes on the mob, the
    crossbow pulled in to the hip; braking, the left hand brushes the floor; up into the carry."""
    dip = {**CARRY, 'body@p': (0, -1.8, -0.8), 'chest': (-10, 0, 0), 'body': (-4, 0, 0)}
    low = {**CARRY, 'body@p': (0, -4.5, 0.5), 'body': (-12, 0, 0), 'chest': (-12, 0, 0), 'torso': (0, -30, 0),
           'right_arm': (30, 30, 20), 'right_forearm': (100, 0, 0), 'left_arm': (10, 0, -30), 'left_forearm': (20, 0, 0)}
    low, e1 = arm_to(low, 'right', [('rhand', (5.8, 12.5, -2.0), 1.0), ('xfront', (3.5, 14.0, -11.0), 0.6)])
    low, e2 = arm_to(low, 'left', [('lhand', (-8.0, 9.0, 1.0), 1.0)])
    # down on the left side so the fist reaches the floor (the body rolled a little to the left)
    brush = {**low, 'body@p': (0, -7.2, 1.0), 'body': (-14, 0, 9), 'chest': (-14, 0, 6), 'left_arm': (-10, 0, -10)}
    brush, e3 = arm_to(brush, 'left', [('lfist', (-8.5, 0.5, 0.5), 1.0)])
    brake = {**brush, 'body@p': (0, -7.0, 0.6)}
    brake, e4 = arm_to(brake, 'left', [('lfist', (-8.5, 0.5, -1.5), 1.0)])
    up = L.mix(brake, CARRY, 0.6)
    up['body@p'] = (0, -1.6, 0.2)
    keys = [(0, CARRY), (DB_DIP, dip, 'inout'), (DB_OFF, dip), (DB_LOW, low, 'out'), (DB_BRUSH, brush, 'inout'),
            (DB_BRAKE, brake, 'inout'), (DB_UP, up, 'inout'), (DB_LEN, CARRY, 'inout')]
    feet = (Feet().step('left', DB_OFF - 0.02, DB_LOW, -2.2, 6.5, 6, lift=1.0)
            .step('right', DB_OFF, DB_LOW, 2.2, -3.0, -20, lift=0.6)
            .step('left', DB_BRAKE + 0.02, DB_UP + 0.04, -1.9, 0.0, 0, lift=1.4)
            .step('right', DB_UP - 0.02, DB_LEN - 0.04, 1.9, 0.0, 0, lift=1.0))
    a = bake('crossbow_dash_back', DB_LEN, design('dash_back', DB_LEN, keys), feet, mob=_mob_after(DB_LEN))
    a.errors = (e1, e2, e3, e4)
    return a


#: Every frame smoothed over this much (s) once baked (author, 2026-11-08: "smoother"): the quickest parts
#: of the turns (the arms whipping round at the slap and over the head into the blow onto the spine) had
#: half as much again of a jolt as the sword's turn (sword_spin) the author called ideal; now about as much.
SOFTEN = 0.035


def made():
    """The five, in the order of the chain."""
    a = [crossbow_thrust(), crossbow_slap(), crossbow_spine(), crossbow_leap_shot(), crossbow_dash_back()]
    for x in a:
        kit.soften(x, SOFTEN, LEGS)
    # the head follows the look where the body does not turn round (the push, the hop, the dash)
    for x in (a[0], a[3], a[4]):
        x.tracks.pop(('head', 'rotation'), None)
    return a


# --------------------------------------------------------------------------------------------
# the file: the author's rig of 2026-10-29 (the grey figure is the mob) with the crossbow in hand
# --------------------------------------------------------------------------------------------

def _outliner_node(items, wanted):
    for it in items:
        if isinstance(it, dict):
            if it.get('uuid') == wanted:
                return it
            hit = _outliner_node(it.get('children', []), wanted)
            if hit:
                return hit
    return None


def add_crossbow(model):
    """The crossbow group in the right fist, as kinetrix_rig builds its items, shown; the sword hidden."""
    spec = kr.ITEMS[XBOW]
    fr = kr.item_frame(spec['side'], *spec['display'])
    g = {'name': XBOW, 'origin': [round(v, 4) for v in fr['origin']], 'rotation': [round(v, 3) for v in fr['rotation']],
         'color': 3, 'uuid': str(uuid.uuid4()), 'export': False, 'mirror_uv': False, 'isOpen': False, 'locked': False,
         'visibility': True, 'autouv': 0}
    node = {'uuid': g['uuid'], 'isOpen': False, 'children': []}
    for cname, tex, lo, hi in kr.crossbow_cubes(fr['origin'], fr['scale']):
        u, v = kr.PROP_COLORS[tex]
        faces = {f: {'uv': [u, v, u + 4, v + 4], 'texture': 1} for f in ('north', 'south', 'east', 'west', 'up', 'down')}
        c = {'name': cname, 'box_uv': False, 'rescale': False, 'locked': False, 'light_emission': 0,
             'render_order': 'default', 'allow_mirror_modeling': True, 'from': [round(x, 4) for x in lo],
             'to': [round(x, 4) for x in hi], 'autouv': 0, 'color': 0, 'origin': [round(x, 4) for x in fr['origin']],
             'uv_offset': [0, 0], 'faces': faces, 'type': 'cube', 'uuid': str(uuid.uuid4())}
        model['elements'].append(c)
        node['children'].append(c['uuid'])
    model['groups'].append(g)
    fore = next(x for x in model['groups'] if x['name'] == 'right_forearm')
    _outliner_node(model['outliner'], fore['uuid'])['children'].append(node)


def set_visible(model, group_name, visible):
    """A group and its cubes shown or hidden (refine.set_visible: that module's name is taken on the path)."""
    g = next(g for g in model['groups'] if g['name'] == group_name)
    g['visibility'] = visible
    cubes = {c for c in _outliner_node(model['outliner'], g['uuid']).get('children', []) if isinstance(c, str)}
    for e in model['elements']:
        if e['uuid'] in cubes:
            e['visibility'] = visible


def make_model():
    with open(kit.SRC, encoding='utf-8') as f:
        model = json.load(f)
    model['name'] = 'crossbow_2026-11-08'
    add_crossbow(model)
    for item in ('right_item_sword', 'right_item_sword2', 'right_item_bow2', 'left_item_sword2'):
        set_visible(model, item, False)
    return model


def write(made, dst=DST):
    model = make_model()
    uuids = L.bone_uuids(model)
    for a in made:
        a.tracks.pop(('right_item_bow', 'rotation'), None)     # animlib's still bow track: no bow here
    model['animations'] = [a.to_json(uuids) for a in made]
    model['animations'][0]['selected'] = True
    with open(dst, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, separators=(',', ':'))
    print(f'wrote {dst}: ' + ', '.join(a.name for a in made))


def main():
    import round10     # the crossbow's G, G held and rolls share the file
    write(made() + round10.made())


if __name__ == '__main__':
    main()
