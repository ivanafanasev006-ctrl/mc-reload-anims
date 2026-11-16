"""The trainer villager's clothes (author, 2026-11-16: "all in brown, as the wandering trader's but recoloured brown, dark and
light shades: a training uniform"): the profession overlay of vanilla's villager model, 64x64, drawn from scratch on the
model's own layout (no vanilla pixels copied):

    python3 art/refined/trainer/make_trainer.py

writes moves/src/main/resources/assets/kinetrix_combat/textures/entity/{villager,zombie_villager}/profession/trainer.png
and a preview (the faces unfolded, 6x) here. A brown robe of rough cloth to the shins, a dark belt with a bronze buckle,
lighter cuffs and seams, a dark headband.
"""
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', '..', '..', 'moves', 'src', 'main', 'resources', 'assets', 'kinetrix_combat', 'textures', 'entity')

DARK, SHADE, BASE, LIGHT, HIGH = (58, 38, 22), (88, 60, 36), (118, 82, 50), (146, 106, 68), (172, 132, 90)
BELT, BUCKLE, BUCKLE_HI = (44, 28, 16), (176, 120, 56), (226, 176, 98)


def box(u, v, w, h, d):
    """The faces of a box at (u, v) as rectangles: top, bottom, right, front, left, back (x0, y0, x1, y1)."""
    return {'top': (u + d, v, u + d + w, v + d), 'bottom': (u + d + w, v, u + d + 2 * w, v + d),
            'right': (u, v + d, u + d, v + d + h), 'front': (u + d, v + d, u + d + w, v + d + h),
            'left': (u + d + w, v + d, u + 2 * d + w, v + d + h), 'back': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


def cloth(img, rect, rnd, light_side):
    """Rough cloth: the base with a little noise, darker on the side away from the light (vanilla's: top left)."""
    x0, y0, x1, y1 = rect
    for y in range(y0, y1):
        for x in range(x0, x1):
            r = rnd.random()
            c = SHADE if r < 0.18 else LIGHT if r > 0.9 else BASE
            if not light_side and r < 0.45:
                c = SHADE if c != LIGHT else BASE
            img.putpixel((x, y), c + (255,))


def row(img, rect, y, c):
    x0, _, x1, _ = rect
    for x in range(x0, x1):
        img.putpixel((x, y), c + (255,))


def main():
    rnd = random.Random(16112026)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    body, robe = box(16, 20, 8, 12, 6), box(0, 38, 8, 20, 6)
    arms, arms_mid, leg = box(44, 22, 4, 8, 4), box(40, 38, 8, 4, 4), box(0, 22, 4, 12, 4)
    for part in (body, robe, arms, arms_mid, leg):
        for name, rect in part.items():
            cloth(img, rect, rnd, name in ('top', 'front', 'right'))
    # the robe's hem and its front seam, the cuffs (the arms' ends: the folded arms' middle piece is the hands' row)
    for name in ('right', 'front', 'left', 'back'):
        x0, y0, x1, y1 = robe[name]
        row(img, robe[name], y1 - 1, DARK)
        row(img, robe[name], y1 - 2, SHADE)
    fx0, fy0, fx1, fy1 = robe['front']
    for y in range(fy0, fy1 - 2):
        img.putpixel(((fx0 + fx1) // 2, y), HIGH + (255,))
    for name in ('right', 'front', 'left', 'back'):
        x0, y0, x1, y1 = arms[name]
        row(img, arms[name], y1 - 1, LIGHT)
        row(img, arms[name], y1 - 2, HIGH)
    # the belt, round the body and the robe at the waist, the buckle in front
    for part, wy in ((body, 9), (robe, 9)):
        for name in ('right', 'front', 'left', 'back'):
            x0, y0, x1, y1 = part[name]
            row(img, part[name], y0 + wy, BELT)
            row(img, part[name], y0 + wy + 1, BELT)
        x0, y0, x1, y1 = part['front']
        cx = (x0 + x1) // 2
        for dx, dy, c in ((-1, 0, BUCKLE), (0, 0, BUCKLE_HI), (-1, 1, BUCKLE), (0, 1, BUCKLE)):
            img.putpixel((cx + dx, y0 + wy + dy), c + (255,))
    # the headband round the hat layer, at the forehead
    hat = box(32, 0, 8, 10, 8)
    for name in ('right', 'front', 'left', 'back'):
        x0, y0, x1, y1 = hat[name]
        row(img, hat[name], y0 + 2, DARK)
        row(img, hat[name], y0 + 3, SHADE)
    for sub in ('villager', 'zombie_villager'):
        d = os.path.join(ASSETS, sub, 'profession')
        os.makedirs(d, exist_ok=True)
        img.save(os.path.join(d, 'trainer.png'))
    bg = Image.new('RGBA', (64, 64), (200, 200, 200, 255))
    bg.alpha_composite(img)
    bg.resize((384, 384), Image.NEAREST).save(os.path.join(HERE, 'trainer_preview.png'))
    print('wrote', os.path.normpath(ASSETS))


if __name__ == '__main__':
    main()
