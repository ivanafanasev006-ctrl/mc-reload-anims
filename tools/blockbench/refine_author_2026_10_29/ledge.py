"""The ledge set (author, 2026-10-29, from a description, no draft): hanging from the edge of a drop
by one hand and swinging, moving along the edge hand over hand, hopping along it, pulling up onto
it, and being pulled up by a friend.

Where the ledge is (the mechanic puts the player there): the top of the edge 31 px of the model
above the player's feet (31 * 0.9375 / 16 = 1.82 blocks), the face of the wall 5.12 px in front of
the player (0.3 block: the hitbox against it). A hand holds the edge with its fist 1 px over the
top and just behind the edge, so in the game it closes over the corner.

Moves along or up the ledge are made by the mechanic, not inside the model (as for every move of the
mod): the model keeps its hands where the ledge is while the player is carried. Each move's own path
(ROOT_*) says where the player must be at each moment; docs/moves.md has them in blocks.
"""
import math

import numpy as np

import kit
from kit import L

LEDGE_Y = 31.0
WALL_Z = -5.12
GRIP_Z = WALL_Z - 1.2
HAND_Y = LEDGE_Y + 0.5
PX = 0.9375 / 16  # blocks a pixel of the model


def grip(x):
    return np.array([x, HAND_Y, GRIP_Z])


# --------------------------------------------------------------------------------------------
# Poses
# --------------------------------------------------------------------------------------------

ARMS_STRAIGHT = {**kit.arm_bounds('right', bend=(0, 40)), **kit.arm_bounds('left', bend=(0, 40))}


def hang2(x_l=-6.5, x_r=6.5, legs=None, extra=None):
    """Both hands on the edge, arms nearly straight, the chest a little off the wall, legs hanging."""
    base = {'body@p': (0, 1, -1.1), 'body': (-9, 0, 0),
            'right_arm': (166, 1, -3), 'left_arm': (166, -1, 3), 'right_forearm': (2, 0, 0), 'left_forearm': (2, 0, 0),
            'chest': (2, 0, 0), 'head': (14, 0, 0),
            'right_leg': (5, 0, 1.5), 'left_leg': (8, 0, -1.5), 'right_shin': (-10, 0, 0), 'left_shin': (-16, 0, 0)}
    base.update(legs or {})
    base.update(extra or {})
    vary = kit.arm_vary('right') + kit.arm_vary('left') + [('body@p', 1), ('body@p', 2)]
    tg = [('rhand', grip(x_r), 1), ('lhand', grip(x_l), 1), ('chestfront', 2, WALL_Z + 0.7, 0.4)]
    p, err, _ = kit.solve(base, tg, vary, ARMS_STRAIGHT, reg=0.02)
    return p


def hang1(x=0.0, extra=None):
    """The left hand alone on the edge, right above the player's middle: the body hangs below it
    (its weight under the hand, so it tilts with the hanging shoulder up), the free arm loose."""
    base = {'body@p': (2, 0, -1.5), 'body': (-6, 0, -18),
            'left_arm': (172, -4, 12), 'left_forearm': (2, 0, 0),
            'right_arm': (8, 0, 14), 'right_forearm': (18, 0, 0),
            'chest': (0, 0, 4), 'head': (18, 12, 10),
            'right_leg': (6, 0, 3), 'left_leg': (9, 0, -1), 'right_shin': (-12, 0, 0), 'left_shin': (-20, 0, 0)}
    base.update(extra or {})
    vary = kit.arm_vary('left') + [('body@p', 0), ('body@p', 1), ('body@p', 2), ('body', 2)]
    tg = [('lhand', grip(x), 1), ('hip', 0, x + 0.0, 0.6), ('chestfront', 2, WALL_Z + 0.6, 0.3)]
    p, err, _ = kit.solve(base, tg, vary, {**kit.arm_bounds('left', bend=(0, 20)), ('body', 2): (-30, 0)}, reg=0.01)
    return p


HANG2 = hang2()
HANG1 = hang1()


# --------------------------------------------------------------------------------------------
# Hanging by one hand, swinging (ledge_hang)
# --------------------------------------------------------------------------------------------

SWING_DEG = 35.0    # author: 30-40 degrees each way
SWING_T = 2.5       # a full swing there and back: about a 1.5 m pendulum's, not fast (and on the
                    # 1/24 s grid of the keys and the 1/20 s of the game, so the loop closes exactly)
HANG_CATCH = 0.45   # the catch settles by then


