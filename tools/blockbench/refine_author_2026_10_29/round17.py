"""The author, 2026-11-10, 13:41: the axe's whirlwind (G, G+) turning the other way round.

The whirl's turns are the body's turn about Y (0 to 1800 degrees) and the axe's flat head (right_item_sword about Y); both
the other way round in refined/author_2026-10-30.bbmodel (axe_whirlwind, axe_whirlwind_end). Run after refine.py, which
writes that file:

    python3 tools/blockbench/refine_author_2026_10_29/round17.py
"""
import json
import os

import kit

DST = os.path.join(kit.BB, 'refined', 'author_2026-10-30.bbmodel')
NAMES = ('axe_whirlwind', 'axe_whirlwind_end')
BONES = ('body', 'right_item_sword')


def main():
    with open(DST, encoding='utf-8') as f:
        model = json.load(f)
    if model.get('kinetrix_whirl_reversed'):
        print('already reversed')
        return
    for a in model['animations']:
        if a['name'] not in NAMES:
            continue
        for an in a['animators'].values():
            if an.get('name') not in BONES:
                continue
            for k in an.get('keyframes', []):
                if k['channel'] == 'rotation':
                    for dp in k['data_points']:
                        dp['y'] = -float(dp['y'])
    model['kinetrix_whirl_reversed'] = True
    with open(DST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, separators=(',', ':'))
    print('reversed', ', '.join(NAMES))


if __name__ == '__main__':
    main()
