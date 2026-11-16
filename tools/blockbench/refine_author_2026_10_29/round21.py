"""The author, 2026-11-16, 21:17 (video): "after the fall the sword spins round itself a lot of times in a second".

plunge_land (the landing with the sword driven into the ground, round5) held the blade point down while it came out of the
ground and then turned it back to the normal grip by moving the item's three Blockbench angles in a straight line to zero.
In the game's terms (rightItem, the angles playerAnimator blends one by one) that went first through the place where two of
the angles do the same thing (the second one near 90 degrees: they swung by half a turn and more from one tick to the
next), then turned the blade about 290 degrees about one of them, and ended on angles of (-180, 180, 180): the same as
none, but the game's blend out of the move took them back to zero one by one, another whirl.

Here round5's keys up to FROM (the blade starting out of the ground) keep their turns, written as the plain angles (the second
under 90 degrees: round5's were the other set for the same turn, half turns added), and from there the blade goes back to the
normal grip along a straight line of those angles to none, eased in and out: in the game one smooth turn of about 150
degrees in a third of a second (at most 51 degrees a tick, against the old 87 and 92), ending on the normal grip exactly.

    python3 tools/blockbench/refine_author_2026_10_29/round21.py      (after refine.py)
"""
import json
import math
import os
import uuid

import numpy as np

import kit
import kinetrix_rig as kr

DST = os.path.join(kit.BB, 'refined', 'author_2026-10-29b.bbmodel')
ANIM, ITEM = 'plunge_land', 'right_item_sword'
#: The turn back to the normal grip: from FROM (s; the blade starting out of the ground) to TO, keys every STEP.
FROM, TO, STEP = 0.583333, 0.916667, 1 / 24


def _ease(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def main():
    with open(DST, encoding='utf-8') as fh:
        raw = json.load(fh)
    if raw.get('kinetrix_plunge_short_turn'):
        print('already the short turn')
        return
    model = kr.Model(DST)
    chans = model.channels(next(a for a in model.animations if a['name'] == ANIM))
    group = model.groups[model.by_name[ITEM]]
    if model.groups[group['parent']]['name'] != 'right_forearm':
        raise SystemExit(f'{ITEM} is not in the right fist')
    rest = np.array(group['rotation'], float)
    r_d = kr.item_frame('right', *kr.ITEMS[ITEM]['display'])['r_d']
    S, H = kr.S_FLIP, kr.HAND_ROT

    def game(key):  # (only to report the start in the game's terms)
        """rightItem's turn (as convert_frame makes it) for the item group's key angles (degrees)."""
        return H.T @ S @ kr.rot_zyx(*np.radians(rest + key)) @ S @ r_d.T

    anim = next(a for a in raw['animations'] if a['name'] == ANIM)
    animator = next(an for an in anim['animators'].values() if an.get('name') == ITEM)
    rot = sorted((k for k in animator['keyframes'] if k['channel'] == 'rotation'), key=lambda k: float(k['time']))
    # his keys up to FROM the same turns, their angles as the plain ones (the second under 90 degrees, no half turns
    # thrown in): the straight line to the normal grip from there stays clear of where two angles do the same thing
    prev = None
    for k in rot:
        if float(k['time']) > FROM + 1e-4:
            break
        d = k['data_points'][0]
        total = rest + np.array([float(d[c]) for c in 'xyz'])
        plain = np.degrees(kr.euler_zyx(kr.rot_zyx(*np.radians(total))))
        if prev is not None:
            plain = plain + 360.0 * np.round((prev - plain) / 360.0)
        prev = plain
        k['data_points'] = [{c: round(float(v), 4) + 0.0 for c, v in zip('xyz', plain - rest)}]
    key0 = prev - rest
    e0 = np.array(kr.euler_zyx(game(key0)))
    print(f'{ANIM}: from the point-down grip at {FROM:.3f} s (key {key0.round(1).tolist()}), '
          f'the game\'s angles {np.degrees(e0).round(1).tolist()}')
    n = round((TO - FROM) / STEP)
    new = [(FROM + i * STEP, key0 * (1.0 - _ease(i / n))) for i in range(1, n + 1)]
    template = min(rot, key=lambda k: abs(float(k['time']) - FROM))
    keep = [k for k in animator['keyframes'] if not (k['channel'] == 'rotation' and float(k['time']) > FROM + 1e-4)]
    for t, k in new + [(float(anim['length']), np.zeros(3))]:
        kf = json.loads(json.dumps(template))
        kf['uuid'] = str(uuid.uuid4())
        kf['time'] = round(t, 6)
        kf['data_points'] = [{c: round(float(v), 4) + 0.0 for c, v in zip('xyz', k)}]
        keep.append(kf)
    animator['keyframes'] = keep
    raw['kinetrix_plunge_short_turn'] = True
    with open(DST, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(raw, fh, separators=(',', ':'))
    print(f'{ANIM}: {len(new)} keys of the turn back written to {os.path.relpath(DST, kit.BB)}')


if __name__ == '__main__':
    main()
