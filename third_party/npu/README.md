# Bundled NPU libraries

LiteRT-LM 0.17.0 uses LiteRT `9fe5be45564c868408e6514c8aabb83e211a0911`. Build all dispatch libraries at that revision; do not substitute an unrelated LiteRT release. Qualcomm companions are the official Maven `com.qualcomm.qti:qnn-runtime:2.47.0` AAR, verified by SHA-256 in `scripts/stage_npu_libraries.py`.

Run `scripts/build_npu_dispatch.sh` with Bazel 7.7.0, NDK r28b+, QAIRT 2.47.0.260601, and NeuroPilot SDK paths exported as shown by the script. SDK sources:

- [QAIRT SDK](https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.47.0.260601/v2.47.0.260601.zip), SHA-256 `d3497e110eae82c35a9152a93c0a18bbede402aaf9faa7a97c8079eb0f522b01`.
- [NeuroPilot SDK](https://s3.ap-southeast-1.amazonaws.com/mediatek.neuropilot.com/66f2c33a-2005-4f0b-afef-2053c8654e4f.gz), SHA-256 `f69434d45856964627c750e716b835988a1f07511b6196d7f070fdde26027994`.

Use the SDKs under their included terms. They are not checked into this repository. License notices in `notices/npu-licenses` are included in the APK. Staged libraries are ignored in Git; source builds regenerate them. `native-sha256.json` records the inspected build outputs (compiler/platform changes may change source-built hashes).

The APK includes all three dispatch libraries plus Qualcomm HTP V73/V75/V79/V81. The installer extracts native libraries; runtime symlinks expose only the chosen dispatch and HTP version. MediaTek and Tensor also require compatible device-provided drivers and matching model artifacts. A successful build or ELF check does not establish actual NPU execution; validate on each supported device/model combination.

For a fresh checkout, install NDK r28b and run `python3 scripts/prepare_npu_runtime.py` (Python 3.12+). This downloads checksum-pinned Bazel and SDK build inputs before invoking the build. Existing SDK/Bazel paths can be supplied through the environment to reuse local installations. APK assembly fails clearly when dispatch libraries have not been staged; CI prepares them before debug/release builds.
