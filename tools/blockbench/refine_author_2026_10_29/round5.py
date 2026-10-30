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

Later messages, made here too: celebrate to the author's music (2026-10-29, again 2026-10-30); the
axe's head flat in the whirlwind and the axe's own finisher, finisher_chop (2026-10-30).

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


#: Where an axe's edge faces, in the held item's own frame. The rig's sword stands for any blade: its
#: guard lies in the plane of the game's flat item picture, as an axe's head does, and the axe's edge
#: is on the guard's -x side (up in the stance's grip; author, 2026-10-30: "in the turns the axe just
#: looks up", where that side was up).
EDGE_LOCAL = np.array((-1.0, 0.0, 0.0))


def edge(pose):
    """The way the axe's edge faces in the pose (world, unit)."""
    return kit.fk(pose)[ITEM][:3, :3] @ EDGE_LOCAL


def aimed_flat(pose, direction, edge_dir, start=(0.0, 0.0, 0.0)):
    """The pose with the item turned in the fist so the blade points along `direction` and the
    edge faces `edge_dir` (world; only its part across the blade counts)."""
    want = np.asarray(direction, float)
    want = want / np.linalg.norm(want)
    e = np.asarray(edge_dir, float)
    e = e - want * e.dot(want)
    e = e / np.linalg.norm(e)

    def miss(r):
        q = held(pose, r)
        g, t = blade(q)
        d = t - g
        d = d / np.linalg.norm(d)
        return np.concatenate([(d - want) * 10.0, (edge(q) - e) * 10.0, (np.asarray(r) - np.asarray(start)) * 0.002])

    from scipy.optimize import least_squares
    res = least_squares(miss, np.asarray(start, float), diff_step=1e-3, max_nfev=300)
    return held(pose, tuple(float(v) for v in res.x))


#: The item group's own turn in the rig: Blockbench adds a key's angles to it (angle by angle), so
#: turns of the item are worked out on the sum and the key is what is left after it.
ITEM_REST = np.array(kit.RIG.groups[kit.RIG.by_name[ITEM]]['rotation'], float)


def item_continuous(prev, v):
    """The item's key v made continuous with prev (as kit.continuous, on the whole turn)."""
    if prev is None:
        return tuple(float(x) for x in v)
    return tuple(float(x) for x in kit.continuous(np.add(prev, ITEM_REST), np.add(v, ITEM_REST)) - ITEM_REST)


def turned_between(a, b, f, prev=None):
    """The item's key a fraction f of the way from a to b, along the shortest turn between the two
    (as a wrist turns it; not angle by angle, which tumbles it); continuous with prev."""
    from scipy.spatial.transform import Rotation, Slerp
    ends = Rotation.from_matrix(np.stack([kit.rot_of(ITEM_REST + np.asarray(a, float)),
                                          kit.rot_of(ITEM_REST + np.asarray(b, float))]))
    m = Slerp([0.0, 1.0], ends)([min(1.0, max(0.0, f))]).as_matrix()[0]
    return item_continuous(prev, kit.euler_of(m) - ITEM_REST)


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
    prev_item = None
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
        # the axe (author, 2026-10-30: "the axe just looks up, unturned in the hand: the blade flat to
        # the ground, not upright, facing out"): in the turns the haft out along the arm and a little
        # up, the head flat to the ground, its edge leading into the turn, into whatever it meets. The
        # wrist turns it so over the wind-up and back over the end, along the shortest turn in the fist.
        r = math.radians(yaw)
        out = np.array((math.cos(r), 0.18, -math.sin(r)))     # the body's right, turned with it (Blockbench +Y turns +x to -z)
        lead = np.array((-math.sin(r), 0.0, -math.cos(r)))    # the way that hand goes as the body turns
        if t <= SPIN_TO:
            q = aimed_flat(p, out, lead, start=item)
            item = item_continuous(item, q[ITEM])
            out_rot = item
            f = kit.smooth(t / (SPIN_FROM - 0.05))
            p = held(p, item if f >= 1.0 else turned_between((0.0, 0.0, 0.0), item, f, prev_item))
        else:
            f = kit.smooth(min(1.0, (t - SPIN_TO) / (1.95 - SPIN_TO)))
            p = held(p, turned_between(out_rot, (0.0, 0.0, 0.0), f, prev_item))
        prev_item = p[ITEM]
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


