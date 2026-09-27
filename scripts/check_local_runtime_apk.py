#!/usr/bin/env python3
"""Verify pinned LiteRT-LM/QAIRT payloads and all Android host ELF LOAD alignments."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
from zipfile import ZipFile


def check_host_alignment(name, binary):
    assert binary[:4] == b"\x7fELF", f"{name}: not an ELF library"
    # Qualcomm skeletons are DSP binaries, not Android host shared libraries.
    machine = struct.unpack_from("<H", binary, 18)[0]
    if machine not in (183, 62):  # AArch64 and x86-64
        return False
    assert binary[4:6] == b"\x02\x01", f"{name}: expected little-endian ELF64"
    offset = struct.unpack_from("<Q", binary, 32)[0]
    entry_size, count = struct.unpack_from("<HH", binary, 54)
    for index in range(count):
        header = offset + index * entry_size
        if struct.unpack_from("<I", binary, header)[0] == 1:
            alignment = struct.unpack_from("<Q", binary, header + 48)[0]
            assert alignment >= 16384, f"{name}: LOAD segment is not 16KB aligned"
    return True


def check_apk(apk_path, manifest):
    with ZipFile(apk_path) as apk:
        names = apk.namelist()
        assert len(names) == len(set(names)), f"{apk_path}: duplicate ZIP entries"
        abis = {name.split("/")[1] for name in names if name.startswith("lib/") and name.endswith(".so")}
        assert abis and abis <= {"arm64-v8a", "x86_64"}, f"Unexpected APK ABIs: {abis}"
        checked = 0
        for name, expected in manifest["libraries"].items():
            if name.split("/")[1] not in abis:
                continue
            assert name in names, f"{apk_path}: missing {name}"
            binary = apk.read(name)
            assert len(binary) == expected["bytes"], f"{name}: unexpected size"
            assert hashlib.sha256(binary).hexdigest() == expected["sha256"], f"{name}: version/hash mismatch"
            checked += 1
        aligned = sum(
            check_host_alignment(name, apk.read(name))
            for name in names if name.startswith("lib/") and name.endswith(".so")
        )
        forbidden = {"libLiteRtCompilerPlugin_Qualcomm.so", "libqnn_delegate_jni.so", "libQnnTFLiteDelegate.so", "libQnnIr.so", "libQnnSaver.so"}
        assert not any(Path(name).name in forbidden for name in names), "Unexpected second QNN integration/compiler payload"
        print(f"{apk_path.name}: {checked} pinned runtime libraries verified; {aligned} Android host libraries passed 16KB LOAD alignment")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apks", nargs="+", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    manifest = json.loads((root / "docs/audits/local-runtime-native-libraries.json").read_text())
    for apk in args.apks:
        check_apk(apk, manifest)


if __name__ == "__main__":
    main()
