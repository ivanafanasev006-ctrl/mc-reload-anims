"""Compact summary of a mod jar (or a data/resource pack zip), for research.yml.

Prints mods.toml and mixin configs in full, the class packages with counts, the resource tree
folded by folder, a digest of Better Combat weapon attributes (parents, animations, templates in
full), other files matching FILES in full, and the classes matching CLASSES decompiled with CFR.
"""
import collections
import io
import json
import os
import re
import subprocess
import sys
import zipfile

path = sys.argv[1]
FILES = re.compile(os.environ.get("FILES") or r"META-INF/mods\.toml$|mixins\.json$")
CLASSES = os.environ.get("CLASSES") or ""
CFR = os.environ.get("CFR", "cfr.jar")


def show(name, data, limit=3500):
    text = data[:limit].decode("utf-8", "replace")
    print(f"----- {name}" + (f" (first {limit} of {len(data)} bytes)" if len(data) > limit else ""))
    print(text)


def summarize(z, label, depth=0):
    names = [n for n in z.namelist() if not n.endswith("/")]
    print(f"################ {label}: {len(names)} entries")
    classes = [n for n in names if n.endswith(".class")]
    other = [n for n in names if not n.endswith(".class")]

    for n in names:
        if n.endswith("mods.toml") or n.endswith("fabric.mod.json") or n.endswith("pack.mcmeta"):
            show(n, z.read(n), 6000)

    pk = collections.Counter(n.rsplit("/", 1)[0] for n in classes)
    print(f"=== {len(classes)} classes in {len(pk)} packages")
    for p, c in sorted(pk.items()):
        print(f"  {c:4d} {p}")

    folders = collections.defaultdict(list)
    for n in other:
        if n.endswith((".png", ".ogg", ".nbt")):
            folders[n.rsplit("/", 1)[0] + "/*" + os.path.splitext(n)[1]].append(n)
        else:
            folders[n.rsplit("/", 1)[0] if "/" in n else "."].append(n)
    print(f"=== {len(other)} resources in {len(folders)} folders")
    for f, items in sorted(folders.items()):
        if f.endswith("/*.png") or f.endswith("/*.ogg") or f.endswith("/*.nbt") or len(items) > 25:
            print(f"  {f}: {len(items)} files, e.g. {', '.join(i.rsplit('/', 1)[-1] for i in items[:6])}")
        else:
            print(f"  {f}: {', '.join(i.rsplit('/', 1)[-1] for i in items)}")

    for n in names:
        if n.endswith("mixins.json") or (n.endswith(".json") and "mixin" in n.lower() and "/" not in n):
            show(n, z.read(n), 5000)

    wa = [n for n in other if "/weapon_attributes/" in n and n.endswith(".json")]
    if wa:
        parents = collections.Counter()
        anims = collections.Counter()
        poses = collections.Counter()
        full = []
        for n in wa:
            try:
                j = json.loads(z.read(n))
            except Exception as e:
                print(f"  !! {n}: {e}")
                continue
            parents[j.get("parent")] += 1
            a = j.get("attributes") or {}
            if a.get("pose"):
                poses[a["pose"]] += 1
            for at in a.get("attacks") or []:
                if isinstance(at, dict) and at.get("animation"):
                    anims[at["animation"]] += 1
            if not j.get("parent") or "template" in n or a.get("attacks"):
                full.append(n)
            extra = set(a) - {"attack_range", "pose", "off_hand_pose", "two_handed", "category", "attacks"}
            if extra:
                print(f"  extra attribute keys in {n}: {sorted(extra)}")
        print(f"=== weapon_attributes: {len(wa)} files")
        print("  parents:", dict(parents.most_common(40)))
        print("  poses:", dict(poses.most_common(40)))
        print("  animations:", dict(anims.most_common(60)))
        for n in full[:25]:
            show(n, z.read(n), 2500)

    shown = 0
    for n in other:
        if FILES.search(n) and "/weapon_attributes/" not in n and not n.endswith("mods.toml") and not n.endswith("mixins.json") and shown < 60:
            shown += 1
            show(n, z.read(n))

    for n in names:
        if n.endswith(".jar") and depth < 1:
            try:
                summarize(zipfile.ZipFile(io.BytesIO(z.read(n))), f"{label} >> {n}", depth + 1)
            except Exception as e:
                print(f"  !! nested {n}: {e}")



