# Round: 2026-09-22 - Export consumer compatibility

## Status

- State: `locally verified; PR and protected-merge export pending`
- Branch: `codex/sdk-export-consumer-fix-20260922`
- Start commit: `c533c530bb8615d719b5a2adcf51ec189b6037eb`
- Implementation commit: `054f9ab414c6c1b8e741d22dcef1563e886206ce`
- Supplier artifacts: absent
- Physical-device claims: unchanged and unproven

## Objective

Keep the source-only conformance support consumable as a separate test module
without widening production token construction or shipping virtual-band code
in the application library.

## Reproduction

The SDK package passed because `VirtualBand` compiled in the same module as
the neutral core. The NOOP application correctly compiled exported production
sources as a library and exported virtual-band support as a separate test
target. That consumer build failed because the stale-callback scenario
directly invoked the internal `BandLiveToken` initializer.

## Change

- Obtain a real live token before reconnect on Apple and Android.
- Advance the session generation through the public reconnect contract.
- Submit the old token and old callback generation to preserve the same
  stale-callback assertion.
- Keep `BandLiveToken` construction internal.
- Reject direct live-token construction in exported Apple or Android
  virtual-band support at the repository gate.
- Re-run the complete SDK verification wall, export twice from the protected
  merge, compare the exports, and repin the application artifact.

## Evidence

- `python3 scripts/check_repository.py`: 54 repository files passed.
- `swiftc -parse apple/Sources/NoopBandCore/*.swift`: passed.
- JSON parse across repository JSON files: passed.
- `swift test --package-path apple --jobs 1`: 55/55 tests passed.
- Gradle 8.14.5
  `-p android --no-daemon --max-workers=1 test installDist`: 62/62 tests
  passed and the distribution built.
- `python3 scripts/run_conformance.py`: all 38 automated Swift/Kotlin
  scenarios matched.
- Separate exported-source consumer smoke using the application's real Swift
  package layout, with production source in a library target and virtual-band
  source in a test target: 15/15 tests passed.
- The consumer smoke used the original `@_exported import NoopBandSDK`, not
  testable or private access.
- `git diff --check`: passed.
- Bounded secret-pattern review of the candidate diff and tracked source:
  passed with no match.
- Resource guards remained healthy; the final checks completed with 62% free
  system memory and 28 GiB free disk.

## Remaining gates

- Review, commit, push, and merge normally.
- Produce two byte-identical exports from the merge revision.
- Verify the exported support in the application's separate test target.
- Physical BLE, background, battery, retention, haptic, accuracy, ownership,
  and firmware gates remain outside this deterministic correction.
