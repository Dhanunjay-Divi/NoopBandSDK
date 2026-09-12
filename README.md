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
- Capabilities are negotiated; unsupported data remains missing.
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

The owner-supplied HBand/Veepoo package was statically assessed on
2026-09-12. It is a candidate phone transport, not a production-approved SDK.
The exact production model, function report, printed-label mapping,
possession-proof firmware, redistribution authority, runtime egress, and
physical behavior remain open.

Run the local repository gate:

```bash
python3 scripts/check_repository.py
```
