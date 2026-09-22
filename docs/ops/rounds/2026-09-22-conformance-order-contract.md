# Round: 2026-09-22 - Conformance order contract

## Status

- State: `locally verified; PR and integration pending`
- Branch: `codex/sdk-conformance-order-20260922`
- Start commit: `ed681a7a54330d50f0a207690b8cc3566f0f527b`
- Implementation commit: `4b18e235c1f32a69e3c771581b11082968642d14`
- Supplier artifacts: absent
- Physical-device claims: unchanged and unproven

## Objective

Make each exported conformance runner publish the exact ordered automated
scenario contract consumed by the NOOP application, and make the SDK's shared
verification fail closed when either platform drifts from that order.

## Reproduction

The SDK verifier iterated scenario IDs from `conformance/scenarios.json` and
invoked each platform by ID, so all 38 scenario results passed even though the
Kotlin runner published `live_callback_session_bound` in a different position.
The NOOP application correctly compared the exported ordered list with the
canonical contract and rejected SDK merge `ed681a7`.

## Change

- Align Kotlin's published scenario order with the canonical JSON contract.
- Add a structured `--list` mode to both conformance executables.
- Require exact ordered equality between the canonical contract, Swift runner,
  and Kotlin runner before executing scenario results.
- Preserve the app-side exact-order regression test.

## Observability and privacy

This is a deterministic build-time contract boundary. The verifier emits only
executable basenames, fixed scenario arguments, exit codes, and categorical
failure text; captured child stderr is never forwarded. It does not process or
emit health data, device identifiers, credentials, supplier data, or arbitrary
runtime payloads.

## Evidence

- `swift test --package-path apple --jobs 1`: 55/55 tests passed.
- Gradle 8.14.5
  `-p android --no-daemon --max-workers=1 test installDist`: 62/62 tests
  passed and the conformance distribution built.
- Both executable `--list` responses parsed as JSON arrays with 38 IDs and
  ended in the canonical order:
  `fractional_steps_rejected`, `live_callback_session_bound`,
  `close_active_phase_terminal`, `closed_session_terminal`.
- `python3 scripts/run_conformance.py`: both published ordered lists matched
  the canonical contract before all 38 Swift/Kotlin scenario results matched.
- `python3 scripts/check_repository.py`: 55 repository files passed language,
  binary, and JSON gates.
- Swift parsing across the core and conformance executable: passed.
- Independent JSON parsing across the three source-controlled JSON files:
  passed.
- `git diff --check`: passed.
- Bounded secret-pattern review of added diff lines: zero matches.
- The bounded runner held Swift, Kotlin, and shared-conformance output in
  private round-owned logs. The checks completed with 28 GiB free disk and no
  resource stop.
- Independent review found no P0/P1. It found two P2 and one P3 issue:
  malformed scenario entries could be filtered out, the handoff described the
  pre-fix verifier as current, and failed child stderr could be forwarded.
  All three findings were corrected.
- Two focused negative regressions passed: malformed automation state fails
  closed, and failed child stderr is not included in the verifier error.
- The corrected shared conformance rerun passed all 38 scenarios.
- Scoped re-review confirmed all three findings resolved with no remaining
  P0-P2 issue.

## Remaining gates

- Commit, push once, and merge normally.
- Export the exact merge twice and prove byte identity.
- Repin NOOP application PR `#17` and rerun its Apple/Android artifact gates.
- Physical BLE, background, battery, retention, haptic, accuracy, ownership,
  firmware flashing, and OTA gates remain separate.
