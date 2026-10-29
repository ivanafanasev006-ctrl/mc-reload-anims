"""Shared tools of the 2026-10-29 set: forward kinematics of a pose, a bounded solver that puts hands
and feet where they must be, the partner figure (bones *2), the floor and the ledge.

Poses use the shorthand of ../refine_author_2026_10_28/animlib.py: {'bone': (x, y, z) rotation,
'bone@p': (x, y, z) position}, Blockbench 5 degrees and pixels.
"""
import os
import sys

import numpy as np
from scipy.optimize import least_squares

HERE = os.path.dirname(os.path.abspath(__file__))
BB = os.path.dirname(HERE)
sys.path.insert(0, BB)
sys.path.insert(0, os.path.join(BB, 'refine_author_2026_10_28'))
import kinetrix_rig as kr  # noqa: E402
import animlib as L  # noqa: E402

#: The author's file of 2026-10-29: the rig and the grey partner beside it (bones *2, 20 px to the right).
SRC = os.path.join(BB, 'animations', 'author_2026-10-29.bbmodel')
RIG = kr.Model(SRC)

#: The partner's rest place: the same figure 20 px to the right (its origin in the author's file).
PARTNER_X = 20.0

#: Points on the figure at rest (bone, rest point). A '2' after a name: the same point of the partner.
POINTS = {
    'lhand': ('left_forearm', (-6, 12.5, 0)),
    'rhand': ('right_forearm', (6, 12.5, 0)),
    'lfist': ('left_forearm', (-6, 12, 0)),      # the end of the arm (the fist's flat end)
    'rfist': ('right_forearm', (6, 12, 0)),
    'lelbow': ('left_forearm', (-6, 18, 0)),
    'relbow': ('right_forearm', (6, 18, 0)),
    'lshoulder': ('left_arm', (-6, 22, 0)),
    'rshoulder': ('right_arm', (6, 22, 0)),
    'lknee': ('left_shin', (-1.9, 6, -2)),
    'rknee': ('right_shin', (1.9, 6, -2)),
    'lfoot': ('left_shin', (-1.9, 0, 0)),
    'rfoot': ('right_shin', (1.9, 0, 0)),
    'ltoe': ('left_shin', (-1.9, 0, -2)),
    'rtoe': ('right_shin', (1.9, 0, -2)),
    'lheel': ('left_shin', (-1.9, 0, 2)),
    'rheel': ('right_shin', (1.9, 0, 2)),
    'hip': ('body', (0, 12, 0)),
    'neck': ('chest', (0, 24, 0)),
    'chestfront': ('chest', (0, 21, -2)),
    'belly': ('torso', (0, 15, -2)),
    'back': ('chest', (0, 20, 2)),
    'head': ('head', (0, 28, 0)),
    'headtop': ('head', (0, 32, 0)),
    'eye': ('head', (0, 27.6, -4)),
}

PLAYER_BONES = [b[0] for b in kr.BONES]
PARTNER_BONES = [b + '2' for b in PLAYER_BONES]


def point_def(name):
    if name.endswith('2') and name[:-1] in POINTS:
        bone, p = POINTS[name[:-1]]
        return bone + '2', (p[0] + PARTNER_X, p[1], p[2])
    return POINTS[name]


def chans_of(pose):
    """A pose (shorthand) as constant channels for kinetrix_rig.bb_pose."""
    chans = {}
    for (bone, ch), v in L.parse(pose).items():
        chans.setdefault(bone, {})[ch] = [(0.0, np.array(v, float), 'linear')]
    return chans


def fk(pose):
    return kr.bb_pose(RIG, chans_of(pose), 0.0)


def pt(W, name):
    bone, rp = point_def(name)
    o = RIG.groups[RIG.by_name[bone]]['origin']
    return kr.apply(W[bone], np.asarray(rp, float) - o)


def at(pose, *names):
    W = fk(pose)
    return {n: pt(W, n) for n in names}


def _owner():
    return {c: g['name'] for g in RIG.groups.values() for c in g['children']}


_OWN = _owner()


def corners(W, bones):
    """Every corner of the cubes of these bones in the pose W."""
    out = []
    for e in RIG.data['elements']:
        bone = _OWN.get(e['uuid'])
        if bone not in bones or bone not in W or e.get('inflate', 0):
            continue
        o = RIG.groups[RIG.by_name[bone]]['origin']
        lo, hi = np.array(e['from'], float), np.array(e['to'], float)
        for c in ((a, b, d) for a in (0, 1) for b in (0, 1) for d in (0, 1)):
            out.append(kr.apply(W[bone], lo + (hi - lo) * np.array(c) - o))
    return np.array(out)


def lowest(pose, bones=None, W=None):
    """Lowest point of the player (or of these bones) in pixels; the floor is 0."""
    W = W if W is not None else fk(pose)
    return float(corners(W, set(bones or PLAYER_BONES))[:, 1].min())


