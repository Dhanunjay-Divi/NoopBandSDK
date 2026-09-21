# Round: 2026-09-21 - Runtime state hardening

## Status

- State: `ready for review`
- Branch: `codex/sdk-runtime-hardening-20260921`
- Start commit: `a04c263e7229532038b13c7da343a43747864390`
- Implementation commit: pending
- Protected merge: pending

## Objective

Close deterministic runtime-state gaps found while integrating the neutral SDK
into the NOOP application, preserve matched Apple and Android behavior, and
publish a reproducible source-only export from a truthful private-repository
revision.

## In scope

- Generation-fenced discovery callbacks and explicit scan terminals.
- Immutable capability/checkpoint inputs and idempotent capability callbacks.
- Negotiated non-HR live streams with durable live-batch acknowledgements.
- History eligibility, cursor, receipt, and completion gates.
- Firmware exclusivity followed by mandatory reconnect and renegotiation.
- Opaque operation and live-receipt tokens.
- Fixed-category, identifier-free diagnostics.
- Swift, Kotlin, shared conformance, repository, and export verification.

## Out of scope

- Supplier binaries, firmware, credentials, flashing, BLE transport,
  background execution, haptics, flash retention, battery, physiology,
  possession proof, or physical-device claims.
- Enabling a production first-party adapter.
- Changing or removing the independent WHOOP application test transport.

## Observability

The changed state boundaries use only fixed diagnostic kind, outcome, failure
category, and bounded count buckets. No address, serial, source identity,
health value, sample, timestamp, cursor, token, payload, or arbitrary exception
text may enter diagnostics.

## Starting evidence

- Protected private SDK main:
  `a04c263e7229532038b13c7da343a43747864390`.
- Prior source export:
  `34028a2ab56feb90ae774b0ee0055529ce175723`.
- Application review candidate has matched Apple and Android source changes,
  27 shared conformance scenarios, focused Apple package evidence, and focused
  Android app-module evidence. Those app-repository results are input for this
  round, not proof of this private branch.

## Implementation

- Discovery callbacks are fenced to the scan generation that created them.
  Cancel and failure are explicit generation-fenced terminal outcomes, and a
  stale candidate or terminal callback cannot mutate a newer scan.
- Capability and durable-checkpoint inputs are copied into immutable session
  state. Duplicate capability completion is idempotent, while callbacks from a
  prior generation fail closed.
- Live delivery supports every negotiated sensor stream, not only heart rate.
  A staged live batch receives an opaque receipt and becomes durable only after
  that receipt is acknowledged. Receipts carry a private per-session nonce and
  per-issuance sequence, so a receipt created by another machine cannot commit
  this session. Duplicate identities are bounded and accepted once without
  advancing history state.
- History start requires an eligible negotiated capability and a durable
  checkpoint. Every chunk requires its own opaque durable receipt before the
  cursor advances. History receipts use the same private session and issuance
  binding, incomplete chunks must advance, and completion requires the expected
  cursor chain.
- Firmware entry is exclusive with live and history work. A firmware terminal
  invalidates identity and negotiated capability state, requiring a fresh scan,
  connection, and capability negotiation before another operation.
- Operation and durable-receipt tokens are opaque bounded values and are bound
  to the machine session that issued them. Reconnect interruption and
  completion both require the generation captured by their originating
  transport callback. Diagnostics use fixed lifecycle, outcome,
  failure-category, and count fields only; they do not include addresses,
  serials, identifiers, samples, health values, timestamps, cursors, tokens,
  payloads, or arbitrary exception text.
- The Apple and Kotlin implementations and their deterministic virtual bands
  carry the same state transitions. The application repository remains
  responsible for transport integration; the independent WHOOP test transport
  is unchanged.

## Verification evidence

- Repository guard:
  `python3 scripts/check_repository.py` passed all 40 tracked and untracked
  non-ignored repository files through language, binary, JSON, and hosted
  workflow checks.
- Apple:
  `swift test --package-path apple` ran through the bounded command runner and
  passed 27 tests in one suite with zero failures.
- Kotlin:
  `/Users/divii/noop-sandbox/Noop/android/gradlew -p android --no-daemon
  --no-parallel --max-workers=1 test installDist` ran through the bounded
  command runner, passed 28 tests with zero failures or skips, and completed
  the distribution build.
- Cross-language contract:
  `python3 scripts/run_conformance.py` ran through the bounded command runner
  and matched all 30 Swift/Kotlin scenarios.
- Independent review:
  the first exact-diff review found two P1 defects: cross-machine live/history
  receipts and Swift tokens could collide, and scan/reconnect terminal
  callbacks were not generation fenced. Both findings are corrected with
  dedicated Apple, Kotlin, and shared conformance regressions. Focused
  follow-up found no remaining implementation defect and one P2 coverage gap:
  same-session replay after a newer issuance was not direct. The final scenario
  now replays and rejects an older live receipt, operation token, and history
  receipt within one session on both implementations. A final narrow
  independent review of that regression returned no findings.
- Structured data:
  every repository JSON file outside generated build output parses with
  `python3 -m json.tool`.
- Hygiene:
  `git diff --check` passes.
- Resource boundary:
  verbose Swift, Kotlin, and conformance work used private capped logs, a
  10 GiB free-disk floor, a 10 percent free-memory floor, process-group
  termination, and bounded deadlines through the application repository's
  trusted command runner. No verbose child output was streamed into iTerm.
- What this proves:
  deterministic source behavior, cross-language parity, repository
  distributability, and bounded diagnostic structure for the tested virtual
  scenarios.
- What this does not prove:
  CoreBluetooth or Android BLE transport, background execution, haptics,
  supplier flash retention, battery use, sensor accuracy, possession proof,
  firmware transfer, recovery after radio loss, or any physical-device
  behavior.

## Application follow-up

1. Commit and push this branch once, then merge it through the private
   repository's normal protected review.
2. Export the exact merged source revision twice and compare the two artifacts
   byte-for-byte.
3. Replace and repin the application artifact and verifier to that exact
   protected revision.
4. Run application Apple, Android, release-control, and hosted exact-SHA gates.

## External gates

All supplier, physical-device, legal, redistribution, security, OTA, and
accuracy gates remain open.
