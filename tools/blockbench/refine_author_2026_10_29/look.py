"""Pictures for checking and for the author: the player, the grey partner and the ledge, several
views a frame (same forward kinematics as Blockbench).

    python3 look.py <model.bbmodel> <animation> <out.png> [--times 0,0.5,1] [--views side,tq,front]
"""
import argparse
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kit  # noqa: E402
from kit import kr  # noqa: E402

CAMS = {
    'front': lambda p: (-p[0], p[1], p[2]),
    'back': lambda p: (p[0], p[1], -p[2]),
    'side': lambda p: (-p[2], p[1], -p[0]),       # from the player's right
    'lside': lambda p: (p[2], p[1], p[0]),        # from the player's left
    'top': lambda p: (-p[0], -p[2], -p[1]),       # from above, the player's front up
}


def cam34(deg, elev=0.0):
    a, e = math.radians(deg), math.radians(elev)

    def f(p):
        x = -(p[0] * math.cos(a) + p[2] * math.sin(a))
        z = -p[0] * math.sin(a) + p[2] * math.cos(a)
        y = p[1] * math.cos(e) + z * math.sin(e)
        z2 = z * math.cos(e) - p[1] * math.sin(e)
        return (x, y, z2)
    return f


CAMS['tq'] = cam34(35)
CAMS['tql'] = cam34(-35)
CAMS['rear'] = cam34(160)
CAMS['rearl'] = cam34(-160)
CAMS['wall'] = cam34(180 + 20, 10)   # behind the hanging player, a little from the right and above

HIDDEN = {'right_item_bow', 'left_item_sword', 'right_item_bow2', 'left_item_sword2', 'right_item_sword2'}
STONE = (128, 128, 124, 255)


def figure_polys(W, show=(), model=None, partner=True):
    m = model or kit.RIG
    owner = {c: g['name'] for g in m.groups.values() for c in g['children']}
    texs = {i: kr.texture_image(m, i) for i in range(len(m.data.get('textures', [])))}
    out = []
    for e in m.data['elements']:
        bone = owner.get(e['uuid'])
        if bone is None or bone not in W:
            continue
        if bone in HIDDEN and bone not in show:
            continue
        if bone.endswith('2') and not partner:
            continue
        if bone == 'right_item_sword' and 'no_sword' in show:
            continue
        o = m.groups[m.by_name[bone]]['origin']
        infl = e.get('inflate', 0.0)
        lo = np.array(e['from'], float) - infl
        hi = np.array(e['to'], float) + infl
        crot = np.radians(np.array(e.get('rotation', [0, 0, 0]), float))
        corig = np.array(e.get('origin', o), float)
        grey = bone.endswith('2')
        for fname, cs in kr.FACE_CORNERS.items():
            face = e['faces'].get(fname)
            if not face or face.get('texture') is None:
                continue
            col = kr.face_color(texs.get(face['texture']), face['uv'])
            if col[3] < 40:
                continue
            if grey:
                g = int(sum(col[:3]) / 3)
                col = (int(g * 0.55 + 45), int(g * 0.65 + 70), int(g * 0.55 + 45), 255)
            pts = []
            for c in cs:
                p = lo + (hi - lo) * np.array(c, float)
                p = corig + kr.rot_zyx(*crot) @ (p - corig)
                pts.append(kr.apply(W[bone], p - o))
            out.append((pts, col))
    return out


def box_polys(lo, hi, col=STONE):
    lo, hi = np.asarray(lo, float), np.asarray(hi, float)
    out = []
    for cs in kr.FACE_CORNERS.values():
        out.append(([lo + (hi - lo) * np.array(c, float) for c in cs], col))
    return out


