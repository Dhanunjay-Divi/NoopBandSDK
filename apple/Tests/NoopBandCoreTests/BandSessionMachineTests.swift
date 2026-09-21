import Foundation
import Testing
@testable import NoopBandCore

@Suite("NOOP Band neutral session")
struct BandSessionMachineTests {
    @Test("All deterministic scenarios produce stable terminal results")
    func deterministicScenarios() async throws {
        for scenario in BandConformanceRunner.automatedScenarios {
            let first = try await BandConformanceRunner.run(scenario)
            let second = try await BandConformanceRunner.run(scenario)
            #expect(first == second)
            #expect(first.scenario == scenario)
        }
    }

    @Test("History requires a durable receipt before cursor advance")
    func durableBeforeAcknowledgement() async throws {
        let result = try await BandConformanceRunner.run(
            "history_requires_durable_receipt"
        )
        #expect(result.failure == BandFailureCategory.storage.rawValue)
        #expect(result.acknowledgedCursor == "cursor-2")
        #expect(result.events.firstIndex(of: "ack_rejected")!
            < result.events.firstIndex(of: "history_committed")!)
        #expect(result.events.firstIndex(of: "history_committed")!
            < result.events.firstIndex(of: "history_acknowledged")!)
    }

    @Test("Live delivery never advances the history cursor")
    func liveDoesNotAdvanceHistory() async throws {
        let result = try await BandConformanceRunner.run(
            "live_does_not_advance_history"
        )
        #expect(result.acknowledgedCursor == nil)
        #expect(result.acceptedSamples == 1)
    }

    @Test("Duplicate live identities are accepted once")
    func duplicateLiveIdentitiesAreDeduplicated() async throws {
        let result = try await BandConformanceRunner.run(
            "live_batch_deduplicated"
        )
        #expect(result.acceptedSamples == 1)
    }

    @Test("History cursor chains fail closed")
    func historyCursorChainsFailClosed() async throws {
        let result = try await BandConformanceRunner.run(
            "history_cursor_chain_rejected"
        )
        #expect(result.failure == BandFailureCategory.historyStalled.rawValue)
        #expect(result.acknowledgedCursor == "cursor-2")
    }

    @Test("Operation capabilities are enforced by the core")
    func operationCapabilitiesAreCoreOwned() async throws {
        let result = try await BandConformanceRunner.run(
            "operation_capability_fail_closed"
        )
        #expect(result.failure == BandFailureCategory.unsupported.rawValue)
    }

    @Test("Oversized metadata is rejected before acceptance")
    func oversizedMetadataIsRejected() async throws {
        let result = try await BandConformanceRunner.run(
            "oversized_metadata_rejected"
        )
        #expect(result.failure == BandFailureCategory.invalidInput.rawValue)
        #expect(result.acceptedSamples == 0)
    }

    @Test("Durable history checkpoints resume from the committed cursor")
    func durableHistoryCheckpointRestores() async throws {
        let result = try await BandConformanceRunner.run(
            "history_checkpoint_restored"
        )
        #expect(result.failure == nil)
        #expect(result.acknowledgedCursor == "cursor-3")
        #expect(result.acceptedSamples == 1)
    }

    @Test("Firmware capability failures retain their dedicated category")
    func firmwareEligibilityIsSpecific() async throws {
        let result = try await BandConformanceRunner.run(
            "firmware_eligibility_specific"
        )
        #expect(result.failure == BandFailureCategory.updateNotEligible.rawValue)
    }

    @Test("Unnegotiated streams fail closed")
    func unnegotiatedStreamsFailClosed() async throws {
        let result = try await BandConformanceRunner.run(
            "unnegotiated_stream_rejected"
        )
        #expect(result.failure == BandFailureCategory.unsupported.rawValue)
        #expect(result.acceptedSamples == 0)
    }

    @Test("Firmware updates cannot overlap live collection")
    func firmwareCannotOverlapLiveCollection() async throws {
        let result = try await BandConformanceRunner.run(
            "firmware_blocked_during_live"
        )
        #expect(result.failure == BandFailureCategory.busy.rawValue)
    }

    @Test("Incomplete and overflowed history requires durable state")
    func historyStateRequiresDurableReceipt() async throws {
        let result = try await BandConformanceRunner.run(
            "history_state_requires_durable_receipt"
        )
        #expect(result.failure == BandFailureCategory.historyStalled.rawValue)
        #expect(result.acknowledgedCursor == "cursor-3")
        #expect(result.events.contains("receipt_rejected"))
    }

    @Test("Sample sequences share Android's signed 64-bit domain")
    func sampleSequenceDomainIsSigned64Bit() throws {
        let accepted = BandSample(
            identity: BandSampleIdentity(
                stream: .heartRate,
                sequence: BandContractLimits.maximumSampleSequence,
                deviceTimeMilliseconds: 1
            ),
            value: 72,
            unit: .beatsPerMinute,
            quality: .accepted
        )
        try accepted.validate()

        let rejected = BandSample(
            identity: BandSampleIdentity(
                stream: .heartRate,
                sequence: BandContractLimits.maximumSampleSequence + 1,
                deviceTimeMilliseconds: 1
            ),
            value: 72,
            unit: .beatsPerMinute,
            quality: .accepted
        )
        #expect(throws: BandFailureCategory.invalidInput) {
            try rejected.validate()
        }
    }

    @Test("Capability callbacks are generation fenced")
    func capabilityCallbacksAreGenerationFenced() async throws {
        let result = try await BandConformanceRunner.run(
            "stale_capability_callback_rejected"
        )
        #expect(result.failure == BandFailureCategory.staleCallback.rawValue)
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Device time rejects negative samples and checkpoints")
    func deviceTimeDomainIsNonnegative() async throws {
        let result = try await BandConformanceRunner.run(
            "invalid_device_time_rejected"
        )
        #expect(result.failure == BandFailureCategory.invalidInput.rawValue)

        let checkpoint = BandHistoryCheckpoint(
            sourceIdentity: "source",
            acknowledgedCursor: nil,
            lastHistoryComplete: nil,
            durableSampleIdentities: [
                BandSampleIdentity(
                    stream: .heartRate,
                    sequence: 1,
                    deviceTimeMilliseconds: -1
                ),
            ]
        )
        #expect(throws: BandFailureCategory.invalidInput) {
            try checkpoint.validate()
        }
    }

    @Test("Each history operation requires its own durable receipt")
    func historyOperationDurabilityIsNotInherited() async throws {
        let result = try await BandConformanceRunner.run(
            "history_operation_requires_own_receipt"
        )
        #expect(result.failure == BandFailureCategory.storage.rawValue)
        #expect(result.events.last == "operation_cancelled")
    }

    @Test("Firmware lifecycle uses firmware diagnostics")
    func firmwareDiagnosticsAreSpecific() async throws {
        let result = try await BandConformanceRunner.run(
            "firmware_diagnostics_specific"
        )
        #expect(result.events.last == "firmware_diagnostics_specific")
    }

    @Test("Incomplete history chunks must advance their cursor")
    func incompleteHistoryRequiresCursorProgress() async throws {
        let result = try await BandConformanceRunner.run(
            "history_nonadvancing_cursor_rejected"
        )
        #expect(result.failure == BandFailureCategory.historyStalled.rawValue)
    }

    @Test("Bounded strings use UTF-8 byte length")
    func boundedStringsUseUTF8Bytes() async throws {
        let result = try await BandConformanceRunner.run(
            "utf8_length_cross_platform"
        )
        #expect(result.failure == BandFailureCategory.invalidInput.rawValue)
    }

    @Test("Sampling requires a negotiated sensor capability")
    func samplingRequiresSensorCapability() async throws {
        let result = try await BandConformanceRunner.run(
            "sampling_requires_sensor_capability"
        )
        #expect(result.failure == BandFailureCategory.unsupported.rawValue)
    }

    @Test("Recent durable identities remain bounded in memory")
    func durableIdentityCacheRemainsBounded() async throws {
        let result = try await BandConformanceRunner.run(
            "durable_identity_cache_bounded"
        )
        #expect(result.events.contains("identity_cache_bounded"))
        #expect(result.acceptedSamples == 2)
    }

    @Test("Active operations support cancellation and failure terminals")
    func operationsHaveExplicitTerminals() async throws {
        let result = try await BandConformanceRunner.run(
            "operation_terminal_paths"
        )
        #expect(result.events.contains("operation_cancelled"))
        #expect(result.events.contains("operation_failed"))
        #expect(result.events.contains("disconnect_recovery"))
        #expect(result.events.contains("stale_callback_rejected"))
        #expect(result.events.last == "reconnected")
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Diagnostics are bounded and structurally identifier-free")
    func diagnosticsAreBoundedAndRedacted() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 2)
        await recorder.record(
            BandDiagnosticEvent(kind: .live, outcome: .completed)
        )
        await recorder.record(
            BandDiagnosticEvent(kind: .history, outcome: .failed)
        )
        await recorder.record(
            BandDiagnosticEvent(kind: .command, outcome: .rejected)
        )
        let events = await recorder.snapshot()
        #expect(events.count == 2)
        #expect(events.first?.kind == .history)

        let encoded = try JSONEncoder().encode(events)
        let text = String(decoding: encoded, as: UTF8.self)
        #expect(!text.contains("virtual-source"))
        #expect(!text.contains("72"))
        #expect(!text.contains("ack-1"))
    }

    @Test("Unknown conformance scenarios fail closed")
    func unknownScenarioFailsClosed() async {
        await #expect(throws: BandFailureCategory.invalidInput) {
            try await BandConformanceRunner.run("not-a-scenario")
        }
    }
}
