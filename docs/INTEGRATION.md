# NOOP application integration

## Boundary

The application consumes one digest-pinned source artifact generated from an
approved `NoopBandSDK` commit. This avoids a live cross-private-repository
dependency while preserving the SDK repository as the authority.

The exporter accepts only the exact checked-out `HEAD` of a clean worktree. It
refuses dirty or falsely labelled source and publishes the finished artifact
atomically.

The artifact contains:

- neutral Swift production core;
- neutral Kotlin production core;
- virtual-band test support;
- capability and conformance contracts; and
- a manifest with the source revision, relative paths, byte counts, and
  SHA-256 digests.

It never contains supplier binaries, credentials, device identifiers, packet
captures, health data, endpoints, or firmware.

## Application rules

1. Commit the generated artifact and manifest to the private NOOP application
   repository under a dedicated generated-source boundary.
2. Treat generated files as read-only. Changes begin in `NoopBandSDK`, rerun
   its tests, regenerate the artifact, and verify every manifest digest.
3. Keep the current WHOOP transport independent and enabled for controlled
   comparison. The neutral NOOP Band path remains default-off.
4. Register only the virtual adapter until an approved supplier adapter
   exists. A simulator or JVM run must never load a device-only supplier
   binary.
5. Route accepted neutral batches into the existing platform storage adapter.
   Live delivery never advances history. A history acknowledgment requires the
   exact durable receipt returned after app-store commit.
6. Persist the source-scoped history checkpoint after acknowledgment and seed a
   replacement process with that cursor plus its bounded recent identity set.
   Never restore one band's checkpoint into another source identity.
7. Persist exact history `complete` and `overflowed` flags before setting
   `historyStateCommitted` in a receipt. An incomplete range cannot complete
   the SDK history operation.
8. Map SDK diagnostic events into the existing bounded app recorder without
   adding identifiers, sample values, payloads, timestamps, URLs, or arbitrary
   exception text.
9. Do not expose pairing, possession, haptic, alarm, wear, firmware, or sensor
   capabilities until the exact supplier and physical gates pass.

Discovery, connection/authentication, capability, live/history delivery,
durable-receipt, and reconnect callbacks must include the session generation
captured for the supplier request. Connection and authentication cancellation
or failure must also include the bounded phase captured for that attempt; the
adapter must not infer phase from current replacement-session state. Sample
sequences and device-time milliseconds use the non-negative signed 64-bit
range on both platforms. Bounded protocol strings use UTF-8 byte counts.
Adapters must reject values outside these domains. On Kotlin/JVM, the adapter
must expect every caller-owned list and set to be snapshotted once under the
SDK limit; collection exceptions, nonterminating/repeating traversal, reported
oversize, and null elements are rejected as `invalidInput`.

## Band connection and authentication pass

The production adapter must preserve these distinct authorities:

1. The NOOP account service authenticates the person and grants one phone the
   collector lease. This is application/cloud authorization, not BLE trust.
2. The phone scans for supplier candidates and exposes only an opaque handle to
   the neutral SDK. Device names, addresses, serials, and advertisements do not
   enter diagnostics. Selection, cancellation, and failure callbacks return the
   opaque scan token issued by that exact neutral session.
3. The supplier transport performs the approved identify/possession challenge.
   The exact vibration, tap, printed-label, or challenge-response behavior is
   unavailable until the reviewed firmware and supplier SDK define it.
4. After possession succeeds, the supplier transport connects and performs its
   approved cryptographic authentication. Long-lived secrets belong in
   platform secure storage and never in this repository, logs, preferences, or
   source artifacts.
5. The adapter reports connection and authentication progress, success, or a
   categorized terminal with the generation captured when that attempt began.
   The neutral session then enters capability negotiation, whose acceptance,
   cancellation, and categorized failures are also explicit generation-fenced
   terminals. Stale connection, authentication, and capability callbacks are
   rejected without changing the replacement session.
6. The app enables only negotiated streams and commands. Unsupported inputs
   remain missing, and firmware operations use their own diagnostics and cannot
   overlap live collection.
7. Live samples are committed through the app storage boundary. History cursor
   progress occurs only after an exact durable receipt; each history operation
   must receive its own terminal durable result. Live and history acceptance
   windows are serialized, and lifecycle terminals cannot discard an unresolved
   persistence receipt. An exact negative receipt releases the reservation
   without advancing durable identity or cursor state.
8. Cancellation, timeout, disconnect, and supplier failure terminate the active
   neutral operation explicitly after pending persistence drains. Established
   non-firmware reconnect interruption passes the exact active
   `BandConnectionToken`, creates a new generation, and returns a
   `BandReconnectToken`. The adapter retains that opaque token and passes it
   with the new generation to
   `resumeAfterReconnect`; successful resume consumes it and returns the new
   connection token. A foreign token is stale even when its generation is
   numerically equal. If reconnect interrupts live collection, the SDK records
   `live/interrupted` before clearing live state and then records
   `reconnect/interrupted`. A disconnected non-firmware `failOperation` call
   returns the same kind of reconnect token because its active operation token
   already authorizes that terminal. A `nil`/`null` result means there is no
   resumable authority. Recovery from connection, capability, and firmware
   failures clears negotiation state and must restart at `beginScan`.
   Authentication failures invalidate the authenticated generation before
   another command can begin.
9. Collector handoff flushes accepted samples and checkpoints, releases the
   account lease, disconnects the old phone, and only then permits another
   authorized phone to collect.

The neutral core now enforces steps 5 through 8 deterministically. Steps 1
through 4 and physical behavior in step 9 require the NOOP account integration,
approved supplier artifacts, firmware contract, secure-key design, and
physical-device evidence.

## Release sequence

1. Merge and privately tag a reviewed SDK revision.
2. Generate the source artifact with that full revision.
3. Verify the artifact manifest independently.
4. Vendor it into an isolated NOOP app branch.
5. Compile Apple and Android apps with the virtual adapter default-off.
6. Run app source-switch and WHOOP regression tests.
7. Preserve all physical claims as open.

The source artifact is a software-integration mechanism, not evidence that the
future supplier adapter or band is production ready.
