"""The author's set of 2026-10-29 (CLAUDE.md: the author's work is a framework; keep the idea and
the key poses, perfect the execution) and the ledge set made from the author's description.

    python3 tools/blockbench/refine_author_2026_10_29/refine.py

reads  tools/blockbench/animations/author_2026-10-29.bbmodel  (never changed)
writes tools/blockbench/refined/author_2026-10-29.bbmodel     (hop_on_kick, slide_under_attack)
       tools/blockbench/refined/ledge_2026-10-29.bbmodel      (the ledge set, with the ledge to see it by)

The same round also changed hop_roll_dodge and finisher_stab (refine_author_2026_10_28b.py) and is
described in docs/moves.md: what each move is for and which key drives which part of the mechanic.

How the keys are made: key poses with eases, or poses worked out every frame (hands and feet that
hold on to something are solved onto it, kit.py); keys on the 1/24 s grid are kept only where
Blockbench's catmullrom would otherwise leave the curve (../refine_author_2026_10_28/animlib.py).
"""
import base64
import copy
import io
import json
import os
import uuid

import numpy as np
from PIL import Image

import kit
import landings
import ledge
import moves
from kit import L

DST = os.path.join(kit.BB, 'refined', 'author_2026-10-29.bbmodel')
DST_LEDGE = os.path.join(kit.BB, 'refined', 'ledge_2026-10-29.bbmodel')
#: The mechanic's paths (moves.paths), read by the mod.
DST_PATHS = os.path.join(os.path.dirname(kit.BB), '..', 'moves', 'src', 'main', 'resources', 'kinetrix_moves_paths.json')

#: The ledge in the ledge file: a block wall whose top edge is where the hands hold (ledge.py). Not a
#: bone of the rig, so it never reaches the game; in each move it moves against the player's path, so
#: in Blockbench the player climbs it as in the game.
WALL = 'ledge_wall'
WALL_FROM, WALL_TO = (-48.0, -24.0, -40.0), (48.0, ledge.LEDGE_Y, ledge.WALL_Z)


