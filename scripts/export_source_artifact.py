#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SAFE_REVISION = re.compile(r"[0-9a-f]{40}")
PRODUCTION_FILES = (
    Path("apple/Sources/NoopBandCore/NoopBandModels.swift"),
    Path("apple/Sources/NoopBandCore/BandDiagnostics.swift"),
    Path("apple/Sources/NoopBandCore/BandSessionMachine.swift"),
    Path("android/src/main/kotlin/com/noop/bandsdk/Models.kt"),
    Path("android/src/main/kotlin/com/noop/bandsdk/Diagnostics.kt"),
    Path("android/src/main/kotlin/com/noop/bandsdk/BandSessionMachine.kt"),
)
TEST_SUPPORT_FILES = (
    Path("apple/Sources/NoopBandCore/VirtualBand.swift"),
    Path("android/src/main/kotlin/com/noop/bandsdk/VirtualBand.kt"),
)
CONTRACT_FILES = (
    Path("spec/capabilities.schema.json"),
    Path("conformance/scenarios.json"),
)


class ExportError(RuntimeError):
    pass


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(64 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def destination_for(relative: Path, category: str) -> Path:
    if relative.parts[0] == "apple":
        return Path(category, "apple", relative.name)
    if relative.parts[0] == "android":
        return Path(category, "android", relative.name)
    return Path(category, relative.parts[-2], relative.name)


def git_output(*arguments: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(ROOT), *arguments],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    if result.returncode != 0:
        raise ExportError("repository revision could not be verified")
    return result.stdout.strip()


def verify_source_revision(revision: str) -> None:
    if git_output("rev-parse", "--verify", "HEAD") != revision:
        raise ExportError("revision must match the checked-out commit")
    if git_output("status", "--porcelain=v1", "--untracked-files=all"):
        raise ExportError("source repository must be clean before export")


def export(revision: str, output: Path) -> int:
    if SAFE_REVISION.fullmatch(revision) is None:
        raise ExportError("revision must be a full lowercase commit SHA")
    verify_source_revision(revision)
    if output.exists():
        if not output.is_dir() or any(output.iterdir()):
            raise ExportError("output directory must not exist or must be empty")
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(
        tempfile.mkdtemp(
            prefix=f".{output.name}.",
            dir=output.parent,
        )
    )

    try:
        entries: list[dict[str, object]] = []
        groups = (
            ("production", PRODUCTION_FILES),
            ("test-support", TEST_SUPPORT_FILES),
            ("contract", CONTRACT_FILES),
        )
        for category, files in groups:
            for relative in files:
                source = ROOT / relative
                if not source.is_file():
                    raise ExportError(f"missing source file: {relative}")
                destination_relative = destination_for(relative, category)
                destination = staging / destination_relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, destination)
                entries.append(
                    {
                        "path": destination_relative.as_posix(),
                        "bytes": destination.stat().st_size,
                        "sha256": digest(destination),
                    }
                )

        entries.sort(key=lambda entry: str(entry["path"]))
        manifest = {
            "schemaVersion": 1,
            "sourceRepository": "Dhanunjay-Divi/NoopBandSDK",
            "sourceRevision": revision,
            "supplierArtifactsIncluded": False,
            "files": entries,
        }
        manifest_path = staging / "noop-band-sdk-manifest.json"
        manifest_path.write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        if output.exists():
            output.rmdir()
        staging.replace(output)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise

    print(
        f"OK: exported {len(entries)} files for {revision} to {output}"
    )
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    result.add_argument("--revision", required=True)
    result.add_argument("--output", required=True, type=Path)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        return export(args.revision, args.output.resolve())
    except (OSError, ExportError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
