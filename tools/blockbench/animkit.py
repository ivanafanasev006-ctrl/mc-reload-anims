"""Editing animations of a Kinetrix rig .bbmodel in code: what tools/blockbench/refined is made with.

    a = Anim.load(model_json, "dodge_left")          # keys of the player's bones, Blockbench 5 values
    a.key("chest", "rotation", 0.2, (-10, 0, 4))      # add or replace a key
    a.ground(model_json, 0.0, 0.5)                    # body height so the lowest point touches the floor
    b = a.mirrored("dodge_right")                     # left <-> right
    a.store(model_json)                               # write back (replaces the animation of that name)

Values are Blockbench 5 values (what its key fields show): rotation in degrees, position in pixels.
"""
import copy
import json
import math
import uuid

import numpy as np

import kinetrix_rig as k

RIG_BONES = [b[0] for b in k.BONES] + list(k.ITEMS)


def _uid():
    return str(uuid.uuid4())


def _groups(data):
    """name -> uuid for every group of the model (Blockbench 4 or 5 layout)."""
    out = {}
    for g in data.get("groups", []):
        out.setdefault(g["name"], g["uuid"])

    def walk(items):
        for it in items:
            if isinstance(it, dict):
                if "name" in it:
                    out.setdefault(it["name"], it["uuid"])
                walk(it.get("children", []))
    walk(data.get("outliner", []))
    return out


def _old(data):
    return k.version_tuple(data.get("meta", {}).get("format_version", "4.0")) < (5, 0, 0)


