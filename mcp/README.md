# Companion server retired

The old `server.js` was an unsupported prototype: it used an incompatible SDK API and legacy SSE transport, allowed unrestricted file paths, and treated Node VM contexts as a security boundary. It no longer starts a network listener or executes tools.

Use **Settings → Tool connections → Marketplace** to select a maintained Streamable HTTP server. Host-required entries run on your own computer, not inside Android. Configure its filesystem scope, authentication, and allowed network interfaces on that host. Preview the destination and run connection diagnostics before enabling tools.

The Android implementation is in `data/agent/tool/McpClientManager.kt`; its discovery and call tests are the supported protocol contract. The retired Node package manifests were removed so dependency scanners do not mistake this documentation for an installable service. No legacy SSE compatibility or sandbox execution is claimed.
