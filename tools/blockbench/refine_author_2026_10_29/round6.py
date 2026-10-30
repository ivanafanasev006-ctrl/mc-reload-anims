"""The author's message of 2026-10-30: the moves it asks for that need animations.

- sword_spin: "on G a sword turns once round on the spot, fast (the sword held out turned flat, as
  in the attack out of a slide or as the axe turns round)". No draft: made from the description, on
  the author's axe_whirlwind (round5.py) with one turn instead of three, on the spot.
- finisher_stab: "the axe's finisher is now shorter than the sword's, in speed and in all; let the
  sword's be the same, but with a stab into the belly, and quick, without a lead-up". Made again on
  the axe's finisher_chop (round5.py): the free hand on the mob's shoulder as the sword draws back to
  the hip, the thrust into the belly at 0.3 s (the mob dies then, as at the axe's blow), the blade
  pulled out at 0.6 s, the stance by 0.95 s (it was 1.5 s, the mob dying at 0.95 s). Kept from the
  author's stab (animations/author_2026-10-28b.bbmodel): the step into the lunge on the left foot,
  the arm reaching straight out at the belly, the free hand holding the mob's shoulder.

The model does not travel: the mechanic places the player; every frame the lowest point of the body is
put on the floor where the feet are down.
"""
import math

import numpy as np

import kit
import round5
from kit import L
from round5 import ITEM, LEGS, TIP_LOCAL, aimed, aimed_flat, held, item_continuous, turned_between

kit.POINTS.setdefault('swordtip', (ITEM, tuple(TIP_LOCAL)))

# --------------------------------------------------------------------------------------------
# sword_spin: one quick turn round on the spot, the blade out flat
# --------------------------------------------------------------------------------------------

#: Times (s): the wind-up to SS_FROM, one turn to the left to SS_TO (Spin.SPIN_FROM, SPIN_TO: 0.1 and
#: 0.4 s, 0.3 s a turn: "fast"), the sword back in front by SS_GUARD, the stance by SS_LEN.
SS_FROM, SS_TO, SS_GUARD, SS_LEN = 0.1, 0.4, 0.58, 0.8
#: The wind-up turns the body this far to the right (degrees); the turn makes up for it (Spin.BLADE_FROM).
SS_WIND = 40.0


def _spin_yaw(t):
    """The body's turn (degrees, to the left) at t: wound up to the right, then a whole turn and the wind-up
    back at an even speed with short ramps, so it ends square."""
    if t <= SS_FROM:
        return -SS_WIND * kit.smooth(t / SS_FROM)
    if t >= SS_TO:
        return 360.0
    total = 360.0 + SS_WIND
    ramp = 0.06
    span = SS_TO - SS_FROM
    v = total / (span - ramp)
    x = t - SS_FROM
    if x < ramp:
        d = v * x * x / (2 * ramp)
    elif x > span - ramp:
        y = span - x
        d = total - v * y * y / (2 * ramp)
    else:
        d = v * (x - ramp / 2)
    return -SS_WIND + d


