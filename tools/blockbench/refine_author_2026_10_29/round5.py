"""The author's fifth message of 2026-10-29: the moves it asks for that need animations.

- plunge_fall, plunge_land: "falling with a sword in the hands he flies and drives it into the
  ground, it stuns him a little, a short time, he pulls the sword out of the ground; it all takes
  20 % off the landing's damage (the same with an axe)". No draft: made from the description.
- axe_whirlwind: "an axe in the hands, LMB + S held, then RMB held: the player turns three times
  round, flies forward like a top and hurts everything round him like a berserker (not very fast,
  but not sluggish)". No draft: made from the description.
- hop_roll_kick: the author's draft (animations/author_2026-10-29b.bbmodel, "it will look very
  good once it is worked on"): a run at a mob, a forward flip over it, a twist in the air that
  turns him to its back, the kick into it, the landing and a roll after it, 4 s. Kept: the crouch,
  the dive up and over, the flip upside down over the mob, the kick out of the twist, the landing
  and the roll on. Changed: 4 s to 2.1 s, the twists a clean flip (the mechanic turns the player
  to the mob, the camera with them, as in hop_on_kick), the limbs kept clear of the body.

The model does not fly or travel: the mechanic carries the player (the paths it reads, see
paths()); every frame the lowest point of the body is put on the floor where the feet are down.
"""
import math

import numpy as np

import kit
import kinetrix_rig as kr
from kit import L

PX = 0.9375 / 16   # blocks a pixel of the model
#: The bones that stand on the floor (the arms and the blade may go lower: a hand on the ground, a blade in it).
LEGS = ['body', 'torso', 'chest', 'head', 'right_leg', 'right_shin', 'left_leg', 'left_shin']

# --------------------------------------------------------------------------------------------
# the held blade: turned about the fist, the grip staying in it
# --------------------------------------------------------------------------------------------

ITEM = 'right_item_sword'
_ITEM_O = np.array(kit.RIG.groups[kit.RIG.by_name[ITEM]]['origin'], float)
#: Points of the sword in its own (unturned) frame: the tip and the pommel.
TIP_LOCAL, POMMEL_LOCAL = np.array((6.0, 3.7, -6.0)), np.array((6.0, 20.6, -6.0))


def _grip_local():
    """The point of the sword that is in the fist at rest (the hand point, in the sword's own frame)."""
    W = kit.fk({})
    hand = kit.pt(W, 'rhand')
    M = np.linalg.inv(W[ITEM])
    return (M @ np.append(hand, 1.0))[:3] + _ITEM_O


GRIP_LOCAL = _grip_local()


def _item_point(W, local):
    return kr.apply(W[ITEM], np.asarray(local, float) - _ITEM_O)


def blade(pose):
    """(grip, tip) of the sword in the pose, world px."""
    W = kit.fk(pose)
    return _item_point(W, GRIP_LOCAL), _item_point(W, TIP_LOCAL)


def held(pose, rot):
    """The pose with the sword turned by rot in the fist: its position moved so the grip stays in the hand."""
    p = dict(pose)
    p[ITEM] = tuple(rot)
    p[ITEM + '@p'] = (0.0, 0.0, 0.0)
    W = kit.fk(p)
    hand = kit.pt(W, 'rhand')
    grip = _item_point(W, GRIP_LOCAL)
    # the item's position is in its parent's (the forearm's) frame: bring the miss into it
    Rf = W['right_forearm'][:3, :3]
    d = Rf.T @ (hand - grip)
    p[ITEM + '@p'] = tuple(float(v) for v in d)
    return p


def aimed(pose, direction, start=(0.0, 0.0, 0.0)):
    """The pose with the sword turned in the fist so the blade points along `direction` (world)."""
    want = np.asarray(direction, float)
    want = want / np.linalg.norm(want)

    def miss(r):
        g, t = blade(held(pose, r))
        d = t - g
        d = d / np.linalg.norm(d)
        return np.concatenate([(d - want) * 10.0, (np.asarray(r) - np.asarray(start)) * 0.002])

    from scipy.optimize import least_squares
    res = least_squares(miss, np.asarray(start, float), diff_step=1e-3, max_nfev=200)
    return held(pose, tuple(float(v) for v in res.x))


def _arms_on(pose, prev, key, rhand, lhand, wl=1.0):
    """The arms solved so the right fist is at rhand and the left one at lhand (weight wl)."""
    import ledge
    tg = [('rhand', rhand, 1.0)]
    if wl > 1e-3:
        tg.append(('lhand', lhand, wl))
    return ledge.on_targets(pose, tg, prev, key)


def _lerp3(a, b, f):
    return tuple(float(x + (y - x) * f) for x, y in zip(a, b))


