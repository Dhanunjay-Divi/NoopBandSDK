# Round: 2026-09-23 - Application PR 17 post-review closeout

## Status

- State: `verified candidate locally green; SDK pull request pending`
- Integration branch: `codex/sdk-pr32-integration-20260923`
- State-machine branch: `codex/sdk-pr32-state-machine-20260923`
- Model-safety branch: `codex/sdk-pr32-models-20260923`
- Start commit: `9fd84ff6af3d48c41fb5af3128efec9dcc6948a4`
- Supplier artifacts: absent and prohibited
- Physical-device claims: unchanged and unproven

## Objective

Close every unresolved deterministic SDK finding on NOOP application PR `#17`,
integrate the corrected SDK through a normal pull request, export the exact
merge twice, repin the application artifact, rerun the complete applicable
local and hosted gates, and integrate the application without bypassing branch
protection.

## Confirmed findings

1. Established authentication or transport-security failures must terminate
   active operations instead of being rejected by the ready/live-only guard.
2. Operation failure terminals must reject categories outside the operation
   failure domain before mutating session state.
3. An operation security failure that ends live collection must record
   `live/interrupted` before the operation terminal.
4. Live and history batches must reject same-identity samples whose payloads
   conflict while retaining duplicate handling for byte-equivalent values.
5. Pairing candidates must redact opaque adapter handles from default Swift
   and Kotlin rendering.
6. Raw Java collection traversal must validate concrete element types and
   normalize wrong-type failures to the fixed invalid-input boundary.
7. The application operations record must not retain the superseded private
   repository gate.
8. Terminal firmware disposition must remain authoritative when the adapter
   also classifies the failure as authentication or transport security.
9. Restored duplicate suppression must retain payload evidence across process
   restart; legacy identity-only checkpoints must not silently discard a
   conflicting replay.
10. Apple and Android must treat signed-zero sample payloads equivalently.
11. Direct Kotlin public model validation must normalize raw Java wrong-type,
    null, oversize, and traversal failures instead of leaking JVM exceptions.

## Scope

- Neutral Apple and Android SDK state machines, models, and deterministic
  regressions.
- Privacy-safe lifecycle evidence using existing fixed diagnostics.
- Public-repository decision alignment.
- Clean source export, application repin, and protected integration.

## Non-goals

- Supplier binaries, adapters, firmware, credentials, or flashing.
- Claims about BLE, background execution, flash depth, haptics, battery,
  physiology, or sensor accuracy.
- Removing or rerouting the independent WHOOP comparison transport.

## Observability

Session security terminals retain only fixed operation kind, outcome, failure
category, bounded counts, and duration buckets. Conflicting samples and invalid
raw collection elements fail through fixed invalid-input categories without
recording values, identities, handles, collection contents, or exception text.
Pairing-candidate default rendering exposes no opaque locator.

## Implementation

- Apple and Android accept established authentication or transport-security
  failures in each exact active operational state, invalidate session
  authority, terminate the operation, and record the operation interruption
  before the authentication terminal. Pending durable persistence remains
  fenced through the existing busy contract.
- `failOperation` validates an operation-specific fixed category set before
  any state mutation. Session/control categories such as closed,
  incompatible, busy, and invalid state/input are rejected as invalid input;
  history and firmware retain their dedicated failure categories.
- Operation authentication/security failures capture active live state before
  invalidation and record `live/interrupted` before the operation failure.
- Live and history deduplication retain the accepted sample payload beside the
  bounded identity cache. Exact duplicates remain duplicates; two values with
  the same identity but differing payloads fail as invalid input without
  advancing durable state.
- Pairing-candidate Swift descriptions/reflection and Kotlin `toString()` expose
  only the type and non-sensitive compatibility flags. The opaque adapter
  handle remains available to the adapter API but is absent from default
  rendering.
- Kotlin bounded list/set snapshots require an expected runtime element class
  for public model traversal and normalize raw Java wrong-type, null, oversize,
  and traversal failures to the fixed invalid-input boundary.
