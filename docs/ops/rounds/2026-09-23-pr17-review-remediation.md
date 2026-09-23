# Round: 2026-09-23 - PR 17 review remediation

## Status

- State: `locally verified; committed by this record`
- Branch: `codex/sdk-pr17-review-remediation-20260923`
- Start commit: `650c89e45ca2ab28e14e76e447a7026479e42b4e`
- Implementation commit: this commit
- Pull request: not requested
- Supplier artifacts: absent and prohibited
- Physical-device claims: unchanged and unproven
- Independent review of implementation commit
  `3831fb63ae336bd88982586fafc608adda6d6280` found three valid follow-up
  issues. Their remediation and evidence are recorded in
  [PR 17 independent-review follow-up](2026-09-23-pr17-independent-review-followup.md).

## Objective

Resolve four verified deterministic SDK findings from the NOOP application
pull request `#17` review while preserving Swift/Kotlin parity:

- bind reconnect interruption to the active connection credential and reconnect
  completion to a newly issued opaque reconnect credential;
- return that reconnect credential from disconnected non-firmware operation
  terminals while keeping invalidated capability and firmware recovery
  scan-only;
- bounded-snapshot Kotlin live-stream requests and normalize hostile collection
  behavior to `invalidInput`;
- record a live interruption before reconnect clears live tracking; and
- reject JVM null elements in every supplier-owned Kotlin list/set snapshot.

## In scope

- Neutral Swift and Kotlin models and session machines.
- Virtual bands, unit tests, shared conformance scenarios, and canonical order.
- Public SDK integration documentation and operations records.

## Out of scope

- Supplier binaries, firmware, credentials, packet captures, identifiers,
  private data, or health data.
- Hosted workflows, pushing, deployment, artifact publication, or application
  repinning.
- Physical BLE, background execution, retention, haptics, battery, accuracy,
  possession, flashing, or OTA claims.

## Observability

Reconnect authority rejection reuses fixed
`reconnect/stale/staleCallback` evidence. Hostile requested-stream rejection
uses fixed `live/rejected/invalidInput` evidence. A reconnect that interrupts
live collection records fixed `live/interrupted` before live tracking is
cleared, followed by `reconnect/interrupted`. No token, generation, collection
element, supplier exception text, identifier, sample, or health value is
recorded.

## Verification plan

1. Add matched connection/reconnect-token authority and live-interruption
   ordering regressions on Swift and Kotlin.
2. Add Kotlin hostile requested-set and JVM-null snapshot regressions.
3. Update every SDK and virtual-band call site and the ordered shared contract.
4. Run the repository, Swift, Kotlin distribution, shared conformance, and diff
   gates with bounded private logs where output is verbose.
5. Commit locally without pushing and record the exact commit and evidence.

## Verification evidence

- Focused Swift reconnect and recovery selection: 4/4 tests passed with one
  worker under the bounded runner.
- Focused Kotlin reconnect, recovery, hostile-set, and JVM-null selection:
  six selected tests passed with one Gradle worker under the bounded runner.
- The first focused Swift compile exposed two stale virtual-band tuple
  bindings. The next focused run exposed two invalid test-fixture assumptions.
  Both were corrected before the passing focused rerun; neither failed run is
  counted as verification.
- Full Swift package with one build worker: 73/73 tests passed.
- Full Kotlin/JVM with one Gradle worker: 81/81 tests passed and `installDist`
  succeeded.
- Shared conformance: all 46 ordered Swift/Kotlin scenarios matched the
  checked-in contract.
- Repository policy: 62 files passed language, binary, JSON, schema, and
  workflow gates.
- JSON parsing, canonical automated-scenario order, bounded added-line
  credential review, and `git diff --check`: passed.
- Verbose commands used private capped logs under
  `/tmp/noop-band-sdk-pr17-review-remediation-20260923`.
- Host-pressure checks found unrelated Xcode walls before the full Swift wall
  and again before the full Gradle wall. Each SDK wall waited until the
  external wall exited; no heavy compiler walls overlapped.
- No independent review, hosted workflow, physical-device result, supplier
  runtime result, export, publication, push, or application repin is claimed.

## External gates

All supplier, physical-device, legal, redistribution, security, signing,
store, OTA, and accuracy gates remain open.
