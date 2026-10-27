"""HUD textures of the combat mod, refined from the author's sketches (art/author/hud):

    python3 art/refined/hud/make_hud.py

writes, for every style (clock, medieval, steel), into art/refined/hud/<style>/:
    shield_full|half|empty.png        9x9, the heater shield of art/author/hud/shield.png
    round_full|half|empty.png         9x9, the round shield of art/author/hud/shield_round.png
    energy_icon.png, energy_icon_infinite.png       9x9 lightning
    energy_bar_bg.png, energy_bar_fill.png, energy_bar_fill_infinite.png   71x5
and art/refined/hud/preview.png: the styles on a mock vanilla HUD, 3x.

Vanilla HUD icons (hearts, armor, food) are 9x9 with a dark outline and light from the top left;
the author's sketches were 16x16, which would stand out of the vanilla rows, so the shapes are
redrawn at 9x9 with the same light. The energy row is 81 px wide like the food row: the 9 px icon,
1 px gap, a 71x5 bar (the experience bar's height), segmented like the author's reference.
"""
import math
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))

# outline, shade, base, light, highlight; empty: the dark inside of a spent icon
STYLES = {
    "clock": dict(o=(59, 38, 10), s=(140, 90, 18), b=(215, 161, 50), l=(242, 207, 94), h=(255, 242, 168),
                  empty=(46, 36, 22), bar_bg=(36, 28, 12), bolt=((80, 50, 8), (196, 140, 20), (250, 214, 60), (255, 246, 170))),
    "medieval": dict(o=(42, 21, 8), s=(110, 52, 16), b=(168, 85, 28), l=(208, 122, 44), h=(237, 166, 90),
                     empty=(40, 26, 18), bar_bg=(30, 18, 10), bolt=((60, 26, 6), (176, 92, 22), (232, 150, 52), (250, 206, 128))),
    "steel": dict(o=(30, 30, 30), s=(110, 110, 110), b=(180, 180, 180), l=(210, 210, 210), h=(238, 238, 238),
                  empty=(43, 43, 43), bar_bg=(26, 26, 30), bolt=((40, 40, 46), (150, 150, 160), (214, 214, 222), (250, 250, 255))),
}

# The heater shield of the author's sketch at 9x9: '#' outline, '.' empty; the rest is shaded by
# position (light from the top left like vanilla icons).
HEATER = [
    ".#######.",
    "#ooooooo#",
    "#ooooooo#",
    "#ooooooo#",
    "#ooooooo#",
    ".#ooooo#.",
    "..#ooo#..",
    "...#o#...",
    "....#....",
]

BOLT = [  # 1 outline, 2 shade, 3 base, 4 light
    ".....111.",
    "....1441.",
    "...1431..",
    "..14311..",
    ".1433331.",
    "..113321.",
    "...1321..",
    "..1321...",
    "..111....",
]


def round_mask():
    rows = []
    for y in range(9):
        row = ""
        for x in range(9):
            d = math.hypot(x - 4, y - 4)
            row += "o" if d <= 3.4 else ("#" if d <= 4.45 else ".")
        rows.append(row)
    return rows


def shade(c, rows, x, y):
    """Rim light as on vanilla icons and the author's sketches: a flat face, a lit rim where the
    outline is above or to the left, a shaded rim where it is below or to the right, the brightest
    pixels in the top-left corner."""
    def out(xx, yy):
        return not (0 <= yy < len(rows) and 0 <= xx < len(rows[yy])) or rows[yy][xx] != "o"
    top_left = out(x - 1, y) or out(x, y - 1)
    bottom_right = out(x + 1, y) or out(x, y + 1) or out(x + 1, y + 1)
    if top_left and (out(x - 1, y - 1) and x + y <= 4):
        return c["h"]
    if top_left and not bottom_right:
        return c["l"]
    if bottom_right and not top_left:
        return c["s"]
    if top_left and bottom_right:
        return c["l"] if x + y < 8 else c["s"]
    return c["b"]


def icon(c, rows, state, boss=False):
    """state: full, half (left half kept, like vanilla half hearts), empty."""
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                px[x, y] = c["o"] + (255,)
            elif ch == "o":
                filled = state == "full" or (state == "half" and x <= 4)
                if not filled:
                    px[x, y] = c["empty"] + (255,)
                    continue
                col = shade(c, rows, x, y)
                if boss:  # the round shield's boss, as in the author's sketch: a ring and a lit center
                    d = math.hypot(x - 4, y - 4)
                    if d < 0.5:
                        col = c["h"]
                    elif d < 1.6:
                        col = c["s"]
                px[x, y] = col + (255,)
    return img


def bolt(c, infinite=False):
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = img.load()
    pal = c["bolt"]
    for y, row in enumerate(BOLT):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            col = pal[int(ch) - 1]
            if infinite and ch in "34":
                col = tuple(min(255, v + 40) for v in col)
            px[x, y] = col + (255,)
    return img


