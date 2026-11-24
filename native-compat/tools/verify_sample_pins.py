#!/usr/bin/env python3
"""Hash pinned public blobs. Does not write script bytes, keys, or service requests."""
import base64, hashlib, json, urllib.request
PINS = [
    ("huibq/1.2.0.js", "f2028068db448a9e0b738106112eb300bcefbcc2", 2727, "a9adeb456690b523008038224091cee31977606302d79082eb06adb3c75c9146"),
    ("qingliang/2.1.0.js", "137716abd28c6ec3449ee661e686816f30545370", 26416, None),
]
TREE = "07dca9589e3734b140ec4ef396ee29dfe63687a2"

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "lx-native-gate", "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)

def main():
    for name, blob, size, expect in PINS:
        data = get(f"https://api.github.com/repos/pdone/lx-music-source/git/blobs/{blob}")
        raw = base64.b64decode(data["content"])
        digest = hashlib.sha256(raw).hexdigest()
        status = "MATCH" if expect in (None, digest) and len(raw) == size else "MISMATCH"
        print(f"{status} {name} bytes={len(raw)} sha256={digest} blob={blob}")
        del raw
    tree = get(f"https://api.github.com/repos/pdone/lx-music-source/git/trees/{TREE}?recursive=1")
    paths = [item["path"] for item in tree.get("tree", [])]
    top = sorted({p.split("/")[0] for p in paths})
    print("license_at_root", any(p in ("LICENSE", "LICENSE.md", "COPYING") for p in paths))
    print("top_level", ",".join(top))
    print("tree_entries", len(paths))
    print("content_reviewed=false execution=false")

if __name__ == "__main__":
    main()
