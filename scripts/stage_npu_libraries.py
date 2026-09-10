"""Stage source-built dispatch libraries and a pinned official Qualcomm AAR for the APK."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "third_party/npu"
QNN_SHA256 = "de56e38f90441eaf9cbb2d4f929968894f4a02b1c3717d4741e12d3a4ea1ff9e"
QNN_URL = "https://repo.maven.apache.org/maven2/com/qualcomm/qti/qnn-runtime/2.47.0/qnn-runtime-2.47.0.aar"


def stage(vendors: Path):
    cache = BASE / "cache"
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / "qnn-runtime-2.47.0.aar"
    if not archive.exists():
        urllib.request.urlretrieve(QNN_URL, archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != QNN_SHA256:
        raise ValueError("Qualcomm runtime checksum mismatch")
    destination = BASE / "jni/arm64-v8a"
    destination.mkdir(parents=True, exist_ok=True)
    for vendor, name in [("google_tensor", "GoogleTensor"), ("mediatek", "MediaTek"), ("qualcomm", "Qualcomm")]:
        shutil.copyfile(vendors / vendor / "dispatch" / f"libLiteRtDispatch_{name}.so", destination / f"libnpu_dispatch_{vendor}.so")
    wanted = {"libQnnHtp.so", "libQnnSystem.so", "libQnnHtpPrepare.so"}
    wanted |= {f"libQnnHtpV{v}{kind}.so" for v in [73, 75, 79, 81] for kind in ["Stub", "Skel"]}
    notices = BASE / "notices/npu-licenses"
    notices.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as aar:
        for name in wanted:
            (destination / name).write_bytes(aar.read("jni/arm64-v8a/" + name))
        for name in ["NOTICE.txt", "LICENSE.pdf"]:
            (notices / ("qualcomm-" + name)).write_bytes(aar.read(name))
    expected = wanted | {f"libnpu_dispatch_{v}.so" for v in ["google_tensor", "mediatek", "qualcomm"]}
    for old in destination.glob("*.so"):
        if old.name not in expected:
            old.unlink()
    hashes = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(destination.glob("*.so"))}
    (BASE / "native-sha256.json").write_text(json.dumps(hashes, indent=2) + "\n")
    print(f"Staged {len(hashes)} native libraries")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 scripts/stage_npu_libraries.py <LiteRT bazel-bin/litert/vendors>")
    stage(Path(sys.argv[1]))
