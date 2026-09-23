# Round: 2026-09-23 - Application PR 17 final review closeout

## Status

- State: `locally verified; independent review and integration pending`
- Branch: `codex/sdk-pr30-final-review-20260923`
- Start commit: `38cf7de3b1c92dd30dad343af2adfa2cb61dea2e`
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
- Android explicitly propagates the category returned by capability-report
  validation during initial negotiation. The prior non-returning helper
  already produced `INCOMPATIBLE`; the explicit branch and regression remove
  the ambiguous control flow without changing late malformed-callback state
  preservation.

## Verification

- Focused Apple capability suspension regressions: `2/2` passed.
- Focused Android capability-category and live-terminal regressions: passed.
- Complete Swift package: `93/93` passed.
- Complete Kotlin/JVM tests and `installDist`: passed.
- Shared conformance: `50/50` Swift/Kotlin scenarios matched.
- Repository gate: `68` files passed language, binary, and JSON checks.
- Capability JSON parsing and `git diff --check`: passed.
- All verbose commands used the bounded runner with private capped logs under
  `/tmp/sdk-pr30-*`; no resource stop occurred.

## Remaining ordered work

1. Complete independent exact-diff review.
2. Commit, push, and integrate the SDK through a normal pull request.
3. Export the exact SDK merge twice and verify byte identity.
4. Repin the NOOP application PR `#17`, rerun its local and hosted exact-head
   gates, resolve reviewed threads, and integrate normally.
5. Remove only round-owned logs, caches, exports, and clean worktrees.

## External gates

Supplier, physical-device, legal, redistribution, security, signing, store,
firmware, OTA, battery, background, and accuracy gates remain open.