def grounded(pose, clearance=0.0, bones=None):
    """The same pose moved up or down so its lowest point is on the floor."""
    p = dict(pose)
    bp = p.get('body@p', (0, 0, 0))
    d = lowest(p, bones) - clearance
    p['body@p'] = (bp[0], bp[1] - d, bp[2])
    return p


def solve(pose, targets, vary, bounds=None, reg=0.01, prefer=None, start=None):
    """Changes the varied values of a pose so the named points reach their targets.

    targets: [(point, world xyz, weight)] or [(point, axis 0..2, value, weight)].
    vary: [(key, axis)] where key is a bone ('rotation') or 'bone@p' (position).
    bounds: {(key, axis): (lo, hi)}. reg pulls the values towards `prefer` (default: the pose's own):
    one weight, or one a value (px of error a degree or a pixel away is worth).
    start: the values to start from (the previous frame's answer), if any.
    Returns (pose, rms error in px).
    """
    pose = dict(pose)
    base = np.array([float(pose.get(k, (0, 0, 0))[ax]) for k, ax in vary])
    pref = base if prefer is None else np.array(prefer, float)
    lo = np.array([(bounds or {}).get((k, ax), (-np.inf, np.inf))[0] for k, ax in vary])
    hi = np.array([(bounds or {}).get((k, ax), (-np.inf, np.inf))[1] for k, ax in vary])
    x0 = np.clip(base if start is None else np.array(start, float), lo + 1e-6, hi - 1e-6)

    def with_x(x):
        p = dict(pose)
        for (k, ax), v in zip(vary, x):
            vec = list(p.get(k, (0, 0, 0)))
            vec[ax] = float(v)
            p[k] = tuple(vec)
        return p

    def resid(x):
        W = fk(with_x(x))
        r = []
        for tg in targets:
            if len(tg) == 3:
                n, w, wt = tg
                r.extend((pt(W, n) - np.asarray(w, float)) * wt)
            else:
                n, ax, val, wt = tg
                r.append((pt(W, n)[ax] - val) * wt)
        return np.concatenate([np.array(r), (x - pref) * np.asarray(reg, float)])

    res = least_squares(resid, x0, bounds=(lo, hi), diff_step=1e-4, xtol=1e-7, ftol=1e-9, max_nfev=400)
    out = with_x(res.x)
    n = sum(3 if len(tg) == 3 else 1 for tg in targets)
    err = float(np.sqrt(np.mean(resid(res.x)[:n] ** 2))) if n else 0.0
    return out, err, res.x


# --- limbs -------------------------------------------------------------------------------------

def arm_vary(side, partner=False):
    s = '2' if partner else ''
    return [(f'{side}_arm{s}', 0), (f'{side}_arm{s}', 1), (f'{side}_arm{s}', 2), (f'{side}_forearm{s}', 0)]


def arm_bounds(side, partner=False, bend=(0.0, 135.0)):
    s = '2' if partner else ''
    return {(f'{side}_forearm{s}', 0): bend}


def leg_vary(side, partner=False):
    s = '2' if partner else ''
    return [(f'{side}_leg{s}', 0), (f'{side}_leg{s}', 1), (f'{side}_leg{s}', 2), (f'{side}_shin{s}', 0)]


def leg_bounds(side, partner=False, bend=(-140.0, 0.0)):
    s = '2' if partner else ''
    return {(f'{side}_shin{s}', 0): bend}


# --- turning a whole pose ----------------------------------------------------------------------

def euler_of(R):
    return np.degrees(np.array(kr.euler_zyx(R)))


def rot_of(v):
    return kr.rot_zyx(*np.radians(v))


def turned_about(pose, R, pivot_point, partner=False):
    """The whole figure turned by matrix R (world) about where one of its points is now."""
    s = '2' if partner else ''
    p = dict(pose)
    W = fk(p)
    piv = pt(W, pivot_point + s if not pivot_point.endswith('2') else pivot_point)
    Rb = W['body' + s][:3, :3]
    p['body' + s] = tuple(euler_of(R @ Rb))
    W2 = fk(p)
    piv2 = pt(W2, pivot_point + s if not pivot_point.endswith('2') else pivot_point)
    bp = np.array(p.get(f'body{s}@p', (0, 0, 0)), float) + (piv - piv2)
    p[f'body{s}@p'] = tuple(bp)
    return p


def shifted(pose, d, partner=False):
    s = '2' if partner else ''
    p = dict(pose)
    p[f'body{s}@p'] = tuple(np.array(p.get(f'body{s}@p', (0, 0, 0)), float) + np.asarray(d, float))
    return p


def continuous(prev, v):
    """Euler angles v made continuous with prev (no 360 degree jumps between frames)."""
    v = np.array(v, float)
    if prev is None:
        return v
    return np.degrees(np.array(kr.continue_angles(tuple(np.radians(prev)), tuple(np.radians(v)))))


