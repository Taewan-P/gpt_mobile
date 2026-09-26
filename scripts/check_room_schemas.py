#!/usr/bin/env python3
"""Require the main app's Room migration schemas to be committed."""
import base64
import gzip
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
# The pre-existing experimental DebugDatabase exports a separate, unversioned
# schema. This gate covers the versioned conversation/backup migration history.
schema_history = "app/schemas/dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2"
result = subprocess.run(
    ["git", "status", "--porcelain", "--untracked-files=all", "--", schema_history],
    cwd=root, check=True, capture_output=True, text=True,
)
if not result.stdout.strip():
    print("Exported Room schemas match the committed migration history.")
    sys.exit(0)
print("Commit the Room schema exported by this build before merging:")
print(result.stdout)
# A compact, lossless CI handoff for contributors without a local Android SDK.
for line in result.stdout.splitlines():
    path = line[3:]
    file = root / path
    if file.is_file() and file.suffix == ".json":
        print("ROOM_SCHEMA_PATH=" + path)
        print("ROOM_SCHEMA_GZIP_BASE64=" + base64.b64encode(gzip.compress(file.read_bytes(), mtime=0)).decode())
sys.exit(1)
