# Round: 2026-09-23 - Kotlin public-entry reentry guard

## Status

- State: `locally verified; commit and protected integration pending`
- Branch: `codex/sdk-reentry-guard-20260923`
- Start commit: `eb5d6d4c6171efaa87a8e36a3c4ba3906efbfb2c`
- Implementation commit:
  `a9b66a9042d95559d9c74e892d13898dc9d79c65`
- Supplier artifacts: absent and prohibited
- Physical-device claims: unchanged and unproven

## Objective

Close the remaining JVM monitor-reentrancy path in the neutral Kotlin session
machine. A caller-owned collection being snapshotted must not be able to call
another public session entry such as `close` or `disconnect` and mutate the
session before the outer traversal fence is checked.

## Implementation

- Every public synchronized `BandSessionMachine` entry rejects while
  caller-owned collection traversal is active.
- Rejections use the existing fixed diagnostic kind, `rejected` outcome, and
  `invalidInput` category without identifiers, payloads, samples, or supplier
  exception text.
- Runtime regressions cover reentrant close and disconnect, unchanged session
  state, fixed diagnostics, and continued usability after rejection.

## Verification plan

1. Audit every public synchronized entry for the guard.
2. Run focused Kotlin reentry tests.
3. Run the complete Kotlin suite and distribution build.
4. Run Swift, shared conformance, repository policy, JSON, and diff gates.
5. Commit and integrate normally before exporting a new source artifact.

## Verification evidence

- Public-entry audit: all 29 public synchronized session-machine entries call
  the traversal-reentry guard before session validation or mutation.
- Focused Kotlin: reentrant close/disconnect and the existing caller-snapshot
  regressions passed.
- Complete Kotlin/JVM: 86/86 tests passed and `installDist` succeeded with one
  Gradle worker.
- Complete Swift package: 75/75 tests passed with one build worker.
- Shared contract: all 46 ordered Swift/Kotlin conformance scenarios matched.
- Repository policy: 64 files passed language, binary, JSON, schema, and
  hosted-workflow gates.
- JSON parsing and `git diff --check` passed.
- Verbose Kotlin, Swift, and conformance commands ran through the NOOP bounded
  runner with private capped logs under `/tmp`.
- No hosted workflow, supplier runtime, source export, application repin,
  protected merge, or physical-device result is claimed yet.

## External gates

Supplier binaries, BLE behavior, background execution, flash retention,
haptics, battery, accuracy, possession, firmware, OTA, legal, redistribution,
signing, store, and physical-device evidence remain open.
