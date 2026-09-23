# Round: 2026-09-23 - PR 17 final contract closeout

## Status

- State: `locally verified; pull-request integration pending`
- Branch: `codex/sdk-pr17-final-contract-closeout-20260923`
- Start commit: `586a5c04bd3edbd445895a881c5d4faa78fb526e`
- Supplier artifacts: absent and prohibited
- Physical-device claims: unchanged and unproven

## Objective

Close the remaining deterministic SDK contract gaps found while reviewing the
NOOP application PR `#17` candidate. Preserve the neutral, source-only,
supplier-independent boundary while making negotiated behavior and bounded
diagnostics sufficient for a future physical adapter.

## Confirmed gaps

- Swift connection completion can return after diagnostic suspension without
  revalidating the exact authenticated connection authority.
- Capability schema `2` cannot bind accepted samples to immutable unit,
  cadence, quality, timestamp, parser, and calibration semantics.
- Capability negotiation cannot declare which operation classes are safe while
  live collection is active.
- Live batch acceptance does not record a bounded accepted-count event before
  application persistence begins.
- Command and disconnect diagnostics omit their fixed operation class or
  disconnect reason.
- Kotlin collection snapshots do not normalize JVM null elements from
  supplier-owned lists and sets to `invalidInput`.

## Implementation plan

1. Introduce capability schema `3` with explicit stream semantics, parser and
   calibration revisions, and operations allowed during live collection.
2. Enforce those negotiated fields on Apple and Android before transport work
   or sample acceptance.
3. Add bounded operation-class and disconnect-reason diagnostic fields plus a
   staged live accepted-count event.
4. Revalidate Swift authority after every newly relevant diagnostic
   suspension.
5. Add matched Swift, Kotlin, schema, and shared conformance regressions.
6. Run complete SDK gates, export twice from the protected merge, and repin the
   application only after byte-identical source artifacts are proven.

## Observability boundary

The new evidence is restricted to fixed enums and count buckets. It must not
contain device identifiers, health values, raw samples, timestamps, supplier
strings, exception text, credentials, or payloads. Diagnostics remain bounded
and best-effort.

## External gates

Supplier binaries, BLE behavior, background execution, flash retention,
haptics, battery, sensor accuracy, possession proof, firmware, OTA, legal,
redistribution, signing, store, and physical-device validation remain open.

## Independent-review remediation

The first exact-diff review found three defects:

- Swift authentication completion treated valid capability progress during its
  diagnostics suspension as stale.
- staged live diagnostics replaced an older staged event across a durable
  completion, corrupting lifecycle chronology;
- Kotlin firmware-disconnect evidence omitted the fixed firmware operation
  class.

The corrected implementation accepts only the same still-authoritative
connection after valid capability progress, keeps a pending staged event
chronologically visible, collapses only fully acknowledged adjacent live
cycles to preserve bounded connection evidence, and emits firmware operation
class parity on Android. Direct regressions cover each path. The conformance
firmware scenario now validates operation-class metadata rather than comparing
only outcome and failure category.

The next split review found three additional defects:

- Apple cleared pending live persistence before cross-actor completion evidence
  was recorded, allowing a later staged event to appear before the prior
  completion;
- the Kotlin schema-2 compatibility constructor traversed hostile collections
  before normalization and fabricated provenance and live-operation policy;
- adding diagnostic fields removed the historical five-argument Java/JVM
  constructor descriptor.

Apple now keeps the pending receipt fenced until completion evidence returns,
with a deterministic concurrent-staging regression. Kotlin removes the
misleading capability constructor and migrates every virtual fixture to an
explicit schema-3 helper. The exact legacy diagnostic constructor is restored
and compiled from Java in the test wall. Independent Apple and Android
corrective-delta re-reviews report no remaining P0-P2 findings.

## Verification

- focused Swift reentrancy and live chronology regressions: passed `2/2`;
- focused Kotlin live chronology and firmware disconnect regressions: passed;
- complete Swift package: passed `83/83`;
- complete Kotlin/JVM tests and `installDist`: passed;
- shared Swift/Kotlin conformance: passed `50/50`;
- repository language, binary, JSON, and schema gate: passed `66` files;
- `git diff --check`: passed.

All verbose commands ran through the bounded runner with private logs under
`/tmp/noop-sdk28-*`. No supplier artifact, identifier, health value, payload,
credential, or external service was used.

Independent exact-diff and corrective-delta reviews are complete with no
remaining P0-P2 findings. Commit, normal pull-request integration,
protected-source export, byte comparison, and application repin remain
pending.
