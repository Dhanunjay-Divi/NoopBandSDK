# Round: 2026-09-22 - PR 17 open-review closeout

## Status

- State: `ready for protected review`
- Branch: `codex/sdk-pr17-open-review-closeout-20260922`
- Start commit: `bdeddf876af4a83c1f9607ea3b6345b969152ab4`
- Protected merge: pending
- Clean export and NOOP application repin: pending

## Objective

Close the deterministic SDK findings still open on NOOP application PR `#17`
after the protected SDK closeout:

- bound restored Android history-checkpoint collection copying before
  retaining caller-owned data; and
- return the exact accepted history samples to Apple and Android storage
  adapters instead of exposing only a count.

The application artifact-verifier ancestor-symlink finding is handled in the
application repository and is not an SDK source change.

## Delivered

- Android restored history checkpoints now inspect the caller-owned set size,
  reject impossible or oversized sizes before iteration, bound traversal, and
  normalize traversal failures to `invalidInput` before retaining a snapshot.
- History acceptance now returns exact accepted rows on Apple and Android.
  Each row carries source identity, provenance lane, parser revision,
  calibration revision, and the accepted sample. Duplicate filtering remains
  identity-based and deterministic across batches.
- Apple and Android persistence fixtures commit only the exact accepted rows,
  rather than receiving the original unfiltered chunk beside an accepted
  count.
- Android live and history acceptance collections are unmodifiable snapshots.
  Both platforms retain an independent staged sample count for durable receipt
  validation, so caller-visible collection state cannot reduce the persistence
  requirement before cursor or durable-identity advancement.
- Matched Apple/Kotlin history tests cover durable duplicates, duplicates
  repeated under a second parser/calibration revision, and exact provenance for
  accepted rows. Kotlin additionally covers oversized/negative checkpoint
  sizes, forbidden traversal, traversal failure normalization, and attempted
  live/history collection mutation.

## Observability review

- Existing fixed-category history diagnostics already distinguish rejection,
  staging, durable completion, and storage failure. This round does not add a
  new lifecycle transition or a new opaque failure that warrants another event.
- Receipt validation continues to emit bounded count buckets and fixed failure
  categories only. No sample value, timestamp, source identity, parser or
  calibration revision, cursor, acknowledgement token, or exception text is
  logged.

## Safety boundary

- No supplier binary, firmware, credential, packet capture, identifier, or
  health data enters Git.
- WHOOP remains an independent application test transport.
- Deterministic software evidence does not prove BLE, background collection,
  flash retention, haptics, battery, accuracy, possession, flashing, or OTA.

## Verification plan

1. Add direct Kotlin adversarial checkpoint-copy tests.
2. Make history acceptance carry immutable accepted sample snapshots on Swift
   and Kotlin and update storage fixtures to persist only that subset.
3. Add matched duplicate-history regressions on both platforms.
4. Run the complete repository, Swift, Kotlin, distribution, conformance,
   JSON, and diff walls before protected review.

## Evidence

| Check | Result | Boundary |
|---|---|---|
| `swift test --package-path apple` | 40/40 passed | Apple accepted-row provenance, receipt serialization, lifecycle, diagnostics, checkpoint, and conformance contracts |
| Android `test installDist` | 47/47 passed; distribution built with zero compiler warnings | Kotlin immutable acceptance collections, bounded checkpoint restore, accepted-row provenance, receipts, and executable conformance surface |
| `python3 scripts/run_conformance.py` | 35/35 Swift/Kotlin scenarios matched checked-in expected results | Deterministic cross-platform state-machine parity |
| `python3 scripts/check_repository.py` | 48 files passed | Language, binary, JSON, and hosted-workflow policy |
| JSON parse and `git diff --check` | Passed | Checked-in structured contracts and diff hygiene |
| Independent pre-fix review | Found one P1 mutable Android history list and one P2 missing batch provenance | Both findings were reproduced and corrected in this round |
| Independent post-fix review | No remaining P0-P2 finding | Unmodifiable Android collections, independent staged counts, exact provenance rows, and Apple/Kotlin first-occurrence parity were rechecked |

## Remaining gates

- Commit, push, protected SDK pull request review, and protected merge.
- Two byte-identical clean exports from the protected merge, followed by the
  application artifact repin and exact app/platform gates.
- Supplier rights, exact-model transport implementation, firmware behavior,
  and physical Apple/Android band validation remain external.
