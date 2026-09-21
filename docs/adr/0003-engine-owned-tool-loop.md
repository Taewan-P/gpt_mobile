# Local tool calling uses the engine-owned loop, bridged to app tools

For remote providers, the app owns the tool loop: the adapter reports a tool-call event, `AgentRunner` executes the tool, and the result goes back in a new request. LiteRT-LM inverts this — tools are registered in `ConversationConfig` and the engine invokes them directly mid-generation. We bridge rather than fight: the chat's bound app tools are wrapped as LiteRT `ToolProvider`s at conversation-creation time, the engine drives the loop (with constrained decoding forcing valid tool syntax), and the wrappers report `ApiState.ToolCall` events into the run timeline as they fire.

## Considered Options

- **Keep the app-owned loop** by prompting the model to emit tool-call JSON and parsing its text output: rejected as fragile on small on-device models (malformed JSON, hallucinated tool names) and because it forfeits LiteRT's constrained decoding.

## Consequences

- Tool execution happens inside the engine's `sendMessageAsync` call, so timeline events arrive during streaming rather than between requests.
- Tool support is a per-model capability flag in the Model Catalog, since small models vary widely in function-calling competence.
