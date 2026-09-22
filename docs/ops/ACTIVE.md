# Active NOOP Band SDK handoff

Last updated: **2026-09-22**

## Current round

- [PR 17 overflow, firmware, and capability closeout](rounds/2026-09-22-pr17-overflow-firmware-capability-closeout.md)

## Current boundary

- Protected branch: `main`
- Active branch: `codex/sdk-pr17-overflow-firmware-closeout-20260922`
- Start commit: `a8f94b5cbda329eaf7793c5a2cece94fb568acc0`
- Implementation commit: pending
- Protected merge: pending
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

## Current evidence

- Swift package: 52/52 tests passed.
- Kotlin/JVM: 56/56 tests passed; `installDist` built successfully.
- Repository gate: 52 files passed language, binary, JSON, capability-schema,
  and workflow policy.
- JSON validation, bounded secret-pattern scan, and `git diff --check`: passed.
- Shared conformance: 36/36 Swift/Kotlin scenarios matched.
- First independent review found one P1 Swift actor-reentrancy issue and two P2
  evidence/documentation issues. The implementation and active handoff were
  corrected. Independent review of that exact corrected diff found no
  remaining P0-P2 issue.
- Independent review of the complete post-review lifecycle candidate also
  found no P0-P2 issue.

## Next ordered actions

1. Implement and verify the active follow-up.
2. Commit, push once, open a protected SDK pull request, and merge normally.
3. Produce two byte-identical clean source exports from the protected merge.
4. Repin NOOP application PR `#17`, rerun its local artifact/app gates, push
   once, and require the final protected hosted checks.
5. Keep supplier/physical-device gates explicit and separate.
