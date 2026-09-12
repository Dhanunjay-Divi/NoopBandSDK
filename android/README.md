# Android adapter

The production adapter wraps the supplier AARs behind neutral Kotlin interfaces.
The demo application, manifest, services, permissions, logging, map key,
cleartext setting, and scan policy are not production inputs.

Required implementation gates:

- one coroutine-owned session and serialized command queue;
- foreground-service use only when justified and user-visible;
- Android 12+ Bluetooth permissions without unrelated phone/storage access;
- capability-gated live/history/haptic/OTA behavior;
- durable app-store commit before checkpoint;
- OEM background and process-death physical matrix;
- unexpected-egress denial and signed-app traffic audit;
- bounded `AppDiagnosticsRecorder` events without identifiers or health data.
