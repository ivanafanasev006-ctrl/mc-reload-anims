"""The author, 2026-11-10: W + Space + G held with a sword, the turns in the air (air_spin_2026-11-10.bbmodel, his example).

"He jumps head down and does what G+ does, but 5 turns, and lands as after C + LMB; work it over, a proper landing in
the fighting style at the end." His take (4.9 s): up and over head down (0.75 s), five turns head down with the blade out
(to 2.5 s), over again and down (3.0 s), a crouch on landing and up (to 4.9 s). Made quicker, his keys kept (sampled at 24
frames a second through a time warp): the jump and the turns 1.3 times faster, the landing and the rise 2.2 times:
sword_air_spin 3.18 s, the turns from 0.58 s to 1.92 s (ticks 12-38), the feet down at 2.31 s (tick 46).

    python3 tools/blockbench/refine_author_2026_10_29/round16.py
"""
import json
import os
import uuid

import kit
import kinetrix_rig as kr

SRC = os.path.join(kit.BB, 'animations', 'air_spin_2026-11-10.bbmodel')
DST = os.path.join(kit.BB, 'refined', 'air_spin_2026-11-10.bbmodel')
FPS, CUT, FAST, LAND = 24, 3.0, 1.3, 2.2


def source(t):
    """The author's time for the made one's t."""
    edge = CUT / FAST
    return t * FAST if t <= edge else CUT + (t - edge) * LAND


def main():
    with open(SRC, encoding='utf-8') as f:
        model = json.load(f)
    m = kr.Model(SRC)
    a = m.animations[0]
    chans = m.channels(a)
    length = CUT / FAST + (a['length'] - CUT) / LAND
    n = int(round(length * FPS))
    out = {'uuid': str(uuid.uuid4()), 'name': 'sword_air_spin', 'loop': 'once', 'override': False,
           'length': round(n / FPS, 4), 'snapping': FPS, 'selected': True, 'anim_time_update': '', 'blend_weight': '',
           'start_delay': '', 'loop_delay': '', 'animators': {}}
    uuids = {g['name']: g['uuid'] for g in model['groups']}
    for bone, ch in chans.items():
        keys = []
        for c in ('rotation', 'position'):
            if c not in ch:
                continue
            for i in range(n + 1):
                v = kr.sample(ch[c], min(source(i / FPS), a['length']))
                keys.append({'channel': c, 'data_points': [{'x': round(float(v[0]), 3), 'y': round(float(v[1]), 3),
                                                            'z': round(float(v[2]), 3)}],
                             'uuid': str(uuid.uuid4()), 'time': round(i / FPS, 4), 'color': -1, 'interpolation': 'catmullrom'})
        if keys and bone in uuids:
            out['animators'][uuids[bone]] = {'name': bone, 'type': 'bone', 'keyframes': keys}
    model['animations'] = [out]
    model['name'] = 'air_spin_2026-11-10'
    with open(DST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, separators=(',', ':'))
    print('wrote', DST, out['length'])


if __name__ == '__main__':
    main()
