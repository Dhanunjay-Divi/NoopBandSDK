# Active NOOP Band SDK handoff

Last updated: **2026-09-21**

## Current round

- [Executable neutral wrapper](rounds/2026-09-21-executable-neutral-wrapper.md)

## Current boundary

- Branch: `codex/noop-band-sdk-core-20260921`
- Start commit: `ee82cc084d361c35267b4228af897e137a6fd66b`
- Implementation commit:
  `0abd9a3ce4f808b51bdc93ad28504ac810914631`
- Supplier binaries: absent and prohibited
- WHOOP app transport: unchanged in the separate NOOP application repository
- Production supplier adapter: unavailable pending approved artifacts and
  physical evidence

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

The private remote push, normal SDK-main integration, and protected NOOP
application artifact integration remain ordered work.
