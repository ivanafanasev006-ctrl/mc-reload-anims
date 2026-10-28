"""Forward kinematics of a pose and a small least-squares solver for placing hands, knees and feet."""
import numpy as np

import animlib as L
import kinetrix_rig as kr
import viz

POINTS = {
    'lhand': ('left_forearm', (-6, 12.5, 0)),
    'rhand': ('right_forearm', (6, 12.5, 0)),
    'lelbow': ('left_forearm', (-6, 18, 0)),
    'relbow': ('right_forearm', (6, 18, 0)),
    'chin': ('head', (0, 24.5, -4)),
    'lcheek': ('head', (-2.5, 24.5, -4)),
    'lear': ('head', (-4, 27, 0)),
    'nose': ('head', (0, 27, -4)),
    'lknee': ('left_shin', (-1.9, 6, -2)),
    'rknee': ('right_shin', (1.9, 6, -2)),
    'lfoot': ('left_shin', (-1.9, 0, 0)),
    'rfoot': ('right_shin', (1.9, 0, 0)),
    'ltoe': ('left_shin', (-1.9, 0, -2)),
    'rtoe': ('right_shin', (1.9, 0, -2)),
    'lheel': ('left_shin', (-1.9, 0, 2)),
    'rheel': ('right_shin', (1.9, 0, 2)),
    'lknee_front': ('left_shin', (-1.9, 5, -2)),
    'rknee_front': ('right_shin', (1.9, 5, -2)),
    'hip': ('body', (0, 12, 0)),
    'neck': ('chest', (0, 24, 0)),
    'bowtop': ('right_item_bow', None),
}


def fk(pose):
    m = viz.model()
    p = L.full(pose)
    chans = {}
    for (bone, ch), v in L.parse(p).items():
        chans.setdefault(bone, {})[ch] = [(0.0, np.array(v, float), 'linear')]
    return kr.bb_pose(m, chans, 0.0)


def pt(W, name):
    m = viz.model()
    bone, rp = POINTS[name]
    o = m.groups[m.by_name[bone]]['origin']
    return kr.apply(W[bone], np.asarray(rp, float) - o)


def pts(pose, *names):
    W = fk(pose)
    return {n: pt(W, n).round(2) for n in names}


def solve(pose, targets, vary, reg=0.002, iters=60):
    """targets: [(point name, world xyz, weight)], or (point, axis index, value, weight) for one coordinate.
    vary: [(bone, axis 0..2, 'r'|'p')]. Returns the pose with the varied values changed."""
    pose = dict(pose)
    x0 = []
    for bone, ax, ch in vary:
        k = bone if ch == 'r' else bone + '@p'
        x0.append(float(pose.get(k, (0, 0, 0))[ax]))
    x0 = np.array(x0)

    def with_x(x):
        p = dict(pose)
        for (bone, ax, ch), v in zip(vary, x):
            k = bone if ch == 'r' else bone + '@p'
            vec = list(p.get(k, (0, 0, 0)))
            vec[ax] = v
            p[k] = tuple(vec)
        return p

    def resid(x):
        W = fk(with_x(x))
        r = []
        for tgt in targets:
            if len(tgt) == 3:
                n, w, wt = tgt
                r.extend((pt(W, n) - np.asarray(w, float)) * wt)
            else:
                n, ax, val, wt = tgt
                r.append((pt(W, n)[ax] - val) * wt)
        r.extend((x - x0) * reg)
        return np.array(r)

    x = x0.copy()
    lam = 1e-2
    r = resid(x)
    for _ in range(iters):
        J = np.zeros((len(r), len(x)))
        for i in range(len(x)):
            dx = np.zeros(len(x)); dx[i] = 0.05
            J[:, i] = (resid(x + dx) - r) / 0.05
        A = J.T @ J + lam * np.eye(len(x))
        step = -np.linalg.solve(A, J.T @ r)
        xn = x + step
        rn = resid(xn)
        if rn @ rn < r @ r:
            x, r = xn, rn
            lam *= 0.5
            if np.abs(step).max() < 1e-3:
                break
        else:
            lam *= 4
    out = with_x(np.round(x, 2))
    return out, float(np.sqrt(np.mean(resid(np.round(x, 2))[:-len(x)] ** 2)) if len(targets) else 0.0)


def lowest(pose):
    """Lowest y of the player's cubes (items and the dummy left out)."""
    m = viz.model()
    W = fk(pose)
    owner = {}
    for u, g in m.groups.items():
        for c in g['children']:
            owner[c] = g['name']
    low = 1e9
    for e in m.data['elements']:
        bone = owner.get(e['uuid'])
        if bone is None or bone.endswith('2') or 'item' in bone or bone not in W:
            continue
        o = m.groups[m.by_name[bone]]['origin']
        lo, hi = np.array(e['from'], float), np.array(e['to'], float)
        for cx in (lo[0], hi[0]):
            for cy in (lo[1], hi[1]):
                for cz in (lo[2], hi[2]):
                    p = kr.apply(W[bone], np.array([cx, cy, cz]) - o)
                    low = min(low, p[1])
    return low


def on_ground(pose, clearance=0.0):
    """The same pose moved up or down so its lowest point touches the ground."""
    p = dict(pose)
    bp = p.get('body@p', (0, 0, 0))
    d = lowest(p) - clearance
    p['body@p'] = (bp[0], round(bp[1] - d, 2), bp[2])
    return p