def _keyed(points):
    """[(t, value)] -> f(t), smoothstep between neighbours (values: tuples)."""
    def f(t):
        if t <= points[0][0]:
            return points[0][1]
        for (t0, a), (t1, b) in zip(points, points[1:]):
            if t <= t1:
                return _lerp3(a, b, kit.smooth((t - t0) / (t1 - t0)))
        return points[-1][1]
    return f


# --------------------------------------------------------------------------------------------
# plunge_fall, plunge_land: a fall with the blade, driven into the ground
# --------------------------------------------------------------------------------------------

#: plunge_land: the feet touch at 0, down onto the right knee and the blade into the ground in
#: front by STAB, held there, a breath (the stun) to PULL, heaved out by OUT (moving breaks the rest
#: off from here: Plunge.STAND), up by PLUNGE_LEN.
STAB, PULL, OUT, PLUNGE_LEN = 0.12, 0.5, 0.68, 1.0
FALL_LEN = 1.0

#: Falling with the blade: both fists high in front of the face, the right one on the grip, the
#: left over it, the point down and a little forward; the knees a little bent, one ahead.
FALL_BODY = {'body': (-4, 0, 0), 'chest': (-6, 0, 0), 'head': (-8, 0, 0),
             'right_leg': (26, 0, 4), 'right_shin': (-38, 0, 0), 'left_leg': (6, 0, -4), 'left_shin': (-20, 0, 0),
             'right_arm': (150, 0, -15), 'right_forearm': (30, 0, 0), 'left_arm': (150, 0, 15), 'left_forearm': (35, 0, 0)}
FALL_HANDS = ((1.0, 23.5, -9.5), (0.0, 26.0, -9.0))
DOWN = (0.0, -1.0, -0.22)


def plunge_fall():
    """Looped while falling: the hold, the legs and the body moving a little in the wind."""
    a = L.Anim('plunge_fall', FALL_LEN, 'loop')
    prev = {}
    item = (0.0, 0.0, 0.0)
    for t in kit.frames(0, FALL_LEN):
        w = math.sin(2 * math.pi * t / FALL_LEN)
        p = dict(FALL_BODY)
        p['body'] = (-4 + 1.5 * w, 0, 2.0 * math.cos(2 * math.pi * t / FALL_LEN))
        p['right_leg'] = (26 + 6 * w, 0, 4)
        p['left_leg'] = (6 - 6 * w, 0, -4)
        p['right_shin'] = (-38 - 6 * w, 0, 0)
        p['left_shin'] = (-20 + 6 * w, 0, 0)
        p = _arms_on(p, prev, 'pfall', FALL_HANDS[0], FALL_HANDS[1])
        p = aimed(p, DOWN, start=item)
        item = p[ITEM]
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a


