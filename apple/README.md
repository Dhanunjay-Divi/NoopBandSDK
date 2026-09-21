# Apple adapter

The production adapter targets supported physical iPhones. The currently
reviewed supplier binary has no simulator, Catalyst, or macOS slice.

The module must expose neutral NOOP models and compile a deterministic simulator
stub without linking the supplier framework. It must not expose supplier
headers to the NOOP application target.

Implemented neutral-core gates:

- one actor-owned session and command queue;
- cancellation and stale-callback rejection;
- capability revision validation and fail-closed incompatibility;
- live/history separation and deterministic deduplication;
- durable app-store receipt before historical checkpoint advance;
- bounded diagnostics with no arbitrary identifier or health-value field; and
- deterministic virtual-band conformance scenarios.

Still-required production adapter gates:

- explicit CoreBluetooth restoration design and physical tests;
- capability-gated live/history/haptic/OTA behavior;
- unexpected-egress denial and signed-app traffic audit;
- app `AppDiagnosticsRecorder` mapping; and
- exact supplier artifact and physical-device evidence.
