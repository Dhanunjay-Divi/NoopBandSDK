# Active NOOP Band SDK handoff

Last updated: **2026-09-21**

## Current round

- [Runtime state hardening](rounds/2026-09-21-runtime-state-hardening.md)

## Current boundary

- Protected branch: `main`
- Active branch: `codex/sdk-runtime-hardening-20260921`
- Start commit: `a04c263e7229532038b13c7da343a43747864390`
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

- Swift package: 27 tests pass.
- Kotlin/JVM: 28 tests pass and the conformance distribution builds.
- Shared contract: 30 Swift/Kotlin scenarios match the checked-in expected
  results.
- Repository gate: all tracked and untracked non-ignored files pass the
  language, binary, JSON, and hosted-workflow guards.
- JSON validation and `git diff --check` pass.
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

The first two protected review rounds are merged. Application integration found
additional deterministic runtime-state gaps. This isolated round is upstreaming
the matched Apple and Android correction before any application artifact is
repinned. Supplier and physical-device gates remain separate.
