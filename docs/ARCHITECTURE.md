# Architecture

## Collector topology

One phone is the active BLE collector for a band. Additional authorized
devices consume account-synchronized history and never open a competing BLE
session. A future collector handoff must:

1. stop accepting new device commands;
2. commit all accepted samples;
3. checkpoint durable history progress;
4. release the collector lease;
5. disconnect the old phone;
6. let the new phone resume from the durable checkpoint.

Silent or concurrent collection is prohibited.

## Layers

1. **Neutral model:** measured samples, units, quality, device time, sequence,
   capability, firmware, battery, wear, and history progress.
2. **Session machine:** discovery, confirmation, authentication, capability,
   live collection, history, command, OTA, disconnect, and recovery states.
3. **Supplier adapter:** platform-specific translation from vendor callbacks to
   neutral events.
4. **Transport:** CoreBluetooth or Android BLE lifecycle and permissions.
5. **Storage handoff:** idempotent batches accepted by the NOOP app store.
6. **Diagnostics:** fixed categories, bounded counts, and durations only.

## Supplier isolation

The application repository depends on a versioned NOOP SDK artifact, not on
raw supplier binaries or demo source. The supplier package is linked only by
the adapter build for supported physical-device targets.

The Apple simulator uses a deterministic stub because the currently reviewed
supplier framework is an arm64 iPhoneOS static archive without a simulator,
Catalyst, or macOS slice. The Mac app is a viewer, not a band collector.

## Command ownership

Every command enters one queue. Capability reads, clock, settings, history
categories, live mode, haptics, alarms, and OTA cannot overlap. Each operation
has:

- a stable operation class;
- start, timeout, cancellation, and terminal states;
- stale-callback rejection;
- a bounded retry policy;
- identifier-free diagnostics.

## Health and data boundary

The SDK transfers measured or supplier-produced values with provenance. It
does not compute NOOP Recovery, Effort, Sleep, Fitness Age, guidance, or
diagnosis. Vendor-named apnea, blood pressure, body composition, disease risk,
or similar outputs remain unavailable unless independently validated for a
reviewed intended use.
