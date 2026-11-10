"""The author, 2026-11-10: W + Space + G held with a sword, the turns in the air (air_spin_2026-11-10.bbmodel, his example;
remade after his video of 01:19, PIECES).

"He jumps head down and does what G+ does, but 5 turns, and lands as after C + LMB; work it over, a proper landing in
the fighting style at the end." His take (4.9 s): up and over head down (0.75 s), five turns head down with the blade out
(to 2.5 s), over again and down (3.0 s), a crouch on landing and up (to 4.9 s). Made quicker, his keys kept (sampled at 24
frames a second through a time warp): the jump and the turns 1.3 times faster, the landing and the rise 2.2 times:
sword_air_spin 3.18 s, the turns from 0.58 s to 1.92 s (ticks 12-38), the feet down at 2.31 s (tick 46).

2026-11-10, 20:00: "after the 5 turns, instead of the slide on the knees: he just drops to the ground and slides 3-4
blocks with the animation of the Ctrl + C slide, and that's all". His drop (to 3.0 s) runs into the slide (refined
author_2026-10-27.bbmodel, slide: from SLIDE_FROM on the way down, at the pace the move plays it, Move.SLIDE's 1.4) to its
end (up), SLIDE_BLEND to meet it; the kneel, its arms and the rise are gone.

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
PIECES = [(0.15, 0.45, 0.75), (1.75 / SPEED, 0.75, 2.5), (0.5 / SPEED, 2.5, 3.0)]
SLIDE = os.path.join(kit.BB, 'refined', 'author_2026-10-27.bbmodel')
SLIDE_FROM, SLIDE_PACE, SLIDE_BLEND = 0.2, 1.4, 0.15


def source(t):
    """The author's time for the made one's t (None: the rise after his pieces)."""
    for d, a, b in PIECES:
        if t <= d + 1e-9:
            return a + (b - a) * t / d
        t -= d
    return None


def main():
    import math
    with open(SRC, encoding='utf-8') as f:
        model = json.load(f)
    m = kr.Model(SRC)
    chans = m.channels(m.animations[0])
    sm = kr.Model(SLIDE)
    slide = sm.channels(next(a for a in sm.animations if a['name'] == 'slide'))
    slide_len = next(a for a in sm.animations if a['name'] == 'slide')['length']
    drop = sum(d for d, _, _ in PIECES)
    length = drop + (slide_len - SLIDE_FROM) / SLIDE_PACE
    n = int(round(length * FPS))
    out = {'uuid': str(uuid.uuid4()), 'name': 'sword_air_spin', 'loop': 'once', 'override': False,
           'length': round(n / FPS, 4), 'snapping': FPS, 'selected': True, 'anim_time_update': '', 'blend_weight': '',
           'start_delay': '', 'loop_delay': '', 'animators': {}}
    uuids = {g['name']: g['uuid'] for g in model['groups']}
    for bone in set(chans) | set(slide):
        if bone not in uuids:
            continue
        keys = []
        for c in ('rotation', 'position'):
            mine, theirs = chans.get(bone, {}).get(c), slide.get(bone, {}).get(c)
            if not mine and not theirs:
                continue
            flip = bone == 'body' and c == 'rotation'

            def at(src):
                v = [float(x) for x in kr.sample(mine, src)] if mine else [0.0, 0.0, 0.0]
                if flip:
                    v[1] = -v[1]
                return v

            last = at(PIECES[-1][2])
            # the slide's angles turned by the whole turns his drop ends at (the body's flips and its five turns)
            turns = [round(x / 360.0) * 360.0 for x in last] if c == 'rotation' else [0.0, 0.0, 0.0]

            def slid(st):
                v = [float(x) for x in kr.sample(theirs, st)] if theirs else [0.0, 0.0, 0.0]
                return [x + q for x, q in zip(v, turns)]

            gap = [x - y for x, y in zip(last, slid(SLIDE_FROM))]
            for i in range(n + 1):
                t = i / FPS
                src = source(t)
                if src is not None:
                    v = at(src)
                else:
                    w = 0.5 - 0.5 * math.cos(math.pi * min(1.0, (t - drop) / SLIDE_BLEND))
                    v = [x + g * (1 - w) for x, g in zip(slid(SLIDE_FROM + (t - drop) * SLIDE_PACE), gap)]
                keys.append({'channel': c, 'data_points': [{'x': round(float(v[0]), 3), 'y': round(float(v[1]), 3),
                                                            'z': round(float(v[2]), 3)}],
                             'uuid': str(uuid.uuid4()), 'time': round(i / FPS, 4), 'color': -1, 'interpolation': 'catmullrom'})
        if keys:
            out['animators'][uuids[bone]] = {'name': bone, 'type': 'bone', 'keyframes': keys}
    model['animations'] = [out]
    model['name'] = 'air_spin_2026-11-10'
    with open(DST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, separators=(',', ':'))
    print('wrote', DST, out['length'], 'drop at', round(drop, 3), 's, tick', round(drop * 20))


if __name__ == '__main__':
    main()
