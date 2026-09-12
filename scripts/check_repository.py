#!/usr/bin/env python3

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PUBLIC_SUFFIXES = {
    ".c",
    ".cc",
    ".cpp",
    ".h",
    ".hpp",
    ".java",
    ".json",
    ".kt",
    ".kts",
    ".m",
    ".md",
    ".mm",
    ".py",
    ".sh",
    ".swift",
    ".txt",
    ".yaml",
    ".yml",
}
BINARY_SUFFIXES = {
    ".a",
    ".aar",
    ".apk",
    ".cer",
    ".dylib",
    ".ipa",
    ".jar",
    ".jks",
    ".key",
    ".keystore",
    ".mobileprovision",
    ".p12",
    ".pem",
    ".so",
}
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")


def repository_files() -> list[Path]:
    result = subprocess.run(
        ["git", "-C", str(ROOT), "ls-files"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode == 0 and result.stdout.strip():
        return [ROOT / line for line in result.stdout.splitlines()]

    ignored = {".git", ".build", ".gradle", "build", "DerivedData"}
    return [
        path
        for path in ROOT.rglob("*")
        if path.is_file() and not any(part in ignored for part in path.parts)
    ]


def main() -> int:
    errors: list[str] = []
    files = repository_files()

    for path in files:
        relative = path.relative_to(ROOT)
        if path.suffix.lower() in BINARY_SUFFIXES:
            errors.append(f"tracked binary is prohibited: {relative}")
            continue
        if ".framework" in path.parts or ".xcframework" in path.parts:
            errors.append(f"tracked Apple binary bundle is prohibited: {relative}")
            continue
        if path.suffix.lower() not in PUBLIC_SUFFIXES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            errors.append(f"non-UTF-8 public file: {relative}")
            continue
        if CJK.search(text):
            errors.append(f"non-English CJK text in public file: {relative}")

    schema_path = ROOT / "spec" / "capabilities.schema.json"
    scenarios_path = ROOT / "conformance" / "scenarios.json"
    reviewed_path = ROOT / "vendor" / "reviewed-artifacts.json"
    for path in (schema_path, scenarios_path, reviewed_path):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            errors.append(f"invalid JSON {path.relative_to(ROOT)}: {error}")

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    print(f"OK: {len(files)} repository files passed language, binary, and JSON gates")
    return 0


if __name__ == "__main__":
    sys.exit(main())
