"""Fetch pinned native build inputs, then build and stage all supported NPU vendors."""
import hashlib
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "third_party/npu/cache"


def fetch(name, url, digest):
    CACHE.mkdir(parents=True, exist_ok=True)
    target = CACHE / name
    if not target.exists():
        partial = target.with_suffix(target.suffix + ".part")
        with urllib.request.urlopen(url, timeout=60) as response, partial.open("wb") as output:
            shutil.copyfileobj(response, output)
        partial.replace(target)
    with target.open("rb") as source:
        actual = hashlib.file_digest(source, "sha256").hexdigest()
    if actual != digest:
        raise ValueError(f"Checksum mismatch: {name}")
    return target


def main():
    env = os.environ.copy()
    sdk = env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT")
    ndk = Path(env.get("ANDROID_NDK_HOME", str(Path(sdk or "") / "ndk/28.1.13356709")))
    if not (ndk / "source.properties").exists():
        raise SystemExit('Install NDK r28b: sdkmanager "ndk;28.1.13356709", or set ANDROID_NDK_HOME')
    env["ANDROID_NDK_HOME"] = str(ndk.resolve())
    host = (platform.system(), platform.machine())
    bazel_files = {
        ("Darwin", "arm64"): ("darwin-arm64", "2533ddc8628a96d1da3c5e99f45442d30fec93ee724316fd9687c78e56a84049"),
        ("Linux", "x86_64"): ("linux-x86_64", "fe7e799cbc9140f986b063e06800a3d4c790525075c877d00a7112669824acbf"),
    }
    if "BAZEL" not in env:
        if host not in bazel_files:
            raise SystemExit("Set BAZEL to a Bazel 7.7.0 binary for this host")
        suffix, digest = bazel_files[host]
        name = "bazel-7.7.0-" + suffix
        binary = fetch(name, "https://github.com/bazelbuild/bazel/releases/download/7.7.0/" + name, digest)
        binary.chmod(0o755)
        env["BAZEL"] = str(binary)
    if "LITERT_QAIRT_SDK" not in env:
        archive = fetch("qairt-2.47.zip", "https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.47.0.260601/v2.47.0.260601.zip", "d3497e110eae82c35a9152a93c0a18bbede402aaf9faa7a97c8079eb0f522b01")
        prefix = "qairt/2.47.0.260601/"
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                if name.startswith(prefix + "include/"):
                    z.extract(name, CACHE)
        env["LITERT_QAIRT_SDK"] = str(CACHE / prefix)
    if "LITERT_NEURO_PILOT_SDK" not in env:
        archive = fetch("neuropilot.tar.gz", "https://s3.ap-southeast-1.amazonaws.com/mediatek.neuropilot.com/66f2c33a-2005-4f0b-afef-2053c8654e4f.gz", "f69434d45856964627c750e716b835988a1f07511b6196d7f070fdde26027994")
        with tarfile.open(archive) as tar:
            tar.extractall(CACHE, filter="data")
        env["LITERT_NEURO_PILOT_SDK"] = str(CACHE / "neuro_pilot")
    subprocess.run([str(ROOT / "scripts/build_npu_dispatch.sh")], env=env, check=True)


if __name__ == "__main__":
    main()
