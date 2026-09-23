# Decisions

| ID | Decision | Status |
|---|---|---|
| SDK-D-001 | The band SDK is a separate private repository and release artifact from the NOOP application repository. | Active |
| SDK-D-002 | Public SDK APIs, source, tests, samples, and documentation are English-only. Original vendor drops remain immutable and untracked rather than rewritten. | Active |
| SDK-D-003 | One phone is the active collector; Mac and other devices are managed-sync viewers. | Active |
| SDK-D-004 | Supplier binaries remain outside Git and outside application builds until rights, SBOM, security, egress, exact-model, and physical gates pass. | Active |
| SDK-D-005 | Supplier types and persistence remain behind neutral Apple/Android adapters; NOOP owns provenance, storage, checkpoints, diagnostics, and product behavior. | Active |
| SDK-D-006 | No GitHub Actions workflow is enabled initially. Local deterministic checks precede one budgeted hosted release check when approved. | Active |
| SDK-D-007 | Cross-platform bounded strings use UTF-8 bytes; samples use non-negative signed 64-bit sequence/time domains; recent identity memory is bounded and application storage remains the durable dedupe authority. | Active |
| SDK-D-008 | Reconnect interruption and completion require exact session-issued connection and reconnect credentials; a disconnected non-firmware operation returns resume authority, while invalidated connection/capability/firmware recovery requires rediscovery. Kotlin snapshots supplier-owned collections with bounded traversal and rejects JVM null elements as fixed invalid input. | Active |
