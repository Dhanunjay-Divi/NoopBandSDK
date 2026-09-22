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
SAFE_SCENARIO = re.compile(r"[a-z][a-z0-9_]{2,63}")
EXPECTED_RESULT_KEYS = {
    "events",
    "finalState",
    "acknowledgedCursor",
    "acceptedSamples",
    "failure",
}

SCHEMA_ANNOTATIONS = {
    "$schema",
    "$id",
    "title",
    "$comment",
}
SCHEMA_KEYWORDS = SCHEMA_ANNOTATIONS | {
    "type",
    "additionalProperties",
    "required",
    "properties",
    "allOf",
    "if",
    "then",
    "const",
    "enum",
    "minLength",
    "maxLength",
    "minimum",
    "maximum",
    "minItems",
    "uniqueItems",
    "items",
    "x-noop-maxUtf8Bytes",
}
SCHEMA_TYPES = {"object", "array", "string", "integer"}


class SchemaValidationError(ValueError):
    pass


def validate_schema_definition(
    schema: object,
    path: str = "$",
) -> None:
    if not isinstance(schema, dict):
        raise ValueError(f"{path}: schema node is not an object")
    unsupported = set(schema) - SCHEMA_KEYWORDS
    if unsupported:
        raise ValueError(
            f"{path}: unsupported schema keywords: {sorted(unsupported)}"
        )

    expected_type = schema.get("type")
    if expected_type is not None and expected_type not in SCHEMA_TYPES:
        raise ValueError(f"{path}: unsupported type {expected_type!r}")

    additional_properties = schema.get("additionalProperties")
    if (
        additional_properties is not None
        and not isinstance(additional_properties, bool)
    ):
        raise ValueError(f"{path}: additionalProperties is not a boolean")

    required = schema.get("required")
    if required is not None and (
        not isinstance(required, list)
        or not all(isinstance(key, str) for key in required)
        or len(required) != len(set(required))
    ):
        raise ValueError(f"{path}: required is invalid")

    properties = schema.get("properties")
    if properties is not None:
        if not isinstance(properties, dict):
            raise ValueError(f"{path}: properties is not an object")
        for key, child in properties.items():
            if not isinstance(key, str):
                raise ValueError(f"{path}: property name is not a string")
            validate_schema_definition(child, f"{path}.properties.{key}")

    all_of = schema.get("allOf")
    if all_of is not None:
        if not isinstance(all_of, list) or not all_of:
            raise ValueError(f"{path}: allOf is not a non-empty array")
        for index, child in enumerate(all_of):
            validate_schema_definition(child, f"{path}.allOf[{index}]")

    for keyword in ("if", "then", "items"):
        if keyword in schema:
            validate_schema_definition(
                schema[keyword],
                f"{path}.{keyword}",
            )

    enum_values = schema.get("enum")
    if enum_values is not None and (
        not isinstance(enum_values, list) or not enum_values
    ):
        raise ValueError(f"{path}: enum is not a non-empty array")

    for keyword in (
        "minLength",
        "maxLength",
        "minimum",
        "maximum",
        "minItems",
        "x-noop-maxUtf8Bytes",
    ):
        value = schema.get(keyword)
        if value is not None and (
            not isinstance(value, int)
            or isinstance(value, bool)
            or value < 0
        ):
            raise ValueError(f"{path}: {keyword} is not a non-negative integer")

    if (
        "minLength" in schema
        and "maxLength" in schema
        and schema["minLength"] > schema["maxLength"]
    ):
        raise ValueError(f"{path}: minLength exceeds maxLength")
    if (
        "minimum" in schema
        and "maximum" in schema
        and schema["minimum"] > schema["maximum"]
    ):
        raise ValueError(f"{path}: minimum exceeds maximum")
    if "uniqueItems" in schema and not isinstance(schema["uniqueItems"], bool):
        raise ValueError(f"{path}: uniqueItems is not a boolean")


