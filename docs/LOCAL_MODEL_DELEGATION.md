# Local research and compact delegation

Configure **Local models → Delegation** and select an enabled on-device or private-server helper profile. The master delegation switch remains off until enabled. A downloaded model must have a working AI profile. A remote main model can then use the local helper for planning, evidence processing and tool-result compression.

## Execution

1. The local helper decides whether public research is useful and returns bounded search queries. Only URLs actually present in the task can be used as explicit starting pages. Invalid plans do not send the whole user message as a fallback query.
2. The app invokes the main profile's enabled `web_search` engines and page reader. The helper ranks observed sources. Optional crawling follows links on the same host, within the total page limit.
3. Long pages contribute query-relevant passages. Local summaries preserve observed source IDs, exact values, disagreements and gaps. The remote model receives a compact JSON brief with source URLs, evidence types and limitations. Model-invented source references are removed.
4. Further research can use `delegate_to_model`. Large results from other tools are processed by the same helper. The original result or its existing redacted trace remains available in the tool trace. Small results and tool errors pass through.
5. After a tool result has been consumed, later primary-model rounds replay only a bounded compact view. Newest results are prioritized; older consumed payloads become placeholders once the replay allowance is exhausted. This prevents successful delegation from being followed by repeated 10K+ remote-context replays.

The helper uses isolated text/JSON requests; it needs no native tool-calling capability. All local inference is serialized per main-model turn. Planning, source selection and summarization share a local-call allowance. Search and page requests use the main profile's authorization checks and shared tool budget. Composite orchestration does not hold a child execution permit, so a concurrency limit of one cannot deadlock nested calls.

## Controls

The tab provides search-query count, results per engine, page count, crawl depth, parallel page requests, page allowance, remote brief allowance, local input/output limits, local-call count, timeout, delegations per turn and the result-compaction threshold. Automatic preparation, research and large-result processing have separate switches.

Input and brief limits use conservative UTF-8 byte estimates, not provider tokenizers. The helper's actual context capacity further limits its input. Main-model output settings remain independent. A zero page limit uses search snippets; crawl depth zero reads selected pages without following links.

## Boundaries and failure behavior

- The profile's tool switches, per-chat exclusions, connection permissions, disabled tools, and memory-excluded Free provider policy continue to apply. Private destination checks use the actual API URL and are repeated before generation.
- Search providers receive generated queries. Remote page-reading MCP services receive requested URLs and their page contents. Local processing does not make those services offline. The built-in page reader retains DNS pinning, special-address rejection, redirect checks and response limits.
- Retrieved content is untrusted data. Local and main prompts distinguish it from instructions. Only observed URLs enter source metadata; this does not independently verify a model's factual conclusions.
- Timeouts and exhausted local allowances retain completed evidence and mark partial results. An unsuccessful summarization never re-executes a completed tool or changes its success into a retryable failure. Parent cancellation propagates.
- Privacy and permission controls are checked independently of compaction. No local chat history or saved memory is added to helper requests automatically.
- Smaller briefs can omit useful detail. Local processing may add latency even while reducing remote context. No speedup or billed-token saving is guaranteed without measuring a representative workload on the configured hardware and providers.

## Validation

Regression tests cover search → read → same-host crawl, query and page limits, source allowlists, UTF-8 bounds, invalid plans, partial evidence on timeout, cancellation, shared-budget exhaustion, single-permit nested tools, private-target rechecks, disabled local tools, serialized helper calls and preservation of completed-action status. Page-reader tests cover structured links and existing network-safety behavior.

Device testing remains necessary for memory pressure, thermal behavior, generation quality, actual token counts and end-to-end latency on downloaded models. Authenticated MCP services require the user's configured credentials.
