# Vendor intake

## Reviewed package

Static review date: 2026-09-12.

| Artifact | SHA-256 |
|---|---|
| Android `vpprotocol-2.3.81.15.aar` | `756514429b1b68328f2152d85126d1dccc9ae5f0ea2d398429bba01d53588c27` |
| iOS `VeepooBleSDK.framework` 2.2 family static archive | `22e9d0154c5fecddbd3a21ef309fb3d33d734ec5f8e671787fa9ee8564d13d35` |

These hashes identify the locally reviewed files. They do not grant
distribution rights or prove authenticity.

## Required supplier answers

- Exact production model and project code.
- Complete function-support report for that firmware.
- Printed label to provisioned identity and SDK device-number mapping.
- Pairing-mode, confirmation, identify-haptic, and possession-event contract.
- Device password, recovery, reset, return, RMA, and replacement behavior.
- History capacity, overflow, record identity, resume, clock, and reset rules.
- Sensor parts, rates, units, calibration, quality, and power modes.
- Raw PPG/IMU availability for the exact project.
- Haptic, alarm, weather, and OTA support for the exact project.
- Written binary and dependency redistribution authority.
- Complete license/notice/SBOM package and privacy disclosures.
- Runtime network behavior and a supported offline/no-vendor-cloud mode.
- Security update, vulnerability, firmware-signing, rollback, and end-of-life
  ownership.
- Representative engineering bands and firmware for Apple/Android testing.

## Import gate

Do not copy a supplier binary into a release artifact until every applicable
item above is recorded and approved. When approved, store the immutable binary
in a restricted artifact registry, pin it by version and digest, and keep only
the digest and retrieval manifest in source control.