class Anim:
    def __init__(self, name, length, loop, keys=None, markers=None, extra=None):
        self.name, self.length, self.loop = name, float(length), loop
        self.keys = keys or {}          # bone -> channel -> list of [time, [x, y, z], interpolation]
        self.markers = markers or []    # seconds
        self.extra = extra or {}        # animators of other bones (an opponent, effects): kept as they were

    # --- reading and writing -------------------------------------------------------------------
    @classmethod
    def load(cls, data, name):
        names = {u: n for n, u in _groups(data).items()}
        old = _old(data)
        anim = next(a for a in data.get("animations", []) if a["name"] in (name, "animation." + name))
        keys, extra = {}, {}
        for u, an in (anim.get("animators") or {}).items():
            bone = names.get(u, an.get("name"))
            if an.get("type", "bone") != "bone" or bone not in RIG_BONES:
                extra[u] = copy.deepcopy(an)
                continue
            for kf in an.get("keyframes", []):
                ch = kf["channel"]
                if ch not in ("rotation", "position"):
                    continue
                dp = kf["data_points"][0]
                # An empty field (or one with only a line break typed into it) is 0 in Blockbench.
                v = [float(str(dp.get(a, 0)).strip() or 0) for a in "xyz"]
                if old:  # Blockbench 4 kept rotation x, y and position x negated
                    v[0] = -v[0]
                    if ch == "rotation":
                        v[1] = -v[1]
                keys.setdefault(bone, {}).setdefault(ch, []).append([float(kf["time"]), v, kf.get("interpolation", "linear")])
        a = cls(k.anim_name(anim), anim.get("length", 1), anim.get("loop", "once"), keys,
                [float(m.get("time", 0)) for m in anim.get("markers") or []], extra)
        a.sort()
        return a

    def store(self, data):
        """Writes this animation into a Blockbench 5 model (replacing one of the same name)."""
        assert not _old(data), "store into a Blockbench 5 model"
        uuids = _groups(data)
        animators = copy.deepcopy(self.extra)
        for bone, chans in self.keys.items():
            kfs = []
            for ch, lst in chans.items():
                for t, v, interp in lst:
                    kfs.append({"channel": ch, "data_points": [{"x": _num(v[0]), "y": _num(v[1]), "z": _num(v[2])}],
                                "uuid": _uid(), "time": round(t, 4), "color": -1, "interpolation": interp})
            animators[uuids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
        anim = {"uuid": _uid(), "name": self.name, "loop": self.loop, "override": False, "length": round(self.length, 4),
                "snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "",
                "loop_delay": "", "animators": animators}
        if self.markers:
            anim["markers"] = [{"time": round(t, 4), "color": 0} for t in self.markers]
        data["animations"] = [a for a in data.get("animations", []) if k.anim_name(a) != self.name] + [anim]

    # --- keys ----------------------------------------------------------------------------------
    def sort(self):
        for chans in self.keys.values():
            for lst in chans.values():
                lst.sort(key=lambda kf: kf[0])

    def channel(self, bone, ch):
        return self.keys.setdefault(bone, {}).setdefault(ch, [])

    def key(self, bone, ch, t, v, interp="catmullrom"):
        lst = self.channel(bone, ch)
        lst[:] = [kf for kf in lst if abs(kf[0] - t) > 1e-4]
        lst.append([float(t), [float(x) for x in v], interp])
        lst.sort(key=lambda kf: kf[0])

    def drop(self, bone, ch=None, times=None):
        """Removes keys of a bone (one channel, or only those at the given times)."""
        for c in ([ch] if ch else list(self.keys.get(bone, {}))):
            lst = self.keys.get(bone, {}).get(c)
            if lst is None:
                continue
            if times is None:
                lst.clear()
            else:
                lst[:] = [kf for kf in lst if all(abs(kf[0] - t) > 1e-4 for t in times)]

    def value(self, bone, ch, t):
        lst = self.keys.get(bone, {}).get(ch)
        if not lst:
            return np.zeros(3)
        return np.array(k.sample([(kf[0], np.array(kf[1]), kf[2]) for kf in lst], t))

    def interp(self, kind, bones=None, channels=("rotation", "position"), t0=-1, t1=1e9):
        for bone, chans in self.keys.items():
            if bones and bone not in bones:
                continue
            for c in channels:
                for kf in chans.get(c, []):
                    if t0 - 1e-4 <= kf[0] <= t1 + 1e-4:
                        kf[2] = kind

    def retime(self, f):
        """Moves every key from time t to f(t) (f must keep the order)."""
        for chans in self.keys.values():
            for lst in chans.values():
                for kf in lst:
                    kf[0] = round(f(kf[0]), 4)
        self.markers = [round(f(t), 4) for t in self.markers]
        self.sort()

    def times(self):
        return sorted({round(kf[0], 4) for c in self.keys.values() for lst in c.values() for kf in lst})

    def mirrored(self, name):
        """The same move to the other side: left and right swap, turns about y and z flip sign,
        sideways moves flip."""
        def other(b):
            if b.startswith("right_"):
                return "left_" + b[6:]
            if b.startswith("left_"):
                return "right_" + b[5:]
            return b
        keys = {}
        for bone, chans in self.keys.items():
            for ch, lst in chans.items():
                for t, v, i in lst:
                    mv = [v[0], -v[1], -v[2]] if ch == "rotation" else [-v[0], v[1], v[2]]
                    keys.setdefault(other(bone), {}).setdefault(ch, []).append([t, mv, i])
        return Anim(name, self.length, self.loop, keys, list(self.markers), {})

    # --- posing against the floor --------------------------------------------------------------
    def model(self, data):
        d = copy.deepcopy(data)
        self.store(d)
        return k.Model(None, d)

    def lowest(self, data, t, model=None):
        """Lowest point of the player at time t (pixels; the floor is 0)."""
        m = model or self.model(data)
        anim = next(a for a in m.animations if k.anim_name(a) == self.name)
        W = k.bb_pose(m, m.channels(anim), t)
        owner = {c: g["name"] for g in m.groups.values() for c in g["children"]}
        low = 1e9
        for e in m.data["elements"]:
            bone = owner.get(e["uuid"])
            if bone not in k.BONE or bone not in W:
                continue
            o = m.groups[m.by_name[bone]]["origin"]
            lo, hi = np.array(e["from"], float), np.array(e["to"], float)
            for c in ((a, b, d) for a in (0, 1) for b in (0, 1) for d in (0, 1)):
                low = min(low, k.apply(W[bone], lo + (hi - lo) * np.array(c) - o)[1])
        return low

    def ground(self, data, t0, t1, mode="touch", step=0.05, extra_y=None):
        """Bakes the body's height between t0 and t1 so the player stands on the floor: "touch"
        puts the lowest point exactly on it (stances, rolls, slides), "above" only lifts what went
        under it. extra_y(t) adds a small bounce on top (pixels)."""
        times = [round(t0 + i * step, 4) for i in range(int(round((t1 - t0) / step)) + 1)]
        # Also every time something else has a key, so a pose placed there keeps its height.
        times = sorted(set(times) | {t for t in self.times() if t0 - 1e-4 <= t <= t1 + 1e-4})
        m = self.model(data)
        fixes = []
        for t in times:
            low = self.lowest(data, t, m)
            y = self.value("body", "position", t)
            dy = -low if mode == "touch" or low < 0 else 0.0
            fixes.append((t, [y[0], y[1] + dy + (extra_y(t) if extra_y else 0.0), y[2]]))
        lst = self.channel("body", "position")
        lst[:] = [kf for kf in lst if not (t0 - 1e-4 <= kf[0] <= t1 + 1e-4)]
        for t, v in fixes:
            lst.append([t, v, "catmullrom"])
        lst.sort(key=lambda kf: kf[0])


HAND = {"right": ("right_arm", "right_forearm", (6.0, 12.0, 0.0)),
        "left": ("left_arm", "left_forearm", (-6.0, 12.0, 0.0))}
FOOT = {"right": ("right_leg", "right_shin", (1.9, 0.0, 0.0)),
        "left": ("left_leg", "left_shin", (-1.9, 0.0, 0.0))}


def point(model, anim_name, t, bone, p):
    """World position (Blockbench pixels) of rest point p of a bone at time t."""
    a = next(x for x in model.animations if k.anim_name(x) == anim_name)
    W = k.bb_pose(model, model.channels(a), t)
    return k.apply(W[bone], np.array(p, float) - model.groups[model.by_name[bone]]["origin"])


def reach(anim, data, t, side, target, limb=HAND, stiffness=0.02, interp="catmullrom"):
    """Keys the upper bone (all three turns) and the lower one (a fold only: x and z) of a limb at
    time t so its end (fist or foot) is at target, changing the current angles as little as it can."""
    from scipy.optimize import minimize
    upper, lower, end = limb[side]
    u0 = anim.value(upper, "rotation", t)
    l0 = anim.value(lower, "rotation", t)
    base = copy.deepcopy(data)
    target = np.array(target, float)

    def place(x):
        a = copy.deepcopy(anim)
        a.key(upper, "rotation", t, x[:3], interp)
        a.key(lower, "rotation", t, (x[3], 0.0, x[4]), interp)
        return a

    def cost(x):
        m = place(x).model(base)
        p = point(m, anim.name, t, lower, end)
        return float(np.sum((p - target) ** 2)) + stiffness * float(np.sum((x - x0) ** 2) / 100.0)

    x0 = np.array([*u0, l0[0], l0[2]], float)
    # Elbows fold forward (x >= 0), knees backward (x <= 0); never the other way.
    fold = (0.0, 160.0) if limb is HAND else (-160.0, 0.0)
    x0[3] = min(max(x0[3], fold[0]), fold[1])
    bounds = [(-360, 360)] * 3 + [fold, (-90, 90)]
    res = minimize(cost, x0, method="Powell", bounds=bounds, options={"xtol": 0.05, "ftol": 1e-4, "maxiter": 4000})
    a = place(res.x)
    anim.keys = a.keys
    m = a.model(base)
    return float(np.linalg.norm(point(m, anim.name, t, lower, end) - target))


def bone_low(model, anim_name, t, bone):
    """Height of the lowest corner of a bone's cubes at time t (a foot's sole, a knee)."""
    a = next(x for x in model.animations if k.anim_name(x) == anim_name)
    W = k.bb_pose(model, model.channels(a), t)
    g = model.groups[model.by_name[bone]]
    o = g["origin"]
    low = 1e9
    for e in model.data["elements"]:
        if e["uuid"] not in g["children"]:
            continue
        lo, hi = np.array(e["from"], float), np.array(e["to"], float)
        for c in ((x, y, z) for x in (0, 1) for y in (0, 1) for z in (0, 1)):
            low = min(low, k.apply(W[bone], lo + (hi - lo) * np.array(c) - o)[1])
    return low


def fold_to(anim, data, t, bone, end, target_y, lo, hi, axis=0, interp="catmullrom"):
    """Folds one lower bone (a shin, a forearm) about one axis at time t until its lowest corner
    (end=None) or its end point is at height target_y; the upper bone stays as it is."""
    from scipy.optimize import minimize_scalar
    base = copy.deepcopy(data)
    v0 = anim.value(bone, "rotation", t)

    def place(x):
        a = copy.deepcopy(anim)
        v = list(v0)
        v[axis] = x
        a.key(bone, "rotation", t, v, interp)
        return a

    def cost(x):
        m = place(x).model(base)
        y = bone_low(m, anim.name, t, bone) if end is None else point(m, anim.name, t, bone, end)[1]
        return (y - target_y) ** 2

    res = minimize_scalar(cost, bounds=(lo, hi), method="bounded", options={"xatol": 0.1})
    anim.keys = place(res.x).keys
    return float(math.sqrt(res.fun))


def _num(x):
    x = round(float(x), 4)
    return 0 if x == 0 else x


def load_model(path):
    with open(path) as f:
        return json.load(f)


def save_model(data, path):
    with open(path, "w") as f:
        json.dump(data, f, indent=1)