def compact(z, label):
    """One screen per jar: who it is, what it hooks, what data it ships."""
    names = [n for n in z.namelist() if not n.endswith("/")]
    print(f"######## {label}: {len(names)} entries")
    for n in names:
        if n.endswith("META-INF/mods.toml"):
            t = z.read(n).decode("utf-8", "replace")
            keep = [l.strip() for l in t.splitlines()
                    if re.match(r"\s*(modId|displayName|version|authors|mandatory|license)\b", l)]
            print("  mods.toml:", " | ".join(keep))
            desc = re.search(r"description\s*=\s*(?:'''|\"\"\"|\"|')(.*?)(?:'''|\"\"\"|\"|')", t, re.S)
            if desc:
                print("  description:", " ".join(desc.group(1).split())[:400])
    classes = [n for n in names if n.endswith(".class")]
    top = collections.Counter("/".join(n.split("/")[:4]) for n in classes)
    print("  packages:", ", ".join(f"{p}({c})" for p, c in top.most_common(25)))
    for n in names:
        if n.endswith("mixins.json"):
            try:
                j = json.loads(z.read(n))
                mx = (j.get("mixins") or []) + (j.get("client") or []) + (j.get("server") or [])
                print(f"  {n}: {j.get('package')}: {', '.join(mx)}")
            except Exception as e:
                print(f"  {n}: {e}")
    folders = collections.Counter()
    for n in names:
        if n.endswith(".class"):
            continue
        parts = n.split("/")
        key = "/".join(parts[:3]) if parts[0] in ("data", "assets") else parts[0]
        folders[key] += 1
    print("  resources:", ", ".join(f"{f}({c})" for f, c in sorted(folders.items())))
    for kind in ("attack_animations", "player_animation", "player_animations"):
        an = [n.rsplit("/", 1)[-1] for n in names if f"/{kind}/" in n and n.endswith(".json")]
        if an:
            print(f"  {kind} ({len(an)}): {', '.join(sorted(an))[:1500]}")
    wa = [n for n in names if "/weapon_attributes/" in n and n.endswith(".json")]
    if wa:
        parents, anims, cats, extra = collections.Counter(), collections.Counter(), collections.Counter(), set()
        for n in wa:
            try:
                j = json.loads(z.read(n))
            except Exception:
                continue
            parents[j.get("parent")] += 1
            a = j.get("attributes") or {}
            cats[a.get("category")] += 1
            extra |= set(a) - {"attack_range", "pose", "off_hand_pose", "two_handed", "category", "attacks"}
            for at in a.get("attacks") or []:
                if isinstance(at, dict):
                    anims[at.get("animation")] += 1
                    extra |= {"attack." + k for k in set(at) - {"conditions", "hitbox", "damage_multiplier", "angle",
                                                               "upswing", "animation", "swing_sound", "impact_sound"}}
        ns = collections.Counter(n.split("/")[1] for n in wa)
        print(f"  weapon_attributes: {len(wa)} files, namespaces {dict(ns)}")
        print(f"    parents: {dict(parents.most_common(15))}")
        print(f"    categories: {dict(cats.most_common(15))}")
        print(f"    animations: {len(anims)} distinct, e.g. {[a for a, _ in anims.most_common(12)]}")
        if extra:
            print(f"    NON-STANDARD keys: {sorted(extra)}")
    other = [n for n in names if FILES.search(n) and not n.endswith(("mods.toml", "mixins.json"))][:12]
    for n in other:
        show(n, z.read(n), 1500)
    for n in names:
        if n.endswith(".jar") and "mixinextras" not in n.lower() and "mixinsquared" not in n.lower():
            try:
                compact(zipfile.ZipFile(io.BytesIO(z.read(n))), f"{label} >> {n}")
            except Exception as e:
                print(f"  !! nested {n}: {e}")


def decompile(z):
    pat = re.compile(CLASSES)
    picked = [n for n in z.namelist() if n.endswith(".class") and pat.search(n) and "$" not in n][:25]
    os.makedirs("cls", exist_ok=True)
    for n in picked:
        out = os.path.join("cls", n)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "wb") as f:
            f.write(z.read(n))
        src = subprocess.run(["java", "-jar", CFR, out, "--silent", "true"], capture_output=True, text=True).stdout
        lines = [l for l in src.splitlines() if l.strip() and not l.startswith("import ")]
        print(f"===== DECOMPILED {n} ({len(lines)} lines)")
        print("\n".join(lines[:300]))


jar = zipfile.ZipFile(path)
if os.environ.get("VERBOSE"):
    summarize(jar, os.path.basename(path))
else:
    compact(jar, os.path.basename(path))
if CLASSES:
    decompile(jar)
