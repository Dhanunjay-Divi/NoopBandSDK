# Round: 2026-09-21 - Second protected review remediation

## Status

- State: `complete`
- Branch: `codex/sdk-second-review-remediation-20260921`
- Start commit: `f32633a9fc63a9edd273f38e97b48c216a798234`
- Implementation commit:
  `db072eb59783fa7dbbc1e51e2c00cefb167467df`
- Evidence commit:
  `89dd6867f7e82d0bb4be99b07cf7349586a0b2f3`
- Protected merge:
  `34028a2ab56feb90ae774b0ee0055529ce175723`

## Objective

Correct the nine additional deterministic contract defects found after the
first protected application review, prove matched Swift and Kotlin behavior,
and publish one reproducible source artifact for the NOOP application
integration branch.

## In scope

- Generation-fenced capability callbacks.
- Non-negative device-time and UTF-8 byte-length input domains.
- Per-history-operation durable completion state and advancing cursors.
- Firmware-specific diagnostics.
- Sampling-capability eligibility.
- A continuously bounded recent sample-identity cache.
- Explicit cancellation and failure terminals for active operations.
- Swift, Kotlin, and shared conformance regressions.
- A clean, digest-pinned source export after local verification.

## Out of scope

- Supplier binaries, firmware, credentials, BLE, background execution,
  haptics, flash retention, battery, physiology, or physical-device claims.
- Enabling a first-party production transport.
- Changing or removing the independent WHOOP application test transport.

## Observability

All changed paths use fixed diagnostic kinds and outcomes only. Capability
staleness, history stalls, operation cancellation/failure, unsupported
sampling, firmware lifecycle, and bounded-memory pressure must remain
diagnosable without device identifiers, sample values, timestamps, cursors,
tokens, payloads, or arbitrary exception text.

## Implementation

- Added generation fencing to capability callbacks so delayed negotiation
  results cannot mutate a replacement session.
- Defined one cross-platform input contract: UTF-8 byte limits for bounded
  strings and non-negative signed 64-bit sequence/device-time values.
- Reset durable completion state for every history operation, rejected
  nonterminal chunks that do not advance, and prohibited chunks after a
  terminal durable receipt.
- Routed firmware begin, completion, and rejection events through the firmware
  diagnostic family.
- Required sampling operations to negotiate at least one supported sensor
  capability.
- Replaced the unbounded identity set with a 65,536-entry recent cache while
  keeping application storage authoritative outside that window. Eviction
  occurs before insertion and uses the same stream wire-value order on both
  platforms.
- Added explicit cancellation and categorized failure terminals that clear the
  active operation and preserve live-collection state where applicable.
- A disconnected operation failure now clears live state, advances generation,
  enters recovery, and makes callbacks from the failed connection stale.
- Added fixed failure categories to diagnostics without adding identifiers,
  sample values, timestamps, cursors, tokens, or payloads.
- Kotlin UTF-8 validation is incremental, allocation-bounded, and rejects
  malformed surrogate pairs rather than silently replacing them.
- Documented the separate account-authentication, band-possession,
  supplier-authentication, capability, storage, reconnect, and collector-handoff
  boundaries without claiming unavailable supplier or physical behavior.

## Verification evidence

- `python3 scripts/check_repository.py`
  - Pass: 39 repository files.
- `swift test --package-path apple`
  - Pass: 24 tests in one suite.
- NOOP repository Gradle wrapper:
  `gradlew -p android --no-daemon test installDist`
  - Pass: 25 tests and the conformance distribution build.
- `python3 scripts/run_conformance.py`
  - Pass: 27 Swift/Kotlin scenarios matched checked-in expected results.
- JSON validation and `git diff --check`
  - Pass.
- Independent read-only source review:
  - Initially found one P1 and four P2 edge defects.
  - Pass after correction: all five findings resolved with no new regression.
- Exact clean export:
  - Two independently generated 10-file artifacts from protected merge
    `34028a2ab56feb90ae774b0ee0055529ce175723` are byte-identical.
  - Every manifest size and SHA-256 digest was independently recomputed.
  - Manifest SHA-256:
    `f0baf194ae0daa51e2d7c02d83b9324efd5bc278080aa622857ecfa32af54f8b`.

## Application follow-up

1. Replace and repin the generated artifact in NOOP application PR `#17`.
2. Run application Apple, Android, release-control, and hosted exact-SHA gates.
3. Preserve the independent WHOOP transport and keep the supplier path
   default-off until physical gates pass.

## External gates

All supplier, physical-device, legal, redistribution, security, OTA, and
accuracy gates remain open.
