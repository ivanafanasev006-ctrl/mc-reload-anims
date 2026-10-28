"""Authoring helper: poses at times with easing -> Blockbench 5 catmullrom keyframes.

Each track (bone, channel) gets target values at times; between two targets the value follows the
easing of the arriving target and is sampled on the 1/24 s grid (every 1/12 s for slow moves), so
Blockbench's catmullrom reproduces the eased curve without overshoot. Holds keep only their ends.
"""
import math
import os
import sys
import uuid as uuidlib

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import numpy as np  # noqa: E402
import kinetrix_rig as kr  # noqa: E402

FPS = 24
ROT_BONES = ['body', 'torso', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm',
             'right_leg', 'right_shin', 'left_leg', 'left_shin', 'right_item_bow']
POS_BONES = ['body', 'right_arm', 'left_arm']


def q(t):
    return round(t * FPS) / FPS


EASE = {
    'linear': lambda f: f,
    'inout': lambda f: 0.5 - 0.5 * math.cos(math.pi * f),
    'in': lambda f: 1 - math.cos(math.pi * f / 2),
    'out': lambda f: math.sin(math.pi * f / 2),
    'in2': lambda f: f * f,
    'out2': lambda f: 1 - (1 - f) ** 2,
    'in3': lambda f: f ** 3,
    'out3': lambda f: 1 - (1 - f) ** 3,
}


def parse(pose):
    """{'bone': (x,y,z) rotation, 'bone@p': (x,y,z) position} -> {(bone, channel): vec}."""
    out = {}
    for k, v in pose.items():
        if k.endswith('@p'):
            out[(k[:-2], 'position')] = np.array(v, float)
        else:
            out[(k, 'rotation')] = np.array(v, float)
    return out


def full(pose):
    """A whole-body pose: every bone not given goes to rest."""
    p = {b: (0, 0, 0) for b in ROT_BONES}
    p.update({b + '@p': (0, 0, 0) for b in POS_BONES})
    p.update(pose)
    return p


def mix(a, b, f):
    """Blend two poses (dicts of the same shorthand)."""
    keys = set(a) | set(b)
    out = {}
    for k in keys:
        va = np.array(a.get(k, (0, 0, 0)), float)
        vb = np.array(b.get(k, (0, 0, 0)), float)
        out[k] = tuple(va + (vb - va) * f)
    return out


def over(base, **kw):
    """Copy of a pose with some bones replaced; use bone__p for positions."""
    p = dict(base)
    for k, v in kw.items():
        p[k.replace('__p', '@p')] = v
    return p


def add(base, delta):
    """Pose plus offsets."""
    p = dict(base)
    for k, v in delta.items():
        p[k] = tuple(np.array(p.get(k, (0, 0, 0)), float) + np.array(v, float))
    return p


