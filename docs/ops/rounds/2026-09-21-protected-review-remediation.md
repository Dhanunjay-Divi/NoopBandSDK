# Round: 2026-09-21 - Protected review remediation

## Status

- State: `in progress`
- Branch: `codex/sdk-review-remediation-20260921`
- Start commit: `e166773c5d3efd68dc5fa24488c9bbdf3ab6e97b`
- Implementation commit: pending

## Objective

Correct the seven deterministic contract defects found during protected NOOP
application review, prove matched Swift and Kotlin behavior, and publish one
new clean source artifact for the application integration branch.

## In scope

- Source-scoped durable history checkpoint restore and export.
- One signed 64-bit sample-sequence domain across platforms.
- Negotiated stream-capability enforcement for live and history batches.
- Firmware-specific eligibility and live-collection exclusion.
- Durable history completion and overflow receipt semantics.
- Swift, Kotlin, and shared conformance regressions.
- A clean digest-pinned source export after local verification.

## Out of scope

- Supplier binaries, firmware, credentials, BLE, background execution, haptic
  behavior, flash retention, battery, physiology, or physical-device claims.
- Enabling a first-party production transport.
- Changing or removing the independent WHOOP application test transport.

## Observability

The changed rejection paths continue to use fixed diagnostic kinds and
outcomes only. Capability rejection, firmware/live exclusion, stale callback,
and durable receipt failure must remain diagnosable without identifiers,
sample values, cursors, tokens, payloads, or arbitrary exception text.

## Verification plan

- `python3 scripts/check_repository.py`
- `swift test --package-path apple`
- `/Users/divii/noop-sandbox/Noop/android/gradlew -p android --no-daemon test installDist`
- `python3 scripts/run_conformance.py`
- two clean artifact exports with recursive equality and independent digest
  verification
- `git diff --check`

## External gates

All supplier, physical-device, legal, redistribution, security, OTA, and
accuracy gates remain open.
