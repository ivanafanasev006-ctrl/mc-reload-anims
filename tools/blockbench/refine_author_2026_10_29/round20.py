"""The author, 2026-11-10, 20:00: the axe's whirlwind (G) with the axe in both hands, not one.

His whirl held the axe at the end of the right arm out sideways, the left arm out the other way. Both hands on the handle
(the left one below the right, nearer the butt, as a right-handed swing holds it), the hands together in front of the
chest, the axe out to the right a little forward, its edge ahead as before (round17): the arms and the axe's turn in the
fist solved (kit.solve's least squares) so the right fist, the left one and the axe's line are where they must be. That
pose takes the place of his while the whirl turns (axe_whirlwind from 0.29 s to 2.5 s); the wind-up brings the hands to it,
the ending (his own, and axe_whirlwind_end) takes them back from it, the same offset blended in and out.

21:06 (videos): "it must turn the way the axe's blade looks; the axe held straighter, the arms more stretched out
ahead": the fists out in front at arm's length, the axe straight on from them (a little to the right), its blade turned
the other way round from round17's (EDGE: that one trailed in the turn).

    python3 tools/blockbench/refine_author_2026_10_29/round20.py      (after refine.py and round17.py)
"""
import json
import math
import os

import numpy as np
from scipy.optimize import least_squares

import kit
import kinetrix_rig as kr

DST = os.path.join(kit.BB, 'refined', 'author_2026-10-30.bbmodel')
ARMS = ('right_arm', 'right_forearm', 'left_arm', 'left_forearm', 'right_item_sword')
#: Where the right fist is in the whirl (pixels from the hip, the body's own frame: +x right, +y up, -z ahead), which way
#: the axe points from it (to the head), how far the left fist is from the right along the handle (towards the butt).
FIST = np.array([1.5, 8.5, -9.3])
AXE = np.array([0.3, 0.0, -1.0])
LEFT_BELOW = 3.0
#: The blade's side against round17's: -1 turns it half round about the handle (the edge ahead in the turn, the author).
EDGE = -1.0
#: How much a bent elbow costs (px of error a degree): the arms kept as straight as the hold lets them.
STRAIGHT = 0.03
#: His whirl's turning, when its arms are its spin pose (s): the wind-up before, his ending after.
SPIN_FROM, SPIN_TO, END_TO = 0.29, 2.5, 2.85
#: axe_whirlwind_end: the hands let go of the two-handed hold over this long (s).
LET_GO = 0.25


def _ease(x):
    x = min(1.0, max(0.0, x))
    return 0.5 - 0.5 * math.cos(math.pi * x)


def _pose_chans(chans, t, over=None):
    out = {}
    for b, cs in chans.items():
        for c, keys in cs.items():
            v = np.array(kr.sample(keys, t), float)
            if over and (b, c) in over:
                v = over[(b, c)]
            out.setdefault(b, {})[c] = [(0.0, v, 'linear')]
    return out


