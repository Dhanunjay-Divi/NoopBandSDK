# Active NOOP Band SDK handoff

Last updated: **2026-09-21**

## Current round

- [Second protected review remediation](rounds/2026-09-21-second-protected-review-remediation.md)

## Current boundary

- Protected branch: `main`
- Start commit: `f32633a9fc63a9edd273f38e97b48c216a798234`
- Implementation commit:
  `db072eb59783fa7dbbc1e51e2c00cefb167467df`
- Evidence commit:
  `89dd6867f7e82d0bb4be99b07cf7349586a0b2f3`
- Protected merge:
  `34028a2ab56feb90ae774b0ee0055529ce175723`
- Supplier binaries: absent and prohibited
- WHOOP app transport: unchanged in the separate NOOP application repository
- Production supplier adapter: unavailable pending approved artifacts and
  physical evidence
- Remote visibility: `PRIVATE`

The round may prove only deterministic software contracts. It cannot prove BLE,
background execution, haptics, flash retention, battery, sensor accuracy,
possession proof, or firmware update behavior.

## Current evidence

- Swift package: 24 tests pass.
- Kotlin/JVM: 25 tests pass and the conformance distribution builds.
- Shared contract: 27 Swift/Kotlin scenarios match the checked-in expected
  results.
- Repository gate: all tracked and untracked non-ignored files pass the
  language, binary, JSON, and hosted-workflow guards.
- JSON validation and `git diff --check` pass.
- Independent read-only review initially found one P1 and four P2 edge defects;
  all five are corrected and the exact follow-up diff has no remaining finding.
- Two clean exports from protected merge `34028a2` are byte-identical. Their
  manifest SHA-256 is
  `f0baf194ae0daa51e2d7c02d83b9324efd5bc278080aa622857ecfa32af54f8b`.

The first protected review remediation is merged. A second review found nine
additional deterministic source-contract defects. Protected `main` now
contains the matched correction, independent review closure, and reproducible
export evidence. Digest-pinned application artifact regeneration remains in
NOOP application PR `#17`. Supplier and physical-device gates remain separate.
