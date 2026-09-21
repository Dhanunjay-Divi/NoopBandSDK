# Round: 2026-09-21 - Executable neutral wrapper

## Status

- State: `in progress`
- Branch: `codex/noop-band-sdk-core-20260921`
- Start commit: `ee82cc084d361c35267b4228af897e137a6fd66b`
- End commit: pending

## Objective

Turn the binary-free SDK boundary into executable, semantically matched Swift
and Kotlin software that is safe to integrate before supplier artifacts arrive.

## In scope

- Versioned neutral models and capability validation.
- One session generation and one serialized exclusive-operation queue.
- Stale callback and stale operation rejection.
- Live and historical sample lanes with deterministic deduplication.
- Durable storage receipt before historical acknowledgement or cursor advance.
- Unknown required protocol/capability revisions failing closed.
- Bounded identifier-free diagnostics.
- Deterministic virtual-band scenarios with cross-platform output parity.
- A digest-pinned source artifact export for later NOOP app integration.

## Out of scope

- Supplier binaries, headers, demo applications, or vendor persistence.
- Real BLE discovery, platform bonding, possession proof, haptics, alarms,
  autonomous flash behavior, sensor accuracy, background collection, or OTA.
- Public traffic, health upload, production activation, or hardware claims.
- Removing or replacing the current WHOOP test transport.

## Observability

The core exposes only fixed diagnostic kinds, fixed outcomes, count buckets,
duration buckets, and a bounded ring. The event type has no arbitrary message,
identifier, timestamp, URL, payload, or health-value field. Tests must prove
capacity eviction and stale/busy/storage outcomes without logging sample data.

## Resource boundary

The host began this round with about 4.5 GiB free disk, below the NOOP app's
normal 10 GiB heavy-build floor. SDK tests are small and will run sequentially
through the bounded runner with a recorded 3 GiB narrow exception. Generated
Swift and Gradle outputs will be removed after evidence is captured.

## Verification plan

- `python3 scripts/check_repository.py`
- Swift build and test.
- Kotlin/JVM build and test.
- Shared conformance runner comparing parsed Swift and Kotlin results.
- Secret/binary/language guard.
- `git diff --check`

Physical and supplier gates remain explicitly open.

## Implementation

- Added executable Swift and Kotlin/JVM neutral session cores.
- Added deterministic virtual-band executables and one shared scenario
  contract.
- Enforced one exclusive operation, generation-bound callbacks, built-in
  capability checks, terminal close behavior, live/history lane separation,
  per-batch deduplication, and durable-before-acknowledgement history.
- Enforced exact history cursor continuity. A malformed or out-of-order cursor
  fails closed without advancing the last durable checkpoint.
- Bounded all callback-controlled identifiers, revisions, cursors, tokens,
  batches, and chunk sample counts. Kotlin rejects negative sample sequences to
  preserve Swift parity.
- Added a digest manifest exporter that refuses a dirty worktree or a revision
  other than exact `HEAD`, and publishes through a temporary directory.
- Added repository gates that inspect tracked and untracked non-ignored files
  and prohibit hosted workflows until budget approval.

## Verification evidence

- `python3 scripts/check_repository.py`
  - Pass: 37 files.
- `swift test --package-path apple`
  - Pass: 9 tests in one suite.
- `/Users/divii/noop-sandbox/Noop/android/gradlew -p android --no-daemon test installDist`
  - Pass: 10 tests; six Gradle tasks executed.
- `python3 scripts/run_conformance.py`
  - Pass: 13 Swift/Kotlin scenarios matched expected results.
- Dirty source export attempt using start commit
  `ee82cc084d361c35267b4228af897e137a6fd66b`
  - Expected rejection: `source repository must be clean before export`.
- `python3 -m json.tool` on both JSON contracts, Python byte compilation,
  `git diff --check`, and repository language/binary gates
  - Pass.

The first shared conformance attempt exposed two contract defects: Swift
omitted explicit null fields and a second operation reported `invalidState`
instead of `busy`. Both platform semantics and the expected contract were
corrected before the final passing wall.

## Remaining ordered work

1. Commit the verified implementation.
2. Export from that exact clean implementation commit and independently verify
   every manifest digest.
3. Record the exact implementation revision and close this round in a
   documentation-only commit.
4. Make the remote repository private before publishing the branch.
5. Merge normally to SDK `main`.
6. Vendor the digest-pinned source artifact into a protected NOOP application
   branch, default the virtual first-party source off, preserve WHOOP, compile
   both phone platforms, and merge only after protected checks pass.

## External gates

- Supplier binary and exact firmware/project identification.
- Written redistribution rights, SBOM, dependency notices, vulnerability
  review, and unexpected-egress audit.
- Approved possession proof and ownership handshake.
- Physical iOS and Android BLE, background, reconnect, flash overflow,
  battery, haptic, alarm, accuracy, and OTA evidence.
