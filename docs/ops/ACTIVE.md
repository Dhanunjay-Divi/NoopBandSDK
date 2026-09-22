# Active NOOP Band SDK handoff

Last updated: **2026-09-22**

## Current round

- [PR 17 post-review lifecycle closeout](rounds/2026-09-22-pr17-post-review-lifecycle-closeout.md)

## Current boundary

- Protected branch: `main`
- Active branch: `codex/sdk-pr17-diagnostics-closeout-20260922`
- Start commit: `8fb464471fdd4ae09d5750feedcc25d50bdb1c20`
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
  findings outside that diff. The post-review closeout now binds every
  connection/authentication/capability callback to the selected candidate,
  revalidates Swift operation tokens after actor suspension, represents
  established ready/live authentication failures, and preserves the prior
  durable cursor on a terminal chunk that omits a new cursor.

## Current evidence

- Swift package: 47/47 tests passed.
- Kotlin/JVM: 52/52 tests passed; `installDist` built successfully.
- Repository gate: 50 files passed language, binary, JSON, and workflow policy.
- JSON validation, bounded secret-pattern scan, and `git diff --check`: passed.
- Shared conformance: 35/35 Swift/Kotlin scenarios matched.
- First independent review found one P1 Swift actor-reentrancy issue and two P2
  evidence/documentation issues. The implementation and active handoff were
  corrected. Independent review of that exact corrected diff found no
  remaining P0-P2 issue.
- Independent review of the complete post-review lifecycle candidate also
  found no P0-P2 issue.

## Next ordered actions

1. Commit, push once, open a protected SDK pull request, and merge normally.
2. Produce two byte-identical clean source exports from the protected merge.
3. Repin NOOP application PR `#17`, rerun its local artifact/app gates, push
   once, and require the final protected hosted checks.
4. Keep supplier/physical-device gates explicit and separate.