def draw(polys, view, size, center=(0, 0), span=64.0, label=None, marks=(), floor=True):
    img = Image.new('RGB', (size, size), (238, 238, 232))
    d = ImageDraw.Draw(img)
    cam = CAMS[view]
    scale = size / span
    ox = size / 2 - center[0] * scale
    oy = size * (0.5 if view == 'top' else 0.85) + center[1] * scale

    def scr(p):
        q = cam(p)
        return (ox + q[0] * scale, oy - q[1] * scale), q[2]
    if view != 'top' and floor:
        d.line([(0, oy), (size, oy)], fill=(170, 170, 160), width=1)
    drawn = []
    for pts, col in polys:
        sp = [scr(p) for p in pts]
        depth = sum(s[1] for s in sp) / 4
        xy = [s[0] for s in sp]
        v1 = np.array(cam(pts[1])) - np.array(cam(pts[0]))
        v2 = np.array(cam(pts[3])) - np.array(cam(pts[0]))
        n = np.cross(v1, v2)
        nn = np.linalg.norm(n)
        light = 0.55 + 0.45 * abs(n[2] / nn) if nn > 1e-9 else 0.7
        drawn.append((depth, xy, tuple(int(c * light) for c in col[:3])))
    for depth, xy, col in sorted(drawn, key=lambda x: -x[0]):
        d.polygon(xy, fill=col, outline=tuple(max(0, c - 40) for c in col))
    for p, c in marks:
        (x, y), _ = scr(p)
        d.ellipse([x - 2.5, y - 2.5, x + 2.5, y + 2.5], fill=c)
    if label:
        d.text((3, 2), label, fill=(0, 0, 0))
    return img


def anim_world(anim, t, model=None):
    """World matrices at time t of an animlib.Anim (its keys as Blockbench plays them)."""
    chans = {}
    for (bone, ch), keys in anim.samples().items():
        chans.setdefault(bone, {})[ch] = [(k[0], np.array(k[1], float), 'catmullrom') for k in keys]
    for (bone, ch), v in anim.linear_static.items():
        chans.setdefault(bone, {})[ch] = [(0.0, np.array(v, float), 'linear')]
    return kr.bb_pose(model or kit.RIG, chans, t)


def lifted(W, dy, bones=None):
    """Matrices of the player moved up by dy (the game lifts the hitbox, the model rides on it)."""
    out = dict(W)
    T = kr.tr((0, dy, 0))
    for b, m in W.items():
        if (bones is None and not b.endswith('2')) or (bones and b in bones):
            out[b] = T @ m
    return out


def sheet(worlds, out, views=('side', 'tq', 'front'), size=200, span=64.0, cols=4, center=(0, 0),
          show=(), boxes=(), labels=None, model=None, partner=True, floor=True):
    """worlds: [(label, W)] or a list of W with labels."""
    nv = len(views)
    cols = min(cols, len(worlds))
    rows = math.ceil(len(worlds) / cols)
    img = Image.new('RGB', (cols * (size * nv + 8), rows * (size + 4)), (255, 255, 255))
    for i, (label, W) in enumerate(worlds):
        polys = figure_polys(W, show, model, partner)
        for b in boxes:
            polys += box_polys(*b)
        r, c = divmod(i, cols)
        for j, v in enumerate(views):
            im = draw(polys, v, size, center=center, span=span, label=(label if j == 0 else v), floor=floor)
            img.paste(im, (c * (size * nv + 8) + j * size, r * (size + 4)))
    img.save(out)
    print('wrote', out, img.size)
    return out


def anim_sheet(anim, times, out, lift=None, **kw):
    ws = []
    for t in times:
        W = anim_world(anim, t)
        if lift:
            W = lifted(W, lift(t))
        ws.append((f'{anim.name} {t:.2f}', W))
    return sheet(ws, out, **kw)


def model_sheet(model, name, times, out, lift=None, **kw):
    a = next(x for x in model.animations if kr.anim_name(x) == name)
    ch = model.channels(a)
    ws = []
    for t in times:
        W = kr.bb_pose(model, ch, t)
        if lift:
            W = lifted(W, lift(t))
        ws.append((f'{name} {t:.2f}', W))
    return sheet(ws, out, model=model, **kw)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('model')
    ap.add_argument('anim')
    ap.add_argument('out')
    ap.add_argument('--times')
    ap.add_argument('--n', type=int, default=8)
    ap.add_argument('--views', default='side,tq,front')
    ap.add_argument('--size', type=int, default=200)
    ap.add_argument('--span', type=float, default=64.0)
    ap.add_argument('--cols', type=int, default=4)
    a = ap.parse_args()
    m = kr.Model(a.model)
    an = next(x for x in m.animations if kr.anim_name(x) == a.anim)
    length = float(an.get('length', 1))
    times = [float(x) for x in a.times.split(',')] if a.times else [length * i / (a.n - 1) for i in range(a.n)]
    model_sheet(m, a.anim, times, a.out, views=a.views.split(','), size=a.size, span=a.span, cols=a.cols)


if __name__ == '__main__':
    main()
