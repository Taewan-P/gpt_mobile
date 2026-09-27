# Local model diagnostics fixes

The supplied diagnostics show MiniCPM5 NPU context creation failing with a
9,260,401,152-byte allocation estimate. The publisher documents the same failure
for that artifact, and a failure for its reduced-cache variant:
https://huggingface.co/Tdamre/MiniCPM5-1B-litert-lm/blob/main/README.md

The marketplace now excludes those two exports and admits the publisher's
Android-tested `MiniCPM5-1B-web.litertlm` CPU/GPU edition with its 2048-token
context capacity. Existing affected downloads receive guidance before native
initialization. Revisit the artifact-specific exclusions when the publisher
replaces them with validated builds.

Legacy GPU profiles can resolve the downloaded GPU edition even after removing
the original NPU download. Capabilities and context capacity come from the
resolved edition. Parent directory names no longer turn GPU files into NPU
packages, and installed NPU files cannot inherit support for a different SoC.

Cancelled or failed conversations close under the generation lock before the
next request starts. NPU startup guidance survives the adapter's error handling.
Opt-in diagnostics include redacted failure reasons and label estimated output
counts instead of reporting `-1`.

Regression tests cover package discovery, edition resolution, SoC checks, error
propagation, and session cleanup. Native QNN operation and memory behavior still
require a physical Snapdragon device; unit tests cannot validate that hardware.