def validate_schema_subset(
    schema: object,
    instance: object,
    path: str = "$",
) -> None:
    if not isinstance(schema, dict):
        raise ValueError(f"{path}: schema node is not an object")
    unsupported = set(schema) - SCHEMA_KEYWORDS
    if unsupported:
        raise ValueError(
            f"{path}: unsupported schema keywords: {sorted(unsupported)}"
        )

    expected_type = schema.get("type")
    type_matches = {
        "object": isinstance(instance, dict),
        "array": isinstance(instance, list),
        "string": isinstance(instance, str),
        "integer": isinstance(instance, int) and not isinstance(instance, bool),
    }
    if expected_type is not None:
        if expected_type not in type_matches:
            raise ValueError(f"{path}: unsupported type {expected_type!r}")
        if not type_matches[expected_type]:
            raise SchemaValidationError(
                f"{path}: expected {expected_type}"
            )

    if "const" in schema and instance != schema["const"]:
        raise SchemaValidationError(f"{path}: const mismatch")
    enum_values = schema.get("enum")
    if enum_values is not None:
        if not isinstance(enum_values, list):
            raise ValueError(f"{path}: enum is not an array")
        if instance not in enum_values:
            raise SchemaValidationError(f"{path}: value is not in enum")

    all_of = schema.get("allOf", [])
    if not isinstance(all_of, list):
        raise ValueError(f"{path}: allOf is not an array")
    for index, child in enumerate(all_of):
        validate_schema_subset(child, instance, f"{path}.allOf[{index}]")

    condition = schema.get("if")
    if condition is not None:
        try:
            validate_schema_subset(condition, instance, f"{path}.if")
        except SchemaValidationError:
            condition_matches = False
        else:
            condition_matches = True
        if condition_matches and "then" in schema:
            validate_schema_subset(schema["then"], instance, f"{path}.then")

    if isinstance(instance, dict):
        required = schema.get("required", [])
        if not isinstance(required, list) or not all(
            isinstance(key, str) for key in required
        ):
            raise ValueError(f"{path}: required is invalid")
        missing = [key for key in required if key not in instance]
        if missing:
            raise SchemaValidationError(
                f"{path}: missing required properties {missing}"
            )
        properties = schema.get("properties", {})
        if not isinstance(properties, dict):
            raise ValueError(f"{path}: properties is not an object")
        if schema.get("additionalProperties") is False:
            extras = set(instance) - set(properties)
            if extras:
                raise SchemaValidationError(
                    f"{path}: additional properties {sorted(extras)}"
                )
        for key, child in properties.items():
            if not isinstance(key, str):
                raise ValueError(f"{path}: property name is not a string")
            if key in instance:
                validate_schema_subset(
                    child,
                    instance[key],
                    f"{path}.{key}",
                )

    if isinstance(instance, str):
        minimum_length = schema.get("minLength")
        maximum_length = schema.get("maxLength")
        maximum_utf8_bytes = schema.get("x-noop-maxUtf8Bytes")
        if minimum_length is not None and len(instance) < minimum_length:
            raise SchemaValidationError(f"{path}: string is too short")
        if maximum_length is not None and len(instance) > maximum_length:
            raise SchemaValidationError(f"{path}: string is too long")
        if (
            maximum_utf8_bytes is not None
            and len(instance.encode("utf-8")) > maximum_utf8_bytes
        ):
            raise SchemaValidationError(
                f"{path}: UTF-8 representation is too long"
            )

    if isinstance(instance, int) and not isinstance(instance, bool):
        minimum = schema.get("minimum")
        maximum = schema.get("maximum")
        if minimum is not None and instance < minimum:
            raise SchemaValidationError(f"{path}: value is below minimum")
        if maximum is not None and instance > maximum:
            raise SchemaValidationError(f"{path}: value is above maximum")

    if isinstance(instance, list):
        minimum_items = schema.get("minItems")
        if minimum_items is not None and len(instance) < minimum_items:
            raise SchemaValidationError(f"{path}: array is too short")
        if schema.get("uniqueItems") is True:
            encoded = [
                json.dumps(item, sort_keys=True, separators=(",", ":"))
                for item in instance
            ]
            if len(encoded) != len(set(encoded)):
                raise SchemaValidationError(f"{path}: array is not unique")
        item_schema = schema.get("items")
        if item_schema is not None:
            for index, item in enumerate(instance):
                validate_schema_subset(
                    item_schema,
                    item,
                    f"{path}[{index}]",
                )


