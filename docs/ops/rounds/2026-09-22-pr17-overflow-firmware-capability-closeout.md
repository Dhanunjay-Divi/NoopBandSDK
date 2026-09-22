# Round: 2026-09-22 - PR 17 overflow, firmware, and capability closeout

## Status

- State: `locally verified and independently reviewed`
- Branch: `codex/sdk-pr17-overflow-firmware-closeout-20260922`
- Start commit:
  `a8f94b5cbda329eaf7793c5a2cece94fb568acc0`
- Implementation commit:
  `fde8068363248418623a8b2805d9a891f3e51f84`
- Protected merge: pending
- Clean export and NOOP application repin: pending

## Objective

Close three deterministic neutral-SDK findings reported against NOOP
application pull request `#17`:

- reject contradictory circular-history overflow ranges and samples outside the
  retained interval;
- expose an explicit terminal disposition for unrecoverable firmware failure;
  and
- reject capability reports that advertise history streams with zero retained
  history.

## Scope

- Matched Swift and Kotlin models, state machines, tests, and shared
  conformance.
- Capability JSON schema parity with runtime validation.
- Fixed-category, identifier-free firmware diagnostics.
- Source-only export and digest-pinned application consumption after protected
  SDK integration.

Out of scope:

- supplier binaries, firmware payloads, credentials, packet captures, device
  identifiers, or health data;
- real BLE, background execution, flash retention, haptics, battery, sensor
  accuracy, possession, flashing, or OTA validation;
- enabling the first-party application transport or removing WHOOP.

## Observability

The firmware terminal uses the bounded
`firmware/terminal/<category>` event, while recoverable failures remain
`firmware/failed/<category>`. No serial, source identity, cursor, range, sample
timestamp, payload, exception text, or other dynamic value is recorded.
Overflow and capability rejections use typed return categories and
deterministic tests rather than new high-frequency diagnostics.

## Verification plan

1. Add matched Swift and Kotlin regressions for range chronology, retained
   sample bounds, zero-retention capability reports, and terminal firmware
   failure.
2. Add a shared terminal-firmware conformance scenario and update the JSON
   capability contract.
3. Run focused tests, complete Swift and Kotlin walls, distribution,
   conformance, repository, JSON, secret-pattern, and diff gates through
   bounded private logs.
4. Merge normally through protected SDK `main`, export twice, compare digests,
   repin application PR `#17`, and rerun its applicable gates.

## External gates

Supplier rights, exact-model artifacts, physical Apple/Android BLE,
background/flash behavior, battery, haptics, sensor accuracy, firmware
flashing, OTA, signing, store, legal, and production gates remain open.

## Local verification

- Focused Swift regressions: 3/3 passed.
- Complete Swift package: 52/52 passed.
- Focused Kotlin regressions: 3 selected cases passed.
- Complete Kotlin/JVM package: 56/56 passed with zero failures, errors, or
  skips; `installDist` succeeded.
- Shared contract: 36/36 Swift/Kotlin scenarios matched the checked-in
  expected results.
- Repository gate: 52 files passed language, binary, JSON, and hosted-workflow
  policy.
- Capability-schema behavioral gate, JSON parsing, changed-source
  secret-pattern scan, and `git diff --check`: passed.
- The repository gate includes a dependency-free validator for every JSON
  Schema keyword used by this capability contract. It traverses every
  subschema independent of fixture contents, fails closed on a future
  unsupported keyword, enforces the normative UTF-8 byte limit, and exercises
  complete live-only, retained-history, contradictory zero-retention, and
  oversized multibyte documents.
- Independent final review found no remaining P0, P1, or P2 issue. Direct
  boundary probing confirmed that 16 two-byte characters satisfy the 32-byte
  hardware-revision limit and 17 are rejected while still satisfying the
  generic 32-code-point limit.
- One attempted final Kotlin rerun used a nonexistent repository-local Gradle
  wrapper and stopped before execution with status `start-error`. The
  corrected invocation used the already provisioned pinned Gradle 8.14.5
  distribution and completed successfully; the failed attempt is retained in
  the private round evidence.
- Bounded private evidence:
  `/private/tmp/noop-sdk-pr17-overflow-firmware-20260922/`.

## Result

- Overflow history now requires the first lost interval to precede and not
  overlap the retained interval, and every supplied sample must fall inside the
  retained bounds.
- Capability reports cannot advertise history streams with zero retained
  history; the Swift, Kotlin, and JSON-schema contracts agree.
- Firmware failures carry an explicit recoverable or terminal disposition. A
  terminal failure clears authenticated authority, advances the callback
  generation, enters the dedicated terminal `firmwareFailure` state, and
  requires a replacement session object.
- Reconnect and direct scan APIs both reject the terminal object. The bounded
  terminal diagnostic is distinct from recoverable failure and records only
  its fixed category; no dynamic diagnostic field was added.
