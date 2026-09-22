# Active NOOP Band SDK handoff

Last updated: **2026-09-22**

## Current round

- [Conformance order contract](rounds/2026-09-22-conformance-order-contract.md)

## Current boundary

- Integration branch: `codex/sdk-conformance-order-20260922`
- GitHub branch protection: absent as of 2026-09-22; PR-only discipline is
  procedural, not enforced by a repository rule
- Active branch: `codex/sdk-conformance-order-20260922`
- Start commit: `ed681a7a54330d50f0a207690b8cc3566f0f527b`
- Implementation commit: pending
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

## Next ordered actions

1. Commit the locally verified correction, push once, and merge normally. Do
   not claim hosted checks: this repository intentionally has no hosted
   workflow.
2. Produce two byte-identical clean source exports from the merge.
3. Repin NOOP application PR `#17`, compile exported support as a separate
   test target, rerun local artifact/app gates, push once, and require final
   protected hosted checks.
4. Keep supplier and physical-device gates explicit and separate.
