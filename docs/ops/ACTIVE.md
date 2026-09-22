# Active NOOP Band SDK handoff

Last updated: **2026-09-22**

## Current round

- [PR 17 open-review closeout](rounds/2026-09-22-pr17-open-review-closeout.md)

## Current boundary

- Protected branch: `main`
- Active branch: `codex/sdk-pr17-open-review-closeout-20260922`
- Start commit: `bdeddf876af4a83c1f9607ea3b6345b969152ab4`
- Implementation commit: current branch candidate
- Evidence commit: current branch candidate
- Protected merge: `277c628d5a1fd9e747871e777d908e41460802fa`
- Supplier binaries: absent and prohibited
- WHOOP app transport: unchanged in the separate NOOP application repository
- Production supplier adapter: unavailable pending approved artifacts and
  physical evidence
- Remote visibility observed on 2026-09-22: `PUBLIC`

The round may prove only deterministic software contracts. It cannot prove BLE,
background execution, haptics, flash retention, battery, sensor accuracy,
possession proof, or firmware update behavior.

The protected final-closeout fixed persistence overlap, capability terminals,
and operation-authentication invalidation. Application PR `#17` then exposed
two deterministic SDK findings: Android checkpoint restore copied an unbounded
caller-owned set before validating its limit, and history acceptance exposed
only a count rather than exact provenance-bearing rows. This round now bounds
checkpoint traversal before retention, returns exact accepted rows with source
and parser/calibration provenance, makes Android acceptance collections
unmodifiable, and validates durable receipts against an independent staged
count. It does not enable a runtime transport.

## Current evidence

- The PR 17 final-closeout branch serializes live/history durable acceptance,
  adds explicit generation-fenced capability cancellation/failure terminals,
  invalidates authenticated sessions on operation authentication failure, and
  prevents cancellation, reconnect, restart, or close from discarding an
  unresolved persistence receipt. Exact negative history receipts release the
  reservation without advancing cursor or durable identity state.
- Current exact local evidence: Swift 40/40; Kotlin/JVM 47/47 plus
  `installDist` with zero compiler warnings; 35/35 shared Swift/Kotlin
  scenarios; and 48 repository files passing language, binary, JSON, and
  workflow gates. JSON and diff checks pass. Independent review found and
  reproduced one P1 mutable Android history-acceptance list and one P2 loss of
  batch provenance; both are corrected with direct regressions. Independent
  follow-up review found no remaining P0-P2 defect or Apple/Kotlin parity gap.
  Protected SDK review/merge, clean exports, application repin, and PR `#17`
  verification remain pending.
- Swift package: 33 tests pass.
- Kotlin/JVM: 36 tests pass and the conformance distribution builds.
- Shared contract: 33 Swift/Kotlin scenarios match the checked-in expected
  results.
- Repository gate: 44 files pass the language, binary, JSON, and
  hosted-workflow guards.
- JSON validation and `git diff --check` pass.
- Connection/authentication completion is generation-fenced on Apple and
  Android. Cancellation and categorized failure terminals have matched
  conformance coverage.
- Rejection of a second pending history chunk records the same bounded busy
  diagnostic on Apple and Android.
- Final independent read-only review found no P0/P1 defect and two P2 gaps:
  authentication diagnostic phase visibility and direct stale
  cancellation/failure coverage. Both are corrected and all post-fix local
  gates are green.
- The first focused follow-up found one P2 phase-attribution defect. Connection
  terminal callbacks now carry their originating bounded phase explicitly.
  The final focused follow-up found no remaining P0/P1/P2 defect or
  Apple/Kotlin parity drift.
- Independent read-only review initially found one P1 and four P2 edge defects;
  all five are corrected and the exact follow-up diff has no remaining finding.
- The current runtime-hardening review found two additional P1 defects:
  cross-machine receipts/Swift tokens could collide, and stale scan/reconnect
  terminal callbacks could mutate current state. Both are corrected with
  matched Apple, Kotlin, and shared conformance regressions. Focused follow-up
  found no remaining implementation defect and one P2 test gap; same-session
  replay of old live/history receipts and an old operation token now has direct
  matched regression coverage. The final narrow review of that added coverage
  returned no findings.
- Two clean exports from protected merge `34028a2` are byte-identical. Their
  manifest SHA-256 is
  `f0baf194ae0daa51e2d7c02d83b9324efd5bc278080aa622857ecfa32af54f8b`.
  This is historical evidence for the prior protected revision; a new clean
  export is required after this round merges.

The prior protected review rounds, including the security-failure session
terminal, are merged on SDK `main` at `277c628d`. The current isolated round
adds bounded rejection evidence for invalid history operation tokens on both
platforms. Focused and complete package tests, the distribution build,
repository gate, JSON/diff checks, and all 33 shared scenarios are locally
green. The related NOOP application WorkManager attempt fence passes 7/7 on
the API 35 managed device. An independent narrow follow-up found no remaining
P0/P1/P2 issue in the strengthened invalid-token coverage. Protected SDK
review/merge, two clean exports, application repin, and final PR `#17`
verification remain pending. Supplier and physical-device gates remain
separate.
