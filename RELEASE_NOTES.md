# GPT Mobile AI 0.9.19.0

## AI profile benchmarks

- Open **Settings → Debug and Statistics → Benchmarks**, beside Usage. AI profiles also have a direct Benchmarks shortcut when debug mode is enabled.
- Explore Overview, Everyday, Compare and History views with score breakdowns, trends, request details and saved test results.
- Run a five-test Quick suite or an eight-test Full suite covering generation, instructions, structured JSON, arithmetic, safe tool use and conversation recall.
- Ratings combine speed, first-response latency, completion reliability, consistency, task accuracy, JSON correctness and tool success. Coverage and sample counts show how much evidence supports each score.
- Local profiles use teal memory-chip icons; remote profiles use purple cloud icons. Their comparisons and rating targets remain separate.
- Everyday observations show real request and tool performance separately from controlled test scores. History retains up to 200 runs, including partial and canceled runs.
- Benchmark requests use a temporary 512-token ceiling without changing saved profile settings or ordinary chat output limits. Remote tests use the selected provider account and may incur its normal charges.

Scores are app-specific comparisons of matching configurations, not a universal model ranking. Missing measurements are labeled; incomplete and canceled runs do not contribute to ratings.

## Local model fixes

- Correct package and chipset detection, and resolve existing GPU editions after an NPU download is removed.
- Exclude known broken MiniCPM5 SM8750 NPU exports and offer the publisher's Android-tested CPU/GPU edition with its 2048-token context capacity.
- Close canceled or failed native sessions before the next request begins.
- Preserve useful NPU startup guidance and include redacted failure details in opt-in diagnostics.

Version code: **76**. Android 12 or newer. Use the ARM64 APK for most phones.
