"""Poses sampled from the author's bow_barrage (tools/blockbench/animations/author_2026-10-28.bbmodel) as pose dicts."""
import animlib  # noqa: F401  (puts the rig tools on the path)
import numpy as np
import kinetrix_rig as kr
import viz
import animlib as L

def at(t, name='bow_barrage'):
    m = viz.model()
    anim = next(a for a in m.animations if a['name'] == name)
    chans = m.channels(anim)
    p = {}
    for bone in L.ROT_BONES:
        ch = chans.get(bone, {})
        if 'rotation' in ch:
            p[bone] = tuple(np.round(kr.sample(ch['rotation'], t), 3))
    for bone in L.POS_BONES:
        ch = chans.get(bone, {})
        if 'position' in ch:
            p[bone + '@p'] = tuple(np.round(kr.sample(ch['position'], t), 3))
    return p
