#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = ROOT / "conformance" / "scenarios.json"


class ConformanceError(RuntimeError):
    pass


def swift_binary() -> Path:
    result = subprocess.run(
        [
            "swift",
            "build",
            "--package-path",
            str(ROOT / "apple"),
            "--show-bin-path",
        ],
        check=False,
        capture_output=True,
        text=True,
        timeout=60,
    )
    if result.returncode != 0:
        raise ConformanceError("Swift binary path is unavailable")
    return Path(result.stdout.strip()) / "noop-band-conformance"


def default_kotlin_binary() -> Path:
    return (
        ROOT
        / "android"
        / "build"
        / "install"
        / "noop-band-conformance"
        / "bin"
        / "noop-band-conformance"
    )


def run_binary(binary: Path, scenario: str) -> dict[str, Any]:
    if not binary.is_file():
        raise ConformanceError(f"missing executable: {binary}")
    result = subprocess.run(
        [str(binary), scenario],
        check=False,
        capture_output=True,
        text=True,
        timeout=10,
    )
    if result.returncode != 0:
        raise ConformanceError(
            f"{binary.name} failed scenario {scenario}: "
            f"{result.stderr.strip()[:200]}"
        )
    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise ConformanceError(
            f"{binary.name} emitted invalid JSON for {scenario}"
        ) from error
    if not isinstance(payload, dict):
        raise ConformanceError(
            f"{binary.name} emitted a non-object for {scenario}"
        )
    return payload


def load_automated_scenarios() -> list[dict[str, Any]]:
    payload = json.loads(SCENARIOS.read_text(encoding="utf-8"))
    scenarios = payload.get("scenarios")
    if payload.get("schemaVersion") != 1 or not isinstance(scenarios, list):
        raise ConformanceError("scenario contract is invalid")
    automated = [
        scenario
        for scenario in scenarios
        if isinstance(scenario, dict) and scenario.get("automated") is True
    ]
    if not automated:
        raise ConformanceError("scenario contract has no automated cases")
    return automated


def verify(
    swift: Path,
    kotlin: Path,
) -> int:
    checked = 0
    for scenario in load_automated_scenarios():
        scenario_id = scenario.get("id")
        expected = scenario.get("expected")
        if not isinstance(scenario_id, str) or not isinstance(expected, dict):
            raise ConformanceError("automated scenario entry is invalid")
        expected_payload = {"scenario": scenario_id, **expected}
        swift_payload = run_binary(swift, scenario_id)
        kotlin_payload = run_binary(kotlin, scenario_id)
        if swift_payload != expected_payload:
            raise ConformanceError(
                f"Swift result differs from expected for {scenario_id}"
            )
        if kotlin_payload != expected_payload:
            raise ConformanceError(
                f"Kotlin result differs from expected for {scenario_id}"
            )
        if swift_payload != kotlin_payload:
            raise ConformanceError(
                f"platform results differ for {scenario_id}"
            )
        checked += 1
    print(f"OK: {checked} Swift/Kotlin conformance scenarios matched")
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    result.add_argument("--swift-bin", type=Path)
    result.add_argument("--kotlin-bin", type=Path)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        return verify(
            (args.swift_bin or swift_binary()).resolve(),
            (args.kotlin_bin or default_kotlin_binary()).resolve(),
        )
    except (OSError, subprocess.SubprocessError, ConformanceError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
