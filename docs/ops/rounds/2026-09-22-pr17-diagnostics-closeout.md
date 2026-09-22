# Round: 2026-09-22 - PR 17 diagnostics closeout

## Status

- State: `locally verified; independent review and protected integration pending`
- Branch: `codex/sdk-pr17-diagnostics-closeout-20260922`
- Start commit: `8fb464471fdd4ae09d5750feedcc25d50bdb1c20`
- Protected merge: pending
- Clean export and NOOP application repin: pending

## Objective

Close two bounded-observability findings from NOOP application PR `#17`:

- record a successful connection terminal before authentication begins; and
- prevent routine successful live receipts from rapidly evicting connection,
  reconnect, rejection, and failure evidence from the bounded diagnostic
  recorder.

The correction must remain deterministic and matched across Swift and Kotlin.
It does not enable a supplier transport or change WHOOP.

## Implementation

- Emit `connection/completed` immediately before
  `authentication/began` in one recorder batch after the generation-fenced
  connection transition.
- Coalesce consecutive successful durable live receipts into one bounded
  event whose count bucket reflects the latest receipt. A rejection, failure,
  or other lifecycle event breaks coalescing so a later success remains
  visible as recovery evidence.
- Continue to record every rejection and failure, plus the ordinary
  live-session start and stop terminals.
- Add matched platform regressions using a 16-event recorder and 130 durable
  live receipts. The tests prove connection begin/completion remains present,
  completion precedes authentication begin, and one long session consumes
  only two live completion slots including the final live-session terminal.
  A second live session receives its own success and terminal slots.

## Observability and privacy

- Diagnostic events remain fixed kind/outcome/count-bucket records.
- No source identity, sample value, timestamp, cursor, token, exception text,
  or other dynamic payload is added.
- Failures and rejections remain unsampled.

## Safety boundary

- No supplier binary, firmware, credential, packet capture, identifier, or
  health data enters Git.
- WHOOP remains an independent application test transport.
- Deterministic software evidence cannot prove BLE, background collection,
  flash retention, haptics, battery, accuracy, possession, flashing, or OTA.

## Verification

| Check | Result | Boundary |
|---|---|---|
| Focused Swift regression | 1/1 passed | Connection terminal ordering and sustained-live diagnostic retention |
| Focused Kotlin regression | 1/1 passed | Matched connection terminal ordering and sustained-live diagnostic retention |
| `swift test --package-path apple` | 43/43 passed | Complete Apple neutral-core, persistence, lifecycle, actor-reentrancy, diagnostic, and conformance package wall |
| Android `test installDist` | 49/49 passed; distribution built | Complete Kotlin neutral-core and executable distribution wall |
| `python3 scripts/run_conformance.py` | 35/35 matched | Existing deterministic Swift/Kotlin scenario parity |
| `python3 scripts/check_repository.py` | 49 files passed | Language, binary, JSON, and hosted-workflow policy |
| JSON parse and `git diff --check` | Passed | Structured-contract and diff hygiene |

The first independent review found a Swift actor-reentrancy risk, eventual
FIFO pressure from periodic sampling, and conflicting active evidence. The
final implementation advances actor state before diagnostic suspension,
coalesces consecutive successful receipts into one slot, proves a second live
session receives separate evidence, records the connection/authentication
transition atomically, and replaces the stale active handoff.
The changed implementation emits no arbitrary console logging and adds no
dynamic diagnostic field.

## Remaining gates

- Complete independent diff review.
- Commit, push, protected SDK pull request review, and protected merge.
- Produce two byte-identical clean exports from the protected merge.
- Repin and verify the NOOP application artifact before resolving the matching
  application review threads.