- SDK-D-010 records the current public source-repository decision and
  supersedes the historical private-repository assumption.
- Terminal firmware disposition is evaluated before authentication/security
  session terminals on both platforms, preserving `firmwareFailure` state and
  terminal firmware diagnostics for that exact combination.
- Checkpoints now carry a bounded `BandSampleFingerprint` set alongside recent
  identities. The platform-neutral SHA-256 contract hashes canonical payload
  bits, unit, and quality; `+0.0` and `-0.0` normalize to the same bits.
  Restored fingerprints distinguish exact duplicates from same-identity
  conflicts without storing raw sample values. The digest is sensitive
  integrity metadata rather than encryption and must remain in encrypted app
  storage outside logs and exports. Legacy identity-only checkpoints retain
  cursor/completion progress but replay samples to the application store
  instead of suppressing them without evidence.
- Kotlin public capability, batch, history-chunk, and checkpoint validation
  snapshot and type-check caller-owned raw collections before traversal.
  Wrong-type, null, oversize, and traversal failures use the fixed
  invalid-input boundary.
- Public Kotlin sample equality and hashing normalize signed zero like Swift,
  so enclosing batch equality is cross-platform consistent.
- Kotlin publishes the previous four-argument JVM checkpoint constructor in
  addition to the new fingerprint-aware constructor.

## Verification

- Isolated model-safety slice:
  - focused Swift `3/3`;
  - complete Swift `101/101`;
  - Kotlin/JVM `106/106` plus `installDist`;
  - shared conformance `50/50`;
  - repository gate `72` files;
  - diff hygiene passed.
- Isolated state-machine slice:
  - complete Swift `103/103`;
  - Kotlin/JVM tests plus `installDist` passed;
  - shared conformance `50/50`;
  - repository gate `71` files;
  - diff hygiene passed.
- Exact combined integration candidate:
  - complete Swift `104/104`;
  - Kotlin/JVM `109/109`, zero failures/errors/skips, plus `installDist`;
  - shared conformance `50/50`;
  - repository gate `73` files;
  - diff hygiene passed.
- Heavy commands used the NOOP bounded runner with capped private logs under
  `/tmp/sdk-pr32-*`. No resource stop occurred.
- First independent exact-diff review found four additional blockers: terminal
  firmware/auth ordering, restart-safe payload conflicts, signed-zero parity,
  and direct Java validation. The corrected candidate now passes:
  - focused Swift session tests `99/99`;
  - complete Swift `106/106`;
  - focused Kotlin/JVM tests;
  - complete Kotlin/JVM `test` plus `installDist`;
  - shared conformance `50/50`;
  - repository gate `73` files;
  - diff hygiene.
- Second independent exact-diff review found four additional issues:
  capability-report validation traversed unsnapshotted Java collections,
  FNV-1a was too collision-prone for authoritative replay suppression,
  public Kotlin signed-zero equality differed from Swift, and the new default
  parameter removed the old Java constructor. The corrected exact candidate
  now passes:
  - focused Swift regression;
  - focused Kotlin public-validation, equality, and constructor regressions;
  - complete Swift `106/106`;
  - complete Kotlin/JVM `114/114`, zero failures/errors/skips, plus
    `installDist`;
  - shared conformance `50/50`;
  - repository gate `73` files;
  - diff hygiene.
- Final independent exact-diff re-review reports no remaining P0-P2 issue.
  No SDK pull request or export is published yet.

## Ordered work

1. Open and normally merge the SDK pull request after review.
2. Generate two clean exports from the exact SDK merge and prove byte identity.
3. Repin application PR `#17`, run focused and complete applicable local
   verification once, and push one replacement candidate.
4. Resolve only findings proven fixed, wait for the exact hosted matrix, and
   integrate through protected auto-merge without administrator bypass.
5. Verify protected `main`, update durable handoff records, and remove only
   round-owned logs, caches, exports, and completed worktrees.

## External gates

Supplier, physical-device, legal, redistribution, security, signing, store,
firmware, OTA, battery, background, and accuracy gates remain open.