# --- the partner -------------------------------------------------------------------------------

def as_partner(pose, place, yaw=180.0):
    """A pose made in a figure's own frame, put on the partner standing at `place` (world px, its
    feet) and turned by `yaw` (180: facing the player). Returns partner keys (bones *2)."""
    out = {}
    Ry = rot_of((0, yaw, 0))
    for k, v in pose.items():
        bone = k[:-2] if k.endswith('@p') else k
        if bone not in PLAYER_BONES:
            continue
        if bone != 'body':
            out[bone + '2' + ('@p' if k.endswith('@p') else '')] = tuple(v)
    body_r = rot_of(pose.get('body', (0, 0, 0)))
    out['body2'] = tuple(euler_of(Ry @ body_r))
    o = RIG.groups[RIG.by_name['body']]['origin']
    o2 = RIG.groups[RIG.by_name['body2']]['origin']
    dp = np.array(pose.get('body@p', (0, 0, 0)), float)
    out['body2@p'] = tuple(Ry @ (o + dp) + np.asarray(place, float) - o2)
    return out


def partner_world_to_own(p_world, place, yaw=180.0):
    """A world point in the partner's own frame (where it would be for the figure at the origin)."""
    Ry = rot_of((0, yaw, 0))
    return Ry.T @ (np.asarray(p_world, float) - np.asarray(place, float))


# --- easing ------------------------------------------------------------------------------------

def smooth(f):
    f = min(1.0, max(0.0, f))
    return f * f * (3 - 2 * f)


def smoother(f):
    f = min(1.0, max(0.0, f))
    return f * f * f * (f * (6 * f - 15) + 10)


def span(t, t0, t1, ease=smooth):
    return ease((t - t0) / (t1 - t0)) if t1 > t0 else float(t >= t1)


def lerp(a, b, f):
    return tuple(np.asarray(a, float) + (np.asarray(b, float) - np.asarray(a, float)) * f)


def frames(t0, t1, fps=24):
    n = int(round((t1 - t0) * fps))
    return [round(t0 + i / fps, 6) for i in range(n + 1)]


def bake(anim, times, pose_at, keys=None):
    """Keys an animlib.Anim with the pose of every time (whole poses)."""
    for t in times:
        p = pose_at(t)
        if keys:
            p = {k: v for k, v in p.items() if (k[:-2] if k.endswith('@p') else k) in keys}
        anim.key(t, p)
    return anim


def mirror_pose(pose):
    """The same pose to the other side: left and right swap, turns about y and z and sideways moves flip."""
    out = {}
    for k, v in pose.items():
        pos = k.endswith('@p')
        bone = k[:-2] if pos else k
        if bone.startswith('right_'):
            bone = 'left_' + bone[6:]
        elif bone.startswith('left_'):
            bone = 'right_' + bone[5:]
        x, y, z = v
        out[bone + ('@p' if pos else '')] = (-x, y, z) if pos else (x, -y, -z)
    return out


def turned_about_point(pose, R, pivot, partner=False):
    """The whole figure turned by matrix R (world) about a fixed world point."""
    s = '2' if partner else ''
    p = dict(pose)
    W = fk(p)
    Wb = W['body' + s]
    p['body' + s] = tuple(euler_of(R @ Wb[:3, :3]))
    # where the body's pivot goes when the figure turns about `pivot`
    want = np.asarray(pivot, float) + R @ (Wb[:3, 3] - np.asarray(pivot, float))
    W2 = fk(p)
    bp = np.array(p.get(f'body{s}@p', (0, 0, 0)), float) + (want - W2['body' + s][:3, 3])
    p[f'body{s}@p'] = tuple(bp)
    return p


class Keys:
    """Designed values of one channel at times, eased between (animlib's monotone curve)."""

    def __init__(self, *pts):
        self.lst = [(t, np.array(v, float), e) for t, v, *rest in pts for e in [rest[0] if rest else 'auto']]
        self.curve = L.Curve(self.lst)

    def __call__(self, t):
        return self.curve(t)[0]


def keys(*pts):
    """keys((t, value[, ease]), ...) -> f(t) -> value; values are numbers or 3-vectors."""
    vec = [(t, (v, 0, 0) if np.isscalar(v) else v, *r) for t, v, *r in pts]
    k = Keys(*vec)
    if all(np.isscalar(p[1]) for p in pts):
        return lambda t: float(k(t)[0])
    return lambda t: tuple(k(t))


def design(a, t):
    """The pose a design animlib.Anim (key poses with eases) has at time t, every track it has."""
    out = {}
    for (bone, ch), lst in a.tracks.items():
        v = a.curve((bone, ch))(t)[0]
        out[bone + ('@p' if ch == 'position' else '')] = tuple(v)
    return out
