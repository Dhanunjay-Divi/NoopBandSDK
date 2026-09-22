# Round: 2026-09-22 - PR 17 final contract closeout

## Status

- State: `locally verified; protected review and merge pending`
- Branch: `codex/sdk-pr17-final-followup-20260922`
- Start commit:
  `7794bae631c1704e18ae5c341fbc32e89c9dc647`
- Implementation commit: pending
- Protected merge: pending
- Clean export and NOOP application repin: pending

## Objective

Close four deterministic contract findings from NOOP application pull request
`#17` without enabling a supplier transport or changing WHOOP:

- revalidate Apple scan authority after the diagnostic actor suspension;
- preserve a pending live or history persistence receipt when an established
  session reports authentication or security failure;
- distinguish supported live streams from supported history streams; and
- carry retained and first-lost history ranges through overflow acceptance and
  the exact durable receipt.

## Scope

- Matched Swift and Kotlin neutral models, session machines, fixtures, tests,
  and shared conformance.
- Source-only export and digest-pinned application consumption after protected
  SDK integration.
- Fixed-category bounded diagnostics only.

Out of scope:

- supplier binaries, firmware, credentials, packet captures, or device
  identifiers;
- physical scanning, pairing, authentication, history offload, haptics, OTA,
  battery, background execution, or sensor accuracy;
- removing or routing the independent WHOOP application transport through this
  SDK.

## Starting evidence

- Protected SDK `main` is at PR `#14` merge `7794bae`.
- The exact NOOP application PR `#17` head passed all ten required hosted
  contexts before four new review threads identified the missing contracts.
- The application review threads remain open; the passing build is not treated
  as proof that the findings are resolved.

## Observability and privacy

- Lifecycle corrections reuse existing fixed diagnostic kind, outcome, failure
  category, count bucket, and duration bucket fields.
- Retained and lost ranges are storage contract metadata. They must not enter
  diagnostic events or arbitrary console output.
- No health value, source identity, cursor, acknowledgement token, credential,
  or supplier payload is added to logs.

## Verification

Completed:

- `swift test --package-path apple`: 51/51 passed.
- Android `test installDist`: 55/55 passed and distribution built.
- `python3 scripts/run_conformance.py`: 35/35 matched.
- `python3 scripts/check_repository.py`: 51 files passed.
- JSON parse and `git diff --check`: passed.

Pending:

- two byte-identical clean exports from the protected merge;
- application artifact verification, Apple/Android integration tests, all
  protected application checks, and review-thread resolution.

## Safety boundary

- Deterministic tests prove software contracts only.
- WHOOP remains the default independent application test transport.
- The first-party source factory remains disabled until approved supplier
  artifacts and physical acceptance evidence exist.