def plunge_land():
    """The landing: the knees give, down onto the right knee, the fists drive the blade into the
    ground in front of the left foot (to the guard: a third of the blade in), the head down a
    breath; both hands heave it out, the right knee comes up, and he stands, the blade back in the
    normal grip."""
    kneel = {'body': (-8, 0, 0), 'chest': (-16, 0, 0), 'head': (-22, 0, 0),
             'right_leg': (4, 0, 5), 'right_shin': (-98, 0, 0), 'left_leg': (84, 0, -6), 'left_shin': (-86, 0, 0),
             'right_arm': (40, 0, -10), 'right_forearm': (20, 0, 0), 'left_arm': (40, 0, 10), 'left_forearm': (25, 0, 0)}
    heave = {**kneel, 'body': (-4, 0, 0), 'chest': (-6, 0, 0), 'head': (-8, 0, 0),
             'right_leg': (18, 0, 5), 'right_shin': (-96, 0, 0), 'left_leg': (78, 0, -6), 'left_shin': (-80, 0, 0)}
    half = {'body': (-3, 0, 0), 'chest': (-8, 0, 0), 'head': (-6, 0, 0),
            'right_leg': (36, 0, 4), 'right_shin': (-62, 0, 0), 'left_leg': (40, 0, -4), 'left_shin': (-52, 0, 0),
            'right_arm': (25, 0, -6), 'right_forearm': (30, 0, 0), 'left_arm': (12, 0, 8), 'left_forearm': (20, 0, 0)}
    rest = {'body': (0, 0, 0)}
    keys = [(0.0, {**FALL_BODY, 'right_leg': (30, 0, 4), 'right_shin': (-45, 0, 0), 'left_leg': (14, 0, -4),
                   'left_shin': (-30, 0, 0)}, 'auto'),
            (STAB, kneel, 'hit'),
            (0.3, {**kneel, 'chest': (-19, 0, 0), 'head': (-25, 0, 0)}, 'inout'),
            (PULL, kneel, 'inout'),
            (OUT, heave, 'out'),
            (0.84, half, 'inout'),
            (PLUNGE_LEN, rest, 'inout')]
    d = L.Anim('plunge_design', PLUNGE_LEN)
    for t, pose, ease in keys:
        d.key(t, pose, ease, whole=True)
    # where the fists are (world px; the floor at 0, the front at -z): from the face down to the
    # grip of a blade standing in the ground in front of the left foot, up with it, then let go of
    rhand = _keyed([(0.0, FALL_HANDS[0]), (STAB, (1.5, 9.5, -10.5)), (PULL, (1.5, 9.5, -10.5)),
                    (OUT, (1.5, 17.0, -10.0))])
    lhand = _keyed([(0.0, FALL_HANDS[1]), (STAB, (0.5, 12.0, -10.0)), (PULL, (0.5, 12.0, -10.0)),
                    (OUT, (0.5, 19.5, -9.5))])
    lhand_w = kit.keys((0.0, 1), (OUT, 1), (0.8, 0))
    rhand_w = kit.keys((0.0, 1), (OUT, 1), (0.84, 0))
    a = L.Anim('plunge_land', PLUNGE_LEN, 'once')
    prev = {}
    item = (0.0, 0.0, 0.0)
    out_rot = None
    for t in kit.frames(0, PLUNGE_LEN):
        p = kit.design(d, t)
        breath = math.sin(2 * math.pi * (t - STAB) / 0.38) if STAB < t < PULL else 0.0
        c = p.get('chest', (0, 0, 0))
        p['chest'] = (c[0] + 2.0 * breath, c[1], c[2])
        # on the floor by the legs first (the hands go where the blade is, wherever the body is)
        p = kit.grounded(p, bones=LEGS)
        if rhand_w(t) > 1e-3:
            p = _arms_on(p, prev, 'pland', rhand(t), lhand(t), lhand_w(t))
        # the point down (and into the ground) until it is out; then back to the normal grip
        if t <= OUT:
            p = aimed(p, DOWN if t < STAB else (0.0, -1.0, -0.12), start=item)
            item = p[ITEM]
            out_rot = item
        else:
            f = kit.smooth(min(1.0, (t - OUT) / (PLUNGE_LEN - 0.08 - OUT)))
            p = held(p, _lerp3(out_rot, (0.0, 0.0, 0.0), f))
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a


# --------------------------------------------------------------------------------------------
# axe_whirlwind: three turns round with the axe out, carried forward like a top
# --------------------------------------------------------------------------------------------

#: The wind-up (the axe drawn back, the body turned away) to SPIN_FROM, three whole turns to the
#: left (the axe leading) to SPIN_TO, the mechanic carrying the player forward meanwhile and the
#: blade hitting everything round once a turn (Whirl), the axe back in front by WHIRL_LEN.
#: 0.5 s a turn: "not very fast, but not sluggish".
SPIN_FROM, SPIN_TO, WHIRL_LEN = 0.25, 1.75, 2.1
TURNS = 3


def _spin_yaw(t):
    """The body's turn (degrees, to the left) at time t: eased in over the first quarter turn and out over the last."""
    if t <= SPIN_FROM:
        return -40.0 * kit.smooth(t / SPIN_FROM)          # the wind-up turns the body a little away
    if t >= SPIN_TO:
        return 360.0 * TURNS
    total = 360.0 * TURNS + 40.0
    ramp = 0.12
    span = SPIN_TO - SPIN_FROM
    # constant speed with a ramp at each end (the area under the speed is the whole turn)
    v = total / (span - ramp)
    x = t - SPIN_FROM
    if x < ramp:
        d = v * x * x / (2 * ramp)
    elif x > span - ramp:
        y = span - x
        d = total - v * y * y / (2 * ramp)
    else:
        d = v * (x - ramp / 2)
    return -40.0 + d


