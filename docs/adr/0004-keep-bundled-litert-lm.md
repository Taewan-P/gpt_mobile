# On-device chat stays on bundled LiteRT-LM

GPT Mobile's local models run through LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android`, currently 0.11.0): `Engine`, `Conversation`, and `Backend` for CPU, GPU, and NPU. That is not the LiteRT runtime Google ships in Play services.

`com.google.android.gms:play-services-litert:17.0.0` replaces a bundled `com.google.ai.edge.litert:litert` dependency and runs `.tflite` models through `CompiledModel` (or CPU `Interpreter`). Its Maven POM is under the Android Software Development Kit License, and it pulls in `play-services-base`, `play-services-basement`, `play-services-tasks`, and `play-services-tflite-impl`. F-Droid's inclusion policy forbids Google Play services libraries and tells upstream to keep a build that does not need them. The same policy rejects apps that fail to rebuild because they require Play services. Google's own migration guide says apps that need NPU today, or that must run without Play services, should keep standalone LiteRT.

LiteRT-LM 0.11.0 already bundles `libLiteRt.so` and `libLiteRtClGlAccelerator.so`. The accelerator is ML Drift, which is Apache-2.0. There is no app-level `Interpreter` or GPU-delegate call to rewrite, and removing Play services would not delete those native libraries. A Play product flavor would not give local chat a second implementation, and it would break the current F-Droid recipe (`gradle: yes`, no flavors) until fdroiddata changed.

`assertNoPlayServicesDependencies` fails `assembleDebug`, `assembleRelease`, and `check` if either runtime classpath resolves `com.google.android.gms`.

## Considered options

- **Switch fully to `play-services-litert`.** Rejected. It does not provide `Engine` / `Conversation`, it does not run without Play services, and it cannot be included in the F-Droid build.
- **Stay on bundled LiteRT-LM and call `CompiledModel` from the app.** Rejected as an app change. `CompiledModel` is the base LiteRT API for `.tflite` graphs. LiteRT-LM already calls that C API inside its own native library. The Kotlin chat path has no `Interpreter` to migrate.
- **Play flavor plus an F-Droid flavor.** Rejected. The Play flavor would still have to link LiteRT-LM to run local chat, so the Play services artifact would be an unused non-free dependency. The F-Droid flavor would be today's graph with a flavor dimension the published metadata does not select.

## Consequences

- Local inference keeps working on devices without Play services, including the F-Droid package `dev.chungjungsoo.gptmobile` 0.9.1 (versionCode 25).
- The APK keeps the LiteRT-LM native runtime. The roughly 8 MB savings in the Play services announcement apply to apps that delete a standalone `litert` artifact, which this app does not declare.
- GPU behavior, Play services module download, and output parity with a Play services `CompiledModel` path are not things this app can measure, because that path is not a substitute for LiteRT-LM.
