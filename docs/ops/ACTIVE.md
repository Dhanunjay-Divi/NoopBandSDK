# Active NOOP Band SDK handoff

Last updated: **2026-09-23**

## Current round

- [Established-session callback authority](rounds/2026-09-23-established-session-authority.md)
- [Scan-token consumption parity](rounds/2026-09-23-scan-token-consumption-parity.md)
- [Kotlin scan-token identity](rounds/2026-09-23-kotlin-scan-token-identity.md)
- [Application PR 17 exact-head remediation](rounds/2026-09-23-app-pr17-exact-head-remediation.md)

## Current boundary

- Integration branch: `codex/sdk-pr25-session-authority-20260923`
- GitHub branch protection: absent as of 2026-09-22; PR-only discipline is
  procedural, not enforced by a repository rule
- Active branch: `codex/sdk-pr25-session-authority-20260923`
- Start commit: `f20f4ed552328a64a8a598aaac72befa1d481262`
- Implementation commit: pending publication
- PR merge: pending
- Supplier binaries: absent and prohibited
- WHOOP application transport: unchanged
- Production supplier adapter: unavailable pending approved artifacts and
  physical evidence

This work proves deterministic neutral-core behavior only. It does not prove
BLE, background execution, flash retention, haptics, battery, sensor accuracy,
possession proof, firmware flashing, or OTA behavior.

## Current implementation

- Swift and Kotlin record `connection/completed` before
  `authentication/began` in one recorder batch.
- Swift moves the session into `authenticating` before the first diagnostic
  suspension, preventing duplicate concurrent starts from restoring or
  overwriting a terminal state.
- Consecutive successful durable live receipts coalesce into one bounded
  diagnostic slot. A failure, rejection, or other lifecycle event breaks
  coalescing so later recovery remains visible.
- The next live session receives its own success and terminal evidence.
- Diagnostic fields remain fixed kind, outcome, failure category, count
  bucket, and duration bucket only.
- The diagnostics-only diff passed independent review with no remaining P0-P2
  finding. The live NOOP application PR then reported four inherited lifecycle
  findings outside that diff. The final closeout additionally revalidates
  transport authority after Swift actor suspension, preserves pending
  persistence on established-session failure, separates live and history
  stream support, and binds retained/lost overflow ranges to the exact durable
  receipt.
- The active follow-up validates overflow chronology and retained sample
  bounds, adds an explicit terminal firmware-failure disposition, and rejects
  history-stream capability reports with zero retention.
- The follow-up's schema gate now traverses every current subschema
  independently of fixtures, rejects unsupported future keywords, and enforces
  the normative UTF-8 byte limits. Final independent review found no remaining
  P0-P2 issue.
- The protected follow-up merged at `a486768`. Exact application review then
  independently confirmed four remaining defects: non-overflow retained-range
  validation, Kotlin capability snapshot failure normalization, session-bound
  live callback authority, and truthful close-phase terminal diagnostics.
- Protected SDK PR `#17` merged those corrections at `c533c530`. The first
  source-artifact consumer build then exposed one narrower conformance-support
  defect: the stale-callback fixture directly constructed an internal live
  token when compiled outside the core module. This round captures a real live
  token before reconnect and reuses it after generation advance.
- SDK PR `#18` merged that export-consumer correction at `ed681a7`. The NOOP
  application then found that Kotlin publishes all 38 automated scenario IDs
  in a different order from the canonical JSON contract. This round aligns the
  Kotlin order, adds structured list output to both executables, and requires
  the shared verifier to compare both ordered lists before invoking scenarios.
- SDK PR `#19` merged that order contract at `823930fa`. The NOOP application
  review then found that a malformed delayed Kotlin capability callback could
  move an already-ready session to `INCOMPATIBLE`. The active correction keeps
  initial negotiation fail-closed while preserving established session state
  and recording the same bounded invalid-input rejection for a late callback.
- SDK PR `#20` merged that late-capability correction at `9bc2eedc`. The final
  NOOP application PR `#17` review then confirmed four remaining deterministic
  defects: hostile Kotlin `List.size` failure normalization, restored
  checkpoint loss after a different source connected first, absence of a
  graceful disconnect API, and stale release-control provenance.
