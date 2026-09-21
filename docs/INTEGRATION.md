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

Sample sequences use the non-negative signed 64-bit range on both platforms.
Adapters must reject supplier values outside `0 ... Int64.max`.

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
