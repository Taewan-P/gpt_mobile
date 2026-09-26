# GPT Mobile AI (Improved) v0.9.16.0

## Backup and restore

- Rebuild modern and migrated backups in a fresh current database before replacing app data.
- Restore records by column name, preserving values when column order changes, applying defaults for new optional fields, and accepting retired fields.
- Preserve existing data when a backup is corrupt, incompatible, or missing required records. Existing encrypted and section-selective backups remain supported.

## Local models

- Separate NPU packages from GPU/CPU downloads so Snapdragon phones no longer silently download an incompatible NPU file for GPU use.
- QNN marketplace recommendations and Hugging Face results match the phone chipset. Hide QNN selection on unsupported phones.
- GPU/CPU requests use LiteRT even when QNN is preferred; reuse warm engines and avoid trying NPU-only binaries on GPU/CPU.
- Simplify the library with a separate Settings tab and a Profile button for existing profiles.
- Preserve downloaded Hub model capabilities alongside model files, including in model backups.

## Themes, tools and conversations

- Five theme presets plus named custom theme profiles, selection, deletion and backup support.
- Tool connections gain a Settings tab for context and usage limits, with maximum app allowances by default.
- Simpler built-in model delegation setup for downloaded, Ollama and llama profiles, with advanced limits collapsed.
- One flat conversation input surface; themed favourite labels, add controls, home divider, settings buttons and response revision controls.
- Matching themed ellipsis controls expand copy, select and edit actions on user and assistant messages.
- Red error text; context/token estimates only in Debug mode; a clear preparation message above the loading bar.
- Disable unavailable Free provider selections, accept known endpoint forms from older profiles, strip incompatible provider options, surface empty responses, and offer continuation at response limits.

## Validation

- Regression coverage for schema normalization and rollback, package/backend selection, chipset eligibility, saved themes and Free response handling.
- Physical GPU/NPU execution depends on device drivers and the exact compiled package; release validation does not replace testing on a supported phone.