def ledge_hang():
    """The left hand catches the edge and the body swings left and right under it, 35 degrees each
    way. Loops from the marker at half a swing; the legs and the free arm trail behind the swing,
    the head keeps nearer upright and looks up at the hand."""
    w = 2 * math.pi / SWING_T
    prev = {}

    def pose(t):
        th = SWING_DEG * math.sin(w * t)
        lag = lambda d: math.sin(w * t - d)  # noqa: E731
        c = 1 - kit.span(t, 0.0, HANG_CATCH, kit.smoother)   # the catch fades out
        jolt = math.sin(math.pi * min(1.0, t / HANG_CATCH)) if t < HANG_CATCH else 0.0
        p = dict(HANG1)
        # the free arm and the legs trail the swing (they keep going a little at each end)
        p['right_arm'] = (8 + 60 * c, 0, 14 + 0.32 * SWING_DEG * lag(0.75) + 20 * c)
        p['right_forearm'] = (18 + 6 * math.sin(w * t * 2 - 0.4) + 25 * c, 0, 0)
        legz = 0.22 * SWING_DEG * lag(0.6)
        p['right_leg'] = (6 + 22 * jolt, 0, 3 + legz)
        p['left_leg'] = (9 + 30 * jolt, 0, -1 + legz)
        p['right_shin'] = (-12 - 4 * abs(math.sin(w * t)) - 30 * jolt, 0, 0)
        p['left_shin'] = (-20 - 6 * abs(math.sin(w * t - 0.3)) - 35 * jolt, 0, 0)
        p['chest'] = (0, 0, 4 - 0.12 * SWING_DEG * lag(0.4))
        hx, hy, hz = HANG1['head']
        p['head'] = (hx - 4 * c, hy, hz - 0.45 * th)
        # the catch: the elbow takes the weight and gives a little, then straightens
        p['left_forearm'] = (2 + 22 * jolt, 0, 0)
        p = kit.solve(p, [('lhand', grip(0.0), 1)], kit.arm_vary('left') + [('body@p', 1)],
                      kit.arm_bounds('left', bend=(0, 40)), reg=0.02,
                      start=prev.get('arm'))[0] if jolt > 0 else p
        # the whole figure swings about the hand, and twists a little about the arm
        tw = kit.rot_of((0, 9 * math.cos(w * t), 0))
        p = kit.turned_about(p, tw, 'lhand')
        p = kit.turned_about(p, kit.rot_of((0, 0, th)), 'lhand')
        p['body'] = tuple(kit.continuous(prev.get('body'), p['body']))
        prev['body'] = p['body']
        return p

    length = SWING_T / 2 + SWING_T
    a = L.Anim('ledge_hang', length, 'loop')
    kit.bake(a, kit.frames(0, length), pose)
    a.markers = [SWING_T / 2]
    a.protect = a.markers   # the loop closes on the marker's frame exactly
    return a


# --------------------------------------------------------------------------------------------
# Hand over hand along the edge (ledge_shimmy_right, ledge_shimmy_left)
# --------------------------------------------------------------------------------------------

SHIMMY_STEP = 10.0   # px the player goes in one round (each hand moves once): 0.59 block
SHIMMY_T = 1.0       # s a round: 0.59 block a second
SHIMMY_REACH = 0.34  # share of the round a hand is off the edge


def _shimmy_hand(t, mean, phase):
    """(position, off the edge 0..1) of one hand at time t of the round, in the player's frame:
    planted, it goes back as fast as the player goes on (so it stays on the same spot of the
    edge); off the edge, it swings ahead over the top."""
    T, D, d = SHIMMY_T, SHIMMY_STEP, SHIMMY_REACH
    u = ((t - phase) % T) / T
    v = D / T
    if u < d:
        f = kit.smooth(u / d)
        x = mean - D * (1 - d) / 2 + D * (1 - d) * f
        lift = math.sin(math.pi * u / d)
        return np.array([x, HAND_Y + 1.8 * lift, GRIP_Z + 0.9 * lift]), lift
    x = mean + D * (1 - d) / 2 - v * (u - d) * T
    return grip(x), 0.0


def ledge_shimmy(side='right'):
    """Both hands on the edge; the leading hand lets go, swings ahead and takes hold, then the
    other follows to it (as on monkey bars). The body hangs a little towards whichever hand holds
    it alone and the legs trail; the head looks the way the player goes. Loops."""
    T = SHIMMY_T
    prev = {}

    def right(t):
        hr, lr = _shimmy_hand(t, 7.0, 0.0)
        hl, ll = _shimmy_hand(t, -7.0, T / 2)
        wr, wl = 1 - lr, 1 - ll
        sup = (wr * hr[0] + wl * hl[0]) / max(1e-6, wr + wl)
        p = dict(HANG2)
        p['body@p'] = (0.18 * sup, HANG2['body@p'][1], HANG2['body@p'][2])
        bz = 0.55 * sup
        p['body'] = (HANG2['body'][0], 0, bz)
        # the legs hang more upright than the body tilts, a moment behind it
        hr2, lr2 = _shimmy_hand(t - 0.12, 7.0, 0.0)
        hl2, ll2 = _shimmy_hand(t - 0.12, -7.0, T / 2)
        sup_late = ((1 - lr2) * hr2[0] + (1 - ll2) * hl2[0]) / max(1e-6, 2 - lr2 - ll2)
        lz = -0.45 * sup_late
        p['right_leg'] = (5 + 3 * math.sin(2 * math.pi * t / T), 0, 1.5 + lz)
        p['left_leg'] = (8 - 3 * math.sin(2 * math.pi * t / T), 0, -1.5 + lz)
        p['right_shin'] = (-10 - 5 * (0.5 + 0.5 * math.sin(2 * math.pi * t / T + 0.5)), 0, 0)
        p['left_shin'] = (-16 - 5 * (0.5 - 0.5 * math.sin(2 * math.pi * t / T + 0.5)), 0, 0)
        p['chest'] = (2, 0, -0.25 * bz)
        p['head'] = (12, -16, -0.3 * bz)
        p['right_forearm'] = (2 + 22 * lr, 0, 0)
        p['left_forearm'] = (2 + 22 * ll, 0, 0)
        vary = kit.arm_vary('right') + kit.arm_vary('left') + [('body@p', 1)]
        b = {**kit.arm_bounds('right', bend=(0, 70)), **kit.arm_bounds('left', bend=(0, 70))}
        pref = [*HANG2['right_arm'], 2 + 22 * lr, *HANG2['left_arm'], 2 + 22 * ll, HANG2['body@p'][1]]
        reg = [0.004] * 8 + [0.05]
        p, err, x = kit.solve(p, [('rhand', hr, 1), ('lhand', hl, 1)], vary, b, reg=reg, prefer=pref,
                              start=prev.get('x'))
        prev['x'] = x
        return p

    a = L.Anim(f'ledge_shimmy_{side}', T, 'loop')
    a.tol_rot, a.tol_pos = 0.3, 0.06
    kit.bake(a, kit.frames(0, T), right if side == 'right' else (lambda t: kit.mirror_pose(right(t))))
    return a