# --------------------------------------------------------------------------------------------
# finisher_chop: the finisher with an axe (author, 2026-10-30)
# --------------------------------------------------------------------------------------------

#: Author, 2026-10-30: "a finisher for the axe, so that it does not stab into the belly like the
#: sword but strikes from above, at the head or just at the body, with a vertical swing". No draft:
#: made from the description. The mob stands at point blank, facing the player (the finisher's reach,
#: 0.8 block from the player's middle to its side: its middle 18.8 px in front); the grey figure
#: stands for it here, weakened and stooping, and never reaches the game.
#: The free hand takes it by the shoulder (CH_GRAB), the axe goes up and hangs down behind the back
#: (to CH_TOP, a last inch to CH_HOLD), comes over and down onto it by the neck (CH_HIT:
#: Strikes.CHOP_HIT, the kill) and on into its chest (CH_DEEP), stays in while it sags, is wrenched
#: out (CH_PULL) and the stance comes back by CH_LEN. The blow takes 0.12 s from the top (CH_HOLD to
#: CH_HIT): two and a half of the game's ticks, so the swing is seen, not skipped.
CH_GRAB, CH_TOP, CH_HOLD, CH_HIT, CH_DEEP, CH_PULL, CH_LEN = 0.17, 0.42, 0.46, 14 / 24, 16 / 24, 0.9, 1.4167
MOB_AT = (0.0, 0.0, -18.8)
#: Where the axe's head goes, on the mob as it is then: down onto the top of its shoulder by the neck
#: (its left one, before the player's right hand) at the blow (CH_HIT), then on down into its chest
#: (CH_DEEP). The game's arms do not reach over the head, so at point blank a blow onto the head's
#: top would come up at it, the haft upright; onto the shoulder it comes down from above. The axe is
#: long for point blank: its head goes in a little further, into the root of the neck (and the mob
#: stands nearer or further in the game anyway).
HIT_ON, HIT_OFFSET = 'lshoulder2', np.array((-1.5, 2.0, 0.5))
DEEP_ON, DEEP_OFFSET = 'chestfront2', np.array((3.0, -1.0, -2.5))
#: The arm's turn about its own length is held near its design (a solver left free finds the same
#: fist with the arm turned half round), and the elbow only bends.
ARM_LIMITS = {('right_arm', 1): (-60.0, 40.0), ('right_arm', 2): (-25.0, 45.0), ('right_forearm', 0): (0.0, 140.0)}
kit.POINTS.setdefault('axetip', (ITEM, tuple(TIP_LOCAL)))


#: The wrist cocked for the chop: the haft turned this far from across the fist towards the line of
#: the forearm, as a hand holds an axe up for a blow (the head then leads the swing less steeply).
CHOP_COCK = 35.0


def chop_grip():
    """The axe's turn in the fist for the chop: the game's grip (the haft across the fist, out of
    the thumb's side) turned half round about the haft, so the edge faces away from the elbow, the
    way the head goes in a blow from above (in the game's grip it faces the elbow: the blow would
    land with the back of the head); and the wrist cocked by CHOP_COCK."""
    from scipy.spatial.transform import Rotation
    axis = (TIP_LOCAL - GRIP_LOCAL) / np.linalg.norm(TIP_LOCAL - GRIP_LOCAL)
    turned = kit.rot_of(ITEM_REST) @ Rotation.from_rotvec(np.pi * axis).as_matrix()
    rolled = tuple(float(v) for v in kit.euler_of(turned) - ITEM_REST)
    # cocked: at rest (the forearm hanging) the haft tips from forward towards the ground
    rest = held({}, rolled)
    g, t = blade(rest)
    cock = Rotation.from_euler('x', -CHOP_COCK, degrees=True).as_matrix()
    return aimed_flat(rest, cock @ (t - g), cock @ edge(rest), start=rolled)[ITEM]