def axe_whirlwind():
    """The wind-up: the knees bend wide, the body turns away to the right, the axe out behind; then
    the whole body turns three times to the left, the axe arm straight out at shoulder height and
    the blade leading, the other arm out for balance, the feet stepping round under it, the body
    leaning into the turn; then the turn runs down, the axe swings in front and the stance comes back."""
    wind = {'body': (0, 0, 0), 'torso': (0, -30, 0), 'chest': (-10, 0, 0), 'head': (-4, 25, 0),
            'right_leg': (18, 0, 12), 'right_shin': (-34, 0, 0), 'left_leg': (12, 0, -12), 'left_shin': (-28, 0, 0),
            'right_arm': (-25, 0, 70), 'right_forearm': (10, 0, 0), 'left_arm': (35, 0, -35), 'left_forearm': (40, 0, 0)}
    spin = {'torso': (0, 0, 0), 'chest': (-6, 0, 0), 'head': (-6, 0, 0),
            'right_leg': (10, 0, 10), 'right_shin': (-22, 0, 0), 'left_leg': (10, 0, -10), 'left_shin': (-22, 0, 0),
            'right_arm': (8, 0, 86), 'right_forearm': (4, 0, 0), 'left_arm': (8, 0, -80), 'left_forearm': (10, 0, 0)}
    guard = {'torso': (0, 0, 0), 'chest': (-4, 0, 0), 'head': (0, 0, 0),
             'right_leg': (8, 0, 4), 'right_shin': (-14, 0, 0), 'left_leg': (8, 0, -4), 'left_shin': (-14, 0, 0),
             'right_arm': (40, 0, 10), 'right_forearm': (35, 0, 0), 'left_arm': (20, 0, -12), 'left_forearm': (30, 0, 0)}
    keys = [(0.0, {}, 'auto'), (SPIN_FROM, wind, 'inout'), (SPIN_FROM + 0.12, spin, 'out'),
            (SPIN_TO - 0.1, spin, 'linear'), (1.9, guard, 'inout'), (WHIRL_LEN, {}, 'inout')]
    d = L.Anim('whirl_design', WHIRL_LEN)
    for t, pose, ease in keys:
        d.key(t, pose, ease, whole=True)
    a = L.Anim('axe_whirlwind', WHIRL_LEN, 'once')
    item = (0.0, 0.0, 0.0)
    out_rot = None
    prev_body = None
    for t in kit.frames(0, WHIRL_LEN):
        p = kit.design(d, t)
        yaw = _spin_yaw(t)
        # leaning into the turn (to the left, the way it spins) and the feet stepping round: a step a half turn
        inspin = kit.span(t, SPIN_FROM, SPIN_FROM + 0.15) * (1 - kit.span(t, SPIN_TO - 0.15, SPIN_TO))
        phase = math.radians(yaw) * 2
        lean = 7.0 * inspin
        bob = 0.8 * inspin * abs(math.sin(phase))
        rl, ll = p['right_leg'], p['left_leg']
        p['right_leg'] = (rl[0] + 14 * inspin * max(0.0, math.sin(phase)), rl[1], rl[2])
        p['left_leg'] = (ll[0] + 14 * inspin * max(0.0, -math.sin(phase)), ll[1], ll[2])
        rs, ls = p['right_shin'], p['left_shin']
        p['right_shin'] = (rs[0] - 22 * inspin * max(0.0, math.sin(phase)), 0, 0)
        p['left_shin'] = (ls[0] - 22 * inspin * max(0.0, -math.sin(phase)), 0, 0)
        p['body'] = (0.0, yaw, -lean)
        p = kit.grounded(p, bones=LEGS)
        bp = p.get('body@p', (0, 0, 0))
        p['body@p'] = (bp[0], bp[1] + bob, bp[2])
        p['body'] = tuple(kit.continuous(prev_body, p['body']))
        prev_body = p['body']
        # the blade: out along the arm and a little up while it turns; back to the normal grip after
        if SPIN_FROM - 0.05 <= t <= SPIN_TO:
            r = math.radians(yaw)
            out = np.array((math.cos(r), 0.18, -math.sin(r)))     # the body's right, turned with it (Blockbench +Y turns +x to -z)
            p = aimed(p, out, start=item)
            item = p[ITEM]
            out_rot = item
        elif t < SPIN_FROM - 0.05:
            f = kit.smooth(t / (SPIN_FROM - 0.05))
            r = math.radians(yaw)
            out = np.array((math.cos(r), 0.18, -math.sin(r)))
            q = aimed(p, out, start=item)
            p = held(p, _lerp3((0.0, 0.0, 0.0), q[ITEM], f))
            item = q[ITEM]
        else:
            f = kit.smooth(min(1.0, (t - SPIN_TO) / (1.95 - SPIN_TO)))
            p = held(p, _lerp3(out_rot, (0.0, 0.0, 0.0), f))
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a


# --------------------------------------------------------------------------------------------
# hop_roll_kick: over the mob in a forward flip, turned to its back, the kick, the landing, a roll on
# --------------------------------------------------------------------------------------------

