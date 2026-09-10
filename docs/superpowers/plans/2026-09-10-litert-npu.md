# LiteRT-LM upgrade and universal NPU implementation plan

**Goal:** Upgrade to 0.17.0 and implement the accepted Auto, vendor selection, and single-artifact replacement behavior.

**Spec:** ../../adr/0004-universal-apk-with-device-specific-local-models.md

**Architecture:** Reuse LocalRuntime, model catalog, WorkManager downloader and existing download/auth dialogs. Select only compatible retained artifacts for inference. Supply vendor dispatch libraries separately and isolate the selected library for LiteRT's loader.

**Constraints:** Preserve existing accelerator preferences, chats and model data. New profiles use Auto. No persistent duplicate variants. Replacement requires explicit app-user confirmation, adequate temporary space, verification before DB swap, and cleanup only after success. No automatic replay after generation or tool execution. Subagents use xai/grok-4.6.

## Tasks

- [x] SDK upgrade: pin 0.17.0, compile existing wrapper against actual AAR; adjust only incompatible calls; run focused inference tests.
- [x] Native packaging (parent; Avicenna artifact research): obtain matched vendor libraries, reproducible packaging, deterministic selected dispatch directory, availability checks and focused tests.
- [x] Replacement storage (parent; Russell resolver/reconciler contributions): explicit resolved download API, retain old READY record during replacement, verify then switch and delete old file, recover/cancel without data loss; focused repository/download regression tests.
- [x] Auto settings (Carver): Auto on new profiles, preserve old selections, label policy separately from CPU/GPU/NPU; focused preference tests.
- [x] Integration (parent + Herschel confirmation UI): resolve actual retained artifact and compatible accelerator candidates; NPU/GPU/CPU initialization fallback only; request confirmed replacement when no compatible artifact is retained; reuse download dialogs and auth flow; prevent retry/download loops.
- [x] Validation: compile, focused tests, lint, assembled APK native-library inspection; use only named target for device tests without clearing data; independent Grok review and fix valid findings.

## Evidence and rulings

- Worktree: `.worktrees/litert-npu`, branch `codex/litert-npu`, baseline `9808262`.
- Existing focused tests (LocalAccelerators, SocVariantResolver, LiteRtLmAdapter, LocalModelRepositoryImpl) passed before implementation.
- Only emulator-5554 was connected at initial device inventory. Physical NPU target requested asynchronously; emulator does not establish NPU support.
- Implementation authorized by user; do not pause for repeated design approval. Keep review and checks serial where they share Gradle outputs.

- All three vendor dispatch libraries built from the pinned LiteRT revision using NDK r28b, Bazel 7.7.0, QAIRT 2.47.0.260601, and NeuroPilot v8_0_10 headers.
- Hilt 2.59.2's Kotlin metadata reader could not read LiteRT-LM 0.17.0 metadata; upgraded Hilt to 2.60.1.
- Universal debug APK has 14 NPU native libraries and 4 license notices; native extraction enabled.
- Integrated unit suite reached 752 passing tests; Android lint passed. Additional writer-lock regression is included in final verification.
- Grok review found active replacement no-op and cancellation/cleanup writer races; fixed with explicit busy errors and LocalModelFileAccess locking. Downloads are serialized (documented simplification); idle reconciliation skips active writers.
- Physical device requested when APK was ready. Still awaiting user connection/model selection; do not claim hardware NPU execution.

- Final local verification: 753 unit tests passed, lintDebug passed, debug and instrumented APKs assembled. ktlint 1.3.1 and git diff --check passed on changed files.
- Fresh Grok review confirmed all three cancellation/cleanup/ignored-replacement findings addressed. Hardware NPU execution remains pending the user's physical device.
- Seven accidental Auto edits made by a subagent outside the worktree were backed up to /tmp/gpt-mobile-accidental-main-auto.patch and restored to their initially clean baseline. Main retains only the user's pre-existing .serena change and this task's design docs.

- Pixel 11 Pro/Tensor G6: optional android.hardware.npu declaration fixes target-SDK-37 direct-NPU denial. NPU-only and real Auto adapter tests generated hello with no GPU/CPU fallback.
- Native Tensor file reads fail on external FUSE storage; new downloads moved to internal noBackup storage, verified legacy migration added with regression tests.
- Latest unit count: 762 passing; final normal WorkManager download and Auto inference test running on the Pixel. See docs/validation/litert-npu-pixel.md.

- Final end-to-end Pixel result: normal WorkManager download finalized to internal no_backup/models (3313938293 bytes); actual Auto adapter selected [npu] and streamed hello. Full device download+inference test passed (486.203s). 762 unit tests, lint, builds and formatting passed. Qualcomm/MediaTek hardware remains untested; only Tensor G6 device execution is claimed.
