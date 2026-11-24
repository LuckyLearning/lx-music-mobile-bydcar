#!/usr/bin/env python3
"""Pinned public build tools only; no credentials, source execution or installers."""
import concurrent.futures, hashlib, io, json, os, pathlib, tarfile, urllib.request
ROOT=pathlib.Path(os.environ.get("LX_NATIVE_TOOLS", "/workspace/lx-native-tools"))
ROOT.mkdir(parents=True, exist_ok=True)
KOTLIN=[("org.jetbrains.kotlin","kotlin-compiler-embeddable","2.1.20"),("org.jetbrains.kotlin","kotlin-stdlib","2.1.20"),("org.jetbrains.kotlin","kotlin-script-runtime","2.1.20"),("org.jetbrains.kotlin","kotlin-reflect","1.6.10"),("org.jetbrains.kotlin","kotlin-daemon-embeddable","2.1.20"),("org.jetbrains.intellij.deps","trove4j","1.0.20200330"),("org.jetbrains.kotlinx","kotlinx-coroutines-core-jvm","1.8.0"),("org.jetbrains","annotations","13.0")]
EXPECTED={
 "org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.20":"c54a00718c8c0e3ee858bf42771c7f401c5e3c1738861e18e9536371042fa1b3",
 "org.jetbrains.kotlin:kotlin-stdlib:2.1.20":"1bcc74e8ce84e2c25eaafde10f1248349cce3062b6e36978cbeec610db1e930a",
 "org.jetbrains.kotlin:kotlin-script-runtime:2.1.20":"ae4397fbb3aa2a1ada09290e753bba99a4114548977c3115526b9958f4cb1a04",
 "org.jetbrains.kotlin:kotlin-reflect:1.6.10":"3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203",
 "org.jetbrains.kotlin:kotlin-daemon-embeddable:2.1.20":"d9e83df1d847a201ba3c016a786ced091be9503997d09f6a9cf1796ee489f374",
 "org.jetbrains.intellij.deps:trove4j:1.0.20200330":"c5fd725bffab51846bf3c77db1383c60aaaebfe1b7fe2f00d23fe1b7df0a439d",
 "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0":"9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5",
 "org.jetbrains:annotations:13.0":"ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478",
}
def get(item):
 g,a,v=item; dest=ROOT/"kotlin"/f"{a}-{v}.jar"; dest.parent.mkdir(exist_ok=True)
 url=f"https://repo.maven.apache.org/maven2/{g.replace('.', '/')}/{a}/{v}/{a}-{v}.jar"
 coord=f"{g}:{a}:{v}"
 if dest.exists() and hashlib.sha256(dest.read_bytes()).hexdigest()!=EXPECTED[coord]:
  dest.unlink()
 if not dest.exists():
  with urllib.request.urlopen(url,timeout=90) as r: dest.write_bytes(r.read())
 digest=hashlib.sha256(dest.read_bytes()).hexdigest()
 if digest!=EXPECTED[coord]:
  raise SystemExit(f"hash mismatch for {coord}")
 return {"coordinate":coord,"url":url,"sha256":digest}
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as ex: records=list(ex.map(get,KOTLIN))
q=ROOT/"quickjs-2026-06-04.tar.xz"; url="https://bellard.org/quickjs/quickjs-2026-06-04.tar.xz"
if not q.exists(): q.write_bytes(urllib.request.urlopen(url,timeout=60).read())
expected="b376e839b322978313d929fd20663b11ba58b75df5a46c126dd19ea2fa70ad2a"
assert hashlib.sha256(q.read_bytes()).hexdigest()==expected, "QuickJS archive mismatch"
if not (ROOT/"quickjs-2026-06-04").exists(): tarfile.open(q).extractall(ROOT,filter="data")
records.append({"coordinate":"QuickJS:2026-06-04","url":url,"sha256":expected})
(ROOT/"download-manifest.json").write_text(json.dumps(records,indent=2)+"\n")
print("Pinned tool artifacts ready:",len(records),"(hashes are integrity pins, not independent signatures)")