def sword_spin():
    """The wind-up: down a little on the knees, the chest turned away to the right, the sword arm
    drawn back out to the side; the turn: the whole body round to the left on the spot, the sword arm
    straight out at shoulder height with the blade out along it, flat, its edge leading, the free arm
    out for balance, leaning into the turn, the feet stepping round under it; then the blade swings in
    front and the stance comes back."""
    wind = {'torso': (0, -24, 0), 'chest': (-8, 0, 0), 'head': (-4, 20, 0),
            'right_leg': (16, 0, 10), 'right_shin': (-30, 0, 0), 'left_leg': (10, 0, -10), 'left_shin': (-24, 0, 0),
            'right_arm': (-20, 0, 74), 'right_forearm': (8, 0, 0), 'left_arm': (30, 0, -30), 'left_forearm': (36, 0, 0)}
    spin = {'torso': (0, 0, 0), 'chest': (-6, 0, 0), 'head': (-6, 0, 0),
            'right_leg': (12, 0, 10), 'right_shin': (-24, 0, 0), 'left_leg': (12, 0, -10), 'left_shin': (-24, 0, 0),
            'right_arm': (6, 0, 88), 'right_forearm': (2, 0, 0), 'left_arm': (6, 0, -78), 'left_forearm': (10, 0, 0)}
    guard = {'torso': (0, 6, 0), 'chest': (-4, 0, 0), 'head': (0, -4, 0),
             'right_leg': (8, 0, 4), 'right_shin': (-14, 0, 0), 'left_leg': (8, 0, -4), 'left_shin': (-14, 0, 0),
             'right_arm': (42, 0, 12), 'right_forearm': (34, 0, 0), 'left_arm': (18, 0, -12), 'left_forearm': (28, 0, 0)}
    keys = [(0.0, {}, 'auto'), (SS_FROM, wind, 'inout'), (SS_FROM + 0.06, spin, 'out'),
            (SS_TO - 0.04, spin, 'linear'), (SS_GUARD, guard, 'inout'), (SS_LEN, {}, 'inout')]
    d = L.Anim('spin_design', SS_LEN)
    for t, pose, ease in keys:
        d.key(t, pose, ease, whole=True)
    a = L.Anim('sword_spin', SS_LEN, 'once')
    item = (0.0, 0.0, 0.0)
    out_rot = None
    prev_item = None
    prev_body = None
    for t in kit.frames(0, SS_LEN):
        p = kit.design(d, t)
        yaw = _spin_yaw(t)
        # leaning into the turn (to the left, the way it goes), the feet stepping round: a step a half turn
        inspin = kit.span(t, SS_FROM - 0.03, SS_FROM + 0.05) * (1 - kit.span(t, SS_TO - 0.05, SS_TO + 0.04))
        phase = math.radians(yaw) * 2
        lean = 8.0 * inspin
        rl, ll = p['right_leg'], p['left_leg']
        p['right_leg'] = (rl[0] + 12 * inspin * max(0.0, math.sin(phase)), rl[1], rl[2])
        p['left_leg'] = (ll[0] + 12 * inspin * max(0.0, -math.sin(phase)), ll[1], ll[2])
        rs, ls = p['right_shin'], p['left_shin']
        p['right_shin'] = (rs[0] - 18 * inspin * max(0.0, math.sin(phase)), 0, 0)
        p['left_shin'] = (ls[0] - 18 * inspin * max(0.0, -math.sin(phase)), 0, 0)
        p['body'] = (0.0, yaw, -lean)
        p = kit.grounded(p, bones=LEGS)
        p['body'] = tuple(kit.continuous(prev_body, p['body']))
        prev_body = p['body']
        # the sword (author: "held out turned flat"): in the turn the blade out along the arm, a little
        # up, flat to the ground, the edge leading into the turn; the wrist turns it so over the wind-up
        # and back as the sword comes in front, along the shortest turn in the fist
        r = math.radians(yaw)
        out = np.array((math.cos(r), 0.12, -math.sin(r)))     # the body's right, turned with it
        lead = np.array((-math.sin(r), 0.0, -math.cos(r)))    # the way that hand goes as the body turns
        if t <= SS_TO:
            q = aimed_flat(p, out, lead, start=item)
            item = item_continuous(item, q[ITEM])
            out_rot = item
            f = kit.smooth(t / (SS_FROM - 0.02))
            p = held(p, item if f >= 1.0 else turned_between((0.0, 0.0, 0.0), item, f, prev_item))
        else:
            f = kit.smooth(min(1.0, (t - SS_TO) / (SS_GUARD - SS_TO)))
            p = held(p, turned_between(out_rot, (0.0, 0.0, 0.0), f, prev_item))
        prev_item = p[ITEM]
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a


# --------------------------------------------------------------------------------------------
# finisher_stab: quick, the blade into the belly
# --------------------------------------------------------------------------------------------

