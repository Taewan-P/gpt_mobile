# GPT Mobile AI 0.9.18.1

## Optional output limits

- Max Output Tokens now accepts values above the recommendation. Leave it blank or select Unlimited to remove the profile’s app-imposed response limit.
- New local profiles start without an output limit. Recommended values remain available as a one-tap suggestion.
- Local response length is separate from context allocation. A short response limit no longer shrinks the engine’s context window.
- Tool connections has an optional global output-token limit, disabled by default. Context reservations no longer silently become response limits.
- Existing saved limits are preserved. To remove them, select Unlimited in the model profile and enable No app output-token limit in Tool connections settings if a global limit is configured.

Provider limits, end-of-response tokens, and the compiled local model’s context capacity still apply. This change does not enlarge an NPU package’s compiled context window.

Version code: **75**. Android 12 or newer. Use the ARM64 APK for most phones.
