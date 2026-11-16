"""The scrolls' item textures (16x16, vanilla style: light from the top left, a dark outline):

    python3 art/refined/items/make_scrolls.py

writes moves/src/main/resources/assets/kinetrix_combat/textures/item/{move,copper,bronze,brass}_scroll.png and a 8x preview
here. A parchment rolled at both ends, three lines of writing, a wax seal on a ribbon: turquoise for a move's scroll; red,
brown and yellow for the bar's ranks, copper, bronze and brass (author, 2026-11-16: "the other scrolls plain, turquoise
below, not red, brown and yellow like the rank scrolls"; those shine like enchanted things in the game).
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', '..', 'moves', 'src', 'main', 'resources', 'assets', 'kinetrix_combat', 'textures', 'item')

# '#' outline, rolls: 'R' dark 'r' base 'q' light; sheet: 'd' shade 'p' base 'l' light 'h' highlight; 'w' writing;
# seal: 'S' dark 'X' base 'x' light; ribbon 'b'
def shape():
    """The grid: two rolls (rows 2-3 and 12-13) and the sheet between them, the seal over the lower roll."""
    g = [['.'] * 16 for _ in range(16)]

    def roll(y):
        for x in range(2, 14):
            g[y - 1][x] = '#'
            g[y + 2][x] = '#'
        g[y][1] = g[y + 1][1] = g[y][14] = g[y + 1][14] = '#'
        for x in range(2, 14):
            g[y][x] = 'r' if x == 13 else 'q'
            g[y + 1][x] = 'R' if x == 13 else 'r'

    for y in range(4, 12):
        g[y][2] = g[y][13] = '#'
        for x in range(3, 13):
            g[y][x] = 'h' if (y == 4 and x < 6) else 'l' if x == 3 else 'd' if (x == 12 or y == 4) else 'p'
    for y, end in ((6, 11), (8, 10), (10, 9)):
        for x in range(4, end):
            g[y][x] = 'w'
    roll(2)
    roll(12)
    # the seal on its ribbon
    for x, y, ch in ((7, 11, 'b'), (8, 11, 'b'), (6, 12, 'S'), (7, 12, 'x'), (8, 12, 'X'), (9, 12, 'S'), (6, 13, 'S'), (7, 13, 'X'),
                     (8, 13, 'X'), (9, 13, 'S'), (7, 14, 'b'), (8, 14, 'b'), (7, 15, 'b'), (9, 15, 'b')):
        g[y][x] = ch
    return [''.join(r) for r in g]


SHAPE = shape()
PARCH = dict(o=(62, 40, 20), R=(120, 84, 46), r=(168, 126, 78), q=(206, 168, 112), d=(184, 150, 100), p=(222, 196, 150),
             l=(238, 220, 182), h=(250, 240, 214), w=(126, 96, 62))
SEALS = {
    'move': dict(S=(16, 78, 80), X=(34, 156, 148), x=(112, 222, 204), b=(24, 118, 114)),
    'copper': dict(S=(98, 18, 18), X=(170, 36, 32), x=(226, 94, 78), b=(150, 30, 28)),
    'bronze': dict(S=(86, 50, 18), X=(160, 100, 42), x=(214, 160, 92), b=(122, 74, 30)),
    'brass': dict(S=(110, 76, 14), X=(212, 164, 52), x=(250, 226, 130), b=(176, 128, 30)),
}


def draw(seal):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    pal = dict(PARCH, **seal)
    for y, row in enumerate(SHAPE):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            c = pal['o'] if ch == '#' else pal[ch]
            img.putpixel((x, y), c + (255,))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    sheet = Image.new('RGBA', (len(SEALS) * (128 + 8) - 8, 16 * 8), (120, 150, 190, 255))
    for i, (name, seal) in enumerate(SEALS.items()):
        im = draw(seal)
        im.save(os.path.join(OUT, f'{name}_scroll.png'))
        sheet.alpha_composite(im.resize((128, 128), Image.NEAREST), (i * (128 + 8), 0))
    sheet.save(os.path.join(HERE, 'scrolls_preview.png'))
    print('wrote', os.path.normpath(OUT))


if __name__ == '__main__':
    main()
