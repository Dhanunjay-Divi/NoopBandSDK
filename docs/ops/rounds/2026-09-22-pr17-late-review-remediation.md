# Round: 2026-09-22 - PR 17 late review remediation

## Status

- State: `ready for protected review`
- Branch: `codex/sdk-pr17-late-review-20260922`
- Start commit: `78c17cbd495353f33b5ef169bd1200ee9a0c35df`
- Implementation commit: pending
- Protected merge: pending
- Clean export and NOOP application repin: pending

## Objective

Close deterministic SDK defects found by the final NOOP application PR `#17`
review without changing the neutral source-only boundary or enabling a supplier
transport.

## In scope

- Snapshot caller-owned Kotlin live/history collections before validation and
  processing.
- Make operation-level security failures invalidate the authenticated session
  and enter the terminal security-failure state on Apple and Android.
- Add matched focused and conformance coverage.
- Run the complete local SDK gates, merge normally through the private SDK
  repository, export the protected revision twice, and repin the NOOP app.

## Out of scope

- Supplier binaries, firmware, credentials, packet captures, identifiers, or
  health data.
- Physical BLE, background execution, retention depth, haptics, battery,
  accuracy, ownership, flashing, or OTA claims.
- Removing or changing the independent WHOOP application transport.

## Starting evidence

- Protected SDK `main` is `78c17cb`.
- NOOP application PR `#17` exact head `8a8960e5` passed all hosted checks, but
  three late P2 review threads remain unresolved.
- Two threads affect this SDK: Kotlin model collections can retain mutable
  caller backing, and non-firmware operation security failures currently
  return to ready/live state with the same identity and capability report.
- The app-specific WorkManager teardown finding remains owned by the NOOP
  application round.

## Observability

The changes retain fixed diagnostic kind, outcome, and failure categories.
They add no identifiers, payloads, health values, collection contents, raw
errors, or high-frequency events. A security failure remains observable as the
operation's bounded failed event while its terminal state is asserted through
deterministic state-machine tests.

## Verification plan

1. Add matched Swift/Kotlin security-terminal regressions and Kotlin mutable
   collection snapshot regressions.
2. Update shared conformance only where behavior is represented by the current
   harness.
3. Run repository, Swift, Kotlin, distribution, conformance, JSON, and diff
   gates with bounded logs.
4. Merge through protected SDK `main`, produce two clean source-only exports,
   compare manifests/digests, then repin and verify the NOOP application.

## Resource boundary

- Before SDK execution, the host had about 10.3 GiB free, no active Swift,
  Xcode, Gradle, or Kotlin compiler process, and the prior complete SDK Swift
  and Kotlin build outputs were each under 100 MiB.
- This source-only SDK round may use an 8.5 GiB free-disk floor for sequential
  Swift and Kotlin verification with capped private logs. The exception does
  not apply to the NOOP application, simulator, Xcode app, or Android app
  verification, which retain the repository's normal 10 GiB floor.

## External gates

All supplier, hardware, legal, redistribution, security-review, signing,
store, carrier, physical-device, OTA, and physiological validation gates
remain open.

## Local verification

- Repository language/binary/JSON gate: 42 files pass.
- Kotlin/JVM: 34 tests pass with zero failures or skips; `installDist`
  succeeds.
- Swift package: 31 tests pass in one suite.
- Shared contract: 33 automated Swift/Kotlin scenarios match the checked-in
  expected results and each other.
- `conformance/scenarios.json` parsing and `git diff --check`: pass.
- Bounded logs and statuses:
  `/tmp/noop-sdk-pr17-late-review-20260922/`.
- Superseded failures remain recorded: the first Kotlin compile exposed an
  incorrectly placed security branch, and the next run exposed a test-fixture
  cardinality assumption. The corrected complete Kotlin run is green.

## Result

- Kotlin live batches are copied once before validation and all subsequent
  processing uses that immutable snapshot.
- Kotlin history chunks copy both the outer batch collection and every nested
  sample collection before validation and processing.
- Swift retains its existing value-semantic array ownership with direct
  regression coverage.
- An operation-level security failure now clears operation, live, identity,
  and capability state; increments the callback generation; and enters the
  terminal security-failure state on Apple and Android.
- The old operation token is rejected as stale, and a new explicit scan and
  authentication flow is required before operations become available again.

This proves deterministic neutral-core behavior only. It does not prove that a
supplier adapter invokes the boundary correctly or that physical BLE,
retention, background, power, haptic, firmware, or sensor behavior works.