def _mob(t):
    """The mob's own pose: stooping, weakened; at the blow its head snaps down, the knees give and it
    sags down and back, away from the player (who leans in over it)."""
    weak = {'chest': (-9, 0, 0), 'head': (-8, 0, 0), 'right_arm': (8, 0, 5), 'right_forearm': (14, 0, 0),
            'left_arm': (8, 0, -5), 'left_forearm': (14, 0, 0), 'right_leg': (5, 0, 2), 'right_shin': (-8, 0, 0),
            'left_leg': (5, 0, -2), 'left_shin': (-8, 0, 0)}
    struck = {**weak, 'body': (8, 0, 0), 'chest': (-10, 0, 0), 'head': (-30, 0, 0), 'right_arm': (-6, 0, 10), 'left_arm': (-6, 0, -10),
              'right_leg': (18, 0, 3), 'right_shin': (-36, 0, 0), 'left_leg': (18, 0, -3), 'left_shin': (-36, 0, 0)}
    sag = {**struck, 'body': (18, 0, 0), 'chest': (-12, 0, 0), 'head': (-24, 0, 0), 'right_leg': (42, 0, 4), 'right_shin': (-84, 0, 0),
           'left_leg': (42, 0, -4), 'left_shin': (-84, 0, 0), 'right_arm': (4, 0, 12), 'left_arm': (4, 0, -12)}
    d = L.Anim('mob', CH_LEN)
    for tk, pose, ease in ((0.0, weak, 'auto'), (CH_HIT, weak, 'auto'), (CH_HIT + 0.08, struck, 'out'),
                           (0.95, sag, 'inout'), (CH_LEN, sag, 'auto')):
        d.key(tk, pose, ease, whole=True)
    return kit.grounded(kit.design(d, t))


def _on_mob(t, name, offset):
    """A point of the mob (as it is at t), moved by offset (world px)."""
    return kit.pt(kit.fk(kit.as_partner(_mob(t), MOB_AT)), name) + offset


def _axe_onto(pose, point):
    """The right arm solved so the axe's head is at `point`, the grip as the pose holds it and the
    arm's designed angles preferred. Returns (pose, miss px)."""
    q, err, x = kit.solve(pose, [('axetip', np.asarray(point, float), 1.0)], kit.arm_vary('right'), ARM_LIMITS,
                          reg=[0.08, 0.1, 0.08, 0.08])
    return q, err


def _left_hand_on(pose, mob_own):
    """The left arm solved so the fist closes over the top front of the mob's right shoulder."""
    W = kit.fk({**pose, **kit.as_partner(mob_own, MOB_AT)})
    target = kit.pt(W, 'rshoulder2') + np.array((0.0, 1.0, 2.6))
    q, err, x = kit.solve(pose, [('lhand', target, 1.0)], kit.arm_vary('left'), kit.arm_bounds('left'), reg=0.004)
    return q


