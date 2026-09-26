# GPT Mobile AI 0.9.17.0

## Free Models
- Free profiles are automatically named **Free Models**.
- **LLM7 is available** following the app owner's approval confirmation. Existing anonymous limits and credential-free requests remain in place.
- Pollinations uses its current anonymous model identifier, retries transient server errors once, rejects error pages, and tests a fresh request instead of a cached response.
- Pollinations is currently returning a server storage failure. The app now identifies that upstream outage and offers actionable provider-switch guidance; this release cannot repair Pollinations' server or guarantee its availability. Conversations are never silently sent to another provider.

## Conversations
- Assistant backgrounds are 15% more opaque when expanded. Backgrounds and timestamps fade in and out over one second with the activity disclosure.
- Remove persistent activity/tool-count text; keep one themed expand/collapse control.
- Open conversations at the true bottom. Favourite response links align the selected response at the top, including after long user prompts.
- User prompt text is 30% more transparent. Archived history, overflow menus and their icons use theme colors.
- The composer uses one solid theme color throughout its text field and container.

## Device location
- Request a fresh location directly from Android's available fused, network and GPS providers, with up to 30 seconds for a cold fix instead of a five-second GPS-only attempt.
- Support approximate permission without requiring GPS, reject stale fixes, and release subscriptions on success, timeout or cancellation. Google Maps does not need to be opened to populate the location cache.
- Provide clearer guidance when permissions, disabled location, background restrictions or poor reception prevent a fix. No new background location permission is requested.

## Debug and Statistics
- Separate **Live**, **Runs** and **Logs** views, live request timing and token observations, device memory, thermal, battery and network state, and pause/resume inspection.
- Show one response diagnostics panel inside expanded activity. Group repeated log rows while preserving every recorded event in exported logs.
- Add token trend charts, latency/throughput scatter plots, outcome charts, and rankings by speed, tokens per second and success rate.
- Tap a profile or scatter point for its latency percentiles, first-token timing, token usage, outcomes and individual requests.
- Attribute primary, delegated and synthesis requests to their actual profiles. Distinguish failures, cancellations and interruptions, and keep estimated usage separate from reported usage.
- Upgrade the database safely from version 30 to 31. Backups continue to restore through named columns and schema migrations; historical delegated requests remain unassigned when their profile cannot be established safely.

Version code: **73**. Android 12 or newer. Use the ARM64 APK for most phones, the universal APK for mixed architectures, or the AAB for distribution tooling. Hardware acceleration still depends on the phone and selected model package.
