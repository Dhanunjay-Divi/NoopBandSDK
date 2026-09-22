# Round: 2026-09-22 - PR 17 final review blockers

## Status

- State: `verified PR candidate`
- Branch: `codex/sdk-pr17-final-blockers-20260922`
- Start commit: `a486768efb873b57515926740d3efa19787de612`
- Implementation commit:
  `47b107be64c27027c10be1f295213609634562d5`
- Protected merge and application repin: pending

## Objective

Close the four deterministic neutral-SDK defects independently confirmed on
the exact NOOP application PR `#17` artifact:

- validate every supplied retained history range, including non-overflow
  chunks;
- normalize Kotlin capability collection-copy failures into one bounded
  invalid-input rejection;
- bind supplier live callbacks to the exact session instance rather than only
  a restartable generation number; and
- terminate the actual interrupted lifecycle phase when a session closes
  instead of manufacturing an unrelated connection cancellation.

## Scope and safety boundary

- Matched Swift and Kotlin production models, session machines, tests, and
  shared conformance where the public contract changes.
- Fixed-category, identifier-free diagnostics only.
- Source-only deterministic SDK behavior.
- No supplier binary, firmware, credential, packet capture, device identifier,
  or health data enters Git.
- WHOOP remains an independent application test transport. The NOOP
  first-party source factory remains disabled until supplier and physical
  acceptance gates pass.

## Starting evidence

- SDK integration branch `main` is `a486768`, merged through PR `#16`.
  GitHub currently reports no branch-protection rule and the repository has no
  hosted workflow by policy, so this round must not describe local evidence as
  a protected hosted check.
- Exact NOOP application PR `#17` head `5a74627d` passed all ten required
  hosted contexts but remains blocked by unresolved review threads.
- Three independent source reviews agree on the four defects above. They also
  confirm that scan authority, pending persistence, range propagation,
  live/history capability separation, overflow chronology, zero-retention
  history rejection, and terminal firmware failure are already implemented.
- No physical band, supplier adapter, background, battery, haptic, retention,
  physiology, or OTA behavior is established by this round.

## Observability

- Capability-copy failure must emit one
  `capability/rejected/invalidInput` terminal event and leave no negotiation
  in progress.
- Close must emit a bounded `cancelled` terminal for each actually active
  lifecycle or operation phase and must not emit an unmatched connection
  cancellation.
- Live callback rejection remains a typed result; no source identity, nonce,
  generation, sequence, sample timestamp, health value, payload, or exception
  text may enter diagnostics.
- Existing bounded event retention and coalescing remain unchanged.

## Verification plan

1. Add focused mirrored regressions before or with each correction.
2. Run complete Swift and Kotlin package walls and distribution.
3. Run shared conformance, repository, JSON, secret-pattern, and diff gates.
4. Review the exact diff independently.
5. Commit and publish one protected SDK candidate, merge normally, export
   twice, compare artifacts, and repin NOOP application PR `#17`.

## Implementation

- Swift and Kotlin now validate every sample against any supplied retained
  history range, regardless of overflow state.
- Kotlin capability collection snapshot failures transition the session to
  `incompatible`, clear the accepted report, emit exactly one bounded
  `capability/rejected/invalidInput` event, and fail with `invalidInput`.
- Live delivery now requires an opaque session-instance token in addition to
  the callback generation. A token from a different session is rejected as a
  stale callback even when both sessions have the same generation.
- Closing an idle session is idempotent and emits no fabricated cancellation.
  Closing an active session emits bounded cancellation events only for the
  actual lifecycle, live, and operation phases. A persistence-pending close is
  rejected against the actual live or history lane.
- Shared conformance now covers cross-session live callbacks, active
  live-plus-history close, and idle close without unmatched connection
  evidence.

## Verification evidence

- `swiftc -parse` over the changed Swift production and test files: passed.
- `swift test --package-path apple --jobs 1`: 55/55 passed. The first run
  correctly exposed two stale test assumptions; after mirrored fixture and
  diagnostic-expectation corrections, the complete rerun passed.
- Gradle 8.14.5
  `-p android --no-daemon --max-workers=1 test installDist`: 62/62 passed and
  the conformance distribution installed. The first run correctly exposed two
  invalid test setups; after retaining-range and negotiated-firmware fixture
  corrections, the complete rerun passed.
- `python3 scripts/run_conformance.py` against the built Swift and Kotlin
  executables: 38/38 scenarios matched. The first comparison found Apple
  returning from the new live-callback case while still collecting; Apple now
  durably acknowledges and stops both sessions before returning `ready`.
- `python3 scripts/check_repository.py`: 53 repository files passed language,
  binary, JSON, capability-schema, and workflow-policy gates.
- JSON parse, bounded secret-pattern scan, and `git diff --check`: passed.
- All heavy walls used the NOOP bounded runner with private logs, one-worker
  concurrency, resource monitoring, and process-group cleanup. The live
  resource probe remained at 61-62% free system memory; no simulator or Gradle
  process remained after either wall.
- Independent exact-diff review could not complete in the available agent
  environment. The first reviewer lost AWS credentials before reading the
  diff; the second remained non-responsive through repeated live waits and was
  closed without output. No independent finding is claimed.
- Local exact-diff review found no remaining P1/P2 issue. It specifically
  checked source compatibility, opaque token construction, cross-session
  authority, terminal-state preservation, fixed-category diagnostics, and
  Swift/Kotlin result parity.

## External gates

Supplier rights, exact-model artifacts, adapter APIs, BLE permissions and
pairing, possession proof, disconnected flash retention and overwrite,
background collection, haptics, battery, sensor accuracy, firmware
flashing/recovery, OTA, signing, store, legal, and physical Apple/Android
validation remain open.