def finisher_chop():
    """A step into the stance, the left foot forward; the free hand takes the mob by the shoulder and
    holds it; the axe goes up and hangs down behind the back, the body leaning back, the right
    shoulder drawn back, the weight on the back foot; a last inch, then the chop: the body drives
    forward and down onto the front knee, the shoulders square, the arm comes down, the axe's head
    over the shoulder and down onto the mob by the neck, the edge first, and on down into its chest;
    it stays in while the mob sags, the hand pressing it down; it is wrenched out, the free hand
    shoving the mob off, and the stance comes back. The axe turns in the fist as it is raised, in
    front (chop_grip: the edge away from the elbow, the wrist cocked), and back at the end; between,
    the grip holds."""
    stance = {'right_leg': (-14, 0, 4), 'right_shin': (-10, 0, 0), 'left_leg': (16, 0, -3), 'left_shin': (-14, 0, 0)}
    ready = {**stance, 'torso': (0, -8, 0), 'chest': (-4, 0, 0), 'head': (2, 4, 0),
             'right_arm': (35, 0, 8), 'right_forearm': (40, 0, 0)}
    top = {'body': (5, 0, 0), 'body@p': (0, 0, 1.0), 'torso': (0, -18, 0), 'chest': (8, 0, 0), 'head': (2, 10, 0),
           'right_arm': (172, 0, -10), 'right_forearm': (85, 0, 0),
           'right_leg': (-10, 0, 4), 'right_shin': (-18, 0, 0), 'left_leg': (18, 0, -3), 'left_shin': (-10, 0, 0)}
    hold = {**top, 'right_arm': (176, 0, -10), 'right_forearm': (92, 0, 0), 'chest': (10, 0, 0)}
    # the arm goes up and comes down straight forward, a little out at the top: the game's arms do not
    # reach over the head, so the axe passes by its right side, in the arm's own plane, never through it
    lift = {**ready, 'torso': (0, -14, 0), 'chest': (2, 0, 0), 'head': (2, 8, 0), 'right_arm': (100, 0, -5), 'right_forearm': (15, 0, 0)}
    hit = {'body': (-4, 0, 0), 'body@p': (0, 0, -1.5), 'torso': (0, 10, 0), 'chest': (-12, 0, 0), 'head': (2, -4, 0),
           'right_arm': (30, 0, 0), 'right_forearm': (60, 0, 0),
           'right_leg': (-18, 0, 4), 'right_shin': (-8, 0, 0), 'left_leg': (26, 0, -3), 'left_shin': (-34, 0, 0)}
    deep = {**hit, 'body': (-5, 0, 0), 'chest': (-16, 0, 0), 'head': (-4, -4, 0), 'torso': (0, 12, 0),
            'right_arm': (20, 0, 0), 'right_forearm': (50, 0, 0), 'left_shin': (-38, 0, 0)}
    stuck = {**deep, 'chest': (-18, 0, 0), 'body@p': (0, 0, -1.7)}
    pull = {**stance, 'body': (-2, 0, 0), 'body@p': (0, 0, -0.8), 'torso': (0, 0, 0), 'chest': (-10, 0, 0), 'head': (0, 0, 0),
            'right_arm': (70, -6, 16), 'right_forearm': (38, 0, 0), 'left_arm': (72, 0, -8), 'left_forearm': (8, 0, 0)}
    back = {'chest': (-4, 0, 0), 'right_arm': (26, 0, 10), 'right_forearm': (34, 0, 0), 'left_arm': (16, 0, -8),
            'left_forearm': (22, 0, 0), 'right_leg': (-6, 0, 3), 'right_shin': (-6, 0, 0), 'left_leg': (8, 0, -2), 'left_shin': (-8, 0, 0)}

    # the blow and the follow-through: the arm solved, with the chop's grip, so the axe's head goes
    # into the mob by the neck, then down into its chest (the stuck pose keeps that arm)
    grip = chop_grip()
    q, err_hit = _axe_onto(held(hit, grip), _on_mob(CH_HIT, HIT_ON, HIT_OFFSET))
    hit['right_arm'], hit['right_forearm'] = q['right_arm'], q['right_forearm']
    q, err_deep = _axe_onto(held(deep, grip), _on_mob(CH_DEEP, DEEP_ON, DEEP_OFFSET))
    deep['right_arm'], deep['right_forearm'] = q['right_arm'], q['right_forearm']
    stuck['right_arm'], stuck['right_forearm'] = deep['right_arm'], deep['right_forearm']

    keys = [(0.0, {}, 'auto'), (CH_GRAB, ready, 'inout'), (0.3, lift, 'auto'), (CH_TOP, top, 'out'), (CH_HOLD, hold, 'linear'),
            (CH_HIT, hit, 'in'), (CH_DEEP, deep, 'out'), (0.8, stuck, 'inout'), (CH_PULL, pull, 'out'),
            (1.08, back, 'inout'), (CH_LEN, {}, 'inout')]
    # the free hand on the mob's shoulder from the grab until the axe is out (solved at each key)
    for i, (tk, pose, ease) in enumerate(keys):
        if CH_GRAB <= tk <= 0.8:
            keys[i] = (tk, _left_hand_on(pose, _mob(tk)), ease)
    d = L.Anim('chop_design', CH_LEN)
    for tk, pose, ease in keys:
        d.key(tk, pose, ease, whole=True)

    rest = (0.0, 0.0, 0.0)

    def grip_at(t):
        if t <= 0.95:
            return turned_between(rest, grip, kit.smooth((t - 0.02) / (0.2 - 0.02)))
        return turned_between(grip, rest, kit.smooth((t - 0.95) / (1.28 - 0.95)))

    a = L.Anim('finisher_chop', CH_LEN, 'once')
    prev_item = None
    for t in kit.frames(0, CH_LEN):
        p = kit.grounded(kit.design(d, t), bones=LEGS)
        p = held(p, item_continuous(prev_item, grip_at(t)))
        prev_item = p[ITEM]
        p.update(kit.as_partner(_mob(t), MOB_AT))
        a.key(t, p)
    a.tol_rot, a.tol_pos = 0.4, 0.08
    a.chop_error = (err_hit, err_deep)
    return a
