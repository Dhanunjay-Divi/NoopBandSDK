# Round: 2026-09-23 - PR 17 independent-review follow-up

## Status

- State: `locally verified; committed by this record`
- Branch: `codex/sdk-pr17-review-remediation-20260923`
- Start commit: `3831fb63ae336bd88982586fafc608adda6d6280`
- Follow-up implementation commit: this commit
- Pull request: not requested
- Supplier artifacts: absent and prohibited
- Physical-device claims: unchanged and unproven

## Objective

Resolve three valid findings from independent review of `3831fb63` without
rewriting that commit:

- prevent reentrant Kotlin caller-owned collection traversal from reviving or
  mutating a closed or superseded session;
- keep Swift firmware-disconnect and reconnect diagnostics adjacent across an
  actor suspension while preserving a concurrently started recovery scan; and
- correct stale operations text that still described merged SDK pull requests
  `#24` and `#25` as pending.

Also add direct Swift and Kotlin proof that reconnect authority returned by an
operation disconnect is bound to its issuing session and cannot be replayed.

## Implementation

- Kotlin captures the complete session lifecycle fence before each
  monitor-held caller-owned collection snapshot and revalidates it before any
  post-snapshot mutation. The audited paths are capability acceptance,
  requested live streams, live batches, and history chunks.
- The restored history checkpoint is snapshotted during construction before
  the session instance is published, so it has no reentrant session target.
- Swift firmware disconnect appends `firmware/interrupted` and
  `reconnect/interrupted` as one recorder batch. After the recorder suspension,
  the operation returns only if the exact recovery state and generation remain
  current.
- Both platforms directly reject operation-derived reconnect credentials from
  another equal-generation session and reject replay after successful resume.

## Observability

Kotlin authority changes during caller-owned traversal record the existing
fixed `<kind>/stale/staleCallback` event. Swift records the two fixed firmware
recovery events atomically; a superseded suspended terminal records
`firmware/stale/staleCallback`. No collection element, token, identifier,
supplier exception, payload, sample, or health value is recorded.

## Verification plan

1. Run focused Kotlin same-thread reentrancy and operation-derived reconnect
   authority tests.
2. Run focused Swift firmware suspension and operation-derived reconnect
   authority tests.
3. Check host pressure and wait for unrelated Swift/Xcode or Gradle walls
   before each complete SDK wall.
4. Run complete Swift, Kotlin `test installDist`, shared conformance,
   repository policy, JSON/order, bounded credential, and diff gates.
5. Commit locally without pushing and record the exact follow-up SHA.

## Verification evidence

- Focused Swift: both selected regressions passed, 2/2.
- Focused Kotlin: all three selected reentrancy and reconnect-authority
  regressions passed, 3/3.
- Complete Swift package with one build worker: 75/75 tests passed.
- Complete Kotlin/JVM with one Gradle worker: 84/84 tests passed and
  `installDist` succeeded.
- Shared conformance: all 46 ordered Swift/Kotlin scenarios matched the
  checked-in contract.
- Repository policy: 63 files passed language, binary, JSON, schema, and
  workflow gates.
- JSON parsing, bounded credential-pattern review, and `git diff --check`
  passed.
- The first focused Swift selector matched zero tests and was not counted.
  The rerun used the package's discovered Swift Testing names and passed 2/2.
- The first focused Kotlin command referenced an absent shared wrapper path
  and exited before Gradle started. The rerun used the application repository
  wrapper against this SDK worktree and passed 3/3.
- Verbose commands used private capped logs under
  `/tmp/noop-band-sdk-pr17-independent-review-followup-20260923`.
- Swift and Kotlin compiler walls ran sequentially. Observed free disk
  remained approximately 13.4 GiB and never crossed the 10 GiB stop floor.
- No hosted workflow, push, supplier runtime, source export, application
  repin, or physical-device result is claimed.

## External gates

Supplier binaries, firmware, BLE, background execution, disconnected-band
retention, haptics, battery, accuracy, possession, OTA, legal, redistribution,
signing, store, and physical-device evidence remain explicitly open.
