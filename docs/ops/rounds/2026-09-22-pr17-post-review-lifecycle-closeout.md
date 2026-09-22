# Round: 2026-09-22 - PR 17 post-review lifecycle closeout

## Status

- State: `local implementation, verification, independent review, and PR publication complete`
- Branch: `codex/sdk-pr17-diagnostics-closeout-20260922`
- Start commit: `8fb464471fdd4ae09d5750feedcc25d50bdb1c20`
- Implementation commit:
  `432c0d59ec76e6fa250f813d660e1ad86cce7c16`
- Pull request: `#14`
- Protected merge: pending
- Clean export and NOOP application repin: pending

## Objective

Close four inherited lifecycle findings reported by the live review of NOOP
application PR `#17` after the diagnostics-only diff passed independent review:

- never return an operation token after a Swift actor suspension has allowed
  that operation to be invalidated;
- represent authentication or security failure discovered while an established
  session is otherwise ready or collecting live data;
- preserve the last durable history cursor when a terminal chunk omits a new
  cursor; and
- bind connection, authentication, and capability callbacks to the exact
  selected pairing candidate rather than only to the scan generation.

The correction must remain deterministic and matched across Swift and Kotlin.
It must not enable a supplier transport or change the independent WHOOP
application transport.

## Delivered

- Return an opaque connection-attempt token from candidate selection and
  require it for connection, authentication, completion, cancellation,
  failure, and capability callbacks.
- Revalidate the active Swift operation token after the diagnostic actor
  suspension and before returning it to the adapter. A deterministic internal
  recorder suspension proves the invalidation race without timing assumptions.
- Add a generation-fenced established-session failure transition limited to
  authentication and security categories in ready and live states.
- Use the prior durable cursor as the effective terminal cursor when a complete
  history chunk omits `nextCursor`.
- Clear connection-attempt tokens whenever reconnect, disconnect, closure, or
  authenticated-session invalidation advances the lifecycle generation.
- Add matched Swift and Kotlin regressions for candidate binding,
  established-session failure, and terminal cursor retention. The existing 35
  shared conformance scenarios remain byte-for-byte matched.

## Observability and privacy

- All new rejection and terminal evidence uses existing fixed diagnostic kind,
  outcome, and failure-category fields.
- The opaque candidate token and handle are never recorded.
- No health value, device identifier, raw callback, exception text, or dynamic
  payload enters diagnostics.

## Safety boundary

- No supplier binary, firmware, credential, packet capture, identifier, or
  health data enters Git.
- WHOOP remains an independent application test transport.
- Deterministic software evidence cannot prove BLE, background collection,
  flash retention, haptics, battery, accuracy, possession, flashing, or OTA.

## Verification

| Evidence | Result | Proves | Does not prove |
|---|---|---|---|
| Swift package wall | 47/47 tests passed | Swift lifecycle implementation, deterministic actor invalidation, candidate token binding, ready/live auth terminals, cursor retention, diagnostics, and existing scenarios compile and pass | CoreBluetooth or physical-band behavior |
| Kotlin/JVM wall and distribution | 52/52 tests passed; `installDist` succeeded | Matched Kotlin lifecycle behavior and executable distribution compile and pass | Android BLE stack, background execution, or OEM behavior |
| Shared conformance | 35/35 automated Swift/Kotlin scenarios matched expected JSON | Existing cross-platform scenario outputs remain deterministic and equal | Supplier protocol correctness or sensor accuracy |
| Repository gate | 50 files passed language, binary, JSON, and hosted-workflow policy | No tracked supplier binary, prohibited workflow, invalid JSON, or public CJK content entered the repository | Redistribution authority |
| JSON, secret, and diff checks | Passed | Contract JSON remains valid, no matching credential pattern was found, and the diff has no whitespace errors | A complete external security audit |
| Independent exact-diff review | No P0-P2 finding | A second reviewer found no blocking correctness, security, API, parity, cursor, lifecycle, or observability defect in this candidate | Physical validation or supplier approval |

All heavy commands used the NOOP bounded runner with private capped logs. The
current evidence contains fixed outcomes and counts only; no health values,
device identifiers, candidate handles, credentials, or raw callbacks were
recorded.

## Remaining gates

- Normal SDK pull request merge and protected-source verification.
- Produce two byte-identical clean exports from the protected merge.
- Repin and verify the NOOP application artifact before resolving the matching
  application review threads.
- Keep supplier rights, exact-model artifacts, adapter implementation,
  physical BLE, background, flash, haptics, battery, accuracy, possession,
  flashing, and OTA evidence open.
