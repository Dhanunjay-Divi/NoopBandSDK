# NOOP Band SDK

Private integration boundary for NOOP first-party band protocol, neutral native
APIs, supplier adapters, deterministic fixtures, and conformance tooling.

This repository intentionally contains no supplier binary. The original vendor
drop is immutable and local until written redistribution rights, complete
dependency notices, exact hardware-project support, security review, and
release ownership are approved.

## Product boundary

```text
band firmware
    |
supplier transport adapter
    |
neutral NOOP Band session and samples
    |
NOOP mobile storage and product adapters
```

- One supported phone is the active BLE collector.
- Mac and additional devices are viewers through explicit NOOP+ sync.
- Device operations are serialized through one queue.
- Capability schema 3 negotiates exact stream semantics, provenance revisions,
  and operation classes that may coexist with live collection.
- Unsupported or semantically mismatched data remains missing.
- The SDK does not score health metrics or make medical claims.
- Screens, analytics, storage, and cloud code never depend on supplier types.

## Repository layout

- `spec/`: versioned neutral capability and protocol contracts.
- `apple/`: Swift neutral API and supplier adapter boundary.
- `android/`: Kotlin neutral API and supplier adapter boundary.
- `conformance/`: cross-platform state and failure scenarios.
- `docs/`: architecture, vendor intake, security, and release records.
- `vendor/`: instructions and approved artifact hashes only; drops are ignored.
- `scripts/`: local validation. Hosted Actions are intentionally absent.

## Current status

The repository now contains executable, semantically matched Swift and
Kotlin/JVM neutral cores plus a deterministic virtual band. The software proves
session serialization, stale-callback rejection, capability fail-closed
behavior, durable-before-ack history, live/history separation, and bounded
diagnostics. It also rejects out-of-order history cursors, duplicate live
identities, nonadvancing ranges, invalid device time, oversized UTF-8 metadata,
and unsupported command classes. Discovery callbacks are bound to the exact
session token; active operations have explicit cancellation and categorized
failure terminals. Connection and authentication progress, completion,
cancellation, and failure are generation-fenced and use bounded diagnostics.
Reconnect interruption and completion additionally require exact
session-issued connection and reconnect credentials, and live interruption is
recorded before reconnect clears live state. A disconnected non-firmware
operation returns resume authority; recovery from connection, capability, and
firmware failures clears negotiation and requires a fresh scan. Kotlin
snapshots hostile caller-owned collections under fixed limits and rejects JVM
null elements as `invalidInput`. Firmware uses a dedicated diagnostic family,
and the recent identity cache remains bounded. Live acceptance records bounded
staged evidence before persistence, then records durable completion without
reordering the pending lifecycle. It does not contain or validate a supplier
transport.

The owner-supplied HBand/Veepoo package was statically assessed on 2026-09-12.
It remains a candidate phone transport, not a production-approved SDK. The
exact production model, function report, printed-label mapping,
possession-proof firmware, redistribution authority, runtime egress, and
physical behavior remain open.

Run the local repository gate:

```bash
python3 scripts/check_repository.py
```

Run platform tests:

```bash
swift test --package-path apple

# Use Gradle 8.14.5 or the pinned NOOP app launcher.
/path/to/gradlew -p android --no-daemon test installDist
```

Compare every deterministic scenario across both executables:

```bash
python3 scripts/run_conformance.py
```

Create an untracked, digest-pinned source artifact for app integration:

```bash
python3 scripts/export_source_artifact.py \
  --revision "$(git rev-parse HEAD)" \
  --output /tmp/noop-band-sdk-artifact
```

See `docs/INTEGRATION.md` for the app boundary. The artifact does not enable a
supplier path, alter WHOOP support, or establish physical behavior.
