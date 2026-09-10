#!/usr/bin/env bash
# Build dispatch libraries from the exact LiteRT revision used by LiteRT-LM 0.17.0.
set -euo pipefail
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK r28b or newer}"
: "${LITERT_QAIRT_SDK:?Set LITERT_QAIRT_SDK to QAIRT 2.47.0.260601 (include/QNN required)}"
: "${LITERT_NEURO_PILOT_SDK:?Set LITERT_NEURO_PILOT_SDK to the NeuroPilot SDK used by LiteRT}"
repo=$(cd "$(dirname "$0")/.." && pwd)
cache="$repo/third_party/npu/cache"
ref=9fe5be45564c868408e6514c8aabb83e211a0911
mkdir -p "$cache"
archive="$cache/litert-$ref.tar.gz"
if [[ ! -f "$archive" ]]; then
    curl --fail --location "https://github.com/google-ai-edge/LiteRT/archive/$ref.tar.gz" --output "$archive"
fi
python3 - "$archive" "$cache" <<'PY'
import hashlib, pathlib, sys, tarfile
archive, cache = map(pathlib.Path, sys.argv[1:])
assert hashlib.sha256(archive.read_bytes()).hexdigest() == '5dbb113744e103f899c7b1b7c5479126b36a0b7414c3d971185c1f02041bfa39', 'LiteRT source checksum mismatch'
with tarfile.open(archive) as tar:
    tar.extractall(cache, filter='data')
PY
cd "$cache/LiteRT-$ref"
# macOS Command Line Tools work without selecting a full Xcode installation.
if [[ "$(uname)" == Darwin ]]; then
    export DEVELOPER_DIR="${DEVELOPER_DIR:-/Library/Developer/CommandLineTools}"
    export BAZEL_USE_CPP_ONLY_TOOLCHAIN=1
fi
"${BAZEL:-bazel}" build --config=android_arm64 --jobs=6 \
    --repo_env=BAZEL_USE_CPP_ONLY_TOOLCHAIN="${BAZEL_USE_CPP_ONLY_TOOLCHAIN:-0}" \
    //litert/vendors/google_tensor/dispatch:dispatch_api_so \
    //litert/vendors/mediatek/dispatch:dispatch_api_so \
    //litert/vendors/qualcomm/dispatch:dispatch_api_so
python3 "$repo/scripts/stage_npu_libraries.py" "$PWD/bazel-bin/litert/vendors"
cp LICENSE "$repo/third_party/npu/notices/npu-licenses/LiteRT-LICENSE"
cp "$LITERT_NEURO_PILOT_SDK/LICENSE AGREEMENT.pdf" "$repo/third_party/npu/notices/npu-licenses/MediaTek-LICENSE.pdf"
