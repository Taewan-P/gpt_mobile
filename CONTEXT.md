# GPT Mobile

An Android chat app where one question can be answered by several AI platforms at once. Platforms are user-defined profiles (`PlatformV2`) dispatched through provider adapters; this glossary pins the language for the app's domain, including on-device local inference.

## Language

### Platforms & chat

**Platform**:
A user-defined provider profile (name, credentials, model, sampling config) that can answer chats. Identified by UUID; typed by a ClientType.
_Avoid_: Provider, service, API (when referring to the profile)

**ClientType**:
The protocol family a Platform speaks (OpenAI, Anthropic, Google, Groq, OpenRouter, Ollama, Custom, and on-device LiteRT-LM). Determines which adapter handles the chat.

**Local Platform**:
A Platform whose ClientType is LiteRT-LM: it answers chats by running a Local Model on-device instead of calling a remote API.
_Avoid_: Offline mode, local chat

**Accelerator**:
The hardware backend a Local Platform runs inference on: CPU, GPU, or NPU. Chosen per profile; each Catalog entry declares which ones the model supports.
_Avoid_: Backend (ambiguous with server backends)

### Local inference

**Model Catalog**:
The remotely hosted list of models available for download (name, download URL, size, requirements, defaults). Owned by this project, not Google's gallery allowlist.
_Avoid_: Allowlist, model list, registry

**Local Model**:
A model artifact (.litertlm file) downloaded to the device, with a lifecycle (not downloaded, downloading, ready, deleted). Referenced by Platforms; owned by the model-management domain, so profiles share it and deleting a profile never deletes the file.
_Avoid_: Model (alone, when the artifact is meant), model file

**Gated Model**:
A Catalog model whose download requires the user to accept a license on Hugging Face and authenticate via HF OAuth.

**Serving**:
Running inference in-app for gpt_mobile's own chats: loading a local model into an engine, keeping it warm, streaming tokens, and unloading. Explicitly NOT exposing an HTTP server for other apps.
_Avoid_: Hosting, server, API endpoint
