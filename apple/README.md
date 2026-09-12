# Apple adapter

The production adapter targets supported physical iPhones. The currently
reviewed supplier binary has no simulator, Catalyst, or macOS slice.

The module must expose neutral NOOP models and compile a deterministic simulator
stub without linking the supplier framework. It must not expose supplier
headers to the NOOP application target.

Required implementation gates:

- one actor-owned session and command queue;
- cancellation and stale-callback rejection;
- explicit CoreBluetooth restoration design and physical tests;
- capability-gated live/history/haptic/OTA behavior;
- durable app-store commit before checkpoint;
- unexpected-egress denial and signed-app traffic audit;
- bounded `AppDiagnosticsRecorder` events without identifiers or health data.
