# Round: 2026-09-22 - PR 17 final remediation

## Status

- State: `in progress`
- Branch: `codex/sdk-pr17-final-remediation-20260922`
- Start commit: `55fdd891fb3e9c4adf610e2b38a21b0adc3fa237`
- Implementation commit: pending
- Protected merge: pending
- Clean export and NOOP application repin: pending

## Objective

Close the final deterministic SDK findings raised on NOOP application PR `#17`
without enabling a supplier transport or changing the independent WHOOP test
path.

## In scope

- Reject oversized Kotlin live/history callback collections before copying the
  complete caller-owned collection.
- Distinguish a staged history chunk from its durable acknowledgement in bounded
  Apple and Android diagnostics.
- Reject fractional step counts on Apple and Android.
- Add matched focused tests and run the complete SDK repository, package,
  distribution, conformance, JSON, and diff gates.
- Merge normally through protected SDK review, export the protected revision
  twice, and repin the NOOP application.

## Out of scope

- Supplier binaries, firmware, credentials, packet captures, identifiers, or
  health data.
- Physical BLE, background execution, history depth, battery, haptics,
  accuracy, possession, flashing, or OTA claims.
- Enabling the first-party source factory or removing WHOOP.

## Starting evidence

- Protected SDK `main` is `55fdd891`.
- NOOP application PR `#17` head `4957659b` is locally green and has every
  hosted gate green except an iOS production-shell job still running at the
  start of this round.
- Fresh review found three SDK-owned defects: unbounded allocation before
  Kotlin collection validation, an ambiguous history completion diagnostic,
  and acceptance of fractional step counts.

## Observability

History staging will use a distinct fixed outcome while durable acknowledgement
retains `completed`. No source identity, token, cursor, sample value, timestamp,
payload, collection content, or raw error enters diagnostics. Oversized input
and fractional steps retain the existing fixed `invalidInput` rejection.

## Verification plan

1. Add matched Apple/Kotlin step and history-diagnostic regressions.
2. Add Kotlin oversized-collection regressions that fail before full traversal.
3. Run `python3 scripts/check_repository.py`.
4. Run `swift test --package-path apple`.
5. Run Android `test installDist` sequentially.
6. Run `python3 scripts/run_conformance.py`, JSON parsing, and
   `git diff --check`.
7. Review, commit, push once, merge normally, export twice, compare the
   manifests and tree digests, then repin the NOOP application.

## External gates

All supplier, hardware, legal, redistribution, security-review, signing,
store, carrier, physical-device, OTA, and physiological validation gates remain
open.

## Local verification

- Swift package: 35/35 tests pass.
- Kotlin/JVM: 39/39 tests pass; `installDist` succeeds.
- Shared contract: 34 automated Swift/Kotlin scenarios match the checked-in
  expected results and each other.
- Repository gate: 45 files pass language, binary, and JSON checks.
- `conformance/scenarios.json` parsing and `git diff --check`: pass.
- Bounded logs and statuses:
  `/tmp/noop-sdk-pr17-final-remediation-20260922/`.
- The first conformance attempt stopped before execution because a clean
  worktree had no Android distribution binary. The required Android
  `test installDist` wall then passed, and the unchanged conformance runner
  passed against that generated executable.

## Result

- Kotlin live/history snapshots reject an advertised or traversed collection
  beyond the per-batch, per-chunk, or total-history bound before copying the
  complete malformed input. Concurrent traversal failures map to
  `invalidInput`.
- Apple and Android record `history/staged` after in-memory acceptance and
  reserve `history/completed` for durable acknowledgement.
- Step samples accept whole counts from zero through one million and reject
  fractional counts on both platforms. The shared conformance suite includes
  this contract.
- No supplier transport, firmware, capability, application formula, or WHOOP
  path was enabled or changed.
