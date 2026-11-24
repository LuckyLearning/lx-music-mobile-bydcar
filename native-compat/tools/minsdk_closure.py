#!/usr/bin/env python3
"""Resolve a candidate Android dependency graph and read each AAR minSdk.

Static manifests only. This does not compile an app or prove runtime behavior.
"""
import json, os, pathlib, struct, urllib.request, zipfile, io, sys
CACHE = pathlib.Path(os.environ.get("LX_MAVEN_CACHE", "native-compat/.local/maven"))
CACHE.mkdir(parents=True, exist_ok=True)
ROOTS = [
    "androidx.compose.ui:ui:1.9.5",
    "androidx.compose.foundation:foundation:1.9.5",
    "androidx.compose.runtime:runtime:1.9.5",
    "androidx.activity:activity-compose:1.10.1",
    "androidx.media3:media3-exoplayer:1.8.0",
    "androidx.media3:media3-session:1.8.0",
    "androidx.media3:media3-common:1.8.0",
    "androidx.room:room-runtime:2.7.2",
    "androidx.datastore:datastore-preferences:1.1.7",
]
HOSTS = [
    "https://dl.google.com/dl/android/maven2",
    "https://repo1.maven.org/maven2",
]

def fetch(url):
    dest = CACHE / url.split("://", 1)[1]
    if dest.exists() and dest.stat().st_size > 0:
        return dest.read_bytes()
    try:
        with urllib.request.urlopen(url, timeout=60) as r:
            data = r.read()
    except Exception:
        return None
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    return data

def artifact_bytes(coord, ext):
    g, a, v = coord.split(":")
    path = f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.{ext}"
    for host in HOSTS:
        data = fetch(f"{host}/{path}")
        if data:
            return data
    return None

def u16(b, o):
    return struct.unpack_from("<H", b, o)[0]
def u32(b, o):
    return struct.unpack_from("<I", b, o)[0]

def pool_strings(buf, start):
    hsize, size = u16(buf, start + 2), u32(buf, start + 4)
    count, flags = u32(buf, start + 8), u32(buf, start + 16)
    strings_at = start + u32(buf, start + 20)
    offsets = [u32(buf, start + hsize + 4 * i) for i in range(count)]
    def take_len(p):
        b = buf[p]
        if b & 0x80:
            return ((b & 0x7F) << 8) | buf[p + 1], 2
        return b, 1
    out = []
    utf8 = flags & 0x100
    for rel in offsets:
        p = strings_at + rel
        if utf8:
            _, s1 = take_len(p)
            ln, s2 = take_len(p + s1)
            p += s1 + s2
            out.append(buf[p:p + ln].decode("utf-8", "replace"))
        else:
            n = u16(buf, p)
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | u16(buf, p)
                p += 2
            out.append(buf[p:p + n * 2].decode("utf-16-le", "replace"))
    return out

def manifest_minsdk(aar):
    try:
        z = zipfile.ZipFile(io.BytesIO(aar))
    except Exception:
        return "not-a-zip"
    name = next((n for n in z.namelist() if n == "AndroidManifest.xml" or n.endswith("/AndroidManifest.xml")), None)
    if not name:
        return "no-manifest"
    buf = z.read(name)
    if buf.lstrip().startswith(b"<?xml") or buf.lstrip().startswith(b"<manifest"):
        import re
        text = buf.decode("utf-8", "replace")
        m = re.search(r"android:minSdkVersion=\"(\d+)\"", text)
        return int(m.group(1)) if m else "minsdk-not-found"
    if buf[:2] != b"\x03\x00":
        return "not-binary-xml"
    pos, strings, end = 0, [], len(buf)
    found = None
    while pos + 8 <= end:
        typ, hsize, size = u16(buf, pos), u16(buf, pos + 2), u32(buf, pos + 4)
        if size < 8 or pos + size > end:
            break
        if typ == 0x0001 and not strings:
            try:
                strings = pool_strings(buf, pos)
            except Exception:
                strings = []
        if typ == 0x0102 and strings:
            try:
                ext = pos + hsize
                attr_start, attr_size, attr_count = u16(buf, ext + 8), u16(buf, ext + 10), u16(buf, ext + 12)
                for i in range(attr_count):
                    at = pos + attr_start + i * attr_size
                    name_idx = u32(buf, at + 4)
                    dtype = buf[at + 15]
                    data = u32(buf, at + 16)
                    name = strings[name_idx] if name_idx < len(strings) else ""
                    if name in ("minSdkVersion", "android:minSdkVersion") and dtype in (0x10, 0x11):
                        found = data
            except Exception:
                pass
        pos += size
    return found if found is not None else "minsdk-not-found"

