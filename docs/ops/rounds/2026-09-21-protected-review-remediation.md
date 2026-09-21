# Round: 2026-09-21 - Protected review remediation

## Status

- State: `complete`
- Branch: `codex/sdk-review-remediation-20260921`
- Start commit: `e166773c5d3efd68dc5fa24488c9bbdf3ab6e97b`
- Implementation commit:
  `f2c1e189d6e703ceecea3502e1ba9ea77d8e2bd7`

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

## Implementation

- Added source-scoped checkpoint restore and bounded checkpoint export. The
  checkpoint retains the last durable cursor, recent identities, and whether
  the acknowledged range still requires a terminal chunk.
- Capped Apple supplier sample sequences at `Int64.max`, matching Android's
  non-negative `Long` domain.
- Rejected live and history streams absent from the negotiated capability
  report.
- Returned `updateNotEligible` for unavailable firmware support and rejected
  firmware operations while live collection is active.
- Carried exact completion and overflow flags through history acceptance and
  required a durable receipt confirming those flags and their metadata commit.
  An incomplete range cannot complete until a terminal chunk is acknowledged.
- Added five shared cross-platform scenarios and mirrored source tests.

## Verification evidence

- `python3 scripts/check_repository.py`
  - Pass: 38 files.
- `swift test --package-path apple`
  - Pass: 15 tests in one suite.
- `/Users/divii/noop-sandbox/Noop/android/gradlew -p android --no-daemon test installDist`
  - Pass: 16 tests and the conformance distribution build.
- `python3 scripts/run_conformance.py`
  - Pass: 18 Swift/Kotlin scenarios matched expected results.
- JSON validation and `git diff --check`
  - Pass.

## Remaining ordered work

1. Merge this private SDK branch normally.
2. Export exact implementation revision
   `f2c1e189d6e703ceecea3502e1ba9ea77d8e2bd7`.
3. Replace and repin the source artifact in NOOP application PR `#17`.
4. Run application artifact, Apple, Android, release-control, and hosted
   exact-SHA gates before protected application integration.

## External gates

All supplier, physical-device, legal, redistribution, security, OTA, and
accuracy gates remain open.
