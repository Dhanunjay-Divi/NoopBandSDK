# Round: 2026-09-22 - PR 17 exact review remediation

## Status

- State: `verified PR candidate`
- Branch: `codex/sdk-pr17-exact-review-remediation-20260922`
- Start commit: `9bc2eedce34c61d49f68001a973fbbda793d04ed`
- Implementation commit:
  `c0652fd89a9cb248617e578c6cd8498960c8c302`
- Pull request: pending
- Supplier artifacts: absent
- Physical-device claims: unchanged and unproven

## Objective

Correct the deterministic SDK issues found by the NOOP application PR `#17`
exact-head review, preserve matched Swift and Kotlin behavior, and produce one
new clean source artifact for the application integration branch.

## In scope

- Normalize supplier-controlled Kotlin list-size failures to `invalidInput`.
- Preserve a restored source checkpoint when a different source connects first.
- Add a generation-fenced intentional disconnect lifecycle that returns the
  reusable session to `idle`.
- Keep diagnostics fixed, bounded, identifier-free, and semantically distinct
  from reconnect failure and terminal close.
- Add mirrored Swift, Kotlin, and shared conformance regressions.
- Export and repin the exact reviewed SDK merge into NOOP application PR `#17`.

## Out of scope

- Supplier binaries, firmware, credentials, packet captures, or health data.
- Physical BLE, background execution, disconnected-band flash behavior,
  haptics, battery, sensor accuracy, possession proof, firmware flashing, or
  OTA claims.
- Enabling the first-party transport or changing the independent WHOOP test
  transport.

## Observability

Intentional disconnect must have its own fixed diagnostic kind and terminal
outcome. Rejected stale or busy disconnect requests must remain diagnosable
without a source identity, candidate handle, reason text, callback payload,
sample value, cursor, token, or arbitrary exception string. Checkpoint-source
selection and malformed supplier collections remain visible through existing
fixed history/live rejection categories.

## Implementation

- Kotlin now normalizes a supplier `List.size` runtime failure to the existing
  `invalidInput` contract before iteration or indexed access.
- A restored history checkpoint remains available when a different source
  connects first and is consumed only when its matching source returns.
- Swift and Kotlin expose an intentional disconnect lifecycle with fixed
  reasons, a distinct bounded diagnostic kind, cancellation of the actual
  active live/operation phases, generation invalidation, and a reusable `idle`
  terminal.
- Swift advances generation before the first diagnostics suspension, making
  operation/live callbacks stale immediately. It retains the invalidated
  active phase until that diagnostic resumes so a concurrent close can still
  emit every actual cancellation before clearing authority.
- Shared conformance adds checkpoint-source mismatch and graceful disconnect
  scenarios, with matched event order and results on both platforms.

## Review remediation

The first independent exact-diff review found one P1 Swift actor-reentrancy
race: history or live staging could occur while disconnect was suspended on
its `began` diagnostic, after the pending-persistence check but before callback
authority was invalidated. Generation advancement now occurs before that
suspension. A direct regression attempts history staging during the suspension
and requires a bounded `staleCallback` rejection.

The first re-review found that clearing the invalidated phase too early could
hide history/live cancellation evidence if `close()` won the suspension race.
The phase now remains visible until the diagnostic resumes, and a close-race
regression requires history, live, and disconnect cancellation events. The
second re-review found the remaining live-only path: tokenless `stopLive()`
could change `disconnecting` back to `ready`. Both platforms now require the
explicit live-collecting state, and a direct Swift race regression requires the
disconnect to complete. Final corrected exact-diff review reports no remaining
P0-P2 finding.

## Verification evidence

- Focused Swift session suite after the final race correction: 60/60 passed.
- Focused Kotlin disconnect and persistence cases: passed.
- `swift test --package-path apple`: 60/60 passed.
- Gradle 8.14.5
  `-p android --no-daemon test installDist`: 66/66 passed and the
  conformance distribution built.
- `python3 scripts/run_conformance.py`: all 40 Swift/Kotlin scenarios matched.
- `python3 scripts/check_repository.py`: 57 repository files passed language,
  binary, and JSON gates.
- Canonical JSON parsing, bounded added-line credential-pattern review, and
  `git diff --check`: passed.
- Every heavy command used the NOOP bounded runner with private capped logs,
  memory/disk floors, deadlines, and process-group cleanup. No resource stop
  occurred.
- Independent corrected exact-diff review reports no remaining P0-P2 finding.
- No supplier binary, firmware, credential, packet capture, identifier,
  health value, or arbitrary exception text was added.

## External gates

All supplier, physical-device, legal, redistribution, security, signing,
carrier, store, OTA, and accuracy gates remain open.

## Remaining gates

- Commit and push once, open a normal pull request, and merge after review.
- Produce two byte-identical clean exports from the merge.
- Repin the NOOP application PR `#17` and rerun its artifact, app-boundary,
  platform-build, release-control, and hosted exact-SHA gates.