def shimmy_speed():
    """Blocks a second the mechanic must carry the player along the edge."""
    return SHIMMY_STEP * PX / SHIMMY_T


# --------------------------------------------------------------------------------------------
# A hop along the edge (ledge_hop_left, ledge_hop_right), as in Uncharted
# --------------------------------------------------------------------------------------------

HOP_BLOCKS = 1.0             # how far one hop goes along the edge
HOP_J = HOP_BLOCKS / PX      # the same in pixels of the model
HOP_RELEASE, HOP_CATCH, HOP_CATCH2 = 0.55, 0.85, 0.95


def ledge_hop(side='left'):
    """Both hands on the edge: the body first swings away from the hop (author: a hop to the left
    swings it right), then back, the hands let go and the player flies along the edge; the leading
    hand catches it, then the other, and the body swings on towards the hop and settles. The
    mechanic carries the player HOP_BLOCKS along the edge between HOP_RELEASE and HOP_CATCH."""
    th = kit.keys((0.0, 0), (0.4, 20), (0.55, -8), (0.85, -16), (1.07, -24), (1.38, 10), (1.62, -4), (1.85, 0))
    lift = kit.keys((0.0, 0), (0.4, 0), (0.55, 2.2), (0.7, 4.0), (0.85, 0.5), (0.93, -1.2), (1.1, 0), (1.85, 0))
    elbow = kit.keys((0.0, 2), (0.38, 6), (0.55, 38), (0.85, 8), (0.93, 26), (1.12, 4), (1.85, 2))
    tuck = kit.keys((0.0, 0), (0.35, 0.35), (0.55, 1.0), (0.72, 0.8), (0.85, 0.1), (1.0, 0.3), (1.3, 0.0), (1.85, 0))
    legs_z = kit.keys((0.0, 0), (0.45, -8), (0.62, 10), (0.95, 4), (1.15, 12), (1.45, -6), (1.7, 2), (1.85, 0))
    look = kit.keys((0.0, 0), (0.3, 1), (1.2, 1), (1.85, 0.3))
    # hands in the player's frame: on the edge, or flying with the player to the next hold
    lhand = kit.keys((HOP_RELEASE, grip(-6.5)), (0.7, (-14.0, HAND_Y + 4.0, GRIP_Z + 1.2)), (HOP_CATCH, grip(-6.5)))
    rhand = kit.keys((HOP_RELEASE, grip(6.5)), (0.75, (7.5, HAND_Y + 2.0, GRIP_Z + 2.5)), (HOP_CATCH2, grip(6.5)))
    prev = {}

    def left(t):
        p = dict(HANG2)
        tk = tuck(t)
        p['right_leg'] = (5 + 30 * tk, 0, 1.5 + legs_z(t))
        p['left_leg'] = (8 + 36 * tk, 0, -1.5 + legs_z(t))
        p['right_shin'] = (-10 - 55 * tk, 0, 0)
        p['left_shin'] = (-16 - 60 * tk, 0, 0)
        p['head'] = (14 + 6 * look(t), 24 * look(t), 0)
        p['right_forearm'] = (elbow(t), 0, 0)
        p['left_forearm'] = (elbow(t), 0, 0)
        p = kit.shifted(p, (0, lift(t), 0))
        mid = (grip(-6.5) + grip(6.5)) / 2
        p = kit.turned_about_point(p, kit.rot_of((0, 0, th(t))), mid)
        p['body'] = tuple(kit.continuous(prev.get('body'), p['body']))
        prev['body'] = p['body']
        hl = grip(-6.5) if t <= HOP_RELEASE or t >= HOP_CATCH else np.array(lhand(t))
        hr = grip(6.5) if t <= HOP_RELEASE or t >= HOP_CATCH2 else np.array(rhand(t))
        vary = kit.arm_vary('right') + kit.arm_vary('left') + [('body@p', 0), ('body@p', 1), ('body@p', 2)]
        b = {**kit.arm_bounds('right', bend=(0, 90)), **kit.arm_bounds('left', bend=(0, 90))}
        pref = [*HANG2['right_arm'], elbow(t), *HANG2['left_arm'], elbow(t), *p['body@p']]
        reg = [0.004] * 8 + [0.05] * 3
        p, err, x = kit.solve(p, [('rhand', hr, 1), ('lhand', hl, 1)], vary, b, reg=reg, prefer=pref,
                              start=prev.get('x'))
        prev['x'] = x
        return p

    a = L.Anim(f'ledge_hop_{side}', 1.85, 'once')
    a.tol_rot, a.tol_pos = 0.3, 0.06
    kit.bake(a, kit.frames(0, 1.85), left if side == 'left' else (lambda t: kit.mirror_pose(left(t))))
    return a


def hop_path(t):
    """Blocks along the edge the hop has carried the player at time t (the mechanic's path)."""
    return HOP_BLOCKS * kit.span(t, HOP_RELEASE, HOP_CATCH, kit.smooth)


