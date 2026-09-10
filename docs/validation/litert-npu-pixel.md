# LiteRT-LM 0.17.0: Pixel NPU validation

Test target: Pixel 11 Pro, Tensor G6, Android API 37. APK target SDK remains 37. Installation uses `adb install -r`; no app-data reset, uninstall, chat deletion, or profile mutation was used.

## Fixed failures

1. The Tensor driver rejected direct NPU execution for an SDK-37 application without the NPU declaration. Added optional `<uses-feature android:name="android.hardware.npu" android:required="false" />`, as documented in [Android 17's release announcement](https://developer.android.com/blog/posts/android-17-is-here).
2. Reading the NPU artifact through emulated external storage failed with `preadv2` / `Operation not supported on transport endpoint`. The same SHA-256-verified artifact passed from internal storage. New model downloads now use `noBackupFilesDir`; legacy external downloads remain resolvable and are migrated under the file-access and generation locks, with size and SHA-256 verification before source deletion.
3. The pinned Gemma 4 G6 artifact reports a maximum context of 4096 at runtime. The G6 catalog entry uses 4096, rather than the guide's 8192. Catalog revision 1 prevents an older hosted/cache snapshot from hiding the new bundled G6 entry.

## Verified device results

- `selectedDispatch_loadsFromInstalledApk`: passed; selected Tensor dispatch loaded from the installed APK via the isolated symlink directory.
- `explicitNpuModel_generatesWithoutAppFallback`: passed; generated `hello` using `Backend.NPU` directly from internal storage.
- `autoProfile_selectsNpuAndStreamsReply`: passed with the real app adapter and runtime; captured engine selection `[npu]`, streamed `hello`, and made no GPU/CPU fallback attempt. Test-only profile and repository did not write user chats or settings.
- Normal WorkManager model download into internal storage followed by Auto inference: passed. Download finalized at 3313938293 bytes under no_backup/models; real Auto adapter selected [npu] and streamed hello. Full download-and-inference test took 486.203 seconds (mostly network download).

Model: `litert-community/gemma-4-E2B-it-litert-lm`, revision `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1`, file `gemma-4-E2B-it_Google_Tensor_G6.litertlm`, size 3313938293 bytes, published SHA-256 `f86c7c19c736e9307267946edef58b0a45494a9127036d54d90dd6b7617c95b7` (verified for the manually transferred test artifact).

## Limits

Qualcomm and MediaTek dispatch libraries are built and packaged but have not been tested on corresponding physical devices. Their driver/model compatibility is not established by the Tensor result. Download replacement validates expected size; model-catalog digests are not currently part of the application download schema. Migration separately verifies source and destination SHA-256 equality.

Local validation after the storage cleanup review: 762 unit tests passed; Android lint, ktlint for changed storage files, debug APK, and instrumented APK builds passed. The optional NPU feature is present in the built manifest with target SDK 37. The review's stale-internal/legacy cleanup edge case is covered by a regression test and fixed by cleaning both previous locations only after committing the replacement record.
