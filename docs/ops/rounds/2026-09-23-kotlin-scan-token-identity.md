# Round: 2026-09-23 - Kotlin scan-token identity

## Status

- State: `complete local SDK verification green; commit and pull request pending`
- Branch: `codex/sdk-pr23-scan-token-identity-20260923`
- Start commit: `1883ad33e840b1aa8b257f4f601f299f883678ca`

## Objective

Close the Android application-integration finding that Kotlin `internal`
visibility is not an authority boundary when SDK sources compile directly into
the application module.

## Implementation

- `BandSessionMachine` retains the exact scan token issued by `beginScan`.
- Scan callbacks require reference identity in addition to nonce and
  generation.
- Successful selection and terminal scan paths clear the retained token.
- A same-module regression proves a field-identical forged token is rejected
  while the issued token remains usable.

## Observability And Limits

The correction reuses bounded `discovery/stale/staleCallback` evidence and adds
no identifiers, payloads, health values, or arbitrary errors. It proves only
deterministic token authority, not BLE, background, retention, haptics,
battery, accuracy, flashing, or OTA behavior.

## Verification

- Focused Kotlin regression: passed.
- Complete Kotlin/JVM tests and `installDist`: passed.
- Swift package: 62/62 passed.
- Shared Swift/Kotlin conformance: 41/41 matched.
- Repository language, binary, JSON, schema, and workflow gate: 59 files
  passed.
- `git diff --check`: passed.