#: Times (s): the crouch out of the run to RK_TAKEOFF, a forward flip tucked over the mob (the
#: mechanic lifts the player over it and turns them half round to its back, RK_TURN_FROM to
#: RK_TURN_TO, the camera with them), the leg chambered as the flip opens, the kick straight into its
#: back at RK_HIT, the landing facing it at RK_LAND, the roll on after it (the mechanic carries the
#: player RK_ROLL_BLOCKS) to RK_ROLL_TO, up by RK_LEN. The author's draft took 4 s.
RK_TAKEOFF, RK_TURN_FROM, RK_TURN_TO, RK_HIT, RK_LAND, RK_ROLL_FROM, RK_ROLL_TO, RK_LEN = \
    0.15, 0.4, 0.68, 0.8, 0.95, 1.05, 1.45, 1.9
RK_ROLL_BLOCKS = 1.5


def hop_roll_kick():
    """The author's poses, in a flip that reads: down into a crouch with the arms swung back (his
    0.25 s), the push off with the arms thrown forward over the head and the body diving after
    them (his 0.75-1.08 s), tucked tight upside down over the mob (his 1.42 s: the twist he drew is
    the mechanic's half turn now), opening out of it with the right leg chambered, the kick
    straight out into the mob's back as the body leans away from it (his 2.0-2.25 s), the landing
    on both feet facing it, and the forward roll after it, tucked (his 3.5-3.92 s), up into the
    guard."""
    import moves
    roll = lambda t: moves._sampled(moves.REF27, 'dodge_front', t)  # noqa: E731
    r20, r30, r45 = roll(0.2), roll(0.3), roll(0.45)
    for r in (r20, r30, r45):
        r.pop('body@p', None)

    def turned(pose, deg):
        b = pose.get('body', (0, 0, 0))
        return {**pose, 'body': (b[0] + deg, b[1], b[2])}

    load = {'body': (-16, 0, 0), 'chest': (-14, 0, 0), 'head': (14, 0, 0),
            'right_leg': (58, 0, 5), 'right_shin': (-98, 0, 0), 'left_leg': (52, 0, -5), 'left_shin': (-92, 0, 0),
            'right_arm': (-42, 0, 12), 'right_forearm': (22, 0, 0), 'left_arm': (-42, 0, -12), 'left_forearm': (22, 0, 0)}
    push = {'body': (-48, 0, 0), 'chest': (-10, 0, 0), 'head': (24, 0, 0),
            'right_leg': (6, 0, 2), 'right_shin': (-12, 0, 0), 'left_leg': (-14, 0, -2), 'left_shin': (-24, 0, 0),
            'right_arm': (165, 0, 10), 'right_forearm': (10, 0, 0), 'left_arm': (165, 0, -10), 'left_forearm': (10, 0, 0)}
    tuck = {'body': (-185, 0, 0), 'chest': (-35, 0, 0), 'head': (-28, 0, 0),
            'right_arm': (72, 0, 18), 'right_forearm': (80, 0, 0), 'left_arm': (72, 0, -18), 'left_forearm': (80, 0, 0),
            'right_leg': (110, 0, 5), 'right_shin': (-128, 0, 0), 'left_leg': (110, 0, -5), 'left_shin': (-128, 0, 0)}
    opening = {'body': (-300, 0, 0), 'chest': (-20, 0, 0), 'head': (-10, 0, 0),
               'right_arm': (60, 0, 30), 'right_forearm': (70, 0, 0), 'left_arm': (60, 0, -30), 'left_forearm': (75, 0, 0),
               'right_leg': (95, 0, 4), 'right_shin': (-120, 0, 0), 'left_leg': (60, 0, -4), 'left_shin': (-80, 0, 0)}
    chamber = {'body': (-350, 0, 0), 'chest': (-6, 0, 0), 'head': (-4, 0, 0),
               'right_arm': (45, 0, 25), 'right_forearm': (75, 0, 0), 'left_arm': (55, 0, -25), 'left_forearm': (80, 0, 0),
               'right_leg': (88, 0, 2), 'right_shin': (-118, 0, 0), 'left_leg': (18, 0, -3), 'left_shin': (-36, 0, 0)}
    kick = {'body': (-340, 0, 0), 'chest': (8, 0, 0), 'head': (-10, 0, 0),
            'right_arm': (-20, 0, 35), 'right_forearm': (20, 0, 0), 'left_arm': (60, 0, -20), 'left_forearm': (85, 0, 0),
            'right_leg': (88, 0, 0), 'right_shin': (-4, 0, 0), 'left_leg': (8, 0, -3), 'left_shin': (-30, 0, 0)}
    land = {'body': (-366, 0, 0), 'chest': (-16, 0, 0), 'head': (2, 0, 0),
            'right_arm': (50, 0, 20), 'right_forearm': (65, 0, 0), 'left_arm': (55, 0, -20), 'left_forearm': (75, 0, 0),
            'right_leg': (60, 0, 6), 'right_shin': (-92, 0, 0), 'left_leg': (56, 0, -6), 'left_shin': (-90, 0, 0)}
    squat = {'body': (-720, 0, 0), 'chest': (-28, 0, 0), 'head': (-4, 0, 0),
             'right_leg': (92, 0, 6), 'right_shin': (-120, 0, 0), 'left_leg': (88, 0, -6), 'left_shin': (-122, 0, 0),
             'right_arm': (58, 0, 15), 'right_forearm': (40, 0, 0), 'left_arm': (55, 0, -15), 'left_forearm': (40, 0, 0)}
    guard = {'body': (-720, 0, 0), 'chest': (-8, 0, 0), 'head': (0, 0, 0),
             'right_leg': (14, 0, 4), 'right_shin': (-22, 0, 0), 'left_leg': (16, 0, -4), 'left_shin': (-24, 0, 0),
             'right_arm': (30, 0, 12), 'right_forearm': (40, 0, 0), 'left_arm': (35, 0, -12), 'left_forearm': (55, 0, 0)}
    keys = [
        (0.0, {}, 'auto'),
        (0.1, load, 'inout'),
        (RK_TAKEOFF + 0.05, push, 'hit'),
        (RK_TURN_FROM, tuck, 'linear'),
        (0.56, opening, 'out'),
        (RK_TURN_TO, chamber, 'inout'),
        (RK_HIT, kick, 'hit'),
        (0.88, {**chamber, 'body': (-352, 0, 0), 'right_leg': (60, 0, 4), 'right_shin': (-80, 0, 0)}, 'out'),
        (RK_LAND, land, 'in'),
        (RK_ROLL_FROM, {**land, 'body': (-405, 0, 0), 'chest': (-35, 0, 0), 'head': (-28, 0, 0),
                        'right_arm': (95, 0, 15), 'right_forearm': (55, 0, 0), 'left_arm': (95, 0, -15), 'left_forearm': (55, 0, 0)}, 'in'),
        (1.15, turned(r20, -360 - 110 - r20.get('body', (0, 0, 0))[0]), 'linear'),
        (1.25, turned(r30, -360 - 200 - r30.get('body', (0, 0, 0))[0]), 'linear'),
        (1.35, turned(r45, -360 - 290 - r45.get('body', (0, 0, 0))[0]), 'linear'),
        (RK_ROLL_TO, {**squat, 'body': (-698, 0, 0)}, 'out'),
        (1.62, squat, 'inout'),
        (1.78, guard, 'inout'),
        (RK_LEN, {'body': (-720, 0, 0)}, 'inout'),
    ]
    d = L.Anim('roll_kick_design', RK_LEN)
    for t, pose, ease in keys:
        d.key(t, pose, ease, whole=True)
    a = L.Anim('hop_roll_kick', RK_LEN, 'once')
    prev = {}
    for t in kit.frames(0, RK_LEN):
        p = kit.grounded(kit.design(d, t))
        p['body'] = tuple(kit.continuous(prev.get('body'), p['body']))
        prev['body'] = p['body']
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a


