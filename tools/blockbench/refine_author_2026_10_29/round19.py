"""The author, 2026-11-10, evening (video): the axe's combo built anew from his example, a blow for each mob.

"With the combo on G+ every mob is hurt at once; it must be EACH one 70 % of his health. The animation is crooked: I gave
you an example of how it should be and you just put it in; you were to build your own from mine." 20:00: "take out the
second at the start where he gets into the stance; each blow a different one, as with the sword's chain finisher, the
poses taking turns."

His take (animations/axe_triple_2026-11-10.bbmodel, 5.83 s): a wind-up (crouched, the axe drawn back, held 0.42-0.75 s),
three blows on quick turns of the whole body (0.8-1.42 s: 225 degrees; 1.7-2.33: 190; 2.5-3.3: 300), then, facing ahead,
the axe up over the head (3.3-3.46 s), held up a while, an overhead chop (4.38-4.46 s), an upswing, the axe up at the right
and a chop down across (5.04-5.12 s), back to the stance. Taken apart into the blows the mechanic puts together, one for
each mob, a turning one and a chop in turn (Whirl.comboNext):

    axe_combo_open   the first turning blow, at once from the stance    0.70 - 1.62 s of his
    axe_combo_chop   the overhead chop (his hold over the head cut out)  3.30 - 3.46, 4.30 - 4.56
    axe_combo_2      the second turning blow                             1.62 - 2.46
    axe_combo_diag   the chop down across from the right                4.72 - 5.22
    axe_combo_3      the third turning blow                              2.46 - 3.33
    axe_combo_end    back to the stance                                  5.17 - 5.83

Each starts and ends in his ready pose (the wind-up's hold, 0.70 s), BLEND each side, so any follows any; the open one
starts from the stance. The turning ones are a whole turn of the body each (his turns scaled to 360 degrees: the player
faces the next mob after it); the chops face ahead; the mechanic turns him to each mob on the way. Every bone sampled at
24 frames a second (each piece at its own pace), smoothed a little (sigma 0.03 s).

    python3 tools/blockbench/refine_author_2026_10_29/round19.py
"""
import json
import os
import uuid

import numpy as np

import kit
import kinetrix_rig as kr

SRC = os.path.join(kit.BB, 'animations', 'axe_triple_2026-11-10.bbmodel')
DST = os.path.join(kit.BB, 'refined', 'axe_combo_2026-11-10.bbmodel')
FPS, SIGMA = 24, 0.03
READY = 0.70
#: Seconds (made) the ends are blended over to the ready pose (the stance at the start of the first), and a cut within one.
BLEND, CUT = 0.13, 0.08
TURN, CHOP = 1.5, 1.3
#: name, his pieces (from, to, how much quicker), start pinned to ('stand', 'ready', None), end pinned, a whole turn
PIECES = [('axe_combo_open', [(READY, 1.62, TURN)], 'stand', 'ready', True),
          ('axe_combo_chop', [(3.30, 3.46, CHOP), (4.30, 4.56, CHOP)], 'ready', 'ready', False),
          ('axe_combo_2', [(1.62, 2.46, TURN)], 'ready', 'ready', True),
          ('axe_combo_diag', [(4.72, 5.22, CHOP)], 'ready', 'ready', False),
          ('axe_combo_3', [(2.46, 3.33, TURN)], 'ready', 'ready', True),
          ('axe_combo_end', [(5.17, 5.83, TURN)], 'ready', None, False)]


def _smooth(x, sigma_frames):
    r = int(np.ceil(3 * sigma_frames))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma_frames) ** 2)
    k /= k.sum()
    pad = np.concatenate([np.repeat(x[:1], r, 0), x, np.repeat(x[-1:], r, 0)])
    return np.stack([np.convolve(pad[:, i], k, mode='valid') for i in range(3)], 1)


def _ease(n):
    """0 to 1 over n frames, cosine."""
    return 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, n + 1))


def _times(segs):
    """His times of the made frames, each piece at its pace; the frames where a cut joins two."""
    out, cuts = [], []
    for t0, t1, speed in segs:
        n = max(1, int(round((t1 - t0) / speed * FPS)))
        ts = list(np.linspace(t0, t1, n + 1))
        if out:
            cuts.append(len(out))
        out.extend(ts if not out else ts)
    return np.array(out), cuts


