# Contributing

## Rules

1. Keep public APIs, comments, tests, and documentation in English.
2. Do not rewrite or sanitize the original supplier drop. Keep it immutable and
   untracked so its provenance and hashes remain auditable.
3. Do not commit binaries, credentials, device identifiers, private packet
   captures, health data, or production endpoints.
4. Keep supplier types inside platform adapter modules.
5. Preserve one active collector and one serialized operation queue per band.
6. Capability-gate every sensor, haptic, history, and firmware operation.
7. Persist accepted samples before advancing any NOOP resume position.
8. Use fixed, bounded diagnostic categories without identifiers or health
   values.
9. Treat simulator and unit tests as software evidence only. BLE, background,
   battery, haptic, OTA, and sensor claims require physical devices.
10. Run `python3 scripts/check_repository.py` before every commit.

No hosted CI workflow should be added until the release owner approves its
budget and required-check policy. Prefer deterministic local checks and one
consolidated hosted verification per reviewed release candidate.
