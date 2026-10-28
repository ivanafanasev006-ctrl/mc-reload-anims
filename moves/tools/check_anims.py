"""Do the animations in the mod match the refined Blockbench file? (CI, .github/workflows/moves.yml)

    python3 moves/tools/check_anims.py <freshly converted dir> moves/src/main/resources/assets/kinetrix_moves/player_animation

Numbers may differ in the last digit between numpy builds; anything above 1e-4 is a real change:
convert again and commit (see moves/README.md).
"""
import json
import os
import sys


def same(a, b, path, out):
    if isinstance(a, dict) and isinstance(b, dict):
        if a.keys() != b.keys():
            out.append(f"{path}: keys {sorted(a.keys() ^ b.keys())}")
            return
        for k in a:
            same(a[k], b[k], f"{path}.{k}", out)
    elif isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            out.append(f"{path}: {len(a)} entries, expected {len(b)}")
            return
        for i, (x, y) in enumerate(zip(a, b)):
            same(x, y, f"{path}[{i}]", out)
    elif isinstance(a, (int, float)) and isinstance(b, (int, float)) and not isinstance(a, bool):
        if abs(a - b) > 1e-4:
            out.append(f"{path}: {a} != {b}")
    elif a != b:
        out.append(f"{path}: {a!r} != {b!r}")


def main(fresh, kept):
    names = sorted(set(os.listdir(fresh)) | set(os.listdir(kept)))
    bad = []
    for n in names:
        fa, fb = os.path.join(fresh, n), os.path.join(kept, n)
        if not os.path.exists(fa) or not os.path.exists(fb):
            bad.append(f"{n}: only in {'the mod' if os.path.exists(fb) else 'the Blockbench file'}")
            continue
        diffs = []
        same(json.load(open(fa)), json.load(open(fb)), n, diffs)
        bad += diffs[:5]
    for line in bad:
        print(line)
    print(f"{len(names)} animations, {'all match' if not bad else 'DIFFERENT'}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
