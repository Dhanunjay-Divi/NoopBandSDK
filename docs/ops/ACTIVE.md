# Active NOOP Band SDK handoff

Last updated: **2026-09-21**

## Current round

- [Executable neutral wrapper](rounds/2026-09-21-executable-neutral-wrapper.md)

## Current boundary

- Branch: `codex/noop-band-sdk-core-20260921`
- Start commit: `ee82cc084d361c35267b4228af897e137a6fd66b`
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

The implementation commit, clean exact-revision export, private remote push,
and application artifact integration remain ordered work in this round.
