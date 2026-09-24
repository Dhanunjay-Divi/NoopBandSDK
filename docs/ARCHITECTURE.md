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

Firmware update is additionally excluded while live collection is active. The
collector must stop live delivery before requesting an update token.
Capability schema 3 declares every other operation class that may coexist with
live collection. Absence from that allowlist fails closed as busy on both
platforms.
Every active operation has an explicit success, cancellation, or categorized
failure terminal. A terminal history receipt ends that history range; the
adapter cannot submit another chunk on the same operation.

## Negotiated sample semantics

Every advertised `(lane, stream)` pair has exactly one immutable semantic
record containing unit, cadence, quality, timestamp meaning, parser revision,
and calibration revision. The accepted capability report has its own revision.
Live acceptances and history persistence rows carry those revisions so the app
can reject drift before storage and can later identify the exact interpretation
used for a durable value.

The SDK records a fixed-count staged event after validating and fencing an
accepted live batch but before application persistence begins. A durable
completion remains distinct until the receipt is acknowledged. Only fully
acknowledged adjacent live cycles may coalesce; a pending staged event cannot
replace or move ahead of an earlier completion.

## Durable history

The application persists a source-scoped history checkpoint containing the
last acknowledged cursor, whether the last durable range was terminal, and a
bounded recent identity set with canonical SHA-256 payload fingerprints. A new
process may restore that checkpoint only for the same source identity. The
fingerprint contract normalizes signed zero and uses the same fixed
platform-neutral canonical bytes on Apple and Android. The digest provides
collision-resistant replay evidence; it is not encryption or a privacy
boundary. The checkpoint is sensitive integrity metadata and must remain in
the application's encrypted local store, outside logs, diagnostics, analytics,
and exports. A legacy identity-only checkpoint may still restore cursor and
completion progress, but its identities do not suppress replay because they
cannot distinguish an exact duplicate from a conflicting payload. Application
storage remains the durable authority for those replays.

History acceptance carries exact completion and circular-buffer overflow
state. Cursor advancement requires a durable receipt that confirms those exact
flags and confirms their metadata was committed. An incomplete range may
advance to its next cursor after that commit, but the history operation cannot
finish until a terminal chunk is durably acknowledged.

The in-memory recent-identity cache never exceeds 65,536 entries. It is a
bounded duplicate-suppression aid, not the durable source of truth. Application
storage remains authoritative for idempotency outside that recent window.

## Callback and input domains

Discovery selection and terminal callbacks carry an opaque token bound to both
the exact session object and the generation captured when scanning began.
Later asynchronous supplier callbacks carry the session generation plus their
session-bound connection, live, operation, or receipt credential. Connection
and authentication terminal callbacks also carry their originating bounded
lifecycle phase. Established non-firmware reconnect interruption requires the
exact active connection credential and returns a new opaque reconnect
credential for the advanced generation. Reconnect completion consumes that
exact credential and returns a replacement connection credential. A reconnect
that ends live collection records `live/interrupted` before clearing the live
lease, then records `reconnect/interrupted`. Foreign-session or
older-generation discovery, connection/authentication, capability, live,
history, receipt, and reconnect callbacks fail closed without mutating the
current session. Equal numeric generations never substitute for issued
credentials. Diagnostics use the originating phase rather than inferring it
from a replacement session.

A non-firmware operation that fails as disconnected is already authorized by
its exact active operation credential. Its `failOperation` terminal therefore
returns the reconnect credential needed to resume the retained identity and
capability negotiation. Other operation terminals return no reconnect
credential. Recovery from connection, capability, and firmware failures
invalidates negotiation state and intentionally requires a fresh `beginScan`;
those paths cannot be resumed with a generation alone.
Firmware disconnect records `firmware/interrupted` and
`reconnect/interrupted` in one ordered batch. Swift revalidates the exact
recovering state and generation after that diagnostic suspension, so a
concurrent recovery scan remains authoritative.

All bounded protocol strings use UTF-8 byte length on Apple and Android.
Sample sequence values use `0 ... Int64.max`, and device time is a non-negative
signed 64-bit millisecond value. The supplier adapter must reject values outside
those domains before they reach application storage. Kotlin snapshots every
supplier-owned list or set through a bounded traversal before validation.
Advertised oversize, traversal overflow, collection exceptions, and JVM null
elements fail as fixed `invalidInput` without retaining the caller-owned
collection. Every monitor-held caller-owned traversal captures session state,
generation, exact active credentials, and issuance sequences, then revalidates
them before mutation. The restored-checkpoint constructor snapshot occurs
before the session instance is published and has no reentrant session target.

## Health and data boundary

The SDK transfers measured or supplier-produced values with provenance. It
does not compute NOOP Recovery, Effort, Sleep, Fitness Age, guidance, or
diagnosis. Vendor-named apnea, blood pressure, body composition, disease risk,
or similar outputs remain unavailable unless independently validated for a
reviewed intended use.