def repository_files() -> list[Path]:
    result = subprocess.run(
        [
            "git",
            "-C",
            str(ROOT),
            "ls-files",
            "--cached",
            "--others",
            "--exclude-standard",
        ],
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

    try:
        capability_schema = json.loads(
            schema_path.read_text(encoding="utf-8")
        )
        validate_schema_definition(capability_schema)
        live_only = {
            "schemaVersion": 2,
            "protocolVersion": "noop-band-v1",
            "hardwareRevision": "hw-1",
            "firmwareVersion": "fw-1",
            "historyDays": 0,
            "capabilities": ["heart_rate"],
            "liveStreams": ["heartRate"],
            "historyStreams": [],
        }
        history = {
            **live_only,
            "historyDays": 7,
            "historyStreams": ["heartRate"],
        }
        validate_schema_subset(capability_schema, live_only)
        validate_schema_subset(capability_schema, history)
        try:
            validate_schema_subset(
                capability_schema,
                {
                    **history,
                    "historyDays": 0,
                },
            )
        except SchemaValidationError:
            pass
        else:
            errors.append(
                "capability schema accepted zero-retention history"
            )
        try:
            validate_schema_subset(
                capability_schema,
                {
                    **live_only,
                    "hardwareRevision": "\u00e9" * 17,
                },
            )
        except SchemaValidationError:
            pass
        else:
            errors.append(
                "capability schema accepted oversized UTF-8 metadata"
            )
        unsupported_schema = json.loads(json.dumps(capability_schema))
        unsupported_schema["properties"]["futureOptional"] = {
            "pattern": ".*"
        }
        try:
            validate_schema_definition(unsupported_schema)
        except ValueError:
            pass
        else:
            errors.append(
                "capability schema definition accepted an unsupported keyword"
            )
    except (OSError, json.JSONDecodeError, ValueError) as error:
        errors.append(f"capability schema behavior is invalid: {error}")

    try:
        scenario_payload = json.loads(
            scenarios_path.read_text(encoding="utf-8")
        )
        scenarios = scenario_payload.get("scenarios")
        if (
            scenario_payload.get("schemaVersion") != 1
            or not isinstance(scenarios, list)
            or not scenarios
        ):
            errors.append("conformance scenario contract is invalid")
        else:
            identifiers: set[str] = set()
            automated = 0
            for scenario in scenarios:
                if not isinstance(scenario, dict):
                    errors.append("conformance scenario must be an object")
                    continue
                identifier = scenario.get("id")
                if (
                    not isinstance(identifier, str)
                    or SAFE_SCENARIO.fullmatch(identifier) is None
                ):
                    errors.append("conformance scenario id is invalid")
                    continue
                if identifier in identifiers:
                    errors.append(
                        f"duplicate conformance scenario id: {identifier}"
                    )
                identifiers.add(identifier)
                if scenario.get("automated") is True:
                    automated += 1
                    expected = scenario.get("expected")
                    if (
                        not isinstance(expected, dict)
                        or set(expected) != EXPECTED_RESULT_KEYS
                        or not isinstance(expected.get("events"), list)
                        or not expected["events"]
                        or not all(
                            isinstance(event, str) and event
                            for event in expected["events"]
                        )
                        or not isinstance(expected.get("finalState"), str)
                        or not isinstance(expected.get("acceptedSamples"), int)
                        or expected["acceptedSamples"] < 0
                        or (
                            expected.get("acknowledgedCursor") is not None
                            and not isinstance(
                                expected["acknowledgedCursor"], str
                            )
                        )
                        or (
                            expected.get("failure") is not None
                            and not isinstance(expected["failure"], str)
                        )
                    ):
                        errors.append(
                            f"automated scenario is invalid: {identifier}"
                        )
                elif (
                    scenario.get("automated") is not False
                    or not isinstance(scenario.get("externalGate"), str)
                    or not scenario["externalGate"]
                ):
                    errors.append(
                        f"external scenario gate is invalid: {identifier}"
                    )
            if automated == 0:
                errors.append("no automated conformance scenarios")
    except (OSError, json.JSONDecodeError):
        pass

    if any(
        path.relative_to(ROOT).parts[:2] == (".github", "workflows")
        for path in files
    ):
        errors.append("hosted workflows require explicit budget approval")

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    print(f"OK: {len(files)} repository files passed language, binary, and JSON gates")
    return 0


if __name__ == "__main__":
    sys.exit(main())
