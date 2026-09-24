import Testing
@testable import NoopBandCore

@Suite("SDK model rendering privacy")
struct ModelRenderingPrivacyTests {
    @Test("Pairing candidate renders only non-sensitive display state")
    func pairingCandidateRedactsOpaqueHandle() {
        let candidate = BandPairingCandidate(
            handle: Self.candidateHandleSentinel,
            compatible: true,
            identifyEligible: false
        )
        let expected =
            "BandPairingCandidate("
            + "compatible: true, "
            + "identifyEligible: false"
            + ")"
        let description = String(describing: candidate)
        let reflection = String(reflecting: candidate)
        let mirror = Mirror(reflecting: candidate)
        let children = Array(mirror.children)
        var dumpOutput = ""
        dump(candidate, to: &dumpOutput)

        #expect(description == expected)
        #expect(reflection == expected)
        #expect(mirror.displayStyle == .struct)
        #expect(children.count == 2)
        #expect(children[0].label == "compatible")
        #expect(children[0].value as? Bool == true)
        #expect(children[1].label == "identifyEligible")
        #expect(children[1].value as? Bool == false)
        #expect(dumpOutput.contains("compatible"))
        #expect(dumpOutput.contains("identifyEligible"))

        [
            description,
            reflection,
            dumpOutput,
            String(describing: [candidate]),
            String(reflecting: [candidate]),
        ].forEach(expectPrivacySafe)
    }

    @Test("Models redact String, reflection, Mirror, and dump output")
    func modelsRedactDirectRendering() {
        let fixtures = modelFixtures()

        fixtures.forEach { fixture in
            expectPrivacySafeRendering(fixture.value, as: fixture.name)
        }
    }

    @Test("Nested collections redact model content")
    func nestedCollectionsRedactModelContent() {
        let fixtures = modelFixtures()
        let nested = [fixtures.map(\.value)]
        var dumpOutput = ""
        dump(nested, to: &dumpOutput)

        let rendered = [
            String(describing: nested),
            String(reflecting: nested),
            dumpOutput,
        ]
        for output in rendered {
            fixtures.forEach { fixture in
                #expect(output.contains(fixture.name))
            }
            expectPrivacySafe(output)
        }
    }

    private func modelFixtures() -> [ModelFixture] {
        let sampleIdentity = BandSampleIdentity(
            stream: .heartRate,
            sequence: Self.sampleSequenceSentinel,
            deviceTimeMilliseconds: Self.sampleTimeSentinel
        )
        let sample = BandSample(
            identity: sampleIdentity,
            value: Self.sampleValueSentinel,
            unit: .beatsPerMinute,
            quality: .degraded
        )
        let sampleFingerprint = BandSampleFingerprint(
            identity: sampleIdentity,
            payloadFingerprint: Self.fingerprintSentinel
        )
        let batch = BandSampleBatch(
            sourceIdentity: Self.sourceSentinel,
            lane: .history,
            parserRevision: Self.parserSentinel,
            calibrationRevision: Self.calibrationSentinel,
            samples: [sample]
        )
        let retainedRange = BandHistoryRange(
            startDeviceTimeMilliseconds: Self.retainedStartSentinel,
            endDeviceTimeMilliseconds: Self.retainedEndSentinel
        )
        let chunk = BandHistoryChunk(
            chunkIdentity: Self.chunkSentinel,
            previousCursor: Self.previousCursorSentinel,
            nextCursor: Self.nextCursorSentinel,
            complete: false,
            overflowed: true,
            retainedRange: retainedRange,
            firstLostRange: BandHistoryRange(
                startDeviceTimeMilliseconds: Self.lostStartSentinel,
                endDeviceTimeMilliseconds: Self.lostEndSentinel
            ),
            acknowledgementToken: Self.acknowledgementSentinel,
            batches: [batch]
        )

        return [
            ModelFixture(
                name: "BandIdentity",
                value: BandIdentity(
                    sourceIdentity: Self.sourceSentinel,
                    hardwareRevision: Self.hardwareSentinel,
                    firmwareVersion: Self.firmwareSentinel,
                    protocolVersion: Self.protocolSentinel,
                    wrapperRevision: Self.wrapperSentinel
                )
            ),
            ModelFixture(name: "BandSampleIdentity", value: sampleIdentity),
            ModelFixture(name: "BandSample", value: sample),
            ModelFixture(
                name: "BandSampleFingerprint",
                value: sampleFingerprint
            ),
            ModelFixture(name: "BandSampleBatch", value: batch),
            ModelFixture(name: "BandHistoryRange", value: retainedRange),
            ModelFixture(name: "BandHistoryChunk", value: chunk),
            ModelFixture(
                name: "BandHistoryCheckpoint",
                value: BandHistoryCheckpoint(
                    sourceIdentity: Self.sourceSentinel,
                    acknowledgedCursor: Self.checkpointCursorSentinel,
                    lastHistoryComplete: false,
                    durableSampleIdentities: [sampleIdentity],
                    durableSampleFingerprints: [sampleFingerprint]
                )
            ),
            ModelFixture(
                name: "BandSessionSnapshot",
                value: BandSessionSnapshot(
                    state: .historyCollecting,
                    generation: 42,
                    activeOperation: .history,
                    liveActive: false,
                    acknowledgedHistoryCursor: Self.checkpointCursorSentinel,
                    durableSampleCount: 1
                )
            ),
        ]
    }

