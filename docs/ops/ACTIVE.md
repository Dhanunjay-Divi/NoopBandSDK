# Active NOOP Band SDK handoff

Last updated: **2026-09-21**

## Current round

- [Protected review remediation](rounds/2026-09-21-protected-review-remediation.md)

## Current boundary

- Integration branch: `codex/sdk-review-remediation-20260921`
- Target branch: `main`
- Start commit: `e166773c5d3efd68dc5fa24488c9bbdf3ab6e97b`
- Implementation commit: pending
- Supplier binaries: absent and prohibited
- WHOOP app transport: unchanged in the separate NOOP application repository
- Production supplier adapter: unavailable pending approved artifacts and
  physical evidence
- Remote visibility: `PRIVATE`

The round may prove only deterministic software contracts. It cannot prove BLE,
background execution, haptics, flash retention, battery, sensor accuracy,
possession proof, or firmware update behavior.

## Current evidence

- Swift package: 9 tests pass.
- Kotlin/JVM: 10 tests pass and the conformance distribution builds.
- Shared contract: 13 Swift/Kotlin scenarios match the checked-in expected
  results.
- Repository gate: all tracked and untracked non-ignored files pass the
  language, binary, JSON, and hosted-workflow guards.
- Exporter: a dirty worktree is rejected before any artifact directory is
  published.
- Exact clean export: two independently generated 10-file artifacts from
  `0abd9a3ce4f808b51bdc93ad28504ac810914631` were byte-identical; every
  manifest size and SHA-256 digest was independently recomputed.

Protected application review found seven deterministic source-contract defects.
The active round corrects them in the SDK authority before regenerating the
digest-pinned application artifact. Supplier and physical-device gates remain
separate.