# --------------------------------------------------------------------------------------------
# celebrate: the emote to the author's music (made again: author, 2026-10-30)
# --------------------------------------------------------------------------------------------

#: Author, 2026-10-30: "at the start the emote squats, waits until 3.6-4.1 seconds, then flies up
#: fast into a jump and hangs in the air until the 8th second, then simply falls back"; his own
#: celebrate (animations/author_2026-10-27.bbmodel) is "the example of how": the squat with the arms
#: swept back, then a burst up into a pose leant back, the knees bent and the head back. His
#: references: Shidou's goal in Blue Lock (the squat with the arms spread back like wings; then in
#: the air, the chest out, the head thrown back in a scream, the knees bent one above the other, the
#: arms spread down at the sides) and a Roblox emote of it (the leap on the music's drop, the hang
#: over the music; the figure leaves the ground, the player stays on it).
#: The music (art/author/sounds, 8.05 s of sound): hits at 1.33-1.85 and 1.97-2.25 s, silence from
#: 2.7 s, the riser from 3.37, full from 3.65, its loudest hit at 4.75, then a beat every 0.43 s
#: (139 a minute), cut off at 8.05.
SQUAT_IN = 0.46                                # down in the squat
NODS = ((1.33, 0.6), (1.97, 1.0), (2.11, 0.5))  # the hits while he waits: (time, strength)
LOAD_FROM, LOAD_TO = 3.375, 3.58                # the riser: down deeper, the arms further back
TAKEOFF = 3.71                                 # the feet leave the floor (the music full at 3.65)
APEX = 4.0                                     # at the top: 0.29 s up, slowing like a jump
SCREAM = 4.75                                  # the loudest hit: the scream
BEAT = 0.43                                    # the music's beat while he hangs
PULSES = (5.61, 6.47, 7.33)                    # every other beat after the scream
FALL = 8.0                                     # the music stops (8.05): he drops
LAND = 8.29                                    # 1.4 blocks under the game's gravity
CEL_LEN = 9.0
#: How high the figure hangs: its lowest point 24 px over the floor (1.4 blocks; a jump is 1.25).
LIFT = 24.0


