# Round: 2026-09-23 - Application PR 17 exact-head remediation

## Status

- State: `ready for pull request`
- Branch: `codex/sdk-pr22-review-remediation-20260923`
- Start commit: `a9d3f1a2a55b5436bf1b65b0299a27667241afa4`
- Implementation commit: pending
- Pull request: pending
- Supplier artifacts: absent
- Physical-device claims: unchanged and unproven

## Objective

Correct three deterministic neutral-SDK defects found by the NOOP application
PR `#17` review of exact head
`fed31b0f164675b0d1f6ef5a7724262afb23f0af`, preserve matched Swift and Kotlin
behavior, and produce one reviewed source artifact for the application
integration branch.

## In scope

- Bound every iterator step when snapshotting supplier-owned Kotlin sets.
- Bind discovery selection, cancellation, and failure callbacks to the exact
  session object as well as its generation.
- Reject history operations unless the negotiated capability report advertises
  both retained history and at least one history stream.
- Add mirrored Swift, Kotlin, and shared conformance regressions.
- Keep diagnostics fixed, bounded, identifier-free, and free of supplier error
  text or health values.

## Out of scope

- Supplier binaries, firmware, credentials, packet captures, identifiers, or
  health data.
- Physical BLE, background execution, disconnected-band flash behavior,
  haptics, battery, sensor accuracy, possession proof, firmware flashing, or
  OTA claims.
- Enabling the first-party application transport or changing WHOOP as the
  independent application test transport.

## Starting evidence

- Protected SDK `main` is
  `a9d3f1a2a55b5436bf1b65b0299a27667241afa4`.
- The application exact-head review identified:
  - a hostile Kotlin `Set` iterator that can repeat one value forever without
    growing the unique snapshot;
  - discovery callbacks authorized only by a numeric generation that restarts
    for a replacement session object; and
  - history authorization that checks retained days but not negotiated history
    streams.
- Direct source inspection reproduced all three control-flow defects.
- The application production factory remains disabled and WHOOP remains the
  default test transport.

## Observability

The corrections reuse existing fixed `discovery/stale/staleCallback`,
`history/rejected/unsupported`, and input-rejection evidence. No new event or
payload field is required. Tests must prove that rejection does not include a
session nonce, candidate handle, supplier exception text, stream payload,
cursor, or health value.

## Verification evidence

- Swift package: 62/62 tests passed after adding the reflection-redaction
  regression.
- Kotlin/JVM tests and `installDist`: passed.
- Shared Swift/Kotlin conformance: 41/41 scenarios matched.
- Repository language, binary, JSON, schema, and workflow gate: 58 files
  passed.
- JSON parsing, bounded secret-pattern scan, and `git diff --check`: passed.
- The cross-session discovery scenario proves that selection, cancellation,
  and failure callbacks from another session are rejected even when both
  sessions have generation `1`, while the current scan remains usable.
- The Kotlin repeating-element set regression proves traversal is bounded by
  iterator steps rather than unique cardinality.
- The lane-negotiation regressions prove a history operation is rejected before
  it starts when no history stream was negotiated.
- The first independent exact-diff review found three P2 issues: default Swift
  token rendering could expose the private session nonce, the shared scenario
  could hide an incorrect per-callback failure category, and this record named
  the wrong discovery outcome. The token now redacts normal and debug string
  rendering, every foreign select/cancel/fail callback independently requires
  `staleCallback`, and this record uses
  `discovery/stale/staleCallback`.
- The corrected-diff review found one remaining P2 reflection path plus two
  stale evidence statements. `BandScanToken` now publishes a redacted custom
  mirror, the existing test proves `dump` omits field names and UUID-shaped
  values, and the stale evidence statements are corrected here and in
  `ACTIVE.md`.
- Final corrected-diff re-review found no remaining P0-P2 issue.
- Commit, PR, merge, exports, and application repin remain pending.

## External gates

All supplier, physical-device, legal, redistribution, security, signing,
carrier, store, OTA, and accuracy gates remain open.

## Remaining gates

1. Commit, push once, open a normal pull request, and merge after review.
2. Produce two byte-identical clean exports from the exact merge.
3. Repin NOOP application PR `#17`, rerun its local and hosted exact-head
   gates, resolve only proven review findings, and integrate normally.
