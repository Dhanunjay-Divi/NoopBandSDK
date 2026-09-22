# Round: 2026-09-22 - History token observability

## Status

- State: `ready for protected review`
- Branch: `codex/sdk-history-observability-20260922`
- Start commit: `277c628d5a1fd9e747871e777d908e41460802fa`
- Implementation commit: current branch candidate
- Protected merge: pending
- Clean export and NOOP application repin: pending

## Objective

Close the remaining deterministic history-callback observability gap identified
on NOOP application PR `#17`. A stale, foreign, or cross-operation history
token must fail closed and emit the same bounded rejection evidence on Apple
and Android before the application consumes a new source artifact.

## In scope

- Record fixed `history/rejected/<typed category>` evidence when history
  staging or acknowledgement receives an invalid operation token.
- Preserve existing callback-generation fencing and token validation behavior.
- Add matched Apple and Kotlin regressions for staging and acknowledgement.
- Run the complete repository, Swift, Kotlin, distribution, conformance, JSON,
  and diff gates before protected review.
- Export only from a normally merged protected SDK revision.

## Out of scope

- Supplier binaries, firmware, credentials, packet captures, identifiers, or
  health data.
- Real BLE scanning, pairing, authentication, history retention, background
  execution, haptics, battery, accuracy, possession, flashing, or OTA claims.
- Enabling a first-party transport or changing the independent WHOOP
  application transport.

## Starting evidence

- Protected SDK `main` is
  `277c628d5a1fd9e747871e777d908e41460802fa`.
- NOOP application PR `#17` head
  `cdecd2ef587b6f95dd4f2cd535777373680e1ca6` consumes that exact SDK export.
- PR review thread `PRRT_kwDOTiE28c6kmhu_` correctly identifies that
  `stageHistoryChunk` and `acknowledgeHistory` validate the operation token
  before emitting a history diagnostic.
- Existing behavior already rejects the token with `invalidState` or
  `staleCallback`; the missing evidence is the bounded rejection event needed
  to explain a stopped history callback lane.

## Observability

The correction may record only the fixed diagnostic kind, outcome, and failure
category. It must not record operation tokens, generations, cursors, source
identities, device identifiers, sample values, timestamps, payloads, or raw
errors. The event is emitted once per rejected API call and does not add
per-sample or high-frequency success logging.

## Verification plan

1. Add matched Swift and Kotlin token-validation wrappers for both history APIs.
2. Add focused tests proving invalid staging and acknowledgement tokens emit
   `history/rejected/invalidState` or
   `history/rejected/staleCallback` and leave session state unchanged.
3. Run `python3 scripts/check_repository.py`.
4. Run `swift test --package-path apple`.
5. Run the Android `test installDist` wall with the NOOP Gradle wrapper.
6. Run `python3 scripts/run_conformance.py`, JSON validation, and
   `git diff --check`.
7. Review the exact diff, commit, push once, and merge only through protected
   review before producing two clean exports.

## Local verification

- Focused Swift regression: 1/1 pass.
- Complete Swift package: 33/33 pass.
- Focused Kotlin regression: build and selected test pass.
- Complete Kotlin/JVM package: 36/36 pass with zero failures or skips;
  `installDist` succeeds.
- Shared contract: all 33 Swift/Kotlin conformance scenarios match the
  checked-in expected results.
- Repository language, binary, and JSON gate: 44 files pass.
- JSON parse and `git diff --check`: pass.
- Independent narrow follow-up: no P0/P1/P2 finding. It confirmed both
  platforms cover staging and acknowledgement with superseded same-session and
  foreign-session tokens, add exactly one diagnostic per rejection, preserve
  the active history operation, and complete normally with the valid token.
- Bounded logs and statuses:
  `/tmp/noop-sdk-history-observability-20260922/`.
- The related NOOP application WorkManager regression compiles and passes
  7/7 on the API 35 managed device with zero failures, errors, or skips.

## Result

- `stageHistoryChunk` and `acknowledgeHistory` now catch only typed operation
  token validation failures, emit one fixed history rejection event with the
  existing bounded category, and rethrow without changing authorization or
  state-machine behavior.
- Matched Apple and Kotlin tests prove both a superseded same-session token and
  a foreign-session token are observable for both staging and acknowledgement,
  add exactly one bounded event per rejected call, fail closed, leave the
  active history operation intact, and permit the valid token to finish
  normally.
- No identifier, token, cursor, generation, source identity, sample value,
  timestamp, payload, or raw error enters diagnostics.

## External gates

All supplier, hardware, legal, redistribution, security-review, signing,
store, carrier, physical-device, OTA, and physiological validation gates remain
open and unchanged.