def bar(c, kind, w=71, h=5):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for x in range(w):
        for y in range(h):
            edge = x in (0, w - 1) or y in (0, h - 1)
            if kind == "bg":
                px[x, y] = (c["o"] if edge else c["bar_bg"]) + (255,)
                continue
            if edge:
                px[x, y] = c["o"] + (255,)
                continue
            # three inner rows: light, base, shade; a notch every 10 px like the reference segments
            col = (c["l"], c["b"], c["s"])[y - 1]
            if x % 10 == 0:
                col = c["s"] if y < 3 else c["o"]
            if kind == "infinite":
                col = tuple(min(255, int(v * 0.6 + 255 * 0.4)) for v in col)
                if (x + y * 3) % 7 == 0:
                    col = (255, 255, 240)
            px[x, y] = col + (255,)
    return img


def build():
    out = {}
    for name, c in STYLES.items():
        d = os.path.join(HERE, name)
        os.makedirs(d, exist_ok=True)
        tex = {}
        for state in ("full", "half", "empty"):
            tex[f"shield_{state}"] = icon(c, HEATER, state)
            tex[f"round_{state}"] = icon(c, round_mask(), state, boss=True)
        tex["energy_icon"] = bolt(c)
        tex["energy_icon_infinite"] = bolt(c, True)
        tex["energy_bar_bg"] = bar(c, "bg")
        tex["energy_bar_fill"] = bar(c, "fill")
        tex["energy_bar_fill_infinite"] = bar(c, "infinite")
        for k, im in tex.items():
            im.save(os.path.join(d, k + ".png"))
        out[name] = tex
    preview(out)
    return out


# --- a mock vanilla HUD to see the textures in place (the vanilla parts are rough stand-ins) -----

HEART = [".##.##...", "#rr#rr#..", "#rrrrr#..", "#rrrrr#..", ".#rrr#...", "..#r#....", "...#.....", ".........", "........."]
FOOD = ["......##.", ".....#bb#", "....#bbb#", "...#bbb#.", "..#bbb#..", ".##b##...", "#w#.#....", "#w#......", ".#......."]
CHEST = [".##...##.", "#ss###ss#", "#sssssss#", ".#sssss#.", ".#sssss#.", ".#sssss#.", ".#######.", ".........", "........."]
ARROW = ["......##.", ".....#ww#", "....#ww#.", "...#bb#..", "..#bb#...", ".#bb#....", "##b#.....", "#f#......", ".#......."]


def glyph(rows, pal):
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y), pal[ch] + (255,))
    return img


def preview(styles):
    S = 3
    W, H = 300, 70
    heart = glyph(HEART, {"#": (40, 0, 0), "r": (220, 30, 30)})
    food = glyph(FOOD, {"#": (50, 25, 5), "b": (190, 110, 50), "w": (235, 225, 205)})
    chest = glyph(CHEST, {"#": (35, 35, 35), "s": (200, 200, 200)})
    arrow = glyph(ARROW, {"#": (30, 30, 30), "w": (220, 220, 220), "b": (130, 90, 50), "f": (240, 240, 240)})
    rows = []
    for name, tex in styles.items():
        for shape in ("shield", "round"):
            img = Image.new("RGBA", (W, H), (120, 150, 190, 255))
            d = ImageDraw.Draw(img)
            cx = W // 2
            hb_x, hb_y = cx - 91, H - 22
            d.rectangle([hb_x, hb_y, hb_x + 181, hb_y + 21], fill=(60, 60, 60, 255), outline=(20, 20, 20, 255))
            for i in range(9):
                d.rectangle([hb_x + 1 + i * 20, hb_y + 1, hb_x + 20 + i * 20, hb_y + 20], outline=(140, 140, 140, 255))
            d.rectangle([hb_x - 29, hb_y, hb_x - 8, hb_y + 21], fill=(60, 60, 60, 255), outline=(20, 20, 20, 255))
            # arrow counter: left of the offhand slot, count under the icon
            img.alpha_composite(arrow, (hb_x - 48, hb_y + 2))
            d.text((hb_x - 50, hb_y + 11), "64", fill=(255, 255, 255, 255))
            left, right = cx - 91, cx + 91
            for i in range(10):
                img.alpha_composite(heart, (left + i * 8, H - 39))
                img.alpha_composite(chest, (left + i * 8, H - 49))
                img.alpha_composite(food, (right - 9 - i * 8, H - 39))
            # shields: 6.5 of 10, the row above armor on the left
            for i in range(10):
                state = "full" if i < 6 else ("half" if i == 6 else "empty")
                img.alpha_composite(tex[f"{shape}_{state}"], (left + i * 8, H - 59))
            # energy: icon + bar above food on the right (81 px wide), 60 % full
            ex = right - 81
            img.alpha_composite(tex["energy_icon"], (ex, H - 49))
            img.alpha_composite(tex["energy_bar_bg"], (ex + 10, H - 47))
            fill = tex["energy_bar_fill"].crop((0, 0, int(71 * 0.6), 5))
            img.alpha_composite(fill, (ex + 10, H - 47))
            d.text((4, 3), f"{name} / {shape}", fill=(255, 255, 255, 255))
            rows.append(img.resize((W * S, H * S), Image.NEAREST))
    sheet = Image.new("RGBA", (W * S, H * S * len(rows)), (255, 255, 255, 255))
    for i, r in enumerate(rows):
        sheet.paste(r, (0, i * H * S))
    sheet.save(os.path.join(HERE, "preview.png"))


if __name__ == "__main__":
    build()
    print("wrote", HERE)
