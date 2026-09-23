# Round: 2026-09-23 - Established-session callback authority

## Status

- State: `locally verified and independently reviewed; publication pending`
- Branch: `codex/sdk-pr25-session-authority-20260923`
- Start commit: `f20f4ed552328a64a8a598aaac72befa1d481262`
- Implementation commits:
  - `4fdef89d6047a270e90b6b0434461a7aca2ed10e`
  - `2281b3665b2cf83f8445f4fdd52b373925bec551`
- Evidence commit: this documentation-only commit
- Pull request: pending
- Supplier artifacts: absent
- Physical-device claims: unchanged and unproven

## Objective

Resolve the remaining valid deterministic findings from NOOP application pull
request `#17` without changing the existing WHOOP application transport:

- bind established authentication and security terminal callbacks to the exact
  active connection credential, not only a reusable numeric generation;
- require the exact active live token when stopping collection;
- redact Swift opaque tokens, acceptances, accepted-history wrappers, and
  durable receipts from default string, debug-string, reflection, and dump
  output; and
- preserve matched Swift and Kotlin behavior and exported-source compatibility.

## Finding validation

Direct review of exact SDK `main` and the application-vendored export disproved
four additional comments:

- graceful `disconnect(reason:callbackGeneration:)` exists on both platforms;
- scan callbacks already require nonce-bearing session tokens;
- history start already requires positive retention and a nonempty negotiated
  history stream set; and
- Kotlin set snapshots already count and bound iterator steps independently of
  unique cardinality.

Those behaviors remain unchanged and their existing tests remain required.

## Implementation

- `failEstablishedSession` now requires the exact active
  `BandConnectionToken` in addition to the callback generation on Swift and
  Kotlin. A token from another session with the same numeric generation is
  rejected as `staleCallback` without terminating the current session.
- `stopLive` now requires the exact active `BandLiveToken` on both platforms.
  A token from a completed live lease cannot stop a replacement lease.
- Every internal call site and exported virtual-band scenario passes the
  issued token rather than reconstructing or omitting callback authority.
- Swift connection, live, and operation tokens plus live/history acceptances
  and durable receipts now provide redacted normal, debug, reflection, and
  dump representations.
- Kotlin connection and live tokens now use the same stable redacted rendering
  already used by operation tokens, acceptances, and receipts.
- `resumeAfterReconnect` now issues a fresh connection token for the new
  transport generation. The pre-reconnect token remains stale, while the
  returned token can authorize later authentication or security failure
  callbacks.
- Swift revalidates the replacement token after diagnostic suspension without
  requiring the session to remain `.ready`; a legitimate concurrent live start
  therefore cannot make the reconnect token inaccessible to its caller.
- The portable contract adds `established_failure_session_bound` and
  `superseded_live_stop_rejected`, plus
  `reconnected_established_failure_authorized`, bringing the automated
  cross-platform scenario count to 45.

## Safety and observability

The corrected paths reuse fixed `authentication/stale/staleCallback` and
`live/stale/staleCallback` diagnostics. No identifiers, callback credentials,
health values, samples, cursors, or supplier error text are recorded.
Redaction tests must cover normal description, debug description, reflection,
and dump output because synthesized Swift representations can otherwise expose
private receipt state and accepted health samples.

## Verification plan

1. Add mirrored Swift and Kotlin stale established-terminal and stale live-stop
   regressions.
2. Add Swift rendering/reflection regressions for every opaque token,
   acceptance, accepted-history wrapper, and durable receipt.
3. Run complete Swift, Kotlin plus distribution, shared conformance,
   repository, JSON, secret-pattern, and diff gates.
4. Obtain independent review, commit, push once, merge normally, and produce
   two byte-identical clean exports before repinning NOOP application PR `#17`.

## Verification evidence

- `swift test --package-path apple --jobs 1`: 69/69 tests passed.
- Gradle `test installDist`: passed and the Kotlin conformance distribution
  built successfully.
- `python3 scripts/run_conformance.py`: 45/45 ordered Swift/Kotlin scenarios
  matched the checked-in contract.
- `python3 scripts/check_repository.py`: 61 repository files passed language,
  binary, and JSON policy gates.
- Independent JSON parsing and `git diff --check`: passed.
- Bounded secret-pattern review of added lines: zero matches.
- The first 44-scenario invocation used stale pre-change executables and
  failed before publishing a result. Both conformance executables were rebuilt
  from the candidate source; the required rerun then passed all 44 scenarios.
  No stale-binary result is counted as evidence.
- Independent review found that reconnect originally cleared connection
  authority without issuing a replacement. Both platforms now return a fresh
  token, reject the retired token, and pass a shared reconnect scenario.
- Focused re-review then found a Swift actor-reentrancy path where a concurrent
  live start could make the valid replacement token inaccessible after the
  reconnect diagnostic suspended. The post-suspension guard now validates
  generation and exact token authority, and a direct suspended-record
  regression passes.
- Final exact-diff re-review reports no remaining P0-P2 finding.
- Two independently generated 10-file source exports from implementation head
  `2281b3665b2cf83f8445f4fdd52b373925bec551` are byte-identical. Candidate
  manifest SHA-256:
  `d980188805fc7e1424501c365773e0b65542c163077d481fa266b91925d563ac`.
  The application will consume a new export from the exact merge commit, not
  this pre-merge candidate.
- Output remained in private bounded logs under `/tmp`; no raw health values,
  identifiers, callback credentials, or supplier payloads were added to
  diagnostics.

## Remaining gates

1. Commit, push once, open the pull request, and merge normally.
2. Produce two byte-identical clean source exports from the exact merge.
3. Repin NOOP application PR `#17`, rerun exported-source consumer and
   application gates, and complete its protected hosted review.

## External gates

Supplier binaries, firmware, BLE, background execution, disconnected-band
retention, haptics, battery, accuracy, possession, OTA, legal, redistribution,
signing, store, and physical-device evidence remain explicitly open.