def build(chans, bones, name, segs, pin0, pin1, whole):
    ts, cuts = _times(segs)
    n = len(ts)
    nb, nc = int(round(BLEND * FPS)), int(round(CUT * FPS))
    seq = {}
    for b in bones:
        for c in ('rotation', 'position'):
            keys = chans.get(b, {}).get(c)
            if not keys:
                continue
            x = np.array([kr.sample(keys, t) for t in ts])
            # a cut: the later piece starts where the earlier ended, blended back to its own over nc frames
            for i in cuts:
                gap = x[i] - x[i - 1]
                if c == 'rotation':
                    gap -= np.round(gap / 360.0) * 360.0
                w = np.zeros(n)
                w[i:i + nc + 1] = 1 - _ease(nc)[:len(w[i:i + nc + 1])]
                x[i:] -= np.outer(w[i:], gap)
            x = np.delete(x, cuts, axis=0) if cuts else x
            m = len(x)
            ready = np.array(kr.sample(keys, READY))
            start = ready.copy() if pin0 == 'ready' else np.zeros(3)
            end = ready.copy()
            if b == 'body' and c == 'rotation':
                y0, y1 = x[0, 1], x[-1, 1]
                x[:, 1] = (x[:, 1] - y0) * (360.0 / (y1 - y0)) if whole else x[:, 1] - y0
                start[1], end[1] = 0.0, (360.0 if whole else 0.0)
            elif c == 'rotation':
                # whole turns of a bone kept: the pinned angles the nearest the piece's own ends
                start = start + np.round((x[0] - start) / 360.0) * 360.0
                end = end + np.round((x[-1] - end) / 360.0) * 360.0
            if pin0:
                w = np.zeros(m)
                w[:nb + 1] = 1 - _ease(nb)
                x = x + np.outer(w, start - x[0])
            if pin1:
                w = np.zeros(m)
                w[m - nb - 1:] = _ease(nb)
                x = x + np.outer(w, end - x[-1])
            seq[(b, c)] = x
    m = len(next(iter(seq.values())))
    anim = {'uuid': str(uuid.uuid4()), 'name': name, 'loop': 'once', 'override': False,
            'length': round((m - 1) / FPS, 4), 'snapping': FPS, 'selected': False, 'anim_time_update': '',
            'blend_weight': '', 'start_delay': '', 'loop_delay': '', 'animators': {}}
    for (bone, c), raw in seq.items():
        if np.abs(raw).max() < 1e-3:
            continue
        x = _smooth(raw, SIGMA * FPS)
        x[0], x[-1] = raw[0], raw[-1]
        keys = anim['animators'].setdefault(bone, [])
        for i, v in enumerate(x):
            keys.append({'channel': c, 'data_points': [{'x': round(float(v[0]), 3), 'y': round(float(v[1]), 3),
                                                        'z': round(float(v[2]), 3)}],
                         'uuid': str(uuid.uuid4()), 'time': round(i / FPS, 4), 'color': -1, 'interpolation': 'catmullrom'})
    return anim


def main():
    model = kr.Model(SRC)
    take = model.animations[0]
    chans = model.channels(take)
    bones = [b for b in chans]
    with open(SRC, encoding='utf-8') as f:
        raw = json.load(f)
    uuids = {g['name']: g['uuid'] for g in raw['groups']}
    made = []
    for name, segs, p0, p1, whole in PIECES:
        a = build(chans, bones, name, segs, p0, p1, whole)
        a['animators'] = {uuids[b]: {'name': b, 'type': 'bone', 'keyframes': k} for b, k in a['animators'].items()}
        made.append(a)
    made[0]['selected'] = True
    raw['name'] = 'axe_combo_2026-11-10'
    raw['animations'] = made
    with open(DST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(raw, f, separators=(',', ':'))
    print(f'wrote {DST}: ' + ', '.join(f"{a['name']} {a['length']} ({round(a['length'] * 20)} ticks)" for a in made))


if __name__ == '__main__':
    main()
