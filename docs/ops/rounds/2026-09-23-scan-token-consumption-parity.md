# Round: 2026-09-23 - Scan-token consumption parity

## Status

- State: `implementation committed; pull request pending`
- Branch: `codex/sdk-scan-token-consumption-20260923`
- Start commit: `1b4c614e180a58130c3d1c5967841affe754242d`
- Implementation commit:
  `8338e503f3a002cf5e4f99b4265b2ad82b203596`
- Pull request: pending

## Objective

Make post-selection discovery callbacks fail with the same bounded
`staleCallback` contract on Swift and Kotlin without changing valid connection
authority.

## Starting evidence

- Kotlin retains the exact issued scan token and clears it after candidate
  selection.
- Swift validates only the scan token nonce and generation, so a consumed token
  reaches the later state guard and returns a different failure category.
- Independent application-diff review classified the divergence as P2 because
  adapters can take different retry and diagnostic paths for the same delayed
  supplier callback.

## Implementation

- Swift retains the active scan token while discovery is open.
- Selection, cancellation, scan failure, and session close consume the token.
- Swift validation requires the token to remain active in addition to matching
  the issuing session and generation.
- One shared scenario sends late select, cancel, and failure callbacks after a
  valid selection, requires `staleCallback` for each, and proves the valid
  connection can still reach ready.

## Observability And Limits

The correction reuses fixed `discovery/stale/staleCallback` evidence. It adds no
identifier, candidate, payload, health value, credential, or arbitrary error
text. Deterministic tests cannot prove BLE timing, background execution,
retention, haptics, battery, accuracy, flashing, or OTA behavior.

## Verification

- Focused Swift regression: 1/1 passed.
- Focused Kotlin regression: passed.
- The first direct suspension-test compile failed because the test used the
  nonexistent failure category `.connection`; the fixture was corrected to
  the valid terminal category `.timeout`, and the rerun passed 1/1.
- Direct Swift suspension-boundary regression: 1/1 passed.
- Complete Swift package: 64/64 passed.
- Complete Kotlin/JVM: 71/71 passed; `installDist` built successfully.
- Shared conformance: 42/42 ordered Swift/Kotlin results matched.
- Repository language, binary, JSON, schema, and workflow gate: 60 files
  passed.
- JSON parsing and `git diff --check`: passed.
- Initial exact-diff independent review found one P2 gap: the shared scenario
  exercised late callbacks only after selection returned.
- The corrected direct Swift test suspends candidate selection during its
  diagnostic record, verifies late select, cancel, and failure callbacks all
  fail with `staleCallback`, then proves the original connection token remains
  usable.
- Corrected exact-diff independent re-review found no remaining P0-P2 issue.

## Remaining Gates

1. Commit, push once, open a normal pull request, and merge after review.
2. Produce two byte-identical source exports from the exact merge and repin the
   application artifact.
