#!/usr/bin/env python3
"""Capture Android gfxinfo at a fixed time after tapping Send."""

import argparse
import re
import subprocess
import time
from pathlib import Path


METRICS = (
    "Total frames rendered",
    "Janky frames",
    "50th percentile",
    "90th percentile",
    "95th percentile",
    "Number Slow UI thread",
    "Number Slow issue draw commands",
)


def summary(gfxinfo: str) -> str:
    lines = []
    for metric in METRICS:
        match = re.search(rf"^{re.escape(metric)}: .+$", gfxinfo, re.MULTILINE)
        if match:
            lines.append(match.group(0))
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", default="emulator-5554")
    parser.add_argument("--delay", type=float, default=4.0)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        fixture = "Total frames rendered: 42\nJanky frames: 3 (7.14%)\n50th percentile: 8ms\n"
        assert summary(fixture).splitlines() == fixture.strip().splitlines()
        print("PASS: gfxinfo summary parser")
        return
    if args.delay <= 0:
        parser.error("--delay must be positive")

    adb = ("adb", "-s", args.device)
    subprocess.run((*adb, "shell", "dumpsys", "gfxinfo", "dev.chungjungsoo.gptmobile", "reset"), check=True, stdout=subprocess.DEVNULL)
    subprocess.run((*adb, "shell", "input", "tap", "954", "2232"), check=True)
    time.sleep(args.delay)
    captured = subprocess.check_output(
        (*adb, "shell", "dumpsys", "gfxinfo", "dev.chungjungsoo.gptmobile", "framestats"),
        text=True,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(captured)
    print(summary(captured))


if __name__ == "__main__":
    main()
