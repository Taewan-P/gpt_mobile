# Delegation diagnostics repairs — gateway 12.1.1

The October 1 diagnostic failures exposed a routing defect: delegated client tools could be removed by gateway domain filtering or hidden by the local-first routing phase. Delegated requests now run one bounded model round with their original messages and tool schemas, returning client tool calls to Android for execution. Gateway memory and MCP discovery are bypassed for these child requests.

| Reported issue | Repair |
| --- | --- |
| Worker tool fixture never executes | Dedicated child-agent gateway path preserves supplied schemas and client-owned calls; plaintext recovery only accepts supplied tool names. |
| SSE aborts, connection failures and stalled gateway | Explicit completion markers required; bounded retries only before assistant output or tool fragments; gateway child queue/read/stream deadlines; bounded upstream connection recovery. |
| Extreme first-response waits | Configured first-response deadline applies to llama workers; heartbeats do not count as model progress. Timers use a monotonic clock. |
| All throughput values missing | Backend llama.cpp timings survive SSE and agent usage aggregation; native runtime decode metrics are used when available. One-chunk providers expose a labelled estimated end-to-end rate if native timing is absent. |
| Empty delegate results | Short nonblank factual answers accepted; content block arrays decoded; parsing failures retained as errors. Genuine empty output enters recovery. |
| Fallback is null | Available fallback tried after the first failed or empty call; interactive recovery keeps the existing model-choice/primary-only dialog. A failed profile cannot be revisited within the same task's recovery chain. |
| Unavailable delegates | Installation/input capacity preflight before selection and recovery. Provider overhead is tracked per worker so a large-overhead helper cannot exhaust another helper's budget. No eligible helper leaves the primary responsible. |
| Facts lost in compaction | Missing exact identifiers, numeric values, source IDs and observed URLs retained before the summary. If literal preservation cannot fit, keep original evidence instead of presenting a misleading summary. |
| Unsupported OpenAI temperature | Model sampling policy independent of the reasoning toggle; bounded retry removes only explicitly rejected temperature/top_p fields. Tools and output caps stay intact. |
| OpenRouter DNS failure | Wrapped DNS/IO failures get at most three attempts with short backoff before handoff fails. Cancellation is never retried. |
| Misleading benchmark failures | Worker causes retained in case errors. Transport/runtime errors affect reliability rather than being attributed to failed tool selection. Completed runs log score, rank and throughput. |

Benchmarks remain pinned to the selected worker and never silently substitute another model. A tool fixture still requires a real successful call; a guessed answer cannot pass it.

## Validation and operation

Python behavioral tests cover schema forwarding, strict no-tools requests, supplied-name-only recovery, SSE tool/timing preservation and error forwarding. Kotlin regression coverage exercises bounded DNS retries, capability retry, interrupted streams, short/array content, aggregated timing, first-response deadlines, recovery loops, eligibility, fact retention and error classification.

Install/restart the updated gateway script on the local PC to enable the server repairs. Both gateway_v12.py and gateway_v12.1.py contain the same 12.1.1 implementation. An Android build containing these changes is also required for the client and benchmark repairs. Network outages, missing model downloads and slow hardware remain possible; these changes bound and classify those failures rather than guaranteeing that a provider is always available.