    private func expectPrivacySafeRendering<T>(_ value: T, as name: String) {
        let description = String(describing: value)
        let reflection = String(reflecting: value)
        let mirror = Mirror(reflecting: value)
        let children = Array(mirror.children)
        var dumpOutput = ""
        dump(value, to: &dumpOutput)

        #expect(description == name)
        #expect(reflection == name)
        #expect(mirror.displayStyle == .struct)
        #expect(children.count == 1)
        #expect(children.first?.label == "redacted")
        #expect(children.map { String(describing: $0.value) } == [name])

        [
            description,
            reflection,
            dumpOutput,
            children.map {
                "\($0.label ?? "")=\(String(reflecting: $0.value))"
            }.joined(),
        ].forEach(expectPrivacySafe)
    }

    private func expectPrivacySafe(_ rendered: String) {
        Self.sensitiveSentinels.forEach { sentinel in
            #expect(!rendered.contains(sentinel))
        }
        Self.sensitiveLabels.forEach { label in
            #expect(!rendered.contains(label))
        }
    }

    private struct ModelFixture {
        let name: String
        let value: Any
    }

    private static let sourceSentinel = "sentinel-source-4f91"
    private static let candidateHandleSentinel =
        "AA:BB:CC:DD:EE:FF/vendor-sentinel-5a27"
    private static let hardwareSentinel = "sentinel-hardware-2d73"
    private static let firmwareSentinel = "sentinel-firmware-8a15"
    private static let protocolSentinel = "sentinel-protocol-6c24"
    private static let wrapperSentinel = "sentinel-wrapper-0b82"
    private static let parserSentinel = "sentinel-parser-7e36"
    private static let calibrationSentinel = "sentinel-calibration-5d47"
    private static let chunkSentinel = "sentinel-chunk-3a59"
    private static let previousCursorSentinel = "sentinel-cursor-previous-1c68"
    private static let nextCursorSentinel = "sentinel-cursor-next-9b04"
    private static let acknowledgementSentinel = "sentinel-ack-2f76"
    private static let checkpointCursorSentinel =
        "sentinel-cursor-checkpoint-8d13"
    private static let sampleSequenceSentinel: UInt64 = 9_876_543_210
    private static let sampleTimeSentinel: Int64 = 1_977_777_777_777
    private static let sampleValueSentinel = 173.625
    private static let fingerprintSentinel =
        "abcdef0123456789abcdef0123456789"
        + "abcdef0123456789abcdef0123456789"
    private static let lostStartSentinel: Int64 = 1_977_777_770_000
    private static let lostEndSentinel: Int64 = 1_977_777_771_000
    private static let retainedStartSentinel: Int64 = 1_977_777_772_000
    private static let retainedEndSentinel: Int64 = 1_977_777_773_000

    private static let sensitiveSentinels = [
        candidateHandleSentinel,
        sourceSentinel,
        hardwareSentinel,
        firmwareSentinel,
        protocolSentinel,
        wrapperSentinel,
        parserSentinel,
        calibrationSentinel,
        chunkSentinel,
        previousCursorSentinel,
        nextCursorSentinel,
        acknowledgementSentinel,
        checkpointCursorSentinel,
        String(sampleSequenceSentinel),
        String(sampleTimeSentinel),
        String(sampleValueSentinel),
        fingerprintSentinel,
        String(lostStartSentinel),
        String(lostEndSentinel),
        String(retainedStartSentinel),
        String(retainedEndSentinel),
    ]

    private static let sensitiveLabels = [
        "handle",
        "sourceIdentity",
        "hardwareRevision",
        "firmwareVersion",
        "protocolVersion",
        "wrapperRevision",
        "stream",
        "sequence",
        "deviceTimeMilliseconds",
        "identity",
        "value",
        "unit",
        "quality",
        "lane",
        "parserRevision",
        "calibrationRevision",
        "samples",
        "startDeviceTimeMilliseconds",
        "endDeviceTimeMilliseconds",
        "chunkIdentity",
        "previousCursor",
        "nextCursor",
        "complete",
        "overflowed",
        "retainedRange",
        "firstLostRange",
        "acknowledgementToken",
        "batches",
        "acknowledgedCursor",
        "lastHistoryComplete",
        "durableSampleIdentities",
        "durableSampleFingerprints",
        "payloadFingerprint",
    ]
}
