# Android adapter

The production adapter wraps the supplier AARs behind neutral Kotlin interfaces.
The demo application, manifest, services, permissions, logging, map key,
cleartext setting, and scan policy are not production inputs.

Implemented neutral-core gates:

- one synchronized neutral session and serialized command queue;
- session-token-bound discovery plus generation- and credential-fenced
  connection/authentication, capability, data, receipt, and reconnect
  callbacks;
- established non-firmware reconnect interruption authorized by the active
  connection token and resume authorized by the newly issued opaque reconnect
  token;
- disconnected non-firmware operations return resumable reconnect authority,
  while connection, capability, and firmware recovery paths require a fresh
  scan;
- bounded snapshots for requested streams and all supplier-owned collections,
  including fixed `invalidInput` rejection for JVM null elements;
- ordered live/reconnect interruption diagnostics before live state is cleared;
- capability revision validation and fail-closed incompatibility;
- live/history separation and deterministic deduplication;
- durable app-store receipt before historical checkpoint advance;
- explicit operation cancellation and categorized failure terminals;
- UTF-8 byte and non-negative signed 64-bit input domains;
- bounded diagnostics with no arbitrary identifier or health-value field; and
- deterministic virtual-band conformance scenarios.

Still-required production adapter gates:

- one coroutine-owned supplier adapter around the neutral core;
- foreground-service use only when justified and user-visible;
- Android 12+ Bluetooth permissions without unrelated phone/storage access;
- capability-gated live/history/haptic/OTA behavior;
- OEM background and process-death physical matrix;
- unexpected-egress denial and signed-app traffic audit;
- app `AppDiagnosticsRecorder` mapping; and
- exact supplier artifact and physical-device evidence.
