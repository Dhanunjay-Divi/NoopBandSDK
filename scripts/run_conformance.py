#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = ROOT / "conformance" / "scenarios.json"
SAFE_SCENARIO = re.compile(r"[a-z][a-z0-9_]{2,63}")


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


def run_binary_json(binary: Path, argument: str) -> Any:
    if not binary.is_file():
        raise ConformanceError(f"missing executable: {binary}")
    result = subprocess.run(
        [str(binary), argument],
        check=False,
        capture_output=True,
        text=True,
        timeout=10,
    )
    if result.returncode != 0:
        raise ConformanceError(
            f"{binary.name} failed argument {argument} "
            f"with exit code {result.returncode}"
        )
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise ConformanceError(
            f"{binary.name} emitted invalid JSON for {argument}"
        ) from error


def run_scenario(binary: Path, scenario: str) -> dict[str, Any]:
    payload = run_binary_json(binary, scenario)
    if not isinstance(payload, dict):
        raise ConformanceError(
            f"{binary.name} emitted a non-object for {scenario}"
        )
    return payload


def run_scenario_list(binary: Path) -> list[str]:
    payload = run_binary_json(binary, "--list")
    if (
        not isinstance(payload, list)
        or any(not isinstance(item, str) for item in payload)
    ):
        raise ConformanceError(
            f"{binary.name} emitted an invalid scenario list"
        )
    return payload


def load_automated_scenarios() -> list[dict[str, Any]]:
    payload = json.loads(SCENARIOS.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ConformanceError("scenario contract is invalid")
    scenarios = payload.get("scenarios")
    if payload.get("schemaVersion") != 1 or not isinstance(scenarios, list):
        raise ConformanceError("scenario contract is invalid")

    automated: list[dict[str, Any]] = []
    identifiers: set[str] = set()
    for scenario in scenarios:
        if not isinstance(scenario, dict):
            raise ConformanceError("scenario contract entry is invalid")
        identifier = scenario.get("id")
        if (
            not isinstance(identifier, str)
            or SAFE_SCENARIO.fullmatch(identifier) is None
            or identifier in identifiers
        ):
            raise ConformanceError("scenario contract ID is invalid")
        identifiers.add(identifier)

        is_automated = scenario.get("automated")
        if not isinstance(is_automated, bool):
            raise ConformanceError("scenario automation state is invalid")
        if is_automated:
            if not isinstance(scenario.get("expected"), dict):
                raise ConformanceError(
                    f"automated scenario entry is invalid: {identifier}"
                )
            automated.append(scenario)
        elif (
            not isinstance(scenario.get("externalGate"), str)
            or not scenario["externalGate"]
        ):
            raise ConformanceError(
                f"external scenario gate is invalid: {identifier}"
            )

    if not automated:
        raise ConformanceError("scenario contract has no automated cases")
    return automated


def verify(
    swift: Path,
    kotlin: Path,
) -> int:
    scenarios = load_automated_scenarios()
    scenario_ids = [scenario.get("id") for scenario in scenarios]
    if any(not isinstance(scenario_id, str) for scenario_id in scenario_ids):
        raise ConformanceError("automated scenario ID is invalid")

    swift_scenarios = run_scenario_list(swift)
    kotlin_scenarios = run_scenario_list(kotlin)
    if swift_scenarios != scenario_ids:
        raise ConformanceError(
            "Swift published scenario order differs from contract"
        )
    if kotlin_scenarios != scenario_ids:
        raise ConformanceError(
            "Kotlin published scenario order differs from contract"
        )

    checked = 0
    for scenario in scenarios:
        scenario_id = scenario.get("id")
        expected = scenario.get("expected")
        if not isinstance(scenario_id, str) or not isinstance(expected, dict):
            raise ConformanceError("automated scenario entry is invalid")
        expected_payload = {"scenario": scenario_id, **expected}
        swift_payload = run_scenario(swift, scenario_id)
        kotlin_payload = run_scenario(kotlin, scenario_id)
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
