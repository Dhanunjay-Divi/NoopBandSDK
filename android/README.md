# Android adapter

The production adapter wraps the supplier AARs behind neutral Kotlin interfaces.
The demo application, manifest, services, permissions, logging, map key,
cleartext setting, and scan policy are not production inputs.

Implemented neutral-core gates:

- one synchronized neutral session and serialized command queue;
- generation-bound stale callback rejection;
- capability revision validation and fail-closed incompatibility;
- live/history separation and deterministic deduplication;
- durable app-store receipt before historical checkpoint advance;
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