- The active SDK correction now preserves the source-scoped checkpoint,
  normalizes hostile Kotlin list-size access, and implements matched
  generation-fenced graceful disconnect with fixed bounded diagnostics.
  Swift invalidates callback authority before its first diagnostics suspension
  so no staged live/history acceptance can be cleared without a durable
  receipt.
- SDK PR `#21` merged those corrections at `a9d3f1a2`. The NOOP application
  exact-head review then identified three narrower deterministic defects:
  hostile Kotlin set traversal is bounded by unique cardinality rather than
  iterator steps, discovery callbacks use a generation that can collide across
  replacement session objects, and history can start without any negotiated
  history stream. The active round corrects those contracts before the
  application artifact is repinned.
- SDK PR `#23` merged the exact Kotlin scan-token identity correction at
  `1b4c614e`. Independent review of the repinned application then found that
  Apple did not consume its value token after selection, so late discovery
  callbacks produced a different failure category than Kotlin. The active
  round aligns consumed-token behavior and adds a shared post-selection
  callback scenario.
- SDK PR `#24` merged scan-token consumption parity at `f20f4ed`. Exact
  application review then found two narrower authority defects: established
  session failures were generation-bound rather than connection-token-bound,
  and tokenless live stop could terminate a replacement live lease. The
  active correction requires the exact connection/live token on both
  platforms, migrates all call sites, and redacts every Swift persistence
  handoff value from default rendering and reflection.

## Current evidence

- Protected baseline Swift package: 52/52 tests passed.
- Protected baseline Kotlin/JVM: 56/56 tests passed; `installDist` built
  successfully.
- Protected baseline repository gate: 52 files passed language, binary, JSON,
  capability-schema, and workflow policy.
- Protected baseline shared conformance: 36/36 Swift/Kotlin scenarios matched.
- Current remediation Swift package: 55/55 tests passed.
- Current remediation Kotlin/JVM: 62/62 tests passed; `installDist` built
  successfully.
- Current remediation repository gate: 53 files passed language, binary, JSON,
  capability-schema, and workflow policy.
- Current remediation shared conformance: 38/38 Swift/Kotlin scenarios matched.
- Swift parsing, JSON validation, bounded secret-pattern scan, and
  `git diff --check`: passed.
- Prior compatibility-candidate local exact-diff review found no remaining
  P1/P2 issue.
- Independent agent review unavailable: one reviewer lost AWS credentials and
  one produced no output before controlled shutdown. No independent result is
  claimed.
- First independent review found one P1 Swift actor-reentrancy issue and two P2
  evidence/documentation issues. The implementation and active handoff were
  corrected. Independent review of that exact corrected diff found no
  remaining P0-P2 issue.
- Independent review of the complete post-review lifecycle candidate also
  found no P0-P2 issue.
- Source-artifact consumer reproduction: failed only because exported Apple
  virtual-band support called the internal `BandLiveToken` initializer from a
  separate test target. No production API or app runtime failure was involved.
- Current compatibility correction verification:
  - Swift package: 55/55 passed.
  - Kotlin/JVM: 62/62 passed; `installDist` built.
  - Shared conformance: 38/38 exact matches.
  - Repository gate: 54 files passed.
  - Separate exported-source consumer package: 15/15 passed using only the
    public module import.
  - Parse, JSON, diff, and bounded secret-pattern gates: passed.
- Current conformance-order correction verification:
  - Swift package: 55/55 passed.
  - Kotlin/JVM: 62/62 passed; `installDist` built.
  - Both executable scenario lists parse as ordered 38-element JSON arrays and
    exactly match the canonical automated IDs.
  - Shared conformance: 38/38 exact results matched after the new ordered-list
    gate passed.
  - Repository gate: 55 files passed.
  - Swift parse, JSON, diff, and bounded secret-pattern gates: passed.
  - Independent review found no P0/P1. Its two P2 findings identified
    fail-open malformed-contract filtering and stale handoff wording; its P3
    finding identified arbitrary child-stderr forwarding. All three are
    corrected. Two negative regressions and the 38-scenario shared rerun pass;
    scoped re-review reports no remaining P0-P2 issue.
