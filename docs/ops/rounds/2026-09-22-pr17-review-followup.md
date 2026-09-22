# Round: 2026-09-22 - PR 17 review follow-up

## Status

- State: `in progress`
- Branch: `codex/sdk-pr17-review-followup-20260922`
- Start commit: `ee69f0d65d92cdf182ef514951464b5f260c21ae`
- Protected merge: pending

## Objective

Close the final read-only review findings before the NOOP application consumes
the protected SDK export.

## Changes

- Preserve callback-generation, session-state, ownership, and busy rejection
  ordering before bounded Kotlin collection snapshotting.
- Record fixed `rejected/invalidInput` diagnostics when bounded snapshot or
  validation rejects malformed live/history input.
- Cover iterator overflow, advertised-size mismatch, traversal failure
  normalization, lifecycle ordering, and explicit diagnostic-index presence.

## Safety boundary

No supplier adapter, transport, firmware, credential, identifier, health value,
or WHOOP path changes. Physical-device gates remain open.

## Verification

- Kotlin/JVM: 40/40 tests pass; `installDist` succeeds.
- Swift package: 35/35 tests pass.
- Shared contract: 34 Swift/Kotlin scenarios match.
- Repository gate: 46 files pass.
- JSON and `git diff --check`: pass.
- Bounded evidence:
  `/tmp/noop-sdk-pr17-review-followup-20260922/`.
