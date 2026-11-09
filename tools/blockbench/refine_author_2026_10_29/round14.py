"""The author, 2026-11-09: the chain finisher's animations made again (author_2026-11-09F.bbmodel, "updated and better").

The author's file holds the whole chain at two mobs in one take, the player and both mobs (figures 2 and 3) moving:
air_kill turns clockwise onto the second mob, air_kill_ccw the other way. The player stays where he is in it (the mobs
move about him), so the mechanic carries and turns him (ChainInput), as before. Cut into the parts the chain repeats
(the author's keys kept as they are, the cut ends sampled off his curves):

    chain_leap      0    - 1.2  s  down, up off both feet, the sword turned point down, onto the shoulders (LAND 0.5 s),
                                   the blade into the head (KILL 0.8 s), out of it, the arms up for the next
    chain_hop       1.2  - 2.25 s  over onto the next one turning clockwise (LAND 0.55 s), the blade in (KILL 0.75 s), out
    chain_hop_ccw   the same from air_kill_ccw: turning the other way
    chain_flip      2.25 - 3.875 s the back flip off the last one, down on the feet (LAND 0.9 s), up, the sword as held

The leap is cut at 1.2 s, where both takes are still the same (the turn's lean starts at 1.25 s); the hop ends at 2.25 s,
the blade out and up, much as at 1.2 s, so a hop runs on into the next one or into the flip.

    python3 tools/blockbench/refine_author_2026_10_29/round14.py
"""
import copy
import json
import os
import uuid

import kit
import kinetrix_rig as kr

SRC = os.path.join(kit.BB, 'animations', 'author_2026-11-09F.bbmodel')
DST = os.path.join(kit.BB, 'refined', 'chain_2026-11-09.bbmodel')
#: name, the author's take, from, to (s)
PARTS = [('chain_leap', 'air_kill', 0.0, 1.2), ('chain_hop', 'air_kill', 1.2, 2.25),
         ('chain_hop_ccw', 'air_kill_ccw', 1.2, 2.25), ('chain_flip', 'air_kill', 2.25, 3.875)]


def _victims(model):
    """The uuids of the two mobs' figures (body2, body3 and all under them)."""
    groups = {g['uuid']: g for g in model['groups']}
    out = set()

    def under(o):
        u = o if isinstance(o, str) else o['uuid']
        out.add(u)
        if isinstance(o, dict):
            for c in o.get('children', []):
                under(c)

    roots = [o for o in model['outliner'] if isinstance(o, dict) and groups[o['uuid']]['name'] in ('body2', 'body3')]
    for r in roots:
        under(r)
    return out, roots


def _part(m, src, name, t0, t1, player):
    chans = m.channels(src)
    out = {'uuid': str(uuid.uuid4()), 'name': name, 'loop': 'once', 'override': False, 'length': round(t1 - t0, 4),
           'snapping': 24, 'selected': False, 'anim_time_update': '', 'blend_weight': '', 'start_delay': '', 'loop_delay': '',
           'animators': {}}
    for auuid, a in src['animators'].items():
        if auuid not in player or not a.get('keyframes'):
            continue
        bone = a['name']
        keys = []
        for ch in ('rotation', 'position'):
            ck = chans.get(bone, {}).get(ch)
            if not ck:
                continue
            inside = [k for k in a['keyframes'] if k['channel'] == ch and t0 + 1e-4 < k['time'] < t1 - 1e-4]
            for t in (t0, t1):
                v = kr.sample(ck, t)
                keys.append({'channel': ch, 'data_points': [{'x': round(float(v[0]), 4), 'y': round(float(v[1]), 4),
                                                             'z': round(float(v[2]), 4)}],
                             'uuid': str(uuid.uuid4()), 'time': round(t - t0, 4), 'color': -1, 'interpolation': 'catmullrom'})
            for k in inside:
                k = copy.deepcopy(k)
                k['uuid'] = str(uuid.uuid4())
                k['time'] = round(k['time'] - t0, 4)
                keys.append(k)
        if keys:
            keys.sort(key=lambda k: (k['channel'], k['time']))
            out['animators'][auuid] = {'name': bone, 'type': 'bone', 'keyframes': keys}
    return out


def main():
    with open(SRC, encoding='utf-8') as f:
        model = json.load(f)
    m = kr.Model(SRC)
    takes = {a['name']: a for a in model['animations']}
    gone, roots = _victims(model)
    player = {g['uuid'] for g in model['groups'] if g['uuid'] not in gone}
    made = [_part(m, takes[src], name, t0, t1, player) for name, src, t0, t1 in PARTS]
    # the player alone: the mobs' figures out of the model
    model['groups'] = [g for g in model['groups'] if g['uuid'] not in gone]
    model['elements'] = [e for e in model['elements'] if e['uuid'] not in gone]
    model['outliner'] = [o for o in model['outliner'] if o not in roots]
    model['name'] = 'chain_2026-11-09'
    model['animations'] = made
    made[0]['selected'] = True
    with open(DST, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(model, f, separators=(',', ':'))
    print(f'wrote {DST}: ' + ', '.join(a['name'] for a in made))


if __name__ == '__main__':
    main()
