#!/usr/bin/env python3
"""Compile literal Kotlin Regex patterns with ICU, the regex engine used by Android.

Desktop JVM tests use a different regex engine and accept some patterns Android
rejects (for example, [:]). Run on Linux with libicu installed. Interpolated
patterns still need behavioral tests because their runtime values are unknown.
"""

from __future__ import annotations

import ctypes
import ctypes.util
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONSTRUCTOR = re.compile(r'Regex\(\s*(?=")')
LITERAL = re.compile(r'"""(.*?)"""(?!")|("(?:[^"\\]|\\.)*")', re.DOTALL)
INTERPOLATION = re.compile(r'(?<!\\)\$(?:[A-Za-z_]|\{)')


class ParseError(ctypes.Structure):
    _fields_ = [
        ("line", ctypes.c_int32),
        ("offset", ctypes.c_int32),
        ("before", ctypes.c_uint16 * 16),
        ("after", ctypes.c_uint16 * 16),
    ]


class IcuRegex:
    def __init__(self) -> None:
        name = ctypes.util.find_library("icui18n")
        if not name:
            raise RuntimeError("Install the system libicu package to check Android regex compatibility.")
        library = ctypes.CDLL(name)
        version = re.search(r"\.so\.(\d+)", name)
        suffix = "_" + version.group(1) if version else ""

        def symbol(name: str):
            return getattr(library, name + suffix)

        self.open = symbol("uregex_open")
        self.open.argtypes = [ctypes.c_void_p, ctypes.c_int32, ctypes.c_uint32, ctypes.POINTER(ParseError), ctypes.POINTER(ctypes.c_int32)]
        self.open.restype = ctypes.c_void_p
        self.close = symbol("uregex_close")
        self.close.argtypes = [ctypes.c_void_p]
        self.close.restype = None
        self.error_name = symbol("u_errorName")
        self.error_name.argtypes = [ctypes.c_int32]
        self.error_name.restype = ctypes.c_char_p

    def check(self, pattern: str) -> str | None:
        if not pattern:
            return None
        data = pattern.encode("utf-16-le" if sys.byteorder == "little" else "utf-16-be")
        buffer = ctypes.create_string_buffer(data)
        status = ctypes.c_int32(0)
        position = ParseError()
        handle = self.open(buffer, len(data) // 2, 0, ctypes.byref(position), ctypes.byref(status))
        try:
            if status.value > 0:
                return f"{self.error_name(status.value).decode()}; pattern offset {position.offset}"
            return None
        finally:
            if handle:
                self.close(handle)


def main() -> int:
    roots = [pathlib.Path(arg) for arg in sys.argv[1:]] or [ROOT / "app/src/main/kotlin"]
    files = sorted({file for root in roots for file in (root.rglob("*.kt") if root.is_dir() else [root])})
    checker = IcuRegex()
    checked = skipped = errors = 0
    for path in files:
        source = path.read_text(encoding="utf-8")
        for constructor in CONSTRUCTOR.finditer(source):
            position = constructor.end()
            parts = []
            dynamic = False
            while match := LITERAL.match(source, position):
                literal = match.group(1) if match.group(1) is not None else match.group(2)
                dynamic |= INTERPOLATION.search(literal) is not None
                if not dynamic:
                    parts.append(literal if match.group(1) is not None else json.loads(literal.replace(r"\$", "$")))
                position = match.end()
                while position < len(source) and source[position].isspace():
                    position += 1
                if source[position:position + 1] != "+":
                    break
                position += 1
                while position < len(source) and source[position].isspace():
                    position += 1
            if dynamic or source[position:position + 1] not in {",", ")"}:
                skipped += 1
                continue
            pattern = "".join(parts)
            checked += 1
            error = checker.check(pattern)
            if error:
                errors += 1
                line = source.count("\n", 0, constructor.start()) + 1
                print(f"::error file={path},line={line}::Android ICU rejects regex: {error}")
    print(f"Android regex check: {checked} literal patterns, {skipped} dynamic patterns skipped, {errors} errors.")
    return int(errors > 0 or checked == 0)


if __name__ == "__main__":
    raise SystemExit(main())