# --------------------------------------------------------------------------------------------
# Limbs on targets: the limbs of the points that have a weight are solved, the rest is kept
# --------------------------------------------------------------------------------------------

LIMB = {'hand': 'arm', 'fist': 'arm', 'foot': 'leg', 'knee': 'leg', 'toe': 'leg', 'heel': 'leg'}


def limb_of(point):
    """('right', 'arm') for 'rhand', ('left', 'leg') for 'lknee2'..."""
    side = 'right' if point[0] == 'r' else 'left'
    part = next(v for k, v in LIMB.items() if point[1:].rstrip('2') == k)
    return side, part


def on_targets(pose, targets, prev, key, partner=False, body=()):
    """targets: [(point, world xyz, weight 0..1)]. Solves the limbs of the weighted points (their
    designed angles preferred a little, the turn about the limb's own length more), warm-started
    from the previous frame under the same key."""
    s = '2' if partner else ''
    tg = [(n + s, np.asarray(x, float), w) for n, x, w in targets if w > 1e-3]
    if not tg:
        return pose
    vary, b = [], {}
    for n, _, _ in tg:
        side, part = limb_of(n)
        vs = kit.arm_vary(side, partner) if part == 'arm' else kit.leg_vary(side, partner)
        bs = kit.arm_bounds(side, partner, (0, 140)) if part == 'arm' else kit.leg_bounds(side, partner, (-150, 0))
        for v in vs:
            if v not in vary:
                vary.append(v)
        b.update(bs)
    nlimb = len(vary)
    vary += [v for v, _ in body]   # the body may give a little too (each with its own pull back)
    pref = [float(pose.get(k, (0, 0, 0))[ax]) for k, ax in vary]
    reg = [0.03 if ax == 1 else 0.008 for k, ax in vary[:nlimb]] + [w for _, w in body]
    # each joint starts from where it was solved last frame (a limb that just took a target
    # starts from its designed angles), so the solve never jumps to another set of angles
    last = prev.setdefault(key, {})
    start = [last.get(v, pv) for v, pv in zip(vary, pref)]
    out, err, x = kit.solve(pose, tg, vary, b, reg=reg, prefer=pref, start=start)
    last.update(zip(vary, x))
    for v in list(last):
        if v not in vary:
            del last[v]
    return out


# --------------------------------------------------------------------------------------------
# Pulling up onto the edge (ledge_climb_up)
# --------------------------------------------------------------------------------------------

STAND = (0.0, LEDGE_Y, WALL_Z - 5.12)   # where the player stands at the end: on the top, clear of the drop
CLIMB_LEN = 2.0


