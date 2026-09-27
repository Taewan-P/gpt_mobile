# AI profile benchmarks

Open **Settings → Debug and Statistics → Benchmarks**, beside Usage. When debug mode is enabled, individual AI profiles also show Usage and Benchmarks shortcuts; the latter opens with that profile selected.

The old single-prompt connection-doctor benchmark and Usage performance rankings now live in the profile benchmark area. Connection doctor retains connection/context inspection. Its last legacy benchmark report remains available in History without contributing to scores.

## Views

- **Overview:** quick/full test controls, progress/cancellation, an app score, component weights, coverage, raw timing/speed summaries, and score history.
- **Everyday:** current profile/model observations from the last 30 days, actual tool success, request scatter plot, and detailed request history. Synthetic benchmarks are excluded.
- **Compare:** current configurations compared within local and remote groups, plus separate everyday rankings. Teal memory-chip icons indicate on-device execution; purple cloud icons indicate remote execution. Text labels accompany colors.
- **History:** up to 200 runs, including partial/canceled runs, per-test outcomes/previews, runtime/device context and deletion with confirmation.

## Controlled suite v1

Quick contains five tests: generation, exact instruction, JSON structure/values, arithmetic, and a tool round trip. Full repeats generation three times and adds seeded conversation recall (eight tests total). The tool is an in-memory lookup fixture with a freshly generated result; no app, connected MCP, device or delegation tools are supplied.

Each request uses a profile copy with a 512-token output ceiling, temperature zero, streaming enabled, reasoning disabled, and a fixed system instruction. Provider-specific temperature/stream overrides are normalized without changing saved settings or routing. Each test has a 90-second timeout; remote tool tests permit up to three model rounds and three fixture calls. Leaving the screen cancels the run. Remote requests use the selected account and may incur normal provider charges.

Run records checkpoint after each test. Incomplete, canceled and unsupported results never enter scores. Provider failures, missing/empty completions and timeouts remain failures. Unsupported tools are displayed as unavailable rather than zero success.

## Rating

The weighted score uses generation speed (20%), first response (15%), completion reliability (15%), speed consistency (10%), task accuracy (15%), JSON (10%), and tool success (15%). Missing dimensions are excluded and remaining weights normalized. At least three attempted tests and 40% measured weight are required. Consistency requires three speed observations. Coverage and run/test counts remain visible.

Local speed/latency targets are 40 tokens/s and 1500 ms; remote targets are 80 tokens/s and 750 ms. Faster than target caps at 100. These are explicit app-scale targets, not industry benchmarks. Local/remote ratings should not be compared as a universal capability ranking. Task fixtures provide narrow correctness checks, not a general intelligence evaluation.

Ratings combine the latest five finished runs of the same profile, suite, mode and configuration fingerprint. Fingerprints include model, endpoint, routing/provider options, accelerator and relevant local runtime tuning. They exclude credentials. Changing models or tuning does not silently mix old results into the new score.

First response includes request setup, loading/queueing and network time. Decode speed uses the observed first-to-last text interval; a single text chunk has no measured decode speed. Unreported tokens are estimated from characters/4 and labeled `≈`. P95 and median timing, text gaps and outcomes are retained. Client PSS is sampled after each test; thermal/battery observations contextualize conditions and are not efficiency ratings. No remote server memory claim is made.

Everyday tool rates join tool events to their originating run and then match profile, provider and model. Pending/running/canceled tools are excluded. Everyday observations are deliberately separate from controlled scores. Usage retains token/count/outcome accounting, including synthetic model-request usage, so benchmark consumption stays visible.

## Verification

Unit coverage includes deterministic percentiles/weights, missing metrics, configuration boundaries, provider override isolation, streamed timing/usage, both local and remote tool execution, strict JSON, abrupt/empty completions, cancellation/timeout, and durable checkpoints/deletion. Android resource and Room schema checks are unchanged. Physical device testing remains useful for native runtime initialization, thermal behavior and screen layout at large font sizes.
