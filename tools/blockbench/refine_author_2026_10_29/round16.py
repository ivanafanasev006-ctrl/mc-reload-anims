"""The author, 2026-11-10: W + Space + G held with a sword, the turns in the air (air_spin_2026-11-10.bbmodel, his example;
remade after his video of 01:19, PIECES).

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
FPS = 24
#: (seconds made, author's from, author's to): a piece of his take played in that time. The author, 2026-11-10, 01:19:
#: "the hop at the start is useless, straight into the turns; the turns and the run 1.5-1.7 times quicker; down on the
#: knees, sliding a couple of blocks as on Ctrl + C, then he just gets up, no show". His jump cut to its last 0.3 s (over
#: head down), the turns and the drop 1.6 times faster, his kneel (3.25 s) held for the slide, then up into the stance.
#: 01:45: "1.5 times, not 1.6; at the end the arms not spread out, down (or a little apart), not up".
SPEED = 1.5
PIECES = [(0.15, 0.45, 0.75), (1.75 / SPEED, 0.75, 2.5), (0.5 / SPEED, 2.5, 3.0), (0.1, 3.0, 3.25), (0.35, 3.25, 3.6)]
RISE = 0.3
#: The arms from the landing on: down at the sides, a little apart (his were spread out and up), over ARMS_IN seconds.
ARMS_LOW = {'right_arm': (8.0, 0.0, 12.0), 'left_arm': (8.0, 0.0, -12.0), 'right_forearm': (14.0, 0.0, 0.0),
            'left_forearm': (14.0, 0.0, 0.0)}
ARMS_IN = 0.15


def source(t):
    """The author's time for the made one's t (None: the rise after his pieces)."""
    for d, a, b in PIECES:
        if t <= d + 1e-9:
            return a + (b - a) * t / d
        t -= d
    return None


def main():
    with open(SRC, encoding='utf-8') as f:
        model = json.load(f)
    m = kr.Model(SRC)
    a = m.animations[0]
    chans = m.channels(a)
    length = sum(d for d, _, _ in PIECES) + RISE
    n = int(round(length * FPS))
    import math
    kneel = sum(d for d, _, _ in PIECES)
    out = {'uuid': str(uuid.uuid4()), 'name': 'sword_air_spin', 'loop': 'once', 'override': False,
           'length': round(n / FPS, 4), 'snapping': FPS, 'selected': True, 'anim_time_update': '', 'blend_weight': '',
           'start_delay': '', 'loop_delay': '', 'animators': {}}
    uuids = {g['name']: g['uuid'] for g in model['groups']}
    for bone, ch in chans.items():
        keys = []
        for c in ('rotation', 'position'):
            if c not in ch:
                continue
            land = sum(d for d, _, _ in PIECES[:3])
            low = ARMS_LOW.get(bone) if c == 'rotation' else None
            last = list(low) if low else kr.sample(ch[c], PIECES[-1][2])
            # up: from the kneel to the stance, plainly (the angles to the nearest whole turn of standing)
            stand = [round(float(x) / 360.0) * 360.0 if c == 'rotation' else 0.0 for x in last]
            for i in range(n + 1):
                t = i / FPS
                src = source(t)
                if src is not None:
                    v = kr.sample(ch[c], src)
                    if low and t > land:
                        w = 0.5 - 0.5 * math.cos(math.pi * min(1.0, (t - land) / ARMS_IN))
                        v = [float(x) + (y - float(x)) * w for x, y in zip(v, low)]
                else:
                    w = 0.5 - 0.5 * math.cos(math.pi * min(1.0, (t - kneel) / RISE))
                    v = [float(x) + (y - float(x)) * w for x, y in zip(last, stand)]
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
