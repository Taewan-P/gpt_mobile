# One APK with device-specific Local Models

Distribute one universal APK with the supported vendors' native NPU runtimes bundled, and download the Local Model variant appropriate for the device through the Model Catalog. This keeps installation independent of the device's NPU vendor and avoids bundling every large model variant in the APK; it does not promise NPU support on every device.

Automatically prefer NPU for validated device/model combinations, while retaining a manual accelerator override. Device detection and model selection happen at runtime.

New Local Platforms default to Auto accelerator selection. Existing CPU, GPU, and NPU selections remain unchanged; users can explicitly switch existing profiles to Auto.

If NPU initialization fails, automatically try GPU, then CPU, using compatible Local Models already downloaded. If fallback requires another model download, show its size and ask the app user before downloading. Do not assume an NPU-specific model artifact also supports GPU or CPU.

Do not retain NPU and CPU/GPU variants side by side for the same catalog model. When switching requires a different artifact, ask the app user before replacing it. Download and verify the replacement before removing the old file, preserving the existing model if the download fails. This requires temporary space for both files, but only the replacement is retained after a successful switch.

The distribution, automatic NPU preference, initialization-fallback, existing-profile preservation, and model-variant replacement decisions were accepted during the LiteRT-LM upgrade design discussion. The proposed implementation targets LiteRT-LM 0.17.0 and reuses the existing model catalog, downloader, and local runtime.

## Packaging constraint

The inspected 0.17.0 Android AAR contains `liblitertlm_jni.so` for ARM64 and x86-64, but no vendor dispatch libraries. Vendor NPU libraries must therefore be supplied separately in the APK. The [LiteRT revision pinned by 0.17.0](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.0/WORKSPACE) [loads the first dispatch library found in its configured directory](https://github.com/google-ai-edge/LiteRT/blob/9fe5be45564c868408e6514c8aabb83e211a0911/litert/runtime/dispatch/litert_dispatch.cc#L110); expose only the selected vendor's dispatch library to this loader. Matching library builds, Android packaging and loading, and actual NPU execution remain validation requirements, not established support claims.

## Pixel Tensor G6 runtime requirements

Android 17 apps targeting SDK 37 declare the optional `android.hardware.npu` feature for direct NPU access. Tensor G6's driver also requires model files on internal storage: the same verified artifact failed with `preadv2` ENOTSUP through emulated external storage, then passed NPU-only generation from internal storage after the manifest declaration. The public pinned Gemma 4 G6 artifact has a runtime-reported context limit of 4096, which takes precedence over the guide's 8192.