def solve(model, chans, t):
    """The two-handed hold at moment t of the whirl: {(bone, 'rotation'): angles} of the arms and the axe."""
    o = {b: np.array(model.groups[model.by_name[b]]['origin'], float) for b in ('right_forearm', 'left_forearm')}
    base = {b: np.array(kr.sample(chans[b]['rotation'], t), float) for b in ARMS}
    W = kr.bb_pose(model, _pose_chans(chans, t), 0.0)
    Wb = np.linalg.inv(W['body'])
    d = AXE / np.linalg.norm(AXE)
    # the axe's flat as his (round17's edge ahead): its local +X, square to the new line
    side = (Wb @ W['right_item_sword'])[:3, :3] @ np.array([1.0, 0.0, 0.0])
    side = side - d * side.dot(d)
    side = EDGE * side / np.linalg.norm(side)
    # how far the axe's pivot is out from the fist along its line (the rig's hold), kept
    f0 = kr.apply(Wb @ W['right_forearm'], np.array([6.0, 12.0, 0.0]) - o['right_forearm'])
    i0 = kr.apply(Wb @ W['right_item_sword'], [0.0, 0.0, 0.0])
    reach = float(np.linalg.norm(i0 - f0))
    vary = [('right_arm', 0), ('right_arm', 1), ('right_arm', 2), ('right_forearm', 0), ('left_arm', 0), ('left_arm', 1),
            ('left_arm', 2), ('left_forearm', 0), ('right_item_sword', 0), ('right_item_sword', 1), ('right_item_sword', 2)]
    x0 = np.array([base[b][ax] for b, ax in vary])

    def over(x):
        v = {b: base[b].copy() for b in ARMS}
        for (b, ax), q in zip(vary, x):
            v[b][ax] = q
        return {(b, 'rotation'): v[b] for b in ARMS}

    def resid(x):
        W = kr.bb_pose(model, _pose_chans(chans, t, over(x)), 0.0)
        Wb = np.linalg.inv(W['body'])
        rf = kr.apply(Wb @ W['right_forearm'], np.array([6.0, 12.0, 0.0]) - o['right_forearm'])
        lf = kr.apply(Wb @ W['left_forearm'], np.array([-6.0, 12.0, 0.0]) - o['left_forearm'])
        it = Wb @ W['right_item_sword']
        i0 = kr.apply(it, [0.0, 0.0, 0.0])
        tip = kr.apply(it, [0.0, -9.0, 0.0])
        sd = kr.apply(it, [2.0, 0.0, 0.0])
        r = [rf - FIST, lf - (FIST - LEFT_BELOW * d), i0 - (FIST + reach * d), tip - (FIST + (reach + 9.0) * d),
             (sd - i0) - 2.0 * side]
        bend = [min(0.0, x[3]) * 2, max(0.0, x[3] - 130) * 2, min(0.0, x[7]) * 2, max(0.0, x[7] - 130) * 2,
                x[3] * STRAIGHT, x[7] * STRAIGHT]
        return np.concatenate([np.concatenate(r), np.array(bend), (x - x0) * 0.004])

    res = least_squares(resid, x0, diff_step=1e-4, xtol=1e-8, ftol=1e-10, max_nfev=2000)
    err = float(np.sqrt(np.mean(resid(res.x)[:15] ** 2)))
    return over(res.x), err


def main():
    with open(DST, encoding='utf-8') as f:
        raw = json.load(f)
    if raw.get('kinetrix_whirl_two_hands'):
        print('already two-handed')
        return
    model = kr.Model(DST)
    whirl = next(a for a in model.animations if a['name'] == 'axe_whirlwind')
    chans = model.channels(whirl)
    mid = (SPIN_FROM + SPIN_TO) / 2
    held, err = solve(model, chans, mid)
    print('two-handed hold, error %.2f px:' % err, {b: np.round(v, 1).tolist() for (b, _), v in held.items()})
    spin = {b: np.array(kr.sample(chans[b]['rotation'], mid), float) for b in ARMS}
    # the hold in place of his spin pose: the same offset from his, blended in by the wind-up and out by the ending
    offset = {b: held[(b, 'rotation')] - spin[b] for b in ARMS}
    for a in raw['animations']:
        if a['name'] not in ('axe_whirlwind', 'axe_whirlwind_end'):
            continue
        for an in a['animators'].values():
            b = an.get('name')
            if b not in ARMS:
                continue
            for k in an.get('keyframes', []):
                if k['channel'] != 'rotation':
                    continue
                t = float(k['time'])
                if a['name'] == 'axe_whirlwind':
                    w = _ease(t / SPIN_FROM) if t < SPIN_FROM else 1.0 if t <= SPIN_TO else 1.0 - _ease((t - SPIN_TO) / (END_TO - SPIN_TO))
                else:
                    w = 1.0 - _ease(t / LET_GO)
                dp = k['data_points'][0]
                for i, q in enumerate('xyz'):
                    dp[q] = round(float(dp[q]) + float(offset[b][i]) * w, 3)
    raw['kinetrix_whirl_two_hands'] = True
    with open(DST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(raw, f, separators=(',', ':'))
    print('wrote', DST)


if __name__ == '__main__':
    main()