def ledge_climb_up():
    """From both hands on the edge: a short gather, the pull-up (the right foot walks onto the wall
    and pushes), the elbows go over the hands and press, the right knee comes onto the top, then
    the left foot, and the player stands up on the top. The mechanic carries the player along
    `root` (it follows the head, so the camera goes up with it), through the edge's corner."""
    hang = np.array(HANG2['body@p'])
    end = np.array(STAND)
    top = LEDGE_Y
    sz = STAND[2]
    body_p = kit.keys((0.0, hang), (0.22, hang + (0, -0.8, -0.2)), (0.62, (0, 8.2, -1.6)),
                      (0.92, (0, 14.5, -3.6)), (1.15, (0, 20.6, -5.8)), (1.45, (0, 26.0, -8.6)),
                      (1.8, end), (CLIMB_LEN, end))
    body_r = kit.keys((0.0, HANG2['body']), (0.22, (-6, 0, 0)), (0.62, (-6, 0, 0)), (0.92, (-30, 0, 0)),
                      (1.15, (-38, 4, 3)), (1.45, (-20, 0, 0)), (1.8, (0, 0, 0)), (CLIMB_LEN, (0, 0, 0)))
    chest = kit.keys((0.0, HANG2['chest']), (0.62, (4, 0, 0)), (0.92, (-22, 0, 0)), (1.15, (-26, 0, 0)),
                     (1.45, (-18, 0, 0)), (1.8, (-2, 0, 0)), (CLIMB_LEN, (0, 0, 0)))
    head = kit.keys((0.0, HANG2['head']), (0.4, (22, 0, 0)), (0.62, (18, 0, 0)), (0.92, (34, 0, 0)),
                    (1.15, (36, 0, 0)), (1.45, (24, 0, 0)), (1.8, (4, 0, 0)), (CLIMB_LEN, (0, 0, 0)))
    # how much each target holds (0..1) and where
    hands_w = kit.keys((0.0, 1), (1.22, 1), (1.36, 0))
    rfoot_wall = kit.keys((0.0, 0), (0.34, 0), (0.48, 1), (0.92, 1), (1.0, 0))
    rknee_top = kit.keys((0.0, 0), (1.06, 0), (1.2, 1), (1.5, 1), (1.62, 0))
    lfoot_w = kit.keys((0.0, 0), (1.18, 0), (1.24, 1), (CLIMB_LEN, 1))
    rfoot_w = kit.keys((0.0, 0), (1.5, 0), (1.56, 1), (CLIMB_LEN, 1))
    lfoot_path = kit.keys((1.18, (-1.9, 20.0, WALL_Z + 1.5)), (1.32, (-1.9, top + 4.5, WALL_Z - 1.5)),
                          (1.42, (-1.9, top, sz + 1.2)), (1.8, (-1.9, top, sz)), (CLIMB_LEN, (-1.9, top, sz)))
    rfoot_path = kit.keys((1.5, (1.9, top + 1.0, WALL_Z - 1.0)), (1.62, (1.9, top + 3.5, sz + 1.0)),
                          (1.72, (1.9, top, sz)), (CLIMB_LEN, (1.9, top, sz)))
    # free arms after letting go: a little out for balance, then down
    rarm_free = kit.keys((1.22, (40, 0, 10)), (1.5, (25, 0, 24)), (1.8, (0, 0, 4)), (CLIMB_LEN, (0, 0, 0)))
    larm_free = kit.keys((1.22, (40, 0, -10)), (1.5, (30, 0, -20)), (1.8, (0, 0, -4)), (CLIMB_LEN, (0, 0, 0)))
    fore_free = kit.keys((1.22, (30, 0, 0)), (1.5, (25, 0, 0)), (1.8, (4, 0, 0)), (CLIMB_LEN, (0, 0, 0)))
    legs_d = kit.keys((0.0, (5, 0, 1.5)), (0.22, (28, 0, 1.5)), (0.62, (40, 0, 2)), (0.92, (20, 0, 2)),
                      (1.2, (10, 0, 0)), (1.8, (0, 0, 0)), (CLIMB_LEN, (0, 0, 0)))
    shins_d = kit.keys((0.0, (-10, 0, 0)), (0.22, (-45, 0, 0)), (0.62, (-70, 0, 0)), (0.92, (-55, 0, 0)),
                       (1.2, (-40, 0, 0)), (1.8, (0, 0, 0)), (CLIMB_LEN, (0, 0, 0)))
    prev = {}

    def world(t):
        p = dict(HANG2)
        p['body@p'] = body_p(t)
        p['body'] = body_r(t)
        p['chest'] = chest(t)
        p['head'] = head(t)
        held = prev.get('arms', (HANG2['right_arm'], HANG2['right_forearm'], HANG2['left_arm'], HANG2['left_forearm']))
        f = kit.span(t, 1.22, 1.42)
        p['right_arm'] = kit.lerp(held[0], rarm_free(t), f)
        p['right_forearm'] = kit.lerp(held[1], fore_free(t), f)
        p['left_arm'] = kit.lerp(held[2], larm_free(t), f)
        p['left_forearm'] = kit.lerp(held[3], fore_free(t), f)
        lg, sh = legs_d(t), shins_d(t)
        # the leg left hanging stays under the hip however far the body leans over the edge
        lean = max(0.0, -p['body'][0] - p['chest'][0] * 0.3) * kit.span(t, 0.6, 0.9) * (1 - kit.span(t, 1.2, 1.45))
        p['right_leg'], p['left_leg'] = lg, (lg[0] + 4 + 0.9 * lean, 0, -lg[2])
        p['right_shin'], p['left_shin'] = sh, (sh[0] - 6 + 0.6 * lean, 0, 0)
        tg = [('rhand', grip(6.5), hands_w(t)), ('lhand', grip(-6.5), hands_w(t)),
              ('rtoe', (1.9, 13.0, WALL_Z - 0.05), rfoot_wall(t)),
              ('rknee', (1.9, top + 0.05, -8.2), rknee_top(t))]
        if t >= 1.18:
            tg.append(('lfoot', lfoot_path(t), lfoot_w(t)))
        if t >= 1.5:
            tg.append(('rfoot', rfoot_path(t), rfoot_w(t)))
        p = on_targets(p, tg, prev, 'climb')
        if hands_w(t) > 0.999:
            prev['arms'] = (p['right_arm'], p['right_forearm'], p['left_arm'], p['left_forearm'])
        return p

    a = L.Anim('ledge_climb_up', CLIMB_LEN, 'once')
    a.tol_rot, a.tol_pos = 0.3, 0.06
    a.root = carried(a, world, CLIMB_LEN, STAND, (0.75, 1.75))
    return a


def carried(a, world, length, end, forward):
    """Bakes a move that the mechanic carries from where it starts to `end` (world px): up as the
    head goes up (never down, so the camera rises with the head and ends exactly at the standing
    height), forward evenly over the `forward` times. The model is the world pose less that path.
    Returns the path {time: (x, y, z)}."""
    ts = kit.frames(0, length)
    poses = [world(t) for t in ts]
    eyes = [kit.at(p, 'eye')['eye'][1] for p in poses]
    up = np.maximum.accumulate(np.array(eyes) - eyes[0])
    up = up / up[-1] * end[1]
    root = {}
    for t, p, y in zip(ts, poses, up):
        r = np.array([end[0] * kit.span(t, *forward, kit.smoother), y, end[2] * kit.span(t, *forward, kit.smoother)])
        root[round(t, 4)] = r
        a.key(t, kit.shifted(p, -r))
    return root


# --------------------------------------------------------------------------------------------
# Pulled up by a friend (ledge_pulled_up) and the friend pulling (ledge_help_pull)
# --------------------------------------------------------------------------------------------

#: The friend's feet at the start: on the top, facing the drop, toes 2 px from the edge when kneeling.
HELPER_AT = np.array([0.0, LEDGE_Y, WALL_Z - 8.4])
HELPER_BACK = 12.0   # px the friend steps back while pulling (to make room)
PULL_LEN = 2.6
CLASP = 0.9          # the hands meet
LET_GO = 2.05        # and part, the one pulled up on their feet on the top


