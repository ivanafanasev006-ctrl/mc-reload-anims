"""The author, 2026-11-10: the axe's combo on G held, 3 to 6 mobs about (axe_triple_2026-11-10.bbmodel, his example).

"The combo on G+ if 3 to 6 enemies are near, 75 % of each one's health; work the animation over before it goes in."
His take (5.8 s): a crouch and wind-up, three blows each on a quick turn of the whole body (about 1.1, 2.1 and 3.1 s), the
axe swung round overhead, a long way back up. Made quicker, his keys kept (sampled at 24 frames a second through a time
warp): to 4.25 s 1.6 times faster, the rest (the way up) 2.2 times: axe_triple 3.38 s, the blows at 0.70, 1.33 and
1.95 s (Whirl.TRIPLE_HITS).

    python3 tools/blockbench/refine_author_2026_10_29/round18.py
"""
import json
import os
import uuid

import kit
import kinetrix_rig as kr

SRC = os.path.join(kit.BB, 'animations', 'axe_triple_2026-11-10.bbmodel')
DST = os.path.join(kit.BB, 'refined', 'axe_triple_2026-11-10.bbmodel')
FPS, CUT, FAST, UP = 24, 4.25, 1.6, 2.2


def source(t, end):
    edge = CUT / FAST
    return min(end, t * FAST if t <= edge else CUT + (t - edge) * UP)


def main():
    with open(SRC, encoding='utf-8') as f:
        model = json.load(f)
    m = kr.Model(SRC)
    a = m.animations[0]
    chans = m.channels(a)
    n = int(round((CUT / FAST + (a['length'] - CUT) / UP) * FPS))
    out = {'uuid': str(uuid.uuid4()), 'name': 'axe_triple', 'loop': 'once', 'override': False, 'length': round(n / FPS, 4),
           'snapping': FPS, 'selected': True, 'anim_time_update': '', 'blend_weight': '', 'start_delay': '', 'loop_delay': '',
           'animators': {}}
    uuids = {g['name']: g['uuid'] for g in model['groups']}
    for bone, ch in chans.items():
        keys = []
        for c in ('rotation', 'position'):
            for i in range(n + 1) if c in ch else ():
                v = kr.sample(ch[c], source(i / FPS, a['length']))
                keys.append({'channel': c, 'data_points': [{'x': round(float(v[0]), 3), 'y': round(float(v[1]), 3),
                                                            'z': round(float(v[2]), 3)}],
                             'uuid': str(uuid.uuid4()), 'time': round(i / FPS, 4), 'color': -1, 'interpolation': 'catmullrom'})
        if keys and bone in uuids:
            out['animators'][uuids[bone]] = {'name': bone, 'type': 'bone', 'keyframes': keys}
    model['animations'] = [out]
    model['name'] = 'axe_triple_2026-11-10'
    with open(DST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, separators=(',', ':'))
    print('wrote', DST, out['length'])


if __name__ == '__main__':
    main()
