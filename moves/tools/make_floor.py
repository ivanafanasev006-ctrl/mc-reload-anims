"""The GameTest structure: a 7x4x7 room, a stone floor and air above (src/main/resources/data/
kinetrix_moves/structures/floor.nbt). Written by hand as NBT so no other tool is needed:

    python3 moves/tools/make_floor.py
"""
import gzip
import os
import struct

W, H, D = 7, 4, 7
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "data", "kinetrix_moves", "structures", "floor.nbt")

END, INT, STRING, LIST, COMPOUND = 0, 3, 8, 9, 10


def name(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def payload(tag, value):
    if tag == INT:
        return struct.pack(">i", value)
    if tag == STRING:
        return name(value)
    if tag == LIST:
        inner, items = value
        out = struct.pack(">bi", inner if items else END, len(items))
        return out + b"".join(payload(inner, v) for v in items)
    if tag == COMPOUND:
        out = b""
        for key, (t, v) in value.items():
            out += struct.pack(">b", t) + name(key) + payload(t, v)
        return out + struct.pack(">b", END)
    raise ValueError(tag)


def main():
    blocks = []
    for y in range(H):
        for z in range(D):
            for x in range(W):
                blocks.append({"pos": (LIST, (INT, [x, y, z])), "state": (INT, 0 if y == 0 else 1)})
    root = {
        "DataVersion": (INT, 3465),  # 1.20.1
        "size": (LIST, (INT, [W, H, D])),
        "palette": (LIST, (COMPOUND, [{"Name": (STRING, "minecraft:smooth_stone")}, {"Name": (STRING, "minecraft:air")}])),
        "blocks": (LIST, (COMPOUND, blocks)),
        "entities": (LIST, (COMPOUND, [])),
    }
    data = struct.pack(">b", COMPOUND) + name("") + payload(COMPOUND, root)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as f:
        f.write(data)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
