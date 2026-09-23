# Round: 2026-09-23 - Application PR 17 final review closeout

## Status

- State: `locally verified; independent review corrected; integration pending`
- Branch: `codex/sdk-pr31-review-closeout-20260923`
- Start commit: `b027cbd9702936d4903f3293ca605650cdf5c413`
- Supplier artifacts: absent and prohibited
- Physical-device claims: unchanged and unproven

## Objective

Close the three deterministic SDK findings reported against the NOOP
application PR `#17` exact head:

- revalidate Apple capability-completion authority after diagnostics suspend;
- terminate an active live diagnostic phase when an established session fails;
  and
- preserve incompatible capability-validation failures on Android.

## Scope

- Apple and Android neutral session machines.
- Matched deterministic regressions and bounded diagnostic assertions.
- Shared conformance only when a platform-neutral externally observable
  contract requires it.
- Clean source export and application artifact repin after SDK integration.

## Non-goals

- Supplier binaries, firmware, credentials, or adapters.
- BLE, background, retention, haptic, battery, or accuracy claims.
- Any change to the independent WHOOP application transport.

## Observability

Capability completion remains visible as a fixed completed event, followed by
a fixed stale-callback event if teardown wins during the recorder suspension.
An established failure during live collection records `live/interrupted`
before `authentication/rejected`. Capability validation records and returns
the fixed validation category. No identifier, token, report body, sample,
health value, or arbitrary exception text is recorded.

## Implementation

- Apple revalidates generation, the exact connection token, identity,
  capability report, and authorized progress state after the capability
  completion diagnostic suspension. Disconnect or close therefore wins and
  returns `staleCallback`; a valid live or command transition remains usable.
- Apple and Android capture whether live collection was active before an
  established authentication/security terminal invalidates the session. They
  record `live/interrupted` before `authentication/rejected`, using only the
  fixed failure category.
- The reported Android category defect was a control-flow false positive:
  initial validation calls the non-returning incompatible-rejection helper,
  so the later `invalidInput` branch is unreachable in that state. A direct
  unsupported-schema regression now proves the returned category, diagnostic,
  and terminal state without changing the correct production path.

## Verification

- Focused added Apple command-progress suspension regression: `1/1` passed;
  the prior two suspension regressions remain covered by the complete wall.
- Focused Android capability-category and live-terminal regressions: passed.
- Complete Swift package: `94/94` passed.
- Complete Kotlin/JVM tests and `installDist`: passed.
- Shared conformance: `50/50` Swift/Kotlin scenarios matched.
- Repository gate: `68` files passed language, binary, and JSON checks.
- Capability JSON parsing and `git diff --check`: passed.
- All verbose commands used the bounded runner with private capped logs under
  `/tmp/sdk-pr30-*`; no resource stop occurred.
- Independent exact-diff review found no P0/P1 and three P2 test gaps. The
  corrected tests cover valid command progress during Apple diagnostic
  suspension, exact ready-session diagnostic deltas on both platforms, and
  preserve the direct Android incompatible-category proof without retaining
  a behaviorally unnecessary production refactor.

## Remaining ordered work

1. Close the post-integration review findings recorded below.
2. Run the exact SDK verification wall and integrate through a normal pull
   request.
3. Export the exact SDK merge twice and verify byte identity.
4. Repin the NOOP application PR `#17`, rerun its local and hosted exact-head
   gates, resolve reviewed threads, and integrate normally.
5. Remove only round-owned logs, caches, exports, and clean worktrees.

## Post-integration review follow-up

- SDK PR `#30` merged normally at
  `b027cbd9702936d4903f3293ca605650cdf5c413` and was consumed by NOOP
  application PR `#17`.
- Confirmed P2: Apple clears `pendingHistory` before an awaited completion
  diagnostic. A concurrent reconnect or close can then advance generation and
  invalidate the operation while acknowledgement resumes and returns success
  without revalidating authority.
- Confirmed P2: Kotlin data-class descriptions and Swift public debug surfaces
  exposed raw sample values, timestamps, cursors, acknowledgement tokens, and
  source or device identity outside the bounded diagnostic recorder.
- The follow-up must preserve the already durable checkpoint while keeping
  lifecycle changes busy until acknowledgement completion is authority-checked.
  Deterministic suspension tests must cover reconnect, close, and duplicate
  acknowledgement during the diagnostic window.
- Apple now retains `pendingHistory` through the awaited completion diagnostic,
  rejects duplicate acknowledgement while completion is in flight, blocks
  reconnect and close through the existing pending-persistence fence, and
  revalidates generation, the exact history token, history state, and receipt
  sequence before clearing pending authority.
- Apple and Kotlin now render `BandIdentity`, sample identity, sample, sample
  batch, history range, history chunk, history checkpoint, and session snapshot
  as type names only. Swift also supplies redacted custom reflection so
  `Mirror` and `dump` do not expose stored fields or the acknowledged cursor.
- Dedicated Apple concurrency tests pass `4/4`; dedicated model-rendering tests
  pass `2/2` on each platform. The complete Swift package passes `100/100`,
  Kotlin/JVM passes `102/102` plus `installDist`, all `50/50` shared
  conformance scenarios match, the `71`-file repository gate passes, and diff
  hygiene is clean. Heavy commands used capped private logs under
  `/tmp/sdk-pr31-*`.
- Independent exact-diff review found no P0/P1 and two P2 gaps. The final
  correction adds the public session snapshot to the type-only/redacted
  reflection contract so its acknowledged cursor cannot escape, and removes
  obsolete capability-equivalence branch metadata from the active handoff.
  The complete Swift, Kotlin, conformance, repository, and diff gates all pass
  again after those corrections.
- No replacement artifact is published yet. Normal SDK integration, clean
  export, and application repin remain.

## External gates

Supplier, physical-device, legal, redistribution, security, signing, store,
firmware, OTA, battery, background, and accuracy gates remain open.
