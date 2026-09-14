# Mistral Provider Support Design

## Context

[Discussion #335](https://github.com/Taewan-P/gpt_mobile/discussions/335) asks for Mistral to appear as a first-class provider instead of requiring users to infer that the OpenAI-compatible option works. The same request proposed clearer `/v1/` API URL guidance.

The app currently models each configured account as a Platform with a ClientType. Several ClientTypes already share the OpenAI Chat Completions protocol, while retaining provider-specific names and defaults.

## Scope

Phase 1 adds first-class Mistral chat support. A new Mistral Platform provides:

- streaming chat completions;
- the app's existing client-side tool loop;
- inline image input when the selected Mistral model supports vision;
- automatic model context-window discovery;
- provider-specific setup defaults, API-key help, and URL validation.

Amazon Bedrock and Gemini Enterprise Agent Platform are deferred to separate design and implementation phases because their identity and authorization models are independent architectural decisions. Mistral Agents, Conversations, Connectors, OCR, prompt-cache controls, live model discovery, and other Mistral-specific products are also out of scope.

## Domain decision

Add `MISTRAL` to `ClientType`. In this codebase, ClientType means a named API compatibility contract with its own defaults and wire behavior; it does not imply a unique transport protocol. Existing Custom or OpenAI-compatible Platforms that point to Mistral remain unchanged.

This decision updates `CONTEXT.md`. It does not need an ADR: adding or removing one ClientType is isolated, unsurprising in the current model, and inexpensive to reverse.

## Considered approaches

### Dedicated Mistral session over shared protocol machinery — selected

`MistralAdapter` owns Mistral session creation and is selected explicitly by `ChatRepositoryImpl`. It reuses the existing Chat Completions request DTOs, HTTP transport, attachment encoding, and event assembler through a small internal session helper. This creates a provider-owned seam without duplicating an identical wire stack.

### Route through `OpenAICompatibleAdapter` directly — rejected

This is the smallest immediate change, but it leaves no Mistral-owned boundary for documented Mistral differences. The design explicitly requires a dedicated session seam.

### Create Mistral-specific transport and DTOs — rejected

The phase-1 request and response shapes are OpenAI-compatible. Duplicating the transport, DTOs, and stream parser would add maintenance without changing behavior.

### Split provider identity from protocol type — rejected

A separate persisted provider-preset layer would require a broader schema and setup refactor. The current ClientType model already represents branded integrations such as Groq and OpenRouter, so that refactor is not required to deliver Mistral.

## Architecture

### Provider identity and defaults

`ClientType.MISTRAL` is persisted using the existing enum conversion. Adding the enum value does not change the Room schema and requires no database migration.

`ModelConstants` supplies:

- platform name: `Mistral`;
- API base URL: `https://api.mistral.ai/v1/`;
- default model: `mistral-large-latest`.

The model remains editable. The API URL remains editable so users can select Mistral regional inference or a compatible gateway.

### Setup experience

Both platform-creation surfaces show Mistral as a branded choice. The initial setup list places it with the hosted API providers. The details screen links to Mistral's official API-key instructions and explains that the base URL must include the API version.

For Mistral only:

- the API key is required;
- the trimmed API URL must end with `/v1/`;
- an invalid URL shows an inline error and blocks progression or saving;
- the app does not silently rewrite an invalid URL.

Other ClientTypes retain their current validation behavior.

### Request and stream flow

The generic non-Groq Chat Completions session logic moves to a small internal helper used by `OpenAICompatibleAdapter` and `MistralAdapter`. `MistralAdapter` passes the Platform's base URL, bearer token, model, sampling values, output cap, stream setting, messages, and tool definitions through that helper.

The existing event assembler continues to map streamed text, optional reasoning chunks, tool calls, usage, provider failures, and completion. Tool results are appended using the existing Chat Completions message shape on subsequent rounds. No SDK or dependency is added.

### Attachments

Mistral uses the existing inline OpenAI-compatible image encoding rather than OpenAI's file-upload API. `ProviderContextPolicy` applies the existing 12 MiB inline attachment ceiling to Mistral so oversized requests fail locally before network transmission. Actual vision support remains model-dependent.

### Context-window discovery

`HttpRemoteContextWindowLookup` handles Mistral by sending an authenticated `GET` request to:

`{apiBase}/models/{encodedModelId}`

`RemoteContextWindowParser` reads the documented positive integer `max_context_length`. If discovery fails or the field is absent, the existing safe fallback asks the user to enter the model's context window; the app does not guess or hardcode a potentially stale capacity.

## Failure behavior

- Blank Mistral keys and API URLs that do not end in `/v1/` are rejected before save.
- Authentication, rate-limit, model, malformed-request, and stream failures use the existing provider error path.
- Unsupported tool or image combinations return the provider's error; the app does not retry through another ClientType.
- Oversized inline images fail locally using the existing attachment-size error.
- Context discovery failure preserves the Platform and conversation, then requests a manual context-window value.

## Verification

Automated checks cover:

- Mistral defaults and display mapping;
- Mistral-only API-key and `/v1/` validation in both creation flows;
- `max_context_length` parsing and authenticated model lookup;
- the dedicated Mistral session's initial streamed request;
- a second tool round containing the matching tool result;
- inline attachment policy;
- unchanged handling of existing Custom Platforms.

Run the focused tests, `:app:testDebugUnitTest`, `:app:compileDebugKotlin`, and `:app:lintDebug`.

Device verification targets only `emulator-5554` and must preserve existing app data. Install the debug APK in place, then verify that the Mistral choice renders, prefills the approved defaults, blocks an invalid URL, accepts a valid `/v1/` URL with a nonblank key, and persists a Mistral Platform. Remove only the test Platform afterward. A live production request is attempted only if a Mistral key is supplied through a safe channel; otherwise the final report explicitly marks live authentication as unverified.

## Success criteria

Phase 1 is complete when a user can create a Mistral Platform without treating it as OpenAI or Custom, the app routes it through the dedicated Mistral session, context capacity is detected when Mistral returns it, the agreed automated checks pass, and the setup flow passes on `emulator-5554` without clearing app data.

## References

- [Mistral Chat API](https://docs.mistral.ai/api)
- [Mistral first API request](https://docs.mistral.ai/getting-started/quickstarts/developer/first-api-request)
- [Mistral OpenAI migration guide](https://docs.mistral.ai/resources/migration-guides)
- [Mistral Models API](https://docs.mistral.ai/api/endpoint/models)
