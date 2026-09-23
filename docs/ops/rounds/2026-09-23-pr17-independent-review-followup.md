# Round: 2026-09-23 - PR 17 independent-review follow-up

## Status

- State: `latest review remediation locally verified; commit and push pending`
- Branch: `codex/sdk-pr17-review-remediation-20260923`
- Start commit: `3831fb63ae336bd88982586fafc608adda6d6280`
- Follow-up implementation commit:
  `830f9fe1721cc0e842e77b767adecc31ec98ab3b`
- Final reviewed implementation commit:
  `b66ee30ef352c9317191118a7226869d17059465`
- Pull request: `#26`
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
- Kotlin firmware disconnect now also appends `firmware/interrupted` and
  `reconnect/interrupted` through one synchronized recorder batch. This keeps
  the pair adjacent when one recorder is shared by concurrent sessions.
- Kotlin marks supplier-owned collection traversal active before invoking
  `size`, `iterator`, or `next`. A nested capability, live-start, live-batch,
  or history-chunk traversal is rejected before it can recurse, while the
  existing lifecycle fence still catches ordinary state-changing reentrancy.
- Kotlin list/set snapshots normalize a supplier-thrown `BandException` during
  `size`, `iterator`, `hasNext`, or `next` to fixed `invalidInput`; the
  supplier cannot select a diagnostic category such as `busy` or `storage`.
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
- Complete Kotlin/JVM with one Gradle worker: 86/86 tests passed and
  `installDist` succeeded.
- Hosted PR `#26` review identified one valid Kotlin diagnostic-atomicity
  finding. The focused firmware-disconnect regression passed after the batch
  correction, then the complete Kotlin/JVM suite and `installDist` succeeded.
- Review of the next exact head identified a second valid Kotlin same-call
  traversal finding. Three focused reentrancy selections passed after the
  active-traversal guard; the complete Kotlin/JVM suite then passed 85/85 and
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
- Exact-head review of `b66ee30e` found two stale operations statements and
  one valid Kotlin collection-failure defect. After the correction:
  - the focused supplier-`BandException` regression passes;
  - the complete Kotlin/JVM suite passes 86/86 and `installDist` succeeds;
  - all 46 Swift/Kotlin conformance scenarios match;
  - the 63-file repository gate, JSON validation, and diff hygiene pass.
- The first focused invocation failed only because the new local test helper
  passed a function value to the wrong `assertFailsWith` overload. The helper
  now invokes that function explicitly; the corrected rerun is the counted
  result.
- No hosted workflow, supplier runtime, protected merge, source export,
  application repin, or physical-device result is claimed yet.

## External gates

Supplier binaries, firmware, BLE, background execution, disconnected-band
retention, haptics, battery, accuracy, possession, OTA, legal, redistribution,
signing, store, and physical-device evidence remain explicitly open.