def choose_variant(data):
    variants = data.get("variants", [])
    for wanted in ("releaseRuntimeElements-published", "releaseRuntimeElements", "runtimeElements"):
        for variant in variants:
            if variant.get("name") == wanted:
                return variant
    for variant in variants:
        name = variant.get("name", "").lower()
        if "runtime" in name and "debug" not in name and not any(token in name for token in ("ios", "linux", "macos", "wasm", "js", "mingw", "watch", "tvos", "native")):
            return variant
    return None

def deps_of(coord):
    module = artifact_bytes(coord, "module")
    found = []
    if module:
        try:
            data = json.loads(module)
        except Exception:
            data = {}
        variant = choose_variant(data)
        if variant:
            constraints = {}
            for item in variant.get("dependencyConstraints", []):
                ver = item.get("version", {})
                if item.get("group") and item.get("module") and isinstance(ver, dict) and ver.get("requires"):
                    constraints[f"{item['group']}:{item['module']}"] = ver["requires"]
            avail = variant.get("available-at")
            if isinstance(avail, dict) and {"group", "module", "version"} <= set(avail):
                found.append(f"{avail['group']}:{avail['module']}:{avail['version']}")
            for dep in variant.get("dependencies", []):
                ver = dep.get("version", {})
                requires = ver.get("requires") if isinstance(ver, dict) else None
                key = f"{dep.get('group')}:{dep.get('module')}"
                if not requires:
                    requires = constraints.get(key)
                if dep.get("group") and dep.get("module") and requires and not str(requires).startswith("["):
                    found.append(f"{dep['group']}:{dep['module']}:{requires}")
                elif dep.get("group") and dep.get("module"):
                    found.append(f"UNRESOLVED:{dep['group']}:{dep['module']}")
    if found:
        return found
    pom = artifact_bytes(coord, "pom")
    if not pom:
        return []
    text = pom.decode("utf-8", "replace")
    # Small extractor for direct Maven dependencies. Properties and BOMs are reported as unresolved.
    import re
    for block in re.findall(r"<dependency>(.*?)</dependency>", text, re.S):
        if "<optional>true</optional>" in block or "<scope>test</scope>" in block or "<scope>provided</scope>" in block:
            continue
        g = re.search(r"<groupId>([^<]+)</groupId>", block)
        a = re.search(r"<artifactId>([^<]+)</artifactId>", block)
        v = re.search(r"<version>([^<]+)</version>", block)
        if g and a and v and not v.group(1).startswith("${"):
            found.append(f"{g.group(1)}:{a.group(1)}:{v.group(1)}")
        elif g and a:
            found.append(f"UNRESOLVED:{g.group(1)}:{a.group(1)}")
    return found

def main():
    queue, seen, rows, unresolved = list(ROOTS), set(), [], []
    while queue and len(seen) < 400:
        coord = queue.pop(0)
        if coord in seen:
            continue
        seen.add(coord)
        if coord.startswith("UNRESOLVED:"):
            unresolved.append(coord)
            continue
        aar = artifact_bytes(coord, "aar")
        if aar:
            rows.append((coord, "aar", manifest_minsdk(aar)))
        else:
            jar = artifact_bytes(coord, "jar")
            rows.append((coord, "jar" if jar else "missing", "n/a" if jar else "missing"))
        for dep in deps_of(coord):
            if dep not in seen:
                queue.append(dep)
    above = [r for r in rows if isinstance(r[2], int) and r[2] > 21]
    numeric = [r[2] for r in rows if isinstance(r[2], int)]
    unknown = [r for r in rows if r[2] in ("minsdk-not-found", "not-binary-xml", "missing", "not-a-zip", "no-manifest")]
    print(f"artifacts={len(rows)} aars_with_minsdk={len(numeric)} max_minsdk={max(numeric) if numeric else 'none'} above21={len(above)} unknown={len(unknown)} unresolved={len(unresolved)}")
    for coord, kind, sdk in rows:
        print(f"{sdk}\t{kind}\t{coord}")
    if above:
        print("ABOVE21")
        for row in above:
            print("\t".join(map(str, row)))
    if unresolved:
        print("UNRESOLVED")
        for item in unresolved:
            print(item)
    return 0

if __name__ == "__main__":
    sys.exit(main())