#: Which way the held arm of the one pulled up points from the shoulder (to the friend's hand).
CLASP_DIR = kit.keys((0.0, (-0.10, 0.93, -0.35)), (1.3, (-0.08, 0.88, -0.46)), (1.7, (-0.05, 0.80, -0.60)),
                     (PULL_LEN, (-0.05, 0.75, -0.66)))
CLASP_ARM = 9.0   # px from the shoulder: the held arm nearly straight (9.5 is straight)


def clasp_at(pose, t):
    """Where the two hands hold each other: along the held arm of the one pulled up."""
    d = np.array(CLASP_DIR(t))
    return kit.at(pose, 'rshoulder')['rshoulder'] + CLASP_ARM * d / np.linalg.norm(d)


def _helper_root(t):
    """The friend's path in their own frame (backwards is +z)."""
    return np.array([0.0, 0.0, HELPER_BACK * kit.span(t, 1.3, 2.4, kit.smoother)])


def assisted():
    """Both moves at once: the hanging one reaches up with the free hand, the friend kneels at the
    edge and gives a hand, they take hold of each other (CLASP), the friend's other hand takes the
    wrist, leans back and stands up stepping back; the hanging one walks up the wall, puts the
    other hand and a knee on the top and stands up (LET_GO). Returns (pulled, helper, root of the
    pulled, partner keys per time for each file)."""
    top, sz = LEDGE_Y, STAND[2]
    h1 = HANG1
    # --- the one pulled up (world = their frame at the start) ---
    body_p = kit.keys((0.0, h1['body@p']), (0.4, h1['body@p']), (CLASP, np.array(h1['body@p']) + (0, 1.6, 0)),
                      (1.3, (0.5, 8.5, -2.6)), (1.65, (0.3, 18.5, -5.4)), (1.8, (0.15, 22.0, -6.8)),
                      (1.95, (0, 25.5, -8.6)),
                      (2.3, STAND), (PULL_LEN, STAND))
    body_r = kit.keys((0.0, h1['body']), (0.4, h1['body']), (CLASP, (-6, 0, -10)), (1.3, (-12, 0, -3)),
                      (1.65, (-32, 3, 2)), (1.95, (-24, 0, 0)), (2.3, (0, 0, 0)), (PULL_LEN, (0, 0, 0)))
    chest = kit.keys((0.0, h1['chest']), (CLASP, (2, 0, 2)), (1.3, (-6, 0, 0)), (1.65, (-24, 0, 0)),
                     (1.95, (-16, 0, 0)), (2.3, (-2, 0, 0)), (PULL_LEN, (0, 0, 0)))
    head = kit.keys((0.0, h1['head']), (0.4, (30, 6, 4)), (CLASP, (32, -4, 0)), (1.3, (26, 0, 0)),
                    (1.65, (30, 0, 0)), (2.0, (16, 0, 0)), (2.3, (4, 0, 0)), (PULL_LEN, (0, 0, 0)))
    rhand_w = kit.keys((0.0, 0), (0.42, 0), (0.6, 1), (LET_GO, 1), (2.2, 0))
    rhand_p = kit.keys((0.42, (8.0, 14.0, -1.0)), (0.7, (6.5, 29.0, -4.5)), (CLASP, (6.0, 30.0, -5.0)))
    lhand_p = kit.keys((0.0, grip(0.0)), (1.12, grip(0.0)), (1.24, (-3.0, HAND_Y + 3.0, GRIP_Z - 0.6)),
                       (1.36, (-5.0, HAND_Y, GRIP_Z - 2.4)), (1.75, (-5.0, HAND_Y, GRIP_Z - 2.4)))
    lhand_w = kit.keys((0.0, 1), (1.75, 1), (1.9, 0))
    rknee_w = kit.keys((0.0, 0), (1.68, 0), (1.8, 1), (2.02, 1), (2.14, 0))
    lfoot_w = kit.keys((0.0, 0), (1.86, 0), (1.92, 1), (PULL_LEN, 1))
    rfoot_w = kit.keys((0.0, 0), (2.02, 0), (2.08, 1), (PULL_LEN, 1))
    lfoot_p = kit.keys((1.86, (-1.9, 22.0, WALL_Z + 1.5)), (1.98, (-1.9, top + 4.5, WALL_Z - 1.5)),
                       (2.08, (-1.9, top, sz + 1.2)), (2.35, (-1.9, top, sz)), (PULL_LEN, (-1.9, top, sz)))
    rfoot_p = kit.keys((2.02, (1.9, top + 1.0, WALL_Z - 1.0)), (2.14, (1.9, top + 3.5, sz + 1.0)),
                       (2.24, (1.9, top, sz)), (PULL_LEN, (1.9, top, sz)))
    free_r = kit.keys((LET_GO, (150, 0, 10)), (2.3, (20, 0, 8)), (PULL_LEN, (0, 0, 0)))
    free_l = kit.keys((1.75, (40, 0, -10)), (2.1, (25, 0, -16)), (2.4, (0, 0, -3)), (PULL_LEN, (0, 0, 0)))
    free_f = kit.keys((1.75, (25, 0, 0)), (2.3, (10, 0, 0)), (PULL_LEN, (0, 0, 0)))
    legs_d = kit.keys((0.0, (6, 0, 3)), (CLASP, (14, 0, 3)), (1.3, (40, 0, 2)), (1.65, (28, 0, 1)),
                      (2.0, (10, 0, 0)), (2.4, (0, 0, 0)), (PULL_LEN, (0, 0, 0)))
    shins_d = kit.keys((0.0, (-12, 0, 0)), (CLASP, (-25, 0, 0)), (1.3, (-70, 0, 0)), (1.65, (-50, 0, 0)),
                       (2.0, (-35, 0, 0)), (2.4, (0, 0, 0)), (PULL_LEN, (0, 0, 0)))
    rtoe_w = kit.keys((0.0, 0), (1.12, 0), (1.25, 1), (1.55, 1), (1.62, 0))

    prev = {}

    def pulled(t):
        p = dict(h1)
        p['body@p'], p['body'], p['chest'], p['head'] = body_p(t), body_r(t), chest(t), head(t)
        p['right_arm'] = kit.lerp(h1['right_arm'], free_r(t), kit.span(t, LET_GO, 2.2)) if t > LET_GO else p['right_arm']
        if t > 1.75:
            f = kit.span(t, 1.75, 1.95)
            held = prev.get('larm', (h1['left_arm'], h1['left_forearm']))
            p['left_arm'] = kit.lerp(held[0], free_l(t), f)
            p['left_forearm'] = kit.lerp(held[1], free_f(t), f)
        if t > LET_GO:
            held = prev.get('rarm', (h1['right_arm'], h1['right_forearm']))
            f = kit.span(t, LET_GO, 2.22)
            p['right_arm'] = kit.lerp(held[0], free_r(t), f)
            p['right_forearm'] = kit.lerp(held[1], free_f(t), f)
        lg, sh = legs_d(t), shins_d(t)
        lean = max(0.0, -p['body'][0] - 0.3 * p['chest'][0]) * kit.span(t, 1.4, 1.7) * (1 - kit.span(t, 1.85, 2.05))
        p['right_leg'], p['left_leg'] = lg, (lg[0] + 4 + 0.9 * lean, 0, -lg[2])
        p['right_shin'], p['left_shin'] = sh, (sh[0] - 6 + 0.6 * lean, 0, 0)
        c = clasp_at(p, t)
        prev.setdefault('C', {})[round(t, 4)] = c
        rh = c if t >= CLASP else np.array(kit.lerp(rhand_p(t), c, kit.span(t, 0.62, CLASP)))
        tg = [('lhand', lhand_p(t), lhand_w(t)), ('rhand', rh, rhand_w(t)),
              ('rtoe', (1.9, 16.0, WALL_Z - 0.05), rtoe_w(t)),
              ('rknee', (1.9, top + 0.05, -8.4), rknee_w(t))]
        if t >= 1.86:
            tg.append(('lfoot', lfoot_p(t), lfoot_w(t)))
        if t >= 2.02:
            tg.append(('rfoot', rfoot_p(t), rfoot_w(t)))
        p = on_targets(p, tg, prev, 'pulled')
        if lhand_w(t) > 0.999:
            prev['larm'] = (p['left_arm'], p['left_forearm'])
        if rhand_w(t) > 0.999:
            prev['rarm'] = (p['right_arm'], p['right_forearm'])
        return p

    # --- the friend (their own frame: feet at the origin at the start, facing the drop ahead) ---
    own = lambda w: kit.partner_world_to_own(w, HELPER_AT)  # noqa: E731
    hb_p = kit.keys((0.0, (0, 0, 0)), (0.2, (0, -0.5, -0.5)), (0.55, (0, -5.2, -1.2)), (CLASP, (0, -6.0, -1.6)),
                    (1.3, (0, -3.2, 1.5)), (1.7, (0, -1.8, 3.0)), (2.1, (0, -1.2, 0)), (2.45, (0, 0, 0)), (PULL_LEN, (0, 0, 0)))
    hb_r = kit.keys((0.0, (0, 0, 0)), (0.55, (-22, 0, 0)), (CLASP, (-30, 4, 0)), (1.3, (-8, 4, 0)), (1.7, (6, 2, 0)),
                    (2.1, (2, 0, 0)), (2.45, (0, 0, 0)), (PULL_LEN, (0, 0, 0)))
    h_chest = kit.keys((0.0, (0, 0, 0)), (0.55, (-44, 0, 0)), (CLASP, (-56, 0, 0)), (1.3, (-22, 0, 0)), (1.7, (-6, 0, 0)),
                       (2.1, (-4, 0, 0)), (2.45, (0, 0, 0)), (PULL_LEN, (0, 0, 0)))
    h_head = kit.keys((0.0, (0, 0, 0)), (0.55, (-10, 0, 0)), (CLASP, (-4, 0, 0)), (1.3, (-12, 0, 0)), (1.7, (-14, 0, 0)),
                      (2.1, (-8, 0, 0)), (2.45, (0, 0, 0)), (PULL_LEN, (0, 0, 0)))
    # feet and knee in the friend's frame at the start (they step back as their path goes back)
    h_lknee_w = kit.keys((0.0, 0), (0.3, 0), (0.55, 1), (1.12, 1), (1.22, 0))
    h_lfoot = kit.keys((0.0, (-1.9, 0, 0)), (1.12, (-1.9, 0, 0)), (1.22, (-1.9, 2.5, 2.0)), (1.34, (-1.9, 0, 3.5)),
                       (1.95, (-1.9, 0, 3.5)), (2.08, (-1.9, 3.0, 8.0)), (2.2, (-1.9, 0, HELPER_BACK)), (PULL_LEN, (-1.9, 0, HELPER_BACK)))
    h_rfoot = kit.keys((0.0, (1.9, 0, 0)), (0.18, (1.9, 2.5, -3.5)), (0.36, (1.9, 0, -6.0)), (1.5, (1.9, 0, -6.0)),
                       (1.65, (1.9, 3.0, 0.5)), (1.8, (1.9, 0, 7.0)), (2.25, (1.9, 0, 7.0)), (2.36, (1.9, 2.5, 10.0)),
                       (2.46, (1.9, 0, HELPER_BACK)), (PULL_LEN, (1.9, 0, HELPER_BACK)))
    h_rhand_w = kit.keys((0.0, 0), (0.95, 0), (1.08, 1), (LET_GO - 0.05, 1), (2.15, 0))
    h_lhand_w = kit.keys((0.0, 0), (0.35, 0), (0.6, 1), (LET_GO, 1), (2.2, 0))
    h_lreach = kit.keys((0.35, (-6.5, 9.0, -5.0)), (0.65, (-5.0, 5.0, -6.5)), (CLASP, (-4.5, 2.5, -6.0)))
    h_free = kit.keys((LET_GO, (60, 0, -6)), (2.4, (8, 0, -3)), (PULL_LEN, (0, 0, 0)))
    h_free_r = kit.keys((LET_GO, (55, 0, 6)), (2.4, (8, 0, 3)), (PULL_LEN, (0, 0, 0)))
    h_fore = kit.keys((LET_GO, (30, 0, 0)), (2.4, (6, 0, 0)), (PULL_LEN, (0, 0, 0)))

    def helper(t, pw):
        """The friend's pose in their own frame including their path; pw: the pulled one's world pose."""
        p = {k: (0, 0, 0) for k in L.ROT_BONES if k != 'right_item_bow'}
        p['body@p'] = tuple(np.array(hb_p(t)) + _helper_root(t))
        p['body'], p['chest'], p['head'] = hb_r(t), h_chest(t), h_head(t)
        p['right_leg'], p['right_shin'] = (60, 0, 0), (-70, 0, 0)
        p['left_leg'], p['left_shin'] = (-10, 0, 0), (-80, 0, 0)
        p['left_arm'], p['left_forearm'] = (60, 0, -8), (20, 0, 0)
        p['right_arm'], p['right_forearm'] = (20, 0, 8), (30, 0, 0)
        if t > LET_GO:
            f = kit.span(t, LET_GO, 2.25)
            held = prev.get('h_arms')
            if held:
                p['left_arm'] = kit.lerp(held[0], h_free(t), f)
                p['left_forearm'] = kit.lerp(held[1], h_fore(t), f)
                p['right_arm'] = kit.lerp(held[2], h_free_r(t), f)
                p['right_forearm'] = kit.lerp(held[3], h_fore(t), f)
        wrist = kit.pt(kit.fk(pw), 'relbow') * 0.35 + kit.pt(kit.fk(pw), 'rhand') * 0.65
        c = own(prev['C'][round(t, 4)])
        lh = c if t >= CLASP else np.array(kit.lerp(h_lreach(t), c, kit.span(t, 0.62, CLASP)))
        tg = [('lhand', lh, h_lhand_w(t)), ('rhand', own(wrist) + (0.0, 0.0, 0.0), h_rhand_w(t)),
              ('lknee', (-1.9, 0.6, 1.5), h_lknee_w(t)), ('lfoot', h_lfoot(t), 1 - h_lknee_w(t)),
              ('rfoot', h_rfoot(t), 1.0)]
        p = on_targets(p, tg, prev, 'helper', body=((('body@p', 1), 0.15), (('body@p', 2), 0.15),
                                                    (('body', 0), 0.03), (('chest', 0), 0.03)))
        if h_lhand_w(t) > 0.999 and h_rhand_w(t) > 0.999:
            prev['h_arms'] = (p['left_arm'], p['left_forearm'], p['right_arm'], p['right_forearm'])
        return p

    ts = kit.frames(0, PULL_LEN)
    pw_all, hw_all = [], []
    for t in ts:
        pw = pulled(t)
        hw = helper(t, pw)
        pw_all.append(pw)
        hw_all.append(hw)
    # the pulled one is carried like the climb up: up with the head, forward over 1.5-2.35 s
    eyes = [kit.at(p, 'eye')['eye'][1] for p in pw_all]
    up = np.maximum.accumulate(np.array(eyes) - eyes[0])
    up = up / up[-1] * STAND[1]
    a = L.Anim('ledge_pulled_up', PULL_LEN, 'once')
    b = L.Anim('ledge_help_pull', PULL_LEN, 'once')
    for x in (a, b):
        x.tol_rot, x.tol_pos = 0.3, 0.06
    root, hroot = {}, {}
    for t, pw, hw, y in zip(ts, pw_all, hw_all, up):
        r = np.array([0.0, y, STAND[2] * kit.span(t, 1.5, 2.35, kit.smoother)])
        hr = _helper_root(t)
        root[round(t, 4)], hroot[round(t, 4)] = r, hr
        # each file shows the other one as the grey partner
        pa = kit.as_partner(hw, HELPER_AT - r)
        p_in_h = kit.partner_world_to_own(np.zeros(3), HELPER_AT)  # the pulled one's start, in the friend's frame
        pb = kit.as_partner(pw, p_in_h - hr)
        for pp, k in ((pa, 'pa'), (pb, 'pb')):   # the half turn makes its angles flip: keep them continuous
            pp['body2'] = tuple(kit.continuous(prev.get(k), pp['body2']))
            prev[k] = pp['body2']
        a.key(t, {**kit.shifted(pw, -r), **pa})
        b.key(t, {**kit.shifted(hw, -hr), **pb})
    a.root, b.root = root, hroot
    return a, b

