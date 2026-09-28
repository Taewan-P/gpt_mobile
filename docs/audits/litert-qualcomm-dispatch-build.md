# Qualcomm LiteRT dispatch build provenance

The checked-in `libLiteRtDispatch_Qualcomm.so` is built from the LiteRT revision used by the app's LiteRT-LM dependency, against the headers for the packaged QAIRT runtime.

| Input | Value |
| --- | --- |
| LiteRT-LM | `com.google.ai.edge.litertlm:litertlm-android:0.17.1` |
| LiteRT revision | `9fe5be45564c868408e6514c8aabb83e211a0911` |
| QAIRT SDK | `2.50.0.260828` |
| Android NDK | `r28b` |
| Bazel target | `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so` |
| Output SHA-256 | `b248013db82f1b6f397473205b1f492d4ab6965a537dcab6dfe6b92d8cbb87d1` |
| Output size | `691040` bytes |

Build with `LITERT_QAIRT_SDK` pointing to the QAIRT SDK root containing `include/QNN`, using the Android ARM64 LiteRT Bazel configuration. Copy the resulting `libLiteRtDispatch_Qualcomm.so` to `app/src/main/jniLibs/arm64-v8a/`, then regenerate `local-runtime-native-libraries.json` from the packaged APK and run `scripts/check_local_runtime_apk.py`.

All ELF `LOAD` segments in this output are aligned to `0x4000` (16 KiB). This establishes source/runtime ABI provenance and APK structural compatibility; a matching AOT model still requires execution on its target Snapdragon SoC and firmware.
