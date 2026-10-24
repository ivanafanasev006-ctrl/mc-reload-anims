"""Mod list of a CurseForge modpack, without an API key (run by research.yml on a CI runner).

Finds the project and its newest client file through whichever keyless endpoint answers
(CFWidget, curse.tools, the CurseForge site API), downloads the pack, and names every mod in its
manifest by the jar its download link redirects to. Prints the list and the pack's overrides.
"""
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request
import zipfile

UA = os.environ.get("UA", "Mozilla/5.0")
SLUG = os.environ["PACK"]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def fetch(url, data=None, redirect=True, tries=4):
    body = json.dumps(data).encode() if data is not None else None
    headers = {"User-Agent": UA, "Accept": "application/json, */*"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    opener = urllib.request.build_opener() if redirect else urllib.request.build_opener(NoRedirect)
    for i in range(tries):
        try:
            with opener.open(urllib.request.Request(url, data=body, headers=headers), timeout=90) as r:
                return r.status, r.headers, r.read()
        except urllib.error.HTTPError as e:
            if not redirect and e.code in (301, 302, 303, 307, 308):
                return e.code, e.headers, b""
            if e.code in (202, 429, 500, 502, 503) and i < tries - 1:
                time.sleep(5 * (i + 1))
                continue
            return e.code, e.headers, e.read()
        except Exception as e:  # network hiccup
            if i == tries - 1:
                return 0, {}, str(e).encode()
            time.sleep(3)
    return 0, {}, b""


def fetch_json(url, data=None):
    code, _, body = fetch(url, data)
    try:
        return json.loads(body)
    except Exception:
        print(f"!! {url} -> {code}: {body[:200]!r}")
        return None


def find_pack():
    """(project id, [(file id, date, name, server pack?)])"""
    j = fetch_json(f"https://api.cfwidget.com/minecraft/modpacks/{SLUG}")
    if j and j.get("id"):
        files = [(f["id"], f.get("uploaded_at", ""), f.get("display") or f.get("name"), f.get("type")) for f in j.get("files", [])]
        print(f"cfwidget: project {j['id']} '{j.get('title')}', {len(files)} files")
        return j["id"], files
    j = fetch_json(f"https://api.curse.tools/v1/cf/mods/search?gameId=432&classId=4471&slug={SLUG}")
    if j and j.get("data"):
        pid = j["data"][0]["id"]
        fl = fetch_json(f"https://api.curse.tools/v1/cf/mods/{pid}/files?pageSize=50") or {}
        files = [(f["id"], f.get("fileDate", ""), f.get("displayName"), f.get("isServerPack")) for f in fl.get("data", [])]
        print(f"curse.tools: project {pid}, {len(files)} files")
        return pid, files
    j = fetch_json(f"https://www.curseforge.com/api/v1/mods/search?gameId=432&classId=4471&filterText={SLUG.split('-')[0]}&pageSize=20&sortField=2")
    if j and j.get("data"):
        for m in j["data"]:
            print("  site search:", m.get("id"), m.get("slug"), m.get("name"))
        hit = next((m for m in j["data"] if m.get("slug") == SLUG), j["data"][0])
        pid = hit["id"]
        fl = fetch_json(f"https://www.curseforge.com/api/v1/mods/{pid}/files?pageIndex=0&pageSize=50&sort=dateCreated&sortDescending=true") or {}
        files = [(f["id"], f.get("dateCreated", ""), f.get("displayName") or f.get("fileName"), f.get("isServerPack")) for f in fl.get("data", [])]
        print(f"site api: project {pid}, {len(files)} files")
        return pid, files
    sys.exit("no endpoint found the pack")


def jar_name(pid, fid):
    code, headers, _ = fetch(f"https://www.curseforge.com/api/v1/mods/{pid}/files/{fid}/download", redirect=False, tries=3)
    loc = headers.get("Location") if headers else None
    if not loc:
        return f"? ({code})"
    name = loc.split("?")[0].rsplit("/", 1)[-1]
    return urllib.request.unquote(name)


def main():
    pid, files = find_pack()
    for f in sorted(files, key=lambda f: str(f[1]), reverse=True)[:25]:
        print("  file", f)
    client = [f for f in files if not (f[3] is True or "server" in str(f[2]).lower())]
    fid = sorted(client, key=lambda f: str(f[1]))[-1][0]
    print(f"newest client file: {fid}")
    code, _, data = fetch(f"https://www.curseforge.com/api/v1/mods/{pid}/files/{fid}/download")
    if code != 200:
        sys.exit(f"pack download failed: {code}")
    z = zipfile.ZipFile(io.BytesIO(data))
    manifest = json.loads(z.read("manifest.json"))
    print(json.dumps({k: manifest.get(k) for k in ("name", "version", "author", "minecraft")}, indent=1))
    mods = manifest["files"]
    print(f"=== MODS ({len(mods)}) === projectID fileID required jar")
    rows = []
    for m in mods:
        rows.append((jar_name(m["projectID"], m["fileID"]), m["projectID"], m["fileID"], m.get("required", True)))
    for name, p, f, req in sorted(rows, key=lambda r: r[0].lower()):
        print(f"{p}\t{f}\t{'' if req else 'optional'}\t{name}")
    names = [n for n in z.namelist() if not n.endswith("/")]
    print(f"=== OVERRIDES ({len(names)} files) ===")
    dirs = sorted({"/".join(n.split("/")[:3]) for n in names})
    for d in dirs[:500]:
        print("  ", d)
    print("=== combat-related overrides ===")
    keys = ("bettercombat", "better_combat", "combat", "parry", "dodge", "stamina", "epicfight", "roll", "weapon", "kinetrix", "animation")
    shown = 0
    for n in names:
        low = n.lower()
        if any(k in low for k in keys) and not low.endswith((".png", ".ogg", ".jar")) and shown < 80:
            shown += 1
            print(f"----- {n}")
            print(z.read(n)[:5000].decode("utf-8", "replace"))


main()
