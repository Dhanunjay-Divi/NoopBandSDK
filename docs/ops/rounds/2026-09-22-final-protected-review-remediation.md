# Round: 2026-09-22 - Final protected review remediation

## Status

- State: `ready for protected merge`
- Branch: `codex/sdk-final-review-remediation-20260922`
- Start commit: `dab6072eb2b69b07ee34221dbb649a0119547246`
- Implementation commit: `9234b0b6c2e3c10f5314ba9fe876d638e7df0f7a`
- Evidence commit: current record commit
- Protected merge: pending
- Clean export and application repin: pending

## Objective

Close the remaining deterministic findings from NOOP application PR `#17`
without weakening the neutral SDK boundary. The SDK remains the source of
truth; the application may consume only a clean, digest-pinned export after
matched Swift, Kotlin, and shared-contract verification.

## In scope

- Generation-fenced connection and authentication completion.
- Explicit categorized connection/authentication cancellation and failure.
- Bounded busy diagnostics when a second history chunk arrives before the
  first chunk has a durable receipt.
- Matched Swift and Kotlin state-machine behavior and conformance scenarios.
- Review of every non-outdated SDK-related PR `#17` thread.
- Clean source-only export for the application integration candidate.

## Out of scope

- Supplier binaries, firmware, credentials, packet captures, device
  identifiers, health data, or proprietary vendor source.
- Real BLE scanning, pairing, authentication, history retention, background
  execution, haptics, battery, physiological accuracy, OTA, or recovery claims.
- Enabling a first-party production transport or removing the independent
  WHOOP application test transport.
- Application-repository artifact-verifier changes, which remain in the
  corresponding NOOP application operations round.

## Starting evidence

- Private SDK `main` is `dab6072`.
- NOOP application PR `#17` consumes the exact `dab6072` source export.
- All currently running hosted checks exercise that exact export, but an
  independent review found that `connect` still has no callback-generation
  argument and immediately records success without explicit failure terminals.
- Application review separately found that the artifact verifier resolves the
  supplied root before checking whether the root itself is a symlink.

## Observability

Connection and authentication success, cancellation, rejection, timeout,
security failure, and stale callbacks must remain distinguishable using fixed
diagnostic kind/outcome/failure categories only. No address, serial, source
identity, generation value, vendor error, credential, payload, or health value
may enter diagnostics.

## Verification plan

1. Add focused Swift and Kotlin regressions for stale completion, categorized
   failure/cancellation, and successful completion.
2. Add shared scenarios where the behavior is representable in the existing
   conformance harness.
3. Run the repository gate, complete Swift and Kotlin suites, shared
   conformance, JSON validation, and diff hygiene.
4. Export twice from a clean detached exact revision and compare manifests and
   file digests.
5. Replace and repin the application artifact, correct the verifier
   root-symlink defect, then rerun applicable application gates.

## Local verification

- JSON validation and `git diff --check`: pass.
- Swift package: 30 tests pass.
- Kotlin/JVM: 31 tests pass; distribution build succeeds.
- Shared contract: 33 automated Swift/Kotlin scenarios match the checked-in
  expected results.
- Repository gate: 41 files pass language, binary, JSON, and hosted-workflow
  guards.
- Bounded logs and status files:
  `/tmp/noop-sdk-final-review-20260922/`.
- Independent read-only review found no P0/P1 defect and two P2 gaps:
  authentication had no distinct diagnostic boundary, and stale connection
  cancellation/failure callbacks lacked focused regression coverage.
- Both P2 gaps are corrected with matched Apple/Kotlin implementation and
  shared conformance assertions. The post-fix Swift, Kotlin, conformance,
  repository, JSON, and diff gates above are green.
- The first focused follow-up found one remaining P2: stale connection terminal
  diagnostics inferred phase from replacement-session state. The API now
  carries the originating bounded connection/authentication phase explicitly.
- Final focused independent follow-up found no remaining P0/P1/P2 defect and
  no Apple/Kotlin parity drift.

## Result

The neutral Apple and Kotlin cores now expose explicit connection and
authentication lifecycle APIs. Every lifecycle callback carries its originating
session generation; stale completion, cancellation, and failure callbacks fail
closed without mutating the replacement attempt. Terminal callbacks also carry
their originating bounded lifecycle phase, so a replacement session cannot
misclassify diagnostics. Connection and authentication have distinct bounded
diagnostic phases. Cancellation, rejection,
authentication failure, security failure, timeout, disconnect, permission,
unavailability, and internal failure terminate through fixed states and
identifier-free diagnostics. A rejected second pending history chunk now has
matched bounded busy diagnostics.

This result proves deterministic neutral-core behavior only. It does not prove
that a supplier adapter invokes the APIs correctly or that any physical band,
background process, secure key, storage, radio, or firmware behavior works.

## External gates

All supplier, physical-device, legal, redistribution, security, signing,
store, carrier, OTA, and physiological validation gates remain open.