#: Times (s): the free hand on the mob's shoulder and the sword drawn back to the hip (FS_DRAW), the
#: thrust into its belly (FS_HIT: Strikes.FINISH_KILL, the mob dies), a little deeper as the body
#: leans in (FS_DEEP), held while the mob sags on it, pulled out (FS_PULL: Strikes.FINISH_OUT,
#: running on may break off the rest), the stance by FS_LEN. The thrust takes 0.12 s from the draw:
#: two and a half of the game's ticks, so it is seen, not skipped.
FS_DRAW, FS_HIT, FS_DEEP, FS_PULL, FS_LEN = 0.18, 0.3, 0.36, 0.6, 0.95
MOB_AT = round5.MOB_AT
#: Where the blade's point goes, from the front of the mob's belly as it is then: the sword is longer
#: than the mob is deep at point blank, so it goes in at the belly and its point out at the back (as
#: the author's stab did), a little further as the body leans in.
HIT_OFFSET, DEEP_OFFSET = np.array((0.0, -0.5, -7.0)), np.array((0.0, -1.0, -8.5))
#: The arm's turn about its own length is held near its design, the elbow only bends.
ARM_LIMITS = {('right_arm', 1): (-50.0, 40.0), ('right_arm', 2): (-30.0, 40.0), ('right_forearm', 0): (0.0, 120.0)}


def _mob(t):
    """The mob's own pose: stooping, weakened; at the thrust it doubles over the blade, the head down,
    the knees giving, and sags onto it."""
    weak = {'chest': (-9, 0, 0), 'head': (-8, 0, 0), 'right_arm': (8, 0, 5), 'right_forearm': (14, 0, 0),
            'left_arm': (8, 0, -5), 'left_forearm': (14, 0, 0), 'right_leg': (5, 0, 2), 'right_shin': (-8, 0, 0),
            'left_leg': (5, 0, -2), 'left_shin': (-8, 0, 0)}
    struck = {**weak, 'body': (6, 0, 0), 'chest': (-26, 0, 0), 'head': (-22, 0, 0),
              'right_arm': (-18, 0, 8), 'right_forearm': (40, 0, 0), 'left_arm': (-18, 0, -8), 'left_forearm': (40, 0, 0),
              'right_leg': (14, 0, 3), 'right_shin': (-26, 0, 0), 'left_leg': (14, 0, -3), 'left_shin': (-26, 0, 0)}
    sag = {**struck, 'body': (10, 0, 0), 'chest': (-30, 0, 0), 'head': (-28, 0, 0), 'right_leg': (34, 0, 4), 'right_shin': (-66, 0, 0),
           'left_leg': (34, 0, -4), 'left_shin': (-66, 0, 0), 'right_arm': (-4, 0, 12), 'left_arm': (-4, 0, -12)}
    d = L.Anim('mob', FS_LEN)
    for tk, pose, ease in ((0.0, weak, 'auto'), (FS_HIT, weak, 'auto'), (FS_HIT + 0.08, struck, 'out'),
                           (FS_PULL + 0.12, sag, 'inout'), (FS_LEN, sag, 'auto')):
        d.key(tk, pose, ease, whole=True)
    return kit.grounded(kit.design(d, t))


def _belly(t, offset):
    """The mob's belly (as it is at t), moved by offset (world px)."""
    return kit.pt(kit.fk(kit.as_partner(_mob(t), MOB_AT)), 'belly2') + offset


def _point_onto(pose, point):
    """The right arm solved so the blade's point is at `point`, the grip as the pose holds it."""
    q, err, x = kit.solve(pose, [('swordtip', np.asarray(point, float), 1.0)], kit.arm_vary('right'), ARM_LIMITS,
                          reg=[0.08, 0.1, 0.08, 0.08])
    return q, err


def stab_grip(pose):
    """The sword turned in the fist to go on along the forearm (a thrust), from the game's grip
    (the blade up out of the fist when the arm points forward)."""
    W = kit.fk(pose)
    fore = kit.pt(W, 'rhand') - kit.pt(W, 'relbow')
    return aimed(pose, fore)[ITEM]


