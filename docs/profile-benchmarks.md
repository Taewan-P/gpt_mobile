# AI profile benchmarks

Open **Settings → Debug and Statistics → Benchmarks**, beside Usage. When debug mode is enabled, individual AI profiles also show Usage and Benchmarks shortcuts; the latter opens with that profile selected.

The old single-prompt connection-doctor benchmark and Usage performance rankings now live in the profile benchmark area. Connection doctor retains connection/context inspection. Its last legacy benchmark report remains available in History without contributing to scores.

## Views

- **Overview:** quick/full test controls, progress/cancellation, an app score, component weights, coverage, raw timing/speed summaries, and score history.
- **Everyday:** current profile/model observations over 7 days, 30 days or stored history, actual tool success, request scatter plot, and detailed request history. Synthetic benchmarks are excluded.
- **Compare:** current configurations compared within local and remote groups, plus separate everyday rankings. Teal memory-chip icons indicate on-device execution; purple cloud icons indicate remote execution. Text labels accompany colors.
- **History:** up to 200 runs, including partial/canceled runs, per-test outcomes/previews, runtime/device context and deletion with confirmation.

## Controlled suite v1

Quick contains five tests: generation, exact instruction, JSON structure/values, arithmetic, and a tool round trip. Full repeats generation three times and adds seeded conversation recall (eight tests total). The tool is an in-memory lookup fixture with a freshly generated result; no app, connected MCP, device or delegation tools are supplied.

Each request uses a profile copy with a 512-token output ceiling, temperature zero where the provider supports custom sampling, streaming enabled, reasoning disabled, and a fixed system instruction. Provider-specific temperature/stream overrides are normalized without changing saved settings or routing. Fixed-sampling Claude models omit temperature and top-p even with reasoning disabled. Each test has a 90-second timeout; remote tool tests permit up to three model rounds and three fixture calls. Leaving the screen cancels the run. Remote requests use the selected account and may incur normal provider charges.

Local profiles resolve and validate their installed model and accelerator before a run is created. Missing downloads or incompatible packages stop at setup with an actionable error; they do not generate a suite of failed tests or enter profile ratings. Runtime failures after successful setup remain test failures.

Run records checkpoint after each test. Incomplete, canceled and unsupported results never enter scores. Provider failures, missing/empty completions and timeouts remain failures. Unsupported tools are displayed as unavailable rather than zero success.

## Rating

The weighted score uses generation speed (20%), first response (15%), completion reliability (15%), speed consistency (10%), task accuracy (15%), JSON (10%), and tool success (15%). Missing dimensions are excluded and remaining weights normalized. At least three attempted tests and 40% measured weight are required. Consistency requires three speed observations. Coverage and run/test counts remain visible.

Local speed/latency targets are 40 tokens/s and 1500 ms; remote targets are 80 tokens/s and 750 ms. Faster than target caps at 100. These are explicit app-scale targets, not industry benchmarks. Local/remote ratings should not be compared as a universal capability ranking. Task fixtures provide narrow correctness checks, not a general intelligence evaluation.

Ratings combine the latest five finished runs of the same profile, suite, mode and configuration fingerprint. Fingerprints include model, endpoint, routing/provider options, accelerator and relevant local runtime tuning. They exclude credentials. Changing models or tuning does not silently mix old results into the new score.

First response includes request setup, loading/queueing and network time. Decode speed uses the observed first-to-last text interval; a single text chunk has no measured decode speed. Unreported tokens are estimated from characters/4 and labeled `≈`. P95 and median timing, text gaps and outcomes are retained. Client PSS is sampled after each test; thermal/battery observations contextualize conditions and are not efficiency ratings. No remote server memory claim is made.

Everyday tool rates join tool events to their originating run and then match profile, provider and model. Pending/running/canceled tools are excluded. Everyday observations are deliberately separate from controlled scores. Usage retains token/count/outcome accounting, including synthetic model-request usage, so benchmark consumption stays visible.

## Verification

Unit coverage includes deterministic percentiles/weights, missing metrics, configuration boundaries, provider override isolation, streamed timing/usage, both local and remote tool execution, strict JSON, abrupt/empty completions, cancellation/timeout, and durable checkpoints/deletion. Android resource and Room schema checks are unchanged. Physical device testing remains useful for native runtime initialization, thermal behavior and screen layout at large font sizes.

## Delegation pipeline benchmark

The **Delegation** tab tests the selected primary profile and its configured helper
through the same worker gate, watchdog, input/output limits and research coordinator
used in conversations. It runs evidence compaction, a helper tool round trip, and
fixture research through the final primary answer. It records the settings snapshot,
worker/primary input and output tokens, worker calls, elapsed time, page/search counts,
and evidence/brief sizes. Worker token counters reflect provider-reported usage;
providers without usage reports can leave these counters at zero.

Search/page results and the parcel lookup are temporary fixtures. Connected MCP tools,
location, memory and chat history are excluded. Model requests still use configured
providers and incur normal charges. A case stops at 180 seconds; individual workers
also retain the configured first-progress, idle and runtime watchdogs. Low-battery,
disabled-tool and helper eligibility policies still apply. This tests wiring and settings,
not live search-provider availability or performance.

Text transforms (planning, page selection, evidence summaries and memory extraction)
run without tools; the app performs authorized research tool calls. Direct delegated
tasks retain helper tool access. The final answer uses the primary profile/context
output budget rather than the helper brief budget. Failed preparation restores the
primary's authorized tools for recovery and is recorded as a failed tool event.

### Delegate rankings (suite v2)

The Delegation tab has an explicit helper picker. Each suite pins that helper and
fails clearly if it becomes ineligible; it never substitutes another provider.
Rankings use the same primary configuration and delegation settings, and the latest
five finished, non-canceled runs per helper configuration. Legacy delegation runs
remain visible but do not enter the new ranking.

The delegate score weights task success (30%), tool-task success (30%), evidence
accuracy (10%), research/handoff (10%), successful-case latency (10%), and generation
speed (10%). Missing timing is excluded; the score cannot exceed overall task pass
percentage. A rating requires attempts for all three cases. Failed/no-call tool tests
count as failures even when the response is fast. Synthetic research tool executions
also contribute to the displayed valid-call counts.

Metrics include median/p95 successful-case latency, worker first text, estimated
text decode speed, worker/primary input and output tokens, tool executions, evidence
sizes and per-request output-cap violations. Text decode speed uses characters/4
over the observed first-to-last text interval; single chunks have no measured speed.
Unreported worker usage is estimated and labeled. Token totals cover failed attempts
as well as successful ones; token counts alone are not a capability score.

Regular benchmarks reconnect once for a connection abort/reset before text or tool
activity, within the original case deadline. Authentication and model errors are not
retried. Screen rotation no longer cancels a run through composable disposal; explicit
Stop or destruction of the ViewModel cancels it. Delegated tool loops now have four
rounds and four tool calls at most, independent of the primary's larger tool budget.
Per-round cumulative usage is accumulated correctly across tool rounds; output-cap
checks compare each round's usage to its per-request cap, rather than comparing the
whole tool loop to a single-request cap. Google identity errors quarantine the failing
worker immediately; benchmarks preflight missing keys on authenticated providers.
