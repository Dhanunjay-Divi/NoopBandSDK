# Active NOOP Band SDK handoff

Last updated: **2026-09-22**

## Current round

- [Final protected review remediation](rounds/2026-09-22-final-protected-review-remediation.md)

## Current boundary

- Protected branch: `main`
- Active branch: `codex/sdk-final-review-remediation-20260922`
- Start commit: `dab6072eb2b69b07ee34221dbb649a0119547246`
- Implementation commit: pending
- Evidence commit: pending
- Protected merge: pending
- Supplier binaries: absent and prohibited
- WHOOP app transport: unchanged in the separate NOOP application repository
- Production supplier adapter: unavailable pending approved artifacts and
  physical evidence
- Remote visibility: `PRIVATE`

The round may prove only deterministic software contracts. It cannot prove BLE,
background execution, haptics, flash retention, battery, sensor accuracy,
possession proof, or firmware update behavior.

## Current evidence

- Swift package: 30 tests pass.
- Kotlin/JVM: 31 tests pass and the conformance distribution builds.
- Shared contract: 33 Swift/Kotlin scenarios match the checked-in expected
  results.
- Repository gate: 41 files pass the language, binary, JSON, and
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

The prior protected review rounds are merged. The latest application review
found two additional connection-state gaps in the exact exported SDK:
connection completion is not fenced by its originating session generation,
and connection/authentication failures have no explicit terminal API. This
isolated round is upstreaming the matched Apple and Android correction before
any application artifact is repinned. Supplier and physical-device gates
remain separate.
