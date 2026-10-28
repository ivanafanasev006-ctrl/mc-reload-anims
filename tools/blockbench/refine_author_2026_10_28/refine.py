"""Refined animations from the author's set of 2026-10-28 (CLAUDE.md: the author's work is a
framework; keep the idea and the key poses, perfect the execution).

    python3 tools/blockbench/refine_author_2026_10_28/refine.py

reads  tools/blockbench/animations/author_2026-10-28.bbmodel  (never changed)
writes tools/blockbench/refined/author_2026-10-28.bbmodel     (the author's model, only the new animations)

The author's new bow_barrage (2.875 s, four parts) becomes bow_barrage_combo (10 s: each part
longer, a shot and a pause after it) and the four parts on their own. The bow combat flow and the
bow strikes are new. bow_barrage keeps its name for the volley the mod plays
(refined/author_2026-10-27.bbmodel): the mechanic is built on its timing. What each move is and
which key drives which part of the mechanic: docs/moves.md.

How the keys are made (animlib.py): every bone gets target poses at times, a monotone cubic runs
through them (no overshoot), and keys on the 1/24 s grid are kept only where Blockbench's
catmullrom would otherwise leave that curve. Poses: poses.py (the author's poses with feet and
knees put on the ground, the hand thrown back after the release), moves: design.py.
"""
import json
import os

import animlib as L
import design

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(HERE, "animations", "author_2026-10-28.bbmodel")
DST = os.path.join(HERE, "refined", "author_2026-10-28.bbmodel")


def set_visible(model, group_name, visible):
    """A group and its cubes shown or hidden (what Blockbench's eye in the Outliner does)."""
    g = next(g for g in model["groups"] if g["name"] == group_name)
    g["visibility"] = visible

    def find(items):
        for it in items:
            if isinstance(it, dict):
                if it.get("uuid") == g["uuid"]:
                    return it
                hit = find(it.get("children", []))
                if hit:
                    return hit

    cubes = {c for c in find(model["outliner"]).get("children", []) if isinstance(c, str)}
    for e in model["elements"]:
        if e["uuid"] in cubes:
            e["visibility"] = visible


def main():
    with open(SRC, encoding="utf-8") as f:
        model = json.load(f)
    uuids = L.bone_uuids(model)
    model["animations"] = [a.to_json(uuids) for a in design.all_anims()]
    model["animations"][0]["selected"] = True
    # Every animation here is held with the bow: show it, hide the sword of the same hand.
    set_visible(model, "right_item_bow", True)
    set_visible(model, "right_item_sword", False)
    with open(DST, "w", encoding="utf-8") as f:
        json.dump(model, f, separators=(",", ":"))
    print(f"wrote {DST}: {len(model['animations'])} animations")


if __name__ == "__main__":
    main()