def stone_png():
    """A 16x16 stone texture in the vanilla manner: grey, light from the top left, a few cracks."""
    rng = np.random.default_rng(29)
    base = np.array([125, 125, 122])
    img = np.zeros((16, 16, 3), int)
    for y in range(16):
        for x in range(16):
            img[y, x] = base + rng.choice([-14, -7, 0, 0, 7, 12])
    for (x, y) in ((3, 4), (4, 4), (5, 5), (11, 9), (12, 10), (12, 11), (7, 13), (8, 13)):
        img[y, x] = base - 30
    im = Image.fromarray(np.clip(img, 0, 255).astype('uint8'), 'RGB').convert('RGBA')
    buf = io.BytesIO()
    im.save(buf, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()


def add_wall(model):
    """The ledge group with its one cube and its texture."""
    tex = copy.deepcopy(model['textures'][1])
    tex.update(name='ledge_stone.png', id=str(len(model['textures'])), uuid=str(uuid.uuid4()),
               width=16, height=16, uv_width=16, uv_height=16, source=stone_png(), path='', folder='', saved=False)
    model['textures'].append(tex)
    ti = len(model['textures']) - 1
    cube = {'name': 'ledge', 'box_uv': False, 'rescale': False, 'locked': False, 'light_emission': 0,
            'render_order': 'default', 'allow_mirror_modeling': True, 'from': list(WALL_FROM), 'to': list(WALL_TO),
            'autouv': 0, 'color': 7, 'origin': [0, 0, 0], 'uv_offset': [0, 0],
            'faces': {f: {'uv': [0, 0, 16, 16], 'texture': ti} for f in ('north', 'east', 'south', 'west', 'up', 'down')},
            'type': 'cube', 'uuid': str(uuid.uuid4())}
    model['elements'].append(cube)
    g = {'name': WALL, 'origin': [0, 0, 0], 'color': 7, 'uuid': str(uuid.uuid4()), 'export': True,
         'mirror_uv': False, 'isOpen': False, 'locked': False, 'visibility': True, 'autouv': 0, 'selected': False}
    model['groups'].append(g)
    model['outliner'].append({'uuid': g['uuid'], 'isOpen': False, 'children': [cube['uuid']]})


def set_visible(model, group_name, visible):
    """A group and its cubes shown or hidden (what Blockbench's eye in the Outliner does)."""
    g = next(g for g in model['groups'] if g['name'] == group_name)
    g['visibility'] = visible

    def find(items):
        for it in items:
            if isinstance(it, dict):
                if it.get('uuid') == g['uuid']:
                    return it
                hit = find(it.get('children', []))
                if hit:
                    return hit
    cubes = {c for c in find(model['outliner']).get('children', []) if isinstance(c, str)}
    for e in model['elements']:
        if e['uuid'] in cubes:
            e['visibility'] = visible


def wall_keys(a, path, own=None):
    """The ledge moving against the player's path (path: {time: xyz px}), or, for the friend's own
    frame, placed and turned as it is seen from them (own: {time: the friend's place in the world})."""
    for t in sorted(path if own is None else own):
        if own is None:
            a.key(t, {WALL + '@p': tuple(-np.asarray(path[t], float))})
        else:
            h = own[t]
            a.key(t, {WALL + '@p': (h[0], -h[1], h[2]), WALL: (0, 180, 0)})


def bystander(a, length):
    """In the ledge moves without a friend the grey figure stands back on the top, out of the way."""
    a.key(0, {'body2@p': (-20, ledge.LEDGE_Y, -40), 'body2': (0, 180, 0)})
    a.key(length, {'body2@p': (-20, ledge.LEDGE_Y, -40), 'body2': (0, 180, 0)})


def no_bow(a):
    """animlib keeps a still bow track to say a bow is held; these moves hold no bow."""
    a.tracks.pop(('right_item_bow', 'rotation'), None)


def to_json(a, uuids):
    j = a.to_json(uuids)
    if getattr(a, 'markers', None):
        j['markers'] = [{'time': round(t, 4), 'color': 0} for t in a.markers]
    return j


def main():
    with open(kit.SRC, encoding='utf-8') as f:
        src = json.load(f)

    # --- the author's two drafts ---
    model = copy.deepcopy(src)
    # slide_under_attack: made again for the author's second message (moves.py); the PC version
    # (combat.slide_under_attack, a slide then the turn and back) is kept there for reference.
    # hop_on_kick: made again for the author's fourth message (moves.py); the PC version
    # (combat.hop_on_kick, a hang high over the mob) is kept there for reference.
    made = [moves.hop_on_kick(), moves.slide_under_attack(), moves.fall_tuck(), moves.fall_roll(),
            landings.dive_roll(), landings.land_heavy()]
    uuids = L.bone_uuids(model)
    for a in made:
        no_bow(a)
    model['animations'] = [to_json(a, uuids) for a in made]
    model['animations'][0]['selected'] = True
    with open(DST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, separators=(',', ':'))
    print(f'wrote {DST}: ' + ', '.join(a.name for a in made))

    # --- the ledge set ---
    lm = copy.deepcopy(src)
    lm['name'] = 'ledge_2026-10-29'
    add_wall(lm)
    set_visible(lm, 'right_item_sword', False)
    set_visible(lm, 'right_item_sword2', False)
    uuids = L.bone_uuids(lm)
    anims = []
    hang = ledge.ledge_hang()
    bystander(hang, hang.length)
    anims.append(hang)
    v = ledge.SHIMMY_STEP / ledge.SHIMMY_T
    for side in ('right', 'left'):
        s = ledge.ledge_shimmy(side)
        sign = 1 if side == 'right' else -1
        wall_keys(s, {t: (sign * v * t, 0, 0) for t in kit.frames(0, s.length)})
        bystander(s, s.length)
        anims.append(s)
    for side in ('left', 'right'):
        h = ledge.ledge_hop(side)
        sign = -1 if side == 'left' else 1
        wall_keys(h, {t: (sign * ledge.hop_path(t) / ledge.PX, 0, 0) for t in kit.frames(0, h.length)})
        bystander(h, h.length)
        anims.append(h)
    c = ledge.ledge_climb_up()
    wall_keys(c, c.root)
    bystander(c, c.length)
    anims.append(c)
    down = moves.ledge_climb_down(c)
    wall_keys(down, down.root)
    bystander(down, down.length)
    anims.append(down)
    pulled, helper = ledge.assisted()
    wall_keys(pulled, pulled.root)
    places = {t: ledge.HELPER_AT + kit.rot_of((0, 180, 0)) @ r for t, r in helper.root.items()}
    wall_keys(helper, None, own=places)
    anims += [pulled, helper]
    for a in anims:
        no_bow(a)
    lm['animations'] = [to_json(a, uuids) for a in anims]
    lm['animations'][0]['selected'] = True
    with open(DST_LEDGE, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(lm, f, separators=(',', ':'))
    print(f'wrote {DST_LEDGE}: ' + ', '.join(a.name for a in anims))

    # --- the mechanic's paths ---
    data = moves.paths(c, down, pulled, helper, ledge.hop_path)
    # the dive (W + Space + R with no mob near): up and forward [default]
    data['dive_roll'] = [[round(landings.DIVE_LIFT(i / 20) * moves.PX, 4), round(landings.DIVE_FORWARD(i / 20), 4)]
                         for i in range(int(round(landings.DIVE_LEN * 20)) + 1)]
    moves.write_paths(data, os.path.normpath(DST_PATHS))
    print(f'wrote {os.path.normpath(DST_PATHS)}: ' + ', '.join(data))
    return made, anims


if __name__ == '__main__':
    main()