def _pulse(t, t0, rise, fall):
    """0 -> 1 in `rise` seconds from t0, back to 0 over `fall`."""
    return kit.span(t, t0, t0 + rise) * (1 - kit.span(t, t0 + rise, t0 + rise + fall))


def cel_height(t):
    """The figure's lowest point over the floor (px): on it until the take-off; a jump's slowing rise
    to LIFT; floating there, one slow bob a bar (four beats), a push up on the scream; from FALL a
    fall under gravity (the height lost grows with the square of the time)."""
    if t <= TAKEOFF or t >= LAND:
        return 0.0
    if t < APEX:
        f = (t - TAKEOFF) / (APEX - TAKEOFF)
        return LIFT * (1 - (1 - f) ** 2)

    def floating(u):
        bob = 1.2 * math.sin(2 * math.pi * (u - SCREAM) / (4 * BEAT)) * kit.span(u, APEX, APEX + 0.6)
        return LIFT + bob + 1.5 * _pulse(u, SCREAM, 0.08, 0.6)
    if t < FALL:
        return floating(t)
    f = (t - FALL) / (LAND - FALL)
    return floating(FALL) * (1 - f * f)


def celebrate():
    """The author's squat (the chest over the knees, the head down, the arms swept back), held while
    the music is quiet, a nod on each of its hits and the breath held in its silence; on the riser
    down deeper, the arms further back; on the drop the legs push and the figure flies up 1.4 blocks
    in 0.29 s, the arms flung out, into the hang of the references: leant back, the chest out, the
    head back to the sky, the arms spread down at the sides, the knees bent, the right one higher.
    It hangs with the music, floating a little, a scream on its loudest hit, a pulse on every other
    beat; as the music stops it drops, the legs reaching down, lands on bent knees and stands up."""
    # the author's squat (his celebrate, the held part)
    squat = {'body': (0.6, 0, 0), 'chest': (-70, 0, 0), 'head': (-12.5, 0, 0),
             'right_arm': (-63.4, 21.6, 21.3), 'right_forearm': (15, 0, 0), 'left_arm': (-63.4, -21.6, -21.3), 'left_forearm': (15, 0, 0),
             'right_leg': (33.7, -14.3, 10.3), 'right_shin': (-77.5, 0, 0), 'left_leg': (33.7, 14.3, -10.3), 'left_shin': (-77.5, 0, 0)}
    load = {**squat, 'chest': (-76, 0, 0), 'head': (-17, 0, 0),
            'right_arm': (-74, 21.6, 24), 'left_arm': (-74, -21.6, -24),
            'right_leg': (40, -14.3, 10.3), 'right_shin': (-88, 0, 0), 'left_leg': (40, 14.3, -10.3), 'left_shin': (-88, 0, 0)}
    push = {'body': (-6, 0, 0), 'chest': (-14, 0, 0), 'head': (4, 0, 0),
            'right_arm': (-48, 6, 62), 'right_forearm': (12, 0, 0), 'left_arm': (-48, -6, -62), 'left_forearm': (12, 0, 0),
            'right_leg': (6, -4, 4), 'right_shin': (-8, 0, 0), 'left_leg': (6, 4, -4), 'left_shin': (-8, 0, 0)}
    # the hang: the author's pose (leant back 62 degrees, the right knee up, the left leg down) with the
    # references' arch: the chest out and back, the head back to the sky, the arms spread down
    hang = {'body': (21, 0, 0), 'chest': (17, 0, 0), 'head': (36, 0, 0),
            'right_arm': (-58, 6, 55), 'right_forearm': (20, 0, 0), 'left_arm': (-58, -6, -55), 'left_forearm': (20, 0, 0),
            'right_leg': (52, -10, 8), 'right_shin': (-100, 0, 0), 'left_leg': (-12, 10, -8), 'left_shin': (-62, 0, 0)}
    reach = {'body': (4, 0, 0), 'chest': (-4, 0, 0), 'head': (-4, 0, 0),
             'right_arm': (-14, 0, 58), 'right_forearm': (14, 0, 0), 'left_arm': (-14, 0, -58), 'left_forearm': (14, 0, 0),
             'right_leg': (12, -4, 5), 'right_shin': (-20, 0, 0), 'left_leg': (12, 4, -5), 'left_shin': (-20, 0, 0)}
    absorb = {'body': (4, 0, 0), 'chest': (-30, 0, 0), 'head': (14, 0, 0),
              'right_arm': (10, 0, 32), 'right_forearm': (26, 0, 0), 'left_arm': (10, 0, -32), 'left_forearm': (26, 0, 0),
              'right_leg': (50, -8, 6), 'right_shin': (-96, 0, 0), 'left_leg': (50, 8, -6), 'left_shin': (-96, 0, 0)}
    rise = {'body': (2, 0, 0), 'chest': (-10, 0, 0), 'head': (2, 0, 0),
            'right_arm': (4, 0, 14), 'right_forearm': (16, 0, 0), 'left_arm': (4, 0, -14), 'left_forearm': (16, 0, 0),
            'right_leg': (18, -3, 3), 'right_shin': (-34, 0, 0), 'left_leg': (18, 3, -3), 'left_shin': (-34, 0, 0)}
    keys = [(0.0, {}, 'auto'),
            (SQUAT_IN, squat, 'inout'),
            (LOAD_FROM, squat, 'auto'),
            (LOAD_TO, load, 'inout'),
            (TAKEOFF, push, 'in'),
            (APEX + 0.08, hang, 'out'),
            (FALL, hang, 'auto'),
            (LAND - 0.02, reach, 'inout'),
            (8.42, absorb, 'out'),
            (8.72, rise, 'inout'),
            (CEL_LEN, {}, 'inout')]
    d = L.Anim('celebrate_design', CEL_LEN)
    for t, pose, ease in keys:
        d.key(t, pose, ease, whole=True)

    def add(p, bone, dx=0.0, dz=0.0):
        r = p.get(bone, (0, 0, 0))
        p[bone] = (r[0] + dx, r[1], r[2] + dz)

    a = L.Anim('celebrate', CEL_LEN, 'once')
    for t in kit.frames(0, CEL_LEN):
        p = kit.design(d, t)
        # waiting in the squat: a slow breath, held in the music's silence before the riser; a nod on each hit
        sq_w = kit.span(t, SQUAT_IN - 0.1, SQUAT_IN + 0.3) * (1 - kit.span(t, LOAD_FROM, LOAD_FROM + 0.1))
        held = 1 - 0.75 * kit.span(t, 2.6, 2.9)
        add(p, 'chest', 1.2 * math.sin(2 * math.pi * (t - SQUAT_IN) / 2.0) * sq_w * held)
        nod = sum(s * _pulse(t, t0, 0.07, 0.35) for t0, s in NODS) * sq_w
        add(p, 'chest', -3 * nod)
        add(p, 'head', -7 * nod)
        # hanging: a breath with the bob, the shins swaying, the scream, a pulse on every other beat
        hang_w = kit.span(t, APEX - 0.05, APEX + 0.4) * (1 - kit.span(t, FALL - 0.02, FALL + 0.12))
        phase = 2 * math.pi * (t - SCREAM) / (4 * BEAT)
        add(p, 'chest', 1.5 * math.sin(phase) * hang_w)
        add(p, 'right_shin', 3 * math.sin(phase + 0.8) * hang_w)
        add(p, 'left_shin', 3 * math.sin(phase + 2.0) * hang_w)
        scream = _pulse(t, SCREAM, 0.06, 0.55) * hang_w
        beat = sum(_pulse(t, b, 0.05, 0.3) for b in PULSES) * hang_w
        add(p, 'body', 3 * scream)
        add(p, 'chest', 4 * scream + 3 * beat)
        add(p, 'head', 10 * scream + 3 * beat)
        add(p, 'right_forearm', 12 * scream)
        add(p, 'left_forearm', 12 * scream)
        add(p, 'right_arm', dz=14 * scream + 7 * beat)
        add(p, 'left_arm', dz=-14 * scream - 7 * beat)
        add(p, 'right_leg', 5 * scream)
        # the legs turn fast in the push and the landing: the feet a hair over the floor there, so the
        # game's ticks between these keys do not dip them into it
        hair = 0.6 * (kit.span(t, LOAD_TO, TAKEOFF - 0.04) * (1 - kit.span(t, TAKEOFF, TAKEOFF + 0.1))
                      + kit.span(t, LAND - 0.05, LAND) * (1 - kit.span(t, LAND + 0.08, LAND + 0.2)))
        p = kit.grounded(p, clearance=cel_height(t) + hair, bones=LEGS)
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    return a
