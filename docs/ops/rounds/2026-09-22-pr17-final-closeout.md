# Round: 2026-09-22 - PR 17 final closeout

## Status

- State: `ready for protected review`
- Branch: `codex/sdk-pr17-final-closeout-20260922`
- Start commit: `c254cb329963eb262d18c43ae6b25a8340fe77f6`
- Protected merge: pending
- Clean export and NOOP application repin: pending

## Objective

Close the three remaining deterministic SDK findings before the NOOP
application consumes another protected source export.

## In scope

- Serialize live and history durable-acceptance windows so the same sample
  cannot be accepted by both lanes before either receipt is acknowledged.
- Add explicit, generation-fenced capability-negotiation cancellation and
  categorized failure terminals on Apple and Android.
- Invalidate authenticated session state when an active operation reports an
  authentication failure.
- Add matched Apple and Kotlin regressions and rerun the complete neutral-core
  verification wall.

## Out of scope

- Supplier binaries, firmware, credentials, packet captures, identifiers, or
  health data.
- Physical BLE, background execution, flash retention, haptics, battery,
  accuracy, possession, flashing, or OTA claims.
- Removing or changing the independent WHOOP application transport.

## Starting evidence

- Protected SDK `main` is `c254cb3`.
- Application PR `#17` has three unresolved review findings matching the
  objective above.
- The source currently permits one pending live receipt and one pending history
  receipt at the same time, exposes only capability acceptance as a capability
  terminal, and clears only the active operation after ordinary authentication
  failure.

## Observability

The implementation reuses fixed `live` or `history` busy rejection events,
fixed capability terminal outcomes and failure categories, and the existing
operation failed/authentication event. It adds no identifiers, payloads,
health values, raw platform errors, or high-frequency diagnostics.

## Verification plan

1. Add matched Apple and Android direct regressions for all three findings.
2. Run focused tests, then repository, Swift, Kotlin, distribution,
   conformance, JSON, and diff gates with bounded private logs.
3. Merge normally through protected SDK `main`, export the protected source
   twice, compare manifests and tree digests, repin the NOOP application, and
   rerun its relevant local and hosted gates.

## External gates

Supplier, physical-device, firmware, legal, redistribution, security-review,
signing, store, carrier, OTA, and physiological-validation gates remain open.

## Local verification

- Focused Apple session suite: 39/39 tests pass.
- Complete Swift package: 39/39 tests pass.
- Kotlin/JVM session suite: 44/44 tests pass.
- Complete Kotlin/JVM test and `installDist`: pass.
- Shared contract: 35 Swift/Kotlin scenarios match the checked-in expected
  results and each other.
- Repository language, binary, and JSON gate: 47 files pass.
- `conformance/scenarios.json` parsing and `git diff --check`: pass.
- The first independent review found one P1 persistence-terminal race and two
  P2 coverage gaps. The implementation now blocks lifecycle terminals while a
  durable receipt is unresolved, releases only an exact negative receipt,
  covers stale capability cancel/fail plus terminal mappings directly and in
  shared conformance, and exercises authentication invalidation with concurrent
  live/history persistence.
- A second independent read-only review found no remaining P0, P1, or P2
  implementation defect or Apple/Kotlin parity drift. It left only physical
  supplier callback ordering, BLE timing, process-death, flash, background,
  and real-store crash-consistency validation as external risks.
- Bounded logs and statuses:
  `/tmp/noop-sdk-pr17-final-closeout-20260922/`.
