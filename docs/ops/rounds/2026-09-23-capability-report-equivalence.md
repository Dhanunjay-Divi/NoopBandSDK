# Round: 2026-09-23 - Capability report equivalence

## Status

- State: `locally verified and independently reviewed; integration pending`
- Branch: `codex/sdk-capability-equivalence-20260923`
- Start commit: `f8185b753b9e3672e78c7f3ee7596aeae7d53234`
- Supplier artifacts: absent and prohibited
- Physical-device claims: unchanged and unproven

## Objective

Make capability-report identity independent of `streamSemantics` array order
on Apple and Android. Schema 3 requires unique semantic entries but does not
define array ordering, so two otherwise identical reports must remain an
idempotent duplicate rather than an `invalidState` rejection.

## Reproduction

The application source-artifact consumer compiled the exported virtual band in
its separate test target, then reproduced a ready-session rejection when the
same capability sets generated equivalent stream semantics in a different
array order. The state remained ready, but the callback was incorrectly
classified as a changed late report.

## Scope

- Define matched order-insensitive capability-report equality on Swift and
  Kotlin while retaining every field and duplicate-count check.
- Add matched ready-session duplicate-callback regressions.
- Preserve schema validation, source-only export shape, bounded diagnostics,
  WHOOP independence, and all physical/supplier gates.

## Implementation

- Swift compares every string field by exact UTF-8 bytes, including parser and
  calibration revisions inside hashable stream semantics. It compares
  `streamSemantics` as a frequency map, preserving duplicate multiplicity while
  ignoring array order.
- Swift also uses exact UTF-8 identity for `BandIdentity` and every runtime
  comparison of source identities, capability identity revisions, history
  cursors, receipt identities/tokens, and negotiated parser/calibration
  revisions. This keeps post-suspension authority and durable-history decisions
  aligned with Kotlin's exact string identity.
- Apple and Kotlin keep capability-report equality lawful for every publicly
  constructible value. Session entry validates bounded semantics cardinality
  and bounded UTF-8 lengths before invoking equality, so untrusted late reports
  cannot make the actor/monitor traverse an oversized list or revision. Kotlin
  snapshots caller-owned collections with reported-size and iterator-step
  checks before validation; Apple strings stop length validation after the
  configured maximum plus one byte.
- Swift public contract equality for sample batches, history chunks,
  checkpoints, and session snapshots now uses the same exact UTF-8 identity as
  Kotlin for source identities, revisions, cursors, chunk identities, and
  acknowledgement tokens.
- Kotlin defines the same frequency-map contract and a matching
  order-insensitive, multiplicity-sensitive hash code, preserving the
  `equals`/`hashCode` requirement. Its validated strings already reject
  malformed surrogate sequences and use exact code-unit identity.
- Matched tests reverse only the semantics array, require equal reports and
  unchanged ready state, and verify the bounded `capability/stale` outcome.
  Negative cases prove parser-revision drift, NFC/NFD report and parser
  revisions, and different duplicate multiplicities remain unequal.
- Kotlin still snapshots caller-owned collections before session equality is
  evaluated; the existing malformed-late-report regression remains green.

## Independent review

- Initial exact-diff review found no P0/P1 and two P2 defects:
  Swift canonical Unicode equality differed from Kotlin exact string identity,
  and equal count plus set membership discarded duplicate multiplicities.
- Both findings are corrected with exact UTF-8 identity on Swift,
  frequency-map equality on both platforms, matching Kotlin hash behavior, and
  explicit regressions.
- Corrective-delta review found no P0/P1 and two additional P2 runtime parity
  defects: capability identity revisions and negotiated parser/calibration
  revisions still used Swift canonical string equality. Both were corrected.
- A final source audit found the post-suspension `BandIdentity` authority guard
  still depended on synthesized Swift equality. `BandIdentity` now defines
  exact UTF-8 equality, with matched Apple/Kotlin assertions.
- Complete-delta review then found three P2 gaps: unbounded late-report
  equality, synthesized Swift equality in four public contract models, and
  missing runtime regressions for source/checkpoint/cursor/receipt identity.
  All were corrected. A first remediation attempted to bound `equals` itself;
  follow-up review correctly found that this violated reflexivity for invalid
  reports and still allowed oversized strings. The final design restores lawful
  equality and moves bounded validation ahead of every session equality call.
  Exact-current re-review reports no remaining P0-P2 finding.

## Observability

No new diagnostic field is required. The existing fixed
`capability/stale` outcome proves an equivalent duplicate, while
`capability/rejected/invalidState` remains the bounded evidence for a truly
changed late report. No identifiers, health values, payloads, exception text,
or supplier strings are recorded.

## Verification

- Focused Swift capability-equivalence regression: passed `1/1`.
- Focused Kotlin capability-equivalence regression: passed.
- Complete Swift package: passed `91/91`.
- Complete Kotlin/JVM tests: passed `99/99`; `installDist` passed.
- Shared conformance: passed `50/50`.
- Repository gate: `67` files passed language, binary, and JSON checks.
- Capability/conformance JSON parsing and `git diff --check`: passed.
- The first Android focused invocation did not start because this SDK
  repository intentionally has no local `./gradlew`; the counted rerun used
  the approved app-repository wrapper with `-p android` and passed.
- The first exact-current Kotlin rerun stopped at test compilation because the
  new parity assertion lacked its `assertNotEquals` import. The import was
  added; the complete replacement rerun passed `94/94` and `installDist`.
- Final regressions cover oversized and misreported semantics collections,
  million-byte revision rejection before equality, equality reflexivity and
  equal-copy behavior, exact public-model equality, source identity,
  checkpoint restoration, cursor progression, and receipt chunk/token/cursor
  comparisons.
- All verbose commands used the bounded runner with private capped logs under
  `/tmp/sdk-pr29-*`.

## Git and release state

- Commit, push, normal pull-request integration, two clean protected-source
  exports, byte comparison, application repin, replacement app verification,
  and protected app integration remain pending.
- No supplier binary, firmware, credential, device identifier, health value,
  external service, or physical band was used.

## External gates

Supplier binaries, BLE behavior, background execution, flash retention,
haptics, battery, sensor accuracy, possession proof, firmware, OTA, legal,
redistribution, signing, store, and physical-device validation remain open.
