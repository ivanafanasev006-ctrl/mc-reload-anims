"""The author, 2026-11-10, evening (video): the axe's combo built anew from his example, a blow for each mob.

"With the combo on G+ every mob is hurt at once; it must be EACH one 70 % of his health. The animation is crooked: I gave
you an example of how it should be and you just put it in; you were to build your own from mine."

His take (animations/axe_triple_2026-11-10.bbmodel, 5.83 s): a wind-up (crouched, the axe drawn back, held 0.42-0.75 s),
three blows, each on a quick turn of the whole body (0.8-1.42 s: 225 degrees; 1.7-2.33: 190; 2.5-3.3: 300; 720 in all),
a flourish over the shoulder (3.3-5.1 s), back to the stance. Taken apart into pieces the mechanic puts together, one
blow for each mob (Whirl, AxeComboInput):

    axe_combo_open   the wind-up and the first blow            0    - 1.62 s of his
    axe_combo_1      the first blow again (from the ready pose) 0.70 - 1.62
    axe_combo_2      the second                                1.62 - 2.46
    axe_combo_3      the third                                 2.46 - 3.33
    axe_combo_end    back to the stance                        his last 0.66 s (5.17 - 5.83)

Each blow starts and ends in his ready pose (the wind-up's hold, 0.70 s), BLEND each side, so any follows any; each is
one whole turn of the body (his turns scaled to 360 degrees: the player faces the next mob after it, the mechanic turns
him to it on the way). The flourish is left out (a blow a mob: 1.7 s more would be too long). Every bone sampled at
24 frames a second, smoothed a little (sigma 0.03 s), played SPEED times quicker.

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
FPS, SIGMA, SPEED = 24, 0.03, 1.5
READY = 0.70
#: Author's seconds the ends are blended over (to the ready pose, the stance at the end).
BLEND = 0.2
#: name, from, to (his seconds), start pinned to the ready pose, end pinned to it, the body's turn made whole
PIECES = [('axe_combo_open', 0.0, 1.62, False, True, True),
          ('axe_combo_1', READY, 1.62, True, True, True),
          ('axe_combo_2', 1.62, 2.46, True, True, True),
          ('axe_combo_3', 2.46, 3.33, True, True, True),
          ('axe_combo_end', 5.17, 5.83, True, False, False)]


def _smooth(x, sigma_frames):
    r = int(np.ceil(3 * sigma_frames))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma_frames) ** 2)
    k /= k.sum()
    pad = np.concatenate([np.repeat(x[:1], r, 0), x, np.repeat(x[-1:], r, 0)])
    return np.stack([np.convolve(pad[:, i], k, mode='valid') for i in range(3)], 1)


def _ease(n):
    """0 to 1 over n frames, cosine."""
    return 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, n + 1))


def build(chans, bones, name, t0, t1, pin0, pin1, whole):
    ts = np.array(kit.frames(t0, t1, FPS))
    n = len(ts)
    nb = int(round(BLEND * FPS))
    seq = {}
    for b in bones:
        for c in ('rotation', 'position'):
            keys = chans.get(b, {}).get(c)
            if not keys:
                continue
            x = np.array([kr.sample(keys, t) for t in ts])
            ready = np.array(kr.sample(keys, READY))
            start, end = ready.copy(), ready.copy()
            if b == 'body' and c == 'rotation':
                # the turn about Y from 0 to a whole turn (his 225, 190, 300 scaled), the next mob ahead after it
                y0, y1 = x[0, 1], x[-1, 1]
                if whole:
                    x[:, 1] = (x[:, 1] - y0) * (360.0 / (y1 - y0))
                    end[1] = 360.0
                else:
                    x[:, 1] -= y0
                start[1] = 0.0
            if c == 'rotation' and not (b == 'body'):
                # whole turns of a bone kept: the ready pose's angles the nearest the piece's own ends
                start = start + np.round((x[0] - start) / 360.0) * 360.0
                end = end + np.round((x[-1] - end) / 360.0) * 360.0
            if pin0:
                w = np.zeros(n)
                w[:nb + 1] = 1 - _ease(nb)
                x = x + np.outer(w, start - x[0])
            if pin1:
                w = np.zeros(n)
                w[n - nb - 1:] = _ease(nb)
                x = x + np.outer(w, end - x[-1])
            seq[(b, c)] = x
    anim = {'uuid': str(uuid.uuid4()), 'name': name, 'loop': 'once', 'override': False,
            'length': round((n - 1) / FPS / SPEED, 4), 'snapping': FPS, 'selected': False, 'anim_time_update': '',
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
                         'uuid': str(uuid.uuid4()), 'time': round(i / FPS / SPEED, 4), 'color': -1, 'interpolation': 'catmullrom'})
    return anim, seq, ts


def main():
    model = kr.Model(SRC)
    take = model.animations[0]
    chans = model.channels(take)
    bones = [b for b in chans]
    with open(SRC, encoding='utf-8') as f:
        raw = json.load(f)
    uuids = {g['name']: g['uuid'] for g in raw['groups']}
    made = []
    for name, t0, t1, p0, p1, whole in PIECES:
        a, _, _ = build(chans, bones, name, t0, t1, p0, p1, whole)
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
