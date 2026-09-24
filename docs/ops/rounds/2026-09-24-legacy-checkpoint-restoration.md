# Round: 2026-09-24 - Legacy checkpoint restoration

## Status

- State: `locally verified; SDK pull request pending`
- Branch: `codex/sdk-pr33-legacy-checkpoint-20260924`
- Start commit: `50a16fbc75f9ae604e773ff6608c87f0b96b67f7`
- Supplier artifacts: absent and prohibited
- Physical-device claims: unchanged and unproven

## Objective

Preserve the bounded identity state and durable count when restoring a public
legacy history checkpoint that contains identities but no payload
fingerprints. Retain the active SDK-D-007 safety contract: an identity without
payload evidence is replayed to authoritative application storage rather than
being suppressed as a duplicate.

## Implementation

- Apple and Android restore the complete ordered identity set before overlaying
  any available payload fingerprints.
- Checkpoint validation permits the fingerprint identities to be a subset of
  the retained identity set. This represents a legacy checkpoint that is being
  upgraded as samples are safely re-observed.
- Deterministic regressions require the restored cursor and durable count to
  survive, require an identity-only replay to remain accepted, and require the
  next mixed legacy/fingerprinted checkpoint to validate.
- Independent review required one additional restart of that mixed checkpoint.
  The follow-up proves a fingerprinted identity remains duplicate-suppressed,
  an identity-only entry still replays, and a fingerprint outside the retained
  identity set is rejected.

## Observability

No diagnostic schema changes are needed. Restore validation continues to emit
only the existing fixed `history/rejected/invalidInput` event. Identity sets,
fingerprints, cursors, samples, and health values remain outside diagnostics.

## Verification

- Focused Apple checkpoint regression: 1/1 passed.
- Complete Apple package: 106/106 passed.
- Focused Android checkpoint regression: passed.
- Complete Android/JVM: 114/114 passed with zero failures, errors, or skips;
  `installDist` succeeded.
- Shared conformance: all 50 ordered Swift/Kotlin scenarios matched.
- Repository policy: 74 files passed language, binary, and JSON gates.
- Swift parsing and `git diff --check` passed.
- Independent exact-diff review found one P2 test gap: the mixed checkpoint
  needed a second process restart. The corrected Apple and Android focused
  regressions pass, and the exact corrected tree again passes Swift 106/106,
  Kotlin/JVM 114/114 plus `installDist`, conformance 50/50, and the 74-file
  repository gate.
- Heavy commands ran serially with one compiler/JVM worker through the bounded
  runner. Logs and status records are under `/tmp/sdk-pr33-*`.
- During verification, the data volume retained approximately 18 GiB free and
  no bounded command crossed its resource floor. Existing system swap remained
  elevated; no parallel compiler wall was started.

## Next ordered actions

1. Commit and publish one SDK pull request.
2. Merge normally, export the exact protected revision twice, and prove the
   source artifacts are byte-identical.
3. Repin the NOOP application artifact and close only the application review
   threads proven resolved by the exact export.

## External gates

Supplier binaries, BLE, background execution, disconnected-band retention,
battery, haptics, sensor accuracy, possession proof, firmware, OTA, signing,
store distribution, legal, and physical-device validation remain open.
