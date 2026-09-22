# Round: 2026-09-22 - Late capability state preservation

## Status

- State: `locally verified; PR review and integration pending`
- Branch: `codex/sdk-late-capability-state-20260922`
- Start commit: `823930fa16d30ea7849a557823215c913a36fb8b`
- Implementation commit:
  `03f37f4c3a7051c9ff1cb969d971c927bf5594ea`
- Supplier artifacts: absent
- Physical-device claims: unchanged and unproven

## Objective

Keep Kotlin capability negotiation fail-closed for malformed initial reports
without allowing a malformed delayed callback to terminate an established
ready session.

## Reproduction

`acceptCapabilities` snapshots supplier-owned collections before checking
whether capability negotiation is still active. A snapshot failure
unconditionally moved the session to `INCOMPATIBLE` and cleared the accepted
report, so one malformed delayed callback could terminate a healthy session.

## Change

- Mutate capability state on snapshot failure only while the session is
  actively negotiating capabilities.
- Preserve the existing ready-session state and accepted capability report for
  malformed delayed callbacks.
- Retain the existing bounded `capability/rejected/invalidInput` diagnostic.
- Add a Kotlin regression covering traversal failure and mutation during
  snapshot, including proof that live collection remains available.

## Observability and privacy

The correction reuses the existing fixed capability rejection event. It adds
no identifiers, supplier payloads, arbitrary exception text, health data, or
new runtime logging.

## Evidence

- Focused
  `BandSessionMachineTest.malformedLateCapabilityCallbackPreservesReadySession`:
  passed.
- Gradle 8.14.5
  `-p android --no-daemon --max-workers=1 test installDist`: 63/63 tests
  passed and the conformance distribution built.
- `swift test --package-path apple --jobs 1`: 55/55 tests passed.
- `python3 scripts/run_conformance.py`: 38/38 Swift/Kotlin scenarios matched.
- `python3 scripts/check_repository.py`: 56 repository files passed language,
  binary, and JSON gates.
- Swift parsing across the Apple source and executable: passed.
- All three source-controlled JSON files parsed successfully.
- `git diff --check`: passed.
- Bounded secret-pattern review of added lines found only expected test
  variable names containing `token`; no credential values or secret material
  were present.
- The bounded runner kept Kotlin, Swift, conformance, and repository output in
  private capped logs. No resource stop occurred.
- An independent sub-agent review was requested but the agent pool was full.
  No independent result is claimed. Fresh pull-request review remains a merge
  gate.

## Remaining gates

- Commit and push once.
- Obtain fresh pull-request exact-head review with no unresolved P0-P2 finding.
- Merge normally.
- Produce two byte-identical clean exports from the merge and repin the NOOP
  application.
- Physical BLE, background, battery, retention, haptic, accuracy, ownership,
  firmware flashing, and OTA gates remain separate.