class Anim:
    def __init__(self, name, length, loop='once'):
        self.name, self.length, self.loop = name, q(length), loop
        self.tracks = {}
        self.linear_static = {}  # extra constant keys (body2 dummy), linear

    def key(self, t, pose, ease='auto', whole=False):
        t = q(t)
        if whole:
            pose = full(pose)
        for (bone, ch), v in parse(pose).items():
            lst = self.tracks.setdefault((bone, ch), [])
            lst[:] = [k for k in lst if abs(k[0] - t) > 1e-6]
            lst.append((t, v, ease))
            lst.sort(key=lambda k: k[0])
        return self

    def hold(self, t0, t1, pose, ease='auto', whole=True):
        self.key(t0, pose, ease, whole)
        self.key(t1, pose, 'auto', whole)
        return self

    def dummy(self, pos, rot):
        self.linear_static[('body2', 'position')] = pos
        self.linear_static[('body2', 'rotation')] = rot
        return self

    # --- sampling ------------------------------------------------------------------------------
    def curve(self, tk):
        """The designed motion of one track: a monotone cubic (no overshoot) through the targets,
        shaped by the eases ('auto' leaves it to the curve, 'hit' arrives at speed)."""
        return Curve(self.tracks[tk])

    def samples(self):
        """{(bone, channel): [(t, vec)]} catmullrom keyframes reproducing the designed curves."""
        if getattr(self, '_samples', None) is not None and self._samples[0] == self._version():
            return self._samples[1]
        out = {}
        for tk, lst in self.tracks.items():
            if all(np.allclose(v, 0, atol=1e-4) for _, v, _ in lst):
                if tk[0] == 'right_item_bow':  # it tells what is held
                    out[tk] = [(lst[0][0], lst[0][1])]
                continue
            if all(np.allclose(v, lst[0][1], atol=1e-4) for _, v, _ in lst):
                out[tk] = [(lst[0][0], lst[0][1])]
                continue
            tol = 0.12 if tk[1] == 'position' else 0.6
            out[tk] = simplify(self.curve(tk), [t for t, _, _ in lst], tol)
        self._samples = (self._version(), out)
        return out

    def _version(self):
        return tuple((tk, tuple((t, tuple(np.round(v, 5)), e) for t, v, e in lst)) for tk, lst in sorted(self.tracks.items()))

    def to_json(self, bone_uuids, uuid=None):
        animators = {}
        for (bone, ch), keys in sorted(self.samples().items()):
            a = animators.setdefault(bone_uuids[bone], {"name": bone, "type": "bone", "rotation_global": False,
                                                         "quaternion_interpolation": False, "keyframes": []})
            for t, v in keys:
                a["keyframes"].append(kf(ch, t, v, "catmullrom"))
        for (bone, ch), v in self.linear_static.items():
            a = animators.setdefault(bone_uuids[bone], {"name": bone, "type": "bone", "rotation_global": False,
                                                         "quaternion_interpolation": False, "keyframes": []})
            a["keyframes"].append(kf(ch, 0, np.array(v, float), "linear"))
        return {"uuid": uuid or str(uuidlib.uuid4()), "name": self.name, "loop": self.loop, "override": False,
                "length": self.length, "snapping": FPS, "selected": False, "group_name": "", "scope": 0,
                "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                "animators": animators}

    def count(self):
        return sum(len(v) for v in self.samples().values())


class Curve:
    """Monotone piecewise cubic (Fritsch-Carlson) through the targets, per component. Targets
    with an easing other than 'auto' get extra points inside the segment that shape it; a 'hit'
    target is reached at speed and left from rest (a blunt stop)."""

    def __init__(self, lst):
        T, Y, hit = [], [], []
        for i, (t, v, e) in enumerate(lst):
            if i > 0 and e not in ('auto', 'hit'):
                t0, v0 = lst[i - 1][0], lst[i - 1][1]
                for f in (0.25, 0.5, 0.75):
                    T.append(t0 + (t - t0) * f)
                    Y.append(v0 + (v - v0) * EASE[e](f))
                    hit.append(False)
            T.append(t)
            Y.append(np.asarray(v, float))
            hit.append(e == 'hit')
        self.T = np.array(T)
        self.Y = np.array(Y)
        n = len(T)
        h = np.diff(self.T)
        d = np.diff(self.Y, axis=0) / h[:, None]
        m = np.zeros_like(self.Y)
        for k in range(1, n - 1):
            for c in range(3):
                a, b = d[k - 1, c], d[k, c]
                if a * b <= 0:
                    m[k, c] = 0.0
                else:
                    w1, w2 = 2 * h[k] + h[k - 1], h[k] + 2 * h[k - 1]
                    m[k, c] = (w1 + w2) / (w1 / a + w2 / b)
        self.m_in = m.copy()
        self.m_out = m.copy()
        for k in range(1, n):
            if hit[k]:
                self.m_in[k] = 1.3 * d[k - 1]
                self.m_out[k] = 0.0

    def __call__(self, t):
        t = np.atleast_1d(np.asarray(t, float))
        T, Y = self.T, self.Y
        out = np.empty((len(t), 3))
        idx = np.clip(np.searchsorted(T, t, side='right') - 1, 0, len(T) - 2) if len(T) > 1 else np.zeros(len(t), int)
        for j, (tt, k) in enumerate(zip(t, idx)):
            if len(T) == 1 or tt <= T[0]:
                out[j] = Y[0]
                continue
            if tt >= T[-1]:
                out[j] = Y[-1]
                continue
            h = T[k + 1] - T[k]
            s = (tt - T[k]) / h
            s2, s3 = s * s, s * s * s
            out[j] = ((2 * s3 - 3 * s2 + 1) * Y[k] + (s3 - 2 * s2 + s) * h * self.m_out[k]
                      + (-2 * s3 + 3 * s2) * Y[k + 1] + (s3 - s2) * h * self.m_in[k + 1])
        return out


SUB = 4  # checking resolution: samples per 1/24 s frame


def cm_eval(K, V, ts):
    """Blockbench catmullrom through keys at times K (values V), at times ts (all inside [K0, Kn])."""
    out = np.empty((len(ts), 3))
    j = np.clip(np.searchsorted(K, ts, side='right') - 1, 0, len(K) - 2)
    for i, (t, k) in enumerate(zip(ts, j)):
        t0, t1 = K[k], K[k + 1]
        a = (t - t0) / (t1 - t0)
        v0, v1 = V[k], V[k + 1]
        vp = V[k - 1] if k > 0 else v0
        vn = V[k + 2] if k + 2 < len(K) else v1
        a2, a3 = a * a, a * a * a
        out[i] = 0.5 * ((2 * v0) + (-vp + v1) * a + (2 * vp - 5 * v0 + 4 * v1 - vn) * a2 + (-vp + 3 * v0 - 3 * v1 + vn) * a3)
    return out


def simplify(curve, target_times, tol):
    """Keys on the 1/24 s grid, as few as keep Blockbench's catmullrom within tol of the curve
    (and its speed within a margin). Target times are always keyed."""
    f0 = int(round(min(target_times) * FPS))
    f1 = int(round(max(target_times) * FPS))
    frames = list(range(f0, f1 + 1))
    vals = curve(np.array(frames) / FPS)
    fine_t = np.arange(f0 * SUB, f1 * SUB + 1) / (FPS * SUB)
    fine_v = curve(fine_t)
    fine_vel = np.gradient(fine_v, axis=0) * FPS * SUB
    vtol_abs = 6.0 if tol < 0.5 else 30.0
    protected = {f0, f1}
    keys = list(range(len(frames)))  # indices into frames

    def err_without(pos):
        """Error over the segments touched when keys[pos] is removed."""
        ks = keys[:pos] + keys[pos + 1:]
        lo = max(0, pos - 2)
        hi = min(len(ks) - 1, pos + 1)
        K = np.array([frames[k] for k in ks], float) / FPS
        V = vals[ks]
        a, b = ks[lo], ks[hi]
        i0, i1 = (frames[a] - f0) * SUB, (frames[b] - f0) * SUB
        ts = fine_t[i0:i1 + 1]
        got = cm_eval(K, V, ts)
        want = fine_v[i0:i1 + 1]
        e_pos = np.abs(got - want).max() / tol
        vel = np.gradient(got, axis=0) * FPS * SUB
        wv = fine_vel[i0:i1 + 1]
        vt = np.maximum(vtol_abs, 0.12 * np.abs(wv))
        e_vel = (np.abs(vel - wv) / vt).max()
        return max(e_pos, e_vel)

    cost = {}

    def refresh(pos_range):
        for p in pos_range:
            if 0 < p < len(keys) - 1 and frames[keys[p]] not in protected:
                cost[keys[p]] = err_without(p)
            else:
                cost.pop(keys[p], None) if 0 <= p < len(keys) else None

    refresh(range(len(keys)))
    while cost:
        k = min(cost, key=cost.get)
        if cost[k] > 1.0:
            break
        p = keys.index(k)
        del keys[p]
        cost.pop(k)
        refresh(range(max(0, p - 3), min(len(keys), p + 3)))
    return [(frames[k] / FPS, vals[k]) for k in keys]


def num(x):
    x = round(float(x), 4)
    if abs(x) < 1e-9:
        x = 0.0
    return int(x) if x == int(x) else x


def kf(ch, t, v, interp):
    return {"channel": ch, "data_points": [{"x": num(v[0]), "y": num(v[1]), "z": num(v[2])}],
            "uuid": str(uuidlib.uuid4()), "time": round(t, 6), "color": -1, "interpolation": interp,
            "easing": "linear", "easingArgs": []}


# --- model file -------------------------------------------------------------------------------

def bone_uuids(model_json):
    return {g['name']: g['uuid'] for g in model_json['groups']}


def put(model_json, anim_json):
    """Replace (keeping uuid and place) or append an animation by name."""
    anims = model_json['animations']
    for i, a in enumerate(anims):
        if a['name'] == anim_json['name'] or a['name'] == 'animation.' + anim_json['name']:
            anim_json['uuid'] = a['uuid']
            anims[i] = anim_json
            return
    anims.append(anim_json)


# --- probes -------------------------------------------------------------------------------------

def world(model, anim, t):
    chans = {}
    for (bone, ch), keys in anim.samples().items():
        chans.setdefault(bone, {})[ch] = [(k[0], k[1], 'catmullrom') for k in keys]
    return kr.bb_pose(model, chans, t)


def point(W, bone, rest_point, model):
    o = model.groups[model.by_name[bone]]['origin']
    return kr.apply(W[bone], np.asarray(rest_point, float) - o)