- Current late-capability correction verification:
  - Focused malformed delayed-callback regression: passed.
  - Kotlin/JVM: 63/63 passed; `installDist` built.
  - Swift package: 55/55 passed.
  - Shared conformance: 38/38 exact results matched.
  - Repository gate: 56 files passed.
  - Swift parse, JSON, diff, and bounded secret-pattern gates: passed.
  - An independent sub-agent review was requested but no agent slot was
    available. No independent result is claimed; fresh PR review remains
    required before merge.
- Current exact-review remediation verification:
  - Swift package: 60/60 passed after the final tokenless-stop guard.
  - Kotlin/JVM: 66/66 passed; `installDist` built successfully.
  - Shared conformance: 40/40 exact results matched.
  - Repository gate: 57 files passed.
  - JSON, bounded secret-pattern, and diff checks: passed.
  - The first independent review found one P1 Swift actor-reentrancy race
    during disconnect. Callback authority now advances before suspension and
    a direct staging-during-disconnect regression passes. Re-review then found
    close-terminal evidence loss and a live-only `stopLive()` interruption.
    The invalidated phase now remains visible to close, both platforms require
    the live-collecting state before stop, and final corrected exact-diff
    review reports no remaining P0-P2 finding.
- Current application-PR-17 exact-head remediation verification:
  - Swift package: 62/62 passed after the reflection-redaction regression.
  - Kotlin/JVM tests and `installDist`: passed.
  - Shared conformance: 41/41 exact results matched.
  - Repository gate: 58 files passed.
  - JSON, bounded secret-pattern, and diff checks: passed.
  - The first independent review found three P2 issues in token rendering,
    per-callback scenario evidence, and operations wording. The corrected-diff
    review found one remaining P2 reflection path plus two stale evidence
    statements. All are corrected; final exact-diff re-review reports no
  remaining P0-P2 issue.
- Current Kotlin scan-token identity closeout:
  - Field-identical same-module token forgery is rejected by exact object
    identity while the issued token remains usable.
  - Kotlin/JVM tests and `installDist`: passed.
  - Swift package: 62/62 passed.
  - Shared conformance: 41/41 matched.
  - Repository gate: 59 files passed.
  - Diff hygiene: passed.
- Current scan-token consumption parity:
  - focused Swift and Kotlin regressions pass;
  - complete Swift passes 64/64;
  - complete Kotlin/JVM passes 71/71 and `installDist` builds;
  - shared conformance passes 42/42 ordered results;
  - the repository gate passes 60 files and diff hygiene is clean;
  - initial exact-diff review found one P2 suspension-boundary evidence gap;
    the direct Swift regression now pauses candidate selection at its
    diagnostic suspension, proves late select/cancel/failure callbacks are
    stale before the original call returns, and proves the valid connection
    token remains usable;
  - corrected exact-diff re-review found no remaining P0-P2 issue;
  - commit, PR, merge, deterministic export, and application repin remain
    pending.
- Current established-session authority correction:
  - Swift package passes 67/67 tests;
  - Kotlin/JVM tests and `installDist` pass;
  - shared conformance passes 44/44 ordered scenarios;
  - repository policy passes 61 files;
  - JSON parsing, bounded secret-pattern review, and diff hygiene pass;
  - the first conformance invocation detected stale pre-change executables;
    both binaries were rebuilt and the required rerun passed all 44 scenarios;
  - independent exact-diff review, commit, PR, merge, deterministic exports,
    and application repin remain pending.

## Next ordered actions

1. Complete independent exact-diff review, then commit and push once and merge
   the SDK correction normally. Do not claim hosted checks: this repository
   intentionally has no hosted workflow.
2. Produce two byte-identical clean source exports from the exact merge.
3. Repin NOOP application PR `#17`, compile exported support as a separate
   test target, rerun local artifact/app gates, push once, and require final
   protected hosted checks and review.
4. Keep supplier and physical-device gates explicit and separate.
