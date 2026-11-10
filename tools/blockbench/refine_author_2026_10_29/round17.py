"""The author, 2026-11-10, 13:41: the axe's whirlwind (G, G+) turning the other way round.

The whirl's turns are the body's turn about Y (0 to 1800 degrees) and the axe's flat head (right_item_sword about Y); both
the other way round in refined/author_2026-10-30.bbmodel (axe_whirlwind, axe_whirlwind_end): the body's Y negated, the axe
turned half round about its own handle (the edge ahead again). 0.9.17 negated the axe's Y as well, which swapped its head
and handle (author's video, 2026-11-10 evening); undone here. Run after refine.py, which writes that file:

    python3 tools/blockbench/refine_author_2026_10_29/round17.py
"""
import json
import math
import os

import numpy as np

import kit
import kinetrix_rig as kr

DST = os.path.join(kit.BB, 'refined', 'author_2026-10-30.bbmodel')
NAMES = ('axe_whirlwind', 'axe_whirlwind_end')


def _edge_first(rest, keys):
    """The axe's keys turned half round about its handle (the group's own Y, along which the rig's blade lies): the
    edge ahead in the turn the other way round, the head where it was at the end of the handle."""
    prev = None
    for k in sorted(keys, key=lambda k: k['time']):
        dp = k['data_points'][0]
        r = np.radians(rest + np.array([float(dp['x']), float(dp['y']), float(dp['z'])]))
        ang = kr.continue_angles(prev, kr.euler_zyx(kr.rot_zyx(*r) @ kr.ry(math.pi)))
        prev = ang
        v = np.degrees(ang) - rest
        dp['x'], dp['y'], dp['z'] = (round(float(q), 3) for q in v)


def main():
    with open(DST, encoding='utf-8') as f:
        model = json.load(f)
    if model.get('kinetrix_whirl_edge'):
        print('already reversed')
        return
    undo = bool(model.get('kinetrix_whirl_reversed'))     # 0.9.17: the axe's Y negated too: its head and handle swapped
    rest = np.array(next(g for g in model['groups'] if g['name'] == 'right_item_sword')['rotation'], float)
    for a in model['animations']:
        if a['name'] not in NAMES:
            continue
        for an in a['animators'].values():
            name = an.get('name')
            rot = [k for k in an.get('keyframes', []) if k['channel'] == 'rotation']
            if name == 'body' and not undo:
                for k in rot:
                    for dp in k['data_points']:
                        dp['y'] = -float(dp['y'])
            if name == 'right_item_sword':
                if undo:
                    for k in rot:
                        for dp in k['data_points']:
                            dp['y'] = -float(dp['y'])
                _edge_first(rest, rot)
    model['kinetrix_whirl_reversed'] = True
    model['kinetrix_whirl_edge'] = True
    with open(DST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, separators=(',', ':'))
    print('reversed', ', '.join(NAMES))


if __name__ == '__main__':
    main()