def finisher_stab():
    """Straight into it: the free hand takes the mob by the shoulder as the sword draws back to the
    hip, the point forward, the body coiled back on the right foot; the thrust: a step onto the left
    foot, the hips and the right shoulder driving forward behind the arm, which goes out straight and
    puts the blade into the belly; a little deeper as the body leans in; held while the mob doubles
    over it and sags, the hand on its shoulder holding it; the sword pulled out, the hand shoving the
    mob off, and the stance comes back."""
    stance = {'right_leg': (-12, 0, 4), 'right_shin': (-10, 0, 0), 'left_leg': (16, 0, -3), 'left_shin': (-14, 0, 0)}
    draw = {**stance, 'body@p': (0, 0, 1.0), 'torso': (0, -22, 0), 'chest': (-2, 0, 0), 'head': (0, 14, 0),
            'right_arm': (-30, 0, 8), 'right_forearm': (95, 0, 0)}
    hit = {'body': (-3, 0, 0), 'body@p': (0, 0, -2.0), 'torso': (0, 14, 0), 'chest': (-10, 0, 0), 'head': (4, -8, 0),
           'right_arm': (75, 0, 2), 'right_forearm': (8, 0, 0),
           'right_leg': (-22, 0, 4), 'right_shin': (-6, 0, 0), 'left_leg': (30, 0, -3), 'left_shin': (-44, 0, 0)}
    deep = {**hit, 'body@p': (0, 0, -2.6), 'chest': (-14, 0, 0), 'torso': (0, 16, 0), 'head': (6, -8, 0)}
    stuck = {**deep, 'chest': (-16, 0, 0), 'body@p': (0, 0, -2.4)}
    # out: drawn straight back along the blade's line to the hip, the hand shoving the mob off
    pull = {**draw, 'body@p': (0, 0, 0.0), 'torso': (0, -14, 0), 'chest': (-6, 0, 0), 'head': (2, 8, 0),
            'right_arm': (-18, 0, 8), 'right_forearm': (90, 0, 0), 'left_arm': (68, 0, -8), 'left_forearm': (6, 0, 0)}
    back = {'chest': (-4, 0, 0), 'right_arm': (24, 0, 10), 'right_forearm': (32, 0, 0), 'left_arm': (14, 0, -8),
            'left_forearm': (20, 0, 0), 'right_leg': (-6, 0, 3), 'right_shin': (-6, 0, 0), 'left_leg': (8, 0, -2), 'left_shin': (-8, 0, 0)}

    # the thrust and the push on: the arm solved, the sword along the forearm, so the point goes into
    # the belly, then deeper (the stuck pose keeps that arm)
    grip = stab_grip({'right_arm': (80, 0, 0)})
    hit, deep, stuck = (kit.grounded(x, bones=LEGS) for x in (hit, deep, stuck))
    q, err_hit = _point_onto(held(hit, grip), _belly(FS_HIT, HIT_OFFSET))
    hit['right_arm'], hit['right_forearm'] = q['right_arm'], q['right_forearm']
    q, err_deep = _point_onto(held(deep, grip), _belly(FS_DEEP, DEEP_OFFSET))
    deep['right_arm'], deep['right_forearm'] = q['right_arm'], q['right_forearm']
    stuck['right_arm'], stuck['right_forearm'] = deep['right_arm'], deep['right_forearm']

    keys = [(0.0, {}, 'auto'), (FS_DRAW, draw, 'out'), (FS_HIT, hit, 'in'), (FS_DEEP, deep, 'out'),
            (0.5, stuck, 'inout'), (FS_PULL, pull, 'out'), (0.78, back, 'inout'), (FS_LEN, {}, 'inout')]
    # the free hand on the mob's shoulder from the draw until the blade is out (solved at each key)
    for i, (tk, pose, ease) in enumerate(keys):
        if FS_DRAW <= tk <= 0.5:
            keys[i] = (tk, round5._left_hand_on(pose, _mob(tk)), ease)
    d = L.Anim('stab_design', FS_LEN)
    for tk, pose, ease in keys:
        d.key(tk, pose, ease, whole=True)

    rest = (0.0, 0.0, 0.0)

    def grip_at(t):
        if t <= FS_PULL:
            return turned_between(rest, grip, kit.smooth(t / FS_DRAW))
        return turned_between(grip, rest, kit.smooth((t - FS_PULL) / (0.82 - FS_PULL)))

    a = L.Anim('finisher_stab', FS_LEN, 'once')
    prev_item = None
    for t in kit.frames(0, FS_LEN):
        p = kit.grounded(kit.design(d, t), bones=LEGS)
        p = held(p, item_continuous(prev_item, grip_at(t)))
        prev_item = p[ITEM]
        p.update(kit.as_partner(_mob(t), MOB_AT))
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    a.stab_error = (err_hit, err_deep)
    return a
