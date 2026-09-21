# NOOP Band SDK agent entry point

Before material SDK, transport, firmware, application-integration, deployment,
or release work:

1. Read `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`, `docs/ops/ACTIVE.md`, and
   the newest relevant record under `docs/ops/rounds/`.
2. Preserve unrelated work and use an isolated branch or worktree.
3. Keep supplier binaries, credentials, firmware, packet captures, device
   identifiers, and health data out of Git.
4. Treat deterministic tests as software evidence only. BLE, background,
   retention, haptic, battery, accuracy, possession, and OTA claims require the
   exact approved supplier artifact and physical-device evidence.
5. Preserve WHOOP as an independent NOOP application test transport until the
   first-party transport passes its recorded gates.

Before commit, run:

```bash
python3 scripts/check_repository.py
swift test --package-path apple
/path/to/gradlew -p android --no-daemon test installDist
python3 scripts/run_conformance.py
git diff --check
```

Use bounded private logs for verbose builds. Do not add hosted workflows until
the release owner explicitly approves their budget and required-check policy.
