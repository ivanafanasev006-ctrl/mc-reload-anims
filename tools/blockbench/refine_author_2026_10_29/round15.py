"""The author, 2026-11-09 (evening, with videos): the chain finisher made again from his parts (author_2026-11-09F2.bbmodel).

"I made it in separate parts, take from there and work on it. He jumps too high, hangs over the mob; he must get right onto
it as in Blockbench, close, not over it, and there are 2 of them: one from the front, the sword into the back, the other
jumping behind it, into the head; they alternate. The animation itself smoother; at the end, when he jumps off and rolls,
as with Ctrl + W + Space + R roughly."

His parts (the mobs 2 and 3 move about the player in them; the mechanic carries the player, ChainInput):
air_kill_jump 0.5 s, air_kill_stab 0.625 s (in front of the mob, face to face, the blade over its shoulder into its back),
air_kill_hop / _ccw 0.75 s (over to the next, turning either way), air_kill_head 0.5 s (behind it, the blade into its
head), air_kill_flip 1.5 s. Put together (every bone sampled at 24 frames a second, smoothed a little: sigma 0.03 s; a
0.12 s blend where two parts that were not one take meet):

    chain_leap          jump + stab            the first one, from the front
    chain_hop(_ccw)     hop + head             the next, from behind
    chain_hop_stab(_ccw) hop + stab            the one after, from the front again, and so on
    chain_flip          flip to its landing (0.85 s), then hop_roll_kick's back roll on the ground and up (its 0.95 s on)

    python3 tools/blockbench/refine_author_2026_10_29/round15.py
"""
import copy
import json
import os
import uuid

import numpy as np

import kit
import kinetrix_rig as kr

SRC = os.path.join(kit.BB, 'animations', 'author_2026-11-09F2.bbmodel')
KICK = os.path.join(kit.BB, 'refined', 'author_2026-10-29b.bbmodel')
DST = os.path.join(kit.BB, 'refined', 'chain_2026-11-09b.bbmodel')
BONES = ('body', 'torso', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm', 'right_leg',
         'right_shin', 'left_leg', 'left_shin', 'right_item_sword')
FPS, SIGMA, BLEND, ITEM_BLEND = 24, 0.03, 0.12, 0.3
OUT = [('chain_leap', [('air_kill_jump', 0, None), ('air_kill_stab', 0, None)]),
       ('chain_hop', [('air_kill_hop', 0, None), ('air_kill_head', 0, None)]),
       ('chain_hop_ccw', [('air_kill_hop_ccw', 0, None), ('air_kill_head', 0, None)]),
       ('chain_hop_stab', [('air_kill_hop', 0, None), ('air_kill_stab', 0, None)]),
       ('chain_hop_stab_ccw', [('air_kill_hop_ccw', 0, None), ('air_kill_stab', 0, None)]),
       ('chain_flip', [('air_kill_flip', 0, 0.85), ('kick:hop_roll_kick', 0.95, None)])]


def _channels():
    out = {}
    for path, prefix in ((SRC, ''), (KICK, 'kick:')):
        m = kr.Model(path)
        for a in m.animations:
            out[prefix + a['name']] = (a['length'], m.channels(a))
    return out


def _seg(chans, name, t0, t1):
    """{(bone, channel): array of frames} of one part, from t0 to t1."""
    length, ch = chans[name]
    t1 = length if t1 is None else t1
    ts = np.array(kit.frames(t0, t1, FPS))
    out = {}
    for b in BONES:
        for c in ('rotation', 'position'):
            keys = ch.get(b, {}).get(c)
            out[(b, c)] = np.array([kr.sample(keys, t) for t in ts]) if keys else np.zeros((len(ts), 3))
    return out, len(ts)


def _join(a, b):
    """b after a: whole turns taken off b's angles to meet a's; a blend over BLEND where they do not meet."""
    out = {}
    for k in a:
        x, y = a[k], b[k].copy()
        if k[1] == 'rotation':
            y += np.round((x[-1] - y[0]) / 360.0) * 360.0
        n = int(round((ITEM_BLEND if k[0] == 'right_item_sword' else BLEND) * FPS))
        gap = y[0] - x[-1]
        if np.abs(gap).max() > 1e-3:
            w = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, n + 1))[:, None]       # 0 at the joint, 1 n frames on
            y[:n + 1] = y[:n + 1] - gap * (1 - w)
        out[k] = np.concatenate([x, y[1:]])
    return out


def _smooth(x, sigma_frames):
    r = int(np.ceil(3 * sigma_frames))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma_frames) ** 2)
    k /= k.sum()
    pad = np.concatenate([np.repeat(x[:1], r, 0), x, np.repeat(x[-1:], r, 0)])
    return np.stack([np.convolve(pad[:, i], k, mode='valid') for i in range(3)], 1)


def build(chans, name, parts):
    seq = None
    for src, t0, t1 in parts:
        s, _ = _seg(chans, src, t0, t1)
        seq = s if seq is None else _join(seq, s)
    n = len(next(iter(seq.values())))
    anim = {'uuid': str(uuid.uuid4()), 'name': name, 'loop': 'once', 'override': False, 'length': round((n - 1) / FPS, 4),
            'snapping': FPS, 'selected': False, 'anim_time_update': '', 'blend_weight': '', 'start_delay': '', 'loop_delay': '',
            'animators': {}}
    for (bone, c), x in seq.items():
        if np.abs(x).max() < 1e-3:
            continue
        x = _smooth(x, SIGMA * FPS)
        x[0], x[-1] = seq[(bone, c)][0], seq[(bone, c)][-1]     # it starts and ends where the parts do
        keys = anim['animators'].setdefault(bone, [])
        for i, v in enumerate(x):
            keys.append({'channel': c, 'data_points': [{'x': round(float(v[0]), 3), 'y': round(float(v[1]), 3),
                                                        'z': round(float(v[2]), 3)}],
                         'uuid': str(uuid.uuid4()), 'time': round(i / FPS, 4), 'color': -1, 'interpolation': 'catmullrom'})
    return anim


def main():
    with open(SRC, encoding='utf-8') as f:
        model = json.load(f)
    groups = {g['uuid']: g for g in model['groups']}
    # the player alone: the mobs' figures (body2, body3 and all under them) out of the model
    gone = set()

    def under(o):
        u = o if isinstance(o, str) else o['uuid']
        gone.add(u)
        for c in (o.get('children', []) if isinstance(o, dict) else []):
            under(c)

    roots = [o for o in model['outliner'] if isinstance(o, dict) and groups[o['uuid']]['name'] in ('body2', 'body3')]
    for r in roots:
        under(r)
    model['groups'] = [g for g in model['groups'] if g['uuid'] not in gone]
    model['elements'] = [e for e in model['elements'] if e['uuid'] not in gone]
    model['outliner'] = [o for o in model['outliner'] if o not in roots]
    uuids = {g['name']: g['uuid'] for g in model['groups']}
    chans = _channels()
    made = []
    for name, parts in OUT:
        a = build(chans, name, parts)
        a['animators'] = {uuids[b]: {'name': b, 'type': 'bone', 'keyframes': k} for b, k in a['animators'].items()}
        made.append(a)
    made[0]['selected'] = True
    model['name'] = 'chain_2026-11-09b'
    model['animations'] = made
    with open(DST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, separators=(',', ':'))
    print(f'wrote {DST}: ' + ', '.join(f"{a['name']} {a['length']}" for a in made))


if __name__ == '__main__':
    main()
