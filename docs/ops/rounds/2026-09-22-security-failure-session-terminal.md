# Round: 2026-09-22 - Security-failure session terminal

## Status

- State: `ready for protected review`
- Branch: `codex/sdk-security-terminal-20260922`
- Start commit: `44559aeb4b1b50af9e6ab8b8dc786f87821c72d9`
- Implementation commit: pending
- Protected merge: pending
- Clean export and NOOP application repin: pending

## Objective

Close the final deterministic NOOP application PR `#17` SDK review finding:
once a session reaches `securityFailure`, that session object and its nonce are
terminal. Recovery requires a newly constructed session object rather than
calling `beginScan()` on the compromised session.

## In scope

- Reject `beginScan()` from `securityFailure` on Apple and Android.
- Add matched direct regressions proving the old object stays terminal and a
  replacement object can scan.
- Update shared conformance and virtual-band scenarios where they currently
  reopen the same object.
- Run the complete local SDK gates, merge normally through the private SDK
  repository, export the protected revision twice, and repin the NOOP app.

## Out of scope

- Supplier binaries, firmware, credentials, packet captures, identifiers, or
  health data.
- Physical BLE, background execution, retention depth, haptics, battery,
  accuracy, ownership, flashing, or OTA claims.
- Removing or changing the independent WHOOP application transport.

## Starting evidence

- Protected SDK `main` is `44559ae`.
- The prior late-review round made operation security failures clear
  authenticated state and enter `securityFailure`.
- A subsequent application PR review correctly found that Apple and Android
  still permit `beginScan()` from that state, allowing the same session nonce
  to reopen.
- NOOP application PR `#17` exact head `7c79eaac` has all 30 hosted checks
  green. It remains open and unmerged until this SDK correction is exported,
  repinned, and reverified.

## Observability

The correction retains the existing fixed security-failure operation event and
state transition. The rejected restart is exposed through the existing
categorized invalid-state result. No new log event is warranted: deterministic
tests cover success and rejection, and the existing evidence contains no
identifiers, payloads, health values, raw errors, or high-frequency events.

## Verification plan

1. Add matched Apple and Android direct tests for terminal same-object restart
   and successful replacement-session scan.
2. Update affected virtual-band and shared conformance scenarios.
3. Run focused tests, then repository, Swift, Kotlin, distribution,
   conformance, JSON, and diff gates with bounded private logs.
4. Merge through protected SDK `main`, create two clean source-only exports,
   compare manifests and digests, then repin and verify the NOOP application.

## External gates

All supplier, hardware, legal, redistribution, security-review, signing,
store, carrier, physical-device, OTA, and physiological validation gates
remain open.

## Local verification

- Direct Swift regression: 1/1 passes.
- Direct Kotlin regression: 1/1 passes.
- Repository language/binary/JSON gate: 43 files pass.
- Swift package: 32 tests pass in one suite.
- Kotlin/JVM: 35 tests pass with zero failures or skips; `installDist`
  succeeds.
- Shared contract: all 33 automated Swift/Kotlin scenarios match the checked-in
  expected results and each other.
- `conformance/scenarios.json` parsing and `git diff --check`: pass.
- Independent clean-context read-only review found no P0, P1, or P2 defect.
  It noted that connection-originated immutability is exercised through shared
  conformance and replacement nonce isolation through the existing generic
  cross-session regressions.
- Bounded logs and statuses:
  `/tmp/noop-sdk-security-terminal-20260922/`.
- The first focused Android command did not execute because the source-only SDK
  repository has no wrapper at `android/gradlew`. The recorded application
  wrapper at `/Users/divii/noop-sandbox/Noop/android/gradlew` then ran the
  focused and complete SDK Gradle tasks successfully.

## Result

- Apple and Android reject `beginScan()` from `securityFailure` with the
  existing invalid-state category and do not mutate the terminal generation.
- A newly constructed session object can begin scanning normally.
- Operation and connection conformance paths now prove rejection on the old
  object and readiness only through the replacement object.
- Existing fixed-category diagnostics remain unchanged and identifier-free.

This proves deterministic neutral-core behavior only. It does not prove that a
supplier adapter invokes the boundary correctly or that physical BLE,
retention, background, power, haptic, firmware, or sensor behavior works.
