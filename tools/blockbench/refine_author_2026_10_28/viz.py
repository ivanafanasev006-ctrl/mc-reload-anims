"""Pictures of poses and animations for checking (same forward kinematics as Blockbench)."""
import math
import os

import numpy as np
from PIL import Image, ImageDraw

import animlib as L
import kinetrix_rig as kr

#: The author's file the poses come from (never changed).
MODEL_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'animations',
                          'author_2026-10-28.bbmodel')
_model = None


def model():
    global _model
    if _model is None:
        _model = kr.Model(MODEL_PATH)
    return _model


CAMS = {
    'front': lambda p: (-p[0], p[1], p[2]),
    'back': lambda p: (p[0], p[1], -p[2]),
    'side': lambda p: (-p[2], p[1], -p[0]),       # from the player's right
    'lside': lambda p: (p[2], p[1], p[0]),        # from the player's left
    'top': lambda p: (p[0], -p[2], -p[1]),
}


def cam34(deg):
    a = math.radians(deg)
    return lambda p: (-(p[0] * math.cos(a) + p[2] * math.sin(a)), p[1], -p[0] * math.sin(a) + p[2] * math.cos(a))


CAMS['tq'] = cam34(35)
CAMS['tql'] = cam34(-35)
CAMS['rear'] = cam34(160)


def polys(m, W, show):
    out = []
    owner = {}
    for u, g in m.groups.items():
        for c in g['children']:
            owner[c] = g['name']
    texs = {i: kr.texture_image(m, i) for i in range(len(m.data.get('textures', [])))}
    for e in m.data['elements']:
        bone = owner.get(e['uuid'])
        if bone is None or bone not in W:
            continue
        if bone.endswith('2') and bone not in show:
            continue
        if bone in ('right_item_sword', 'right_item_bow', 'left_item_sword') and bone not in show:
            continue
        o = m.groups[m.by_name[bone]]['origin']
        infl = e.get('inflate', 0.0)
        lo = np.array(e['from'], float) - infl
        hi = np.array(e['to'], float) + infl
        crot = np.radians(np.array(e.get('rotation', [0, 0, 0]), float))
        corig = np.array(e.get('origin', o), float)
        for fname, corners in kr.FACE_CORNERS.items():
            face = e['faces'].get(fname)
            if not face or face.get('texture') is None:
                continue
            col = kr.face_color(texs.get(face['texture']), face['uv'])
            if col[3] < 40:
                continue
            pts = []
            for c in corners:
                p = lo + (hi - lo) * np.array(c, float)
                p = corig + kr.rot_zyx(*crot) @ (p - corig)
                pts.append(kr.apply(W[bone], p - o))
            out.append((pts, col))
    return out


def draw(m, W, view, size, show, center=(0, 0), span=44.0, label=None):
    img = Image.new('RGB', (size, size), (238, 238, 232))
    d = ImageDraw.Draw(img)
    cam = CAMS[view]
    scale = size / span
    ox, oy = size / 2 - center[0] * scale, size * 0.88 + center[1] * scale

    def scr(p):
        qq = cam(p)
        return (ox + qq[0] * scale, oy - qq[1] * scale), qq[2]
    if view != 'top':
        d.line([(0, oy), (size, oy)], fill=(170, 170, 160), width=1)
    drawn = []
    for pts, col in polys(m, W, show):
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
    if label:
        d.text((3, 2), label, fill=(0, 0, 0))
    return img


def sheet(cells, out, views=('tq', 'side', 'front'), size=200, cols=None, show=('right_item_bow',), span=44.0):
    """cells: list of (label, W)."""
    m = model()
    nv = len(views)
    cols = cols or len(cells)
    rows = math.ceil(len(cells) / cols)
    img = Image.new('RGB', (cols * (size * nv + 8), rows * (size + 4)), (255, 255, 255))
    for i, (label, W) in enumerate(cells):
        r, c = divmod(i, cols)
        for j, v in enumerate(views):
            im = draw(m, W, v, size, set(show), span=span, label=(label if j == 0 else v))
            img.paste(im, (c * (size * nv + 8) + j * size, r * (size + 4)))
    img.save(out)
    print('wrote', out, img.size)


def pose_world(pose):
    a = L.Anim('tmp', 1)
    a.key(0, pose, whole=True)
    return L.world(model(), a, 0)


def poses(items, out, **kw):
    sheet([(lab, pose_world(p)) for lab, p in items], out, **kw)


def anim_sheet(anim, times, out, **kw):
    m = model()
    sheet([(f'{anim.name} {t:.2f}', L.world(m, anim, t)) for t in times], out, **kw)
