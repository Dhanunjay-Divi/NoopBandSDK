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

    @Test("A checkpoint survives an intervening source connection")
    func restoredCheckpointSurvivesAnInterveningSource() async throws {
        let result = try await BandConformanceRunner.run(
            "history_checkpoint_survives_source_mismatch"
        )
        #expect(result.failure == nil)
        #expect(result.acknowledgedCursor == "cursor-2")
        #expect(result.acceptedSamples == 2)
    }

    @Test("Intentional disconnect returns a reusable idle session")
    func gracefulDisconnectReturnsAReusableIdleSession() async throws {
        let result = try await BandConformanceRunner.run(
            "graceful_disconnect_to_idle"
        )
        #expect(
            result.failure == BandFailureCategory.staleCallback.rawValue
        )
        #expect(result.finalState == BandSessionState.idle.rawValue)
        #expect(result.events.contains("session_reusable"))
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

    @Test("Step samples require integral counts")
    func stepSamplesRequireIntegralCounts() throws {
        let identity = BandSampleIdentity(
            stream: .steps,
            sequence: 1,
            deviceTimeMilliseconds: 1
        )
        for value in [0.0, 1.0, 1_000_000.0] {
            try BandSample(
                identity: identity,
                value: value,
                unit: .count,
                quality: .accepted
            ).validate()
        }

        for value in [0.5, 1.5, 999_999.5] {
            #expect(throws: BandFailureCategory.invalidInput) {
                try BandSample(
                    identity: identity,
                    value: value,
                    unit: .count,
                    quality: .accepted
                ).validate()
            }
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

    @Test("Scan callbacks are bound to the issuing session")
    func scanCallbacksAreSessionBound() async throws {
        let result = try await BandConformanceRunner.run(
            "scan_callback_session_bound"
        )
        #expect(
            result.events == [
                "scan_pair",
                "foreign_select_rejected",
                "foreign_cancel_rejected",
                "foreign_failure_rejected",
                "current_scan_preserved",
                "own_select_accepted",
                "current_session_ready",
            ]
        )
        #expect(result.failure == BandFailureCategory.staleCallback.rawValue)
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Scan callbacks are stale after candidate selection")
    func scanCallbacksAreConsumedAfterSelection() async throws {
        let result = try await BandConformanceRunner.run(
            "scan_callback_consumed_after_selection"
        )
        #expect(
            result.events == [
                "scan_started",
                "candidate_selected",
                "late_select_rejected",
                "late_cancel_rejected",
                "late_failure_rejected",
                "connection_preserved",
                "current_session_ready",
            ]
        )
        #expect(result.failure == BandFailureCategory.staleCallback.rawValue)
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Scan token is consumed before selection diagnostics suspend")
    func scanTokenIsConsumedBeforeSelectionSuspends() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let session = BandSessionMachine(diagnostics: recorder)
        let scanToken = try await session.beginScan()
        await recorder.requestNextRecordSuspensionForTesting()

        let selectionTask = Task {
            try await session.selectCandidate(
                VirtualBandFixtures.candidate,
                callbackGeneration: scanToken
            )
        }
        await recorder.waitForRecordSuspensionForTesting()

        let selecting = await session.snapshot()
        #expect(selecting.state == .candidateSelected)
        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await session.selectCandidate(
                VirtualBandFixtures.candidate,
                callbackGeneration: scanToken
            )
        }
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await session.cancelScan(callbackGeneration: scanToken)
        }
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await session.failScan(
                .timeout,
                callbackGeneration: scanToken
            )
        }

        await recorder.resumeSuspendedRecordForTesting()
        let connectionToken = try await selectionTask.value
        try await session.beginConnection(
            token: connectionToken,
            callbackGeneration: scanToken.generation
        )
        #expect(await session.snapshot().state == .connecting)
    }

    @Test("Scan token string rendering is redacted")
    func scanTokenStringRenderingIsRedacted() async throws {
        let token = try await BandSessionMachine().beginScan()
        expectRedacted(token, as: "BandScanToken")
    }

    @Test("Tokens, acceptances, and receipts are structurally redacted")
    func persistenceHandoffValuesAreRedacted() async throws {
        let (session, generation, connectionToken) =
            try await readySessionWithToken()
        let liveToken = try await session.beginLive()
        let liveAcceptance = try await session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: liveToken,
            callbackGeneration: generation
        )
        let liveReceipt = DurableLiveReceipt(
            acceptance: liveAcceptance,
            committedSamples: liveAcceptance.acceptedSamples.count,
            committed: true
        )
        try await session.acknowledgeLive(
            receipt: liveReceipt,
            callbackGeneration: generation
        )
        try await session.stopLive(token: liveToken)

        let operationToken = try await session.beginOperation(.history)
        let historyAcceptance = try await session.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            token: operationToken,
            callbackGeneration: generation
        )
        let historyReceipt = DurableHistoryReceipt(
            acceptance: historyAcceptance,
            historyStateCommitted: true,
            committedSamples: historyAcceptance.acceptedSamples.count,
            committed: true
        )
        let acceptedHistorySample = try #require(
            historyAcceptance.acceptedSamples.first
        )
        let (
            reconnectSession,
            reconnectGeneration,
            reconnectConnectionToken
        ) = try await readySessionWithToken()
        let reconnectToken = try await reconnectSession.interruptForReconnect(
            token: reconnectConnectionToken,
            callbackGeneration: reconnectGeneration
        )

        expectRedacted(connectionToken, as: "BandConnectionToken")
        expectRedacted(reconnectToken, as: "BandReconnectToken")
        expectRedacted(liveToken, as: "BandLiveToken")
        expectRedacted(operationToken, as: "BandOperationToken")
        expectRedacted(liveAcceptance, as: "LiveAcceptance")
        expectRedacted(
            acceptedHistorySample,
            as: "AcceptedHistorySample"
        )
        expectRedacted(liveReceipt, as: "DurableLiveReceipt")
        expectRedacted(historyAcceptance, as: "HistoryAcceptance")
        expectRedacted(historyReceipt, as: "DurableHistoryReceipt")
    }

    @Test("Receipts and operation tokens are bound to one session")
    func receiptsAndTokensAreSessionBound() async throws {
        let result = try await BandConformanceRunner.run(
            "cross_session_credentials_rejected"
        )
        #expect(result.failure == BandFailureCategory.staleCallback.rawValue)
        #expect(result.events.contains("foreign_live_receipt_rejected"))
        #expect(result.events.contains("foreign_history_receipt_rejected"))
        #expect(result.events.contains("foreign_operation_token_rejected"))
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Reconnect callbacks are bound to issued session credentials")
    func reconnectCallbacksAreSessionBound() async throws {
        let result = try await BandConformanceRunner.run(
            "reconnect_callback_session_bound"
        )
        #expect(result.events == [
            "ready_pair",
            "foreign_interrupt_rejected",
            "current_live_preserved",
            "live_interruption_ordered",
            "foreign_resume_rejected",
            "current_recovery_preserved",
            "own_resume_accepted",
        ])
        #expect(result.failure == BandFailureCategory.staleCallback.rawValue)
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Live callbacks are bound to the issuing session")
    func liveCallbacksAreSessionBound() async throws {
        let (first, firstGeneration) = try await readySession()
        let recorder = BandDiagnosticsRecorder()
        let (second, secondGeneration) = try await readySession(
            diagnostics: recorder
        )
        let firstToken = try await first.beginLive()
        let secondToken = try await second.beginLive()

        #expect(firstGeneration == secondGeneration)
        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await second.stageLiveBatch(
                VirtualBandFixtures.liveBatch,
                token: firstToken,
                callbackGeneration: secondGeneration
            )
        }
        #expect(await second.snapshot().state == .liveCollecting)
        #expect(
            await recorder.snapshot().last == BandDiagnosticEvent(
                kind: .live,
                outcome: .stale,
                failureCategory: .staleCallback
            )
        )

        let accepted = try await second.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: secondToken,
            callbackGeneration: secondGeneration
        )
        #expect(accepted.acceptedSamples.count == 1)
    }

    @Test("Older receipts and operation tokens cannot replay in one session")
    func credentialIssuanceCannotReplay() async throws {
        let result = try await BandConformanceRunner.run(
            "same_session_replay_rejected"
        )
        #expect(result.failure == BandFailureCategory.storage.rawValue)
        #expect(result.events.contains("stale_live_receipt_rejected"))
        #expect(result.events.contains("stale_operation_token_rejected"))
        #expect(result.events.contains("stale_history_receipt_rejected"))
        #expect(result.acknowledgedCursor == "cursor-3")
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Scan and reconnect terminal callbacks are generation fenced")
    func terminalCallbacksAreGenerationFenced() async throws {
        let result = try await BandConformanceRunner.run(
            "stale_terminal_callbacks_rejected"
        )
        #expect(result.failure == BandFailureCategory.staleCallback.rawValue)
        #expect(result.events.contains("stale_scan_cancel_rejected"))
        #expect(result.events.contains("stale_scan_failure_rejected"))
        #expect(result.events.contains("stale_reconnect_interrupt_rejected"))
        #expect(result.events.contains("stale_reconnect_completion_rejected"))
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

    @Test("Unrecoverable firmware failure is terminal")
    func unrecoverableFirmwareFailureIsTerminal() async throws {
        let result = try await BandConformanceRunner.run(
            "firmware_terminal_failure"
        )
        #expect(result.finalState == BandSessionState.firmwareFailure.rawValue)
        #expect(result.failure == BandFailureCategory.invalidState.rawValue)
        #expect(result.events.last == "replacement_scan_started")
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
        #expect(result.events.contains("security_failure_terminal"))
        #expect(result.events.contains("security_failure_stale_token_rejected"))
        #expect(result.events.contains("security_failure_restart_rejected"))
        #expect(result.events.last == "replacement_session_ready")
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Security failure requires a replacement session object")
    func securityFailureRequiresReplacementSession() async throws {
        let session = BandSessionMachine()
        let scanToken = try await session.beginScan()
        let generation = scanToken.generation
        let connectionToken = try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: scanToken
        )
        try await session.beginConnection(
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.beginAuthentication(
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.completeConnection(
            VirtualBandFixtures.identity,
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.acceptCapabilities(
            VirtualBandFixtures.capabilities,
            token: connectionToken,
            callbackGeneration: generation
        )
        let token = try await session.beginOperation(.battery)
        try await session.failOperation(token, category: .securityFailure)
        let terminal = await session.snapshot()

        #expect(terminal.state == .securityFailure)
        await #expect(throws: BandFailureCategory.invalidState) {
            _ = try await session.beginScan()
        }
        let unchanged = await session.snapshot()
        #expect(unchanged.state == .securityFailure)
        #expect(unchanged.generation == terminal.generation)

        let replacement = BandSessionMachine()
        let replacementScanToken = try await replacement.beginScan()
        let replacementGeneration = replacementScanToken.generation
        let replacementSnapshot = await replacement.snapshot()
        #expect(replacementGeneration == 1)
        #expect(replacementSnapshot.state == .scanning)
        #expect(replacementSnapshot.generation == replacementGeneration)
    }

    @Test("Swift batches retain value-semantic sample snapshots")
    func batchCollectionsHaveValueSemantics() throws {
        var samples = VirtualBandFixtures.liveBatch.samples
        let batch = BandSampleBatch(
            sourceIdentity: VirtualBandFixtures.identity.sourceIdentity,
            lane: .live,
            parserRevision: "parser-v1",
            calibrationRevision: "calibration-v1",
            samples: samples
        )
        samples.removeAll()
        #expect(batch.samples.count == 1)

        var batches = [
            BandSampleBatch(
                sourceIdentity: VirtualBandFixtures.identity.sourceIdentity,
                lane: .history,
                parserRevision: "parser-v1",
                calibrationRevision: "calibration-v1",
                samples: batch.samples
            ),
        ]
        let chunk = BandHistoryChunk(
            chunkIdentity: "value-semantics",
            previousCursor: nil,
            nextCursor: nil,
            complete: true,
            overflowed: false,
            retainedRange: nil,
            firstLostRange: nil,
            acknowledgementToken: "value-semantics",
            batches: batches
        )
        batches.removeAll()
        #expect(chunk.batches.count == 1)
        #expect(chunk.batches.first?.samples.count == 1)
        try chunk.validate()
    }

    @Test("History acceptance returns only rows storage may persist")
    func historyAcceptanceReturnsExactPersistableRows() async throws {
        let (session, generation) = try await readySession()
        let store = VirtualBandStore()
        let duplicate = try #require(
            VirtualBandFixtures.liveBatch.samples.first
        )
        let fresh = BandSample(
            identity: BandSampleIdentity(
                stream: duplicate.identity.stream,
                sequence: duplicate.identity.sequence + 100,
                deviceTimeMilliseconds:
                    duplicate.identity.deviceTimeMilliseconds + 1_000
            ),
            value: duplicate.value + 1,
            unit: duplicate.unit,
            quality: duplicate.quality
        )
        let secondFresh = BandSample(
            identity: BandSampleIdentity(
                stream: duplicate.identity.stream,
                sequence: duplicate.identity.sequence + 101,
                deviceTimeMilliseconds:
                    duplicate.identity.deviceTimeMilliseconds + 2_000
            ),
            value: duplicate.value + 2,
            unit: duplicate.unit,
            quality: duplicate.quality
        )
        let liveToken = try await session.beginLive()
        let liveAcceptance = try await session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: liveToken,
            callbackGeneration: generation
        )
        try await session.acknowledgeLive(
            receipt: await store.commit(acceptance: liveAcceptance),
            callbackGeneration: generation
        )
        try await session.stopLive(token: liveToken)

        let token = try await session.beginOperation(.history)
        let sourceBatch = try #require(
            VirtualBandFixtures.historyChunk.batches.first
        )
        let chunk = BandHistoryChunk(
            chunkIdentity: VirtualBandFixtures.historyChunk.chunkIdentity,
            previousCursor: VirtualBandFixtures.historyChunk.previousCursor,
            nextCursor: VirtualBandFixtures.historyChunk.nextCursor,
            complete: VirtualBandFixtures.historyChunk.complete,
            overflowed: VirtualBandFixtures.historyChunk.overflowed,
            retainedRange: BandHistoryRange(
                startDeviceTimeMilliseconds:
                    duplicate.identity.deviceTimeMilliseconds,
                endDeviceTimeMilliseconds:
                    secondFresh.identity.deviceTimeMilliseconds
            ),
            firstLostRange: VirtualBandFixtures.historyChunk.firstLostRange,
            acknowledgementToken:
                VirtualBandFixtures.historyChunk.acknowledgementToken,
            batches: [
                BandSampleBatch(
                    sourceIdentity: sourceBatch.sourceIdentity,
                    lane: sourceBatch.lane,
                    parserRevision: sourceBatch.parserRevision,
                    calibrationRevision: sourceBatch.calibrationRevision,
                    samples: [duplicate, fresh]
                ),
                BandSampleBatch(
                    sourceIdentity: sourceBatch.sourceIdentity,
                    lane: sourceBatch.lane,
                    parserRevision: sourceBatch.parserRevision,
                    calibrationRevision: sourceBatch.calibrationRevision,
                    samples: [fresh, secondFresh]
                ),
            ]
        )
        let acceptance = try await session.stageHistoryChunk(
            chunk,
            token: token,
            callbackGeneration: generation
        )

        #expect(acceptance.acceptedSamples.map(\.sample) == [fresh, secondFresh])
        #expect(acceptance.acceptedSamples[0].sourceIdentity
            == sourceBatch.sourceIdentity)
        #expect(acceptance.acceptedSamples[0].lane == sourceBatch.lane)
        #expect(acceptance.acceptedSamples[0].parserRevision
            == sourceBatch.parserRevision)
        #expect(acceptance.acceptedSamples[0].calibrationRevision
            == sourceBatch.calibrationRevision)
        #expect(acceptance.acceptedSamples[1].parserRevision
            == sourceBatch.parserRevision)
        #expect(acceptance.acceptedSamples[1].calibrationRevision
            == sourceBatch.calibrationRevision)
        #expect(acceptance.acceptedSamples.allSatisfy {
            $0.capabilityReportRevision
                == VirtualBandFixtures.capabilities.reportRevision
        })
        #expect(acceptance.duplicateSamples == 2)
        #expect(await store.commit(acceptance: acceptance).committedSamples == 2)
    }

    @Test("Connection completion callbacks are generation fenced")
    func connectionCallbacksAreGenerationFenced() async throws {
        let result = try await BandConformanceRunner.run(
            "connection_callbacks_generation_fenced"
        )
        #expect(result.failure == BandFailureCategory.staleCallback.rawValue)
        #expect(result.events.contains("connection_failed"))
        #expect(result.events.contains("stale_cancel_rejected"))
        #expect(result.events.contains("stale_failure_rejected"))
        #expect(result.events.contains("stale_terminals_preserved_state"))
        #expect(result.events.contains("stale_connection_rejected"))
        #expect(result.events.contains("stale_phases_preserved"))
        #expect(result.events.contains("connection_diagnostics_bounded"))
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Connection completion revalidates after close")
    func connectionCompletionRevalidatesAfterClose() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let session = BandSessionMachine(diagnostics: recorder)
        let scanToken = try await session.beginScan()
        let generation = scanToken.generation
        let connectionToken = try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: scanToken
        )
        try await session.beginConnection(
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.beginAuthentication(
            token: connectionToken,
            callbackGeneration: generation
        )
        await recorder.requestNextRecordSuspensionForTesting()

        let completion = Task {
            try await session.completeConnection(
                VirtualBandFixtures.identity,
                token: connectionToken,
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()
        try await session.close()
        await recorder.resumeSuspendedRecordForTesting()

        await #expect(throws: BandFailureCategory.staleCallback) {
            try await completion.value
        }
        #expect(await session.snapshot().state == .closed)
    }

    @Test("Connection completion revalidates after capability cancellation")
    func connectionCompletionRevalidatesAfterCapabilityCancellation()
        async throws
    {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let session = BandSessionMachine(diagnostics: recorder)
        let scanToken = try await session.beginScan()
        let generation = scanToken.generation
        let connectionToken = try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: scanToken
        )
        try await session.beginConnection(
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.beginAuthentication(
            token: connectionToken,
            callbackGeneration: generation
        )
        await recorder.requestNextRecordSuspensionForTesting()

        let completion = Task {
            try await session.completeConnection(
                VirtualBandFixtures.identity,
                token: connectionToken,
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()
        try await session.cancelCapabilities(
            token: connectionToken,
            callbackGeneration: generation
        )
        await recorder.resumeSuspendedRecordForTesting()

        await #expect(throws: BandFailureCategory.staleCallback) {
            try await completion.value
        }
        #expect(await session.snapshot().state == .idle)
    }

    @Test("Connection completion permits valid capability progress")
    func connectionCompletionPermitsValidCapabilityProgress() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let session = BandSessionMachine(diagnostics: recorder)
        let scanToken = try await session.beginScan()
        let generation = scanToken.generation
        let connectionToken = try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: scanToken
        )
        try await session.beginConnection(
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.beginAuthentication(
            token: connectionToken,
            callbackGeneration: generation
        )
        await recorder.requestNextRecordSuspensionForTesting()

        let completion = Task {
            try await session.completeConnection(
                VirtualBandFixtures.identity,
                token: connectionToken,
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()
        try await session.acceptCapabilities(
            VirtualBandFixtures.capabilities,
            token: connectionToken,
            callbackGeneration: generation
        )
        await recorder.resumeSuspendedRecordForTesting()

        try await completion.value
        #expect(await session.snapshot().state == .ready)
        #expect(
            !(await recorder.snapshot()).contains {
                $0.kind == .authentication
                    && $0.outcome == .stale
                    && $0.failureCategory == .staleCallback
            }
        )
    }

    @Test("Live operations follow negotiated concurrency")
    func liveOperationsFollowNegotiatedConcurrency() async throws {
        let base = VirtualBandFixtures.capabilities
        let report = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: base.reportRevision,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive: [.battery],
            streamSemantics: base.streamSemantics
        )
        let recorder = BandDiagnosticsRecorder()
        let (session, _) = try await readySession(
            capabilities: report,
            diagnostics: recorder
        )
        let liveToken = try await session.beginLive()

        let battery = try await session.beginOperation(.battery)
        try await session.cancelOperation(battery)
        await #expect(throws: BandFailureCategory.busy) {
            _ = try await session.beginOperation(.haptic)
        }
        #expect(
            await recorder.snapshot().last
                == BandDiagnosticEvent(
                    kind: .command,
                    outcome: .rejected,
                    failureCategory: .busy,
                    operationClass: .haptic
                )
        )
        try await session.stopLive(token: liveToken)
    }

    @Test("Equivalent capability report order is idempotent")
    func equivalentCapabilityReportOrderIsIdempotent() async throws {
        let base = VirtualBandFixtures.capabilities
        #expect(base.streamSemantics.count > 1)
        let reordered = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: base.reportRevision,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive: base.operationsAllowedDuringLive,
            streamSemantics: Array(base.streamSemantics.reversed())
        )
        #expect(base == reordered)
        let original = try #require(base.streamSemantics.first)
        let changedSemantic = BandStreamSemantics(
            lane: original.lane,
            stream: original.stream,
            unit: original.unit,
            cadence: original.cadence,
            nominalIntervalMilliseconds:
                original.nominalIntervalMilliseconds,
            quality: original.quality,
            timestamp: original.timestamp,
            parserRevision: "parser-v2",
            calibrationRevision: original.calibrationRevision
        )
        let changed = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: base.reportRevision,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive: base.operationsAllowedDuringLive,
            streamSemantics: [changedSemantic]
                + Array(base.streamSemantics.dropFirst())
        )
        #expect(base != changed)
        let composed = "\u{00E9}"
        let decomposed = "e\u{0301}"
        let composedRevision = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: composed,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive: base.operationsAllowedDuringLive,
            streamSemantics: base.streamSemantics
        )
        let decomposedRevision = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: decomposed,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive: base.operationsAllowedDuringLive,
            streamSemantics: base.streamSemantics
        )
        try composedRevision.validate()
        try decomposedRevision.validate()
        #expect(composedRevision != decomposedRevision)

        let composedSemantic = BandStreamSemantics(
            lane: original.lane,
            stream: original.stream,
            unit: original.unit,
            cadence: original.cadence,
            nominalIntervalMilliseconds:
                original.nominalIntervalMilliseconds,
            quality: original.quality,
            timestamp: original.timestamp,
            parserRevision: composed,
            calibrationRevision: original.calibrationRevision
        )
        let decomposedSemantic = BandStreamSemantics(
            lane: original.lane,
            stream: original.stream,
            unit: original.unit,
            cadence: original.cadence,
            nominalIntervalMilliseconds:
                original.nominalIntervalMilliseconds,
            quality: original.quality,
            timestamp: original.timestamp,
            parserRevision: decomposed,
            calibrationRevision: original.calibrationRevision
        )
        let composedSemanticReport = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: base.reportRevision,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive: base.operationsAllowedDuringLive,
            streamSemantics: [composedSemantic]
                + Array(base.streamSemantics.dropFirst())
        )
        let decomposedSemanticReport = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: base.reportRevision,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive: base.operationsAllowedDuringLive,
            streamSemantics: [decomposedSemantic]
                + Array(base.streamSemantics.dropFirst())
        )
        try composedSemanticReport.validate()
        try decomposedSemanticReport.validate()
        #expect(composedSemanticReport != decomposedSemanticReport)

        let second = try #require(base.streamSemantics.dropFirst().first)
        let duplicateFirst = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: base.reportRevision,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive: base.operationsAllowedDuringLive,
            streamSemantics: [original, original, second]
        )
        let duplicateSecond = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: base.reportRevision,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive: base.operationsAllowedDuringLive,
            streamSemantics: [original, second, second]
        )
        #expect(duplicateFirst != duplicateSecond)

        let recorder = BandDiagnosticsRecorder()
        let (session, generation, connectionToken) =
            try await readySessionWithToken(diagnostics: recorder)
        try await session.acceptCapabilities(
            reordered,
            token: connectionToken,
            callbackGeneration: generation
        )

        #expect(await session.snapshot().state == .ready)
        #expect(
            await recorder.snapshot().last
                == BandDiagnosticEvent(
                    kind: .capability,
                    outcome: .stale
                )
        )
    }

    @Test("Capability identity revisions use exact UTF-8")
    func capabilityIdentityRevisionsUseExactUTF8() async throws {
        let composed = "\u{00E9}"
        let decomposed = "e\u{0301}"
        let baseIdentity = VirtualBandFixtures.identity
        let identity = BandIdentity(
            sourceIdentity: baseIdentity.sourceIdentity,
            hardwareRevision: composed,
            firmwareVersion: composed,
            protocolVersion: baseIdentity.protocolVersion,
            wrapperRevision: baseIdentity.wrapperRevision
        )
        let canonicallyEquivalentIdentity = BandIdentity(
            sourceIdentity: baseIdentity.sourceIdentity,
            hardwareRevision: decomposed,
            firmwareVersion: decomposed,
            protocolVersion: baseIdentity.protocolVersion,
            wrapperRevision: baseIdentity.wrapperRevision
        )
        #expect(identity != canonicallyEquivalentIdentity)
        let base = VirtualBandFixtures.capabilities
        let mismatches = [
            (hardware: decomposed, firmware: composed),
            (hardware: composed, firmware: decomposed),
        ]

        for mismatch in mismatches {
            let session = BandSessionMachine()
            let scanToken = try await session.beginScan()
            let generation = scanToken.generation
            let connectionToken = try await session.selectCandidate(
                VirtualBandFixtures.candidate,
                callbackGeneration: scanToken
            )
            try await session.beginConnection(
                token: connectionToken,
                callbackGeneration: generation
            )
            try await session.beginAuthentication(
                token: connectionToken,
                callbackGeneration: generation
            )
            try await session.completeConnection(
                identity,
                token: connectionToken,
                callbackGeneration: generation
            )
            let report = BandCapabilityReport(
                schemaVersion: base.schemaVersion,
                reportRevision: base.reportRevision,
                protocolVersion: base.protocolVersion,
                hardwareRevision: mismatch.hardware,
                firmwareVersion: mismatch.firmware,
                historyDays: base.historyDays,
                capabilities: base.capabilities,
                liveStreams: base.liveStreams,
                historyStreams: base.historyStreams,
                operationsAllowedDuringLive:
                    base.operationsAllowedDuringLive,
                streamSemantics: base.streamSemantics
            )

            await #expect(throws: BandFailureCategory.incompatible) {
                try await session.acceptCapabilities(
                    report,
                    token: connectionToken,
                    callbackGeneration: generation
                )
            }
            #expect(await session.snapshot().state == .incompatible)
        }
    }

    @Test("Oversized late capability reports are bounded before equality")
    func oversizedLateCapabilityReportIsBounded() async throws {
        let base = VirtualBandFixtures.capabilities
        let semantic = try #require(base.streamSemantics.first)
        let oversized = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: base.reportRevision,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive:
                base.operationsAllowedDuringLive,
            streamSemantics: Array(
                repeating: semantic,
                count: BandCapabilityReport.maximumStreamSemantics + 1
            )
        )
        #expect(base != oversized)
        let oversizedCopy = oversized
        #expect(oversized == oversizedCopy)
        let original = try #require(base.streamSemantics.first)
        let longSemantic = BandStreamSemantics(
            lane: original.lane,
            stream: original.stream,
            unit: original.unit,
            cadence: original.cadence,
            nominalIntervalMilliseconds:
                original.nominalIntervalMilliseconds,
            quality: original.quality,
            timestamp: original.timestamp,
            parserRevision: String(repeating: "x", count: 1_000_000),
            calibrationRevision: original.calibrationRevision
        )
        let longRevision = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: base.reportRevision,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive:
                base.operationsAllowedDuringLive,
            streamSemantics: [longSemantic]
                + Array(base.streamSemantics.dropFirst())
        )

        let recorder = BandDiagnosticsRecorder()
        let (session, generation, connectionToken) =
            try await readySessionWithToken(diagnostics: recorder)
        let before = await session.snapshot()

        for invalid in [oversized, longRevision] {
            await #expect(throws: BandFailureCategory.invalidInput) {
                try await session.acceptCapabilities(
                    invalid,
                    token: connectionToken,
                    callbackGeneration: generation
                )
            }
            #expect(await session.snapshot() == before)
            #expect(
                await recorder.snapshot().last
                    == BandDiagnosticEvent(
                        kind: .capability,
                        outcome: .rejected,
                        failureCategory: .invalidInput
                    )
            )
        }
    }

    @Test("Public contract string equality uses exact UTF-8")
    func publicContractStringEqualityUsesExactUTF8() throws {
        let composed = "\u{00E9}"
        let decomposed = "e\u{0301}"
        let source = VirtualBandFixtures.historyChunk
        let sourceBatch = try #require(source.batches.first)

        func batch(
            sourceIdentity: String = composed,
            parserRevision: String = composed,
            calibrationRevision: String = composed
        ) -> BandSampleBatch {
            BandSampleBatch(
                sourceIdentity: sourceIdentity,
                lane: sourceBatch.lane,
                parserRevision: parserRevision,
                calibrationRevision: calibrationRevision,
                samples: sourceBatch.samples
            )
        }
        let exactBatch = batch()
        #expect(exactBatch != batch(sourceIdentity: decomposed))
        #expect(exactBatch != batch(parserRevision: decomposed))
        #expect(exactBatch != batch(calibrationRevision: decomposed))

        func chunk(
            chunkIdentity: String = composed,
            previousCursor: String? = composed,
            nextCursor: String? = composed,
            acknowledgementToken: String = composed
        ) -> BandHistoryChunk {
            BandHistoryChunk(
                chunkIdentity: chunkIdentity,
                previousCursor: previousCursor,
                nextCursor: nextCursor,
                complete: source.complete,
                overflowed: source.overflowed,
                retainedRange: source.retainedRange,
                firstLostRange: source.firstLostRange,
                acknowledgementToken: acknowledgementToken,
                batches: [exactBatch]
            )
        }
        let exactChunk = chunk()
        #expect(exactChunk != chunk(chunkIdentity: decomposed))
        #expect(exactChunk != chunk(previousCursor: decomposed))
        #expect(exactChunk != chunk(nextCursor: decomposed))
        #expect(exactChunk != chunk(acknowledgementToken: decomposed))

        let sampleIdentities = Set(sourceBatch.samples.map(\.identity))
        let exactCheckpoint = BandHistoryCheckpoint(
            sourceIdentity: composed,
            acknowledgedCursor: composed,
            lastHistoryComplete: true,
            durableSampleIdentities: sampleIdentities
        )
        #expect(
            exactCheckpoint
                != BandHistoryCheckpoint(
                    sourceIdentity: decomposed,
                    acknowledgedCursor: composed,
                    lastHistoryComplete: true,
                    durableSampleIdentities: sampleIdentities
                )
        )
        #expect(
            exactCheckpoint
                != BandHistoryCheckpoint(
                    sourceIdentity: composed,
                    acknowledgedCursor: decomposed,
                    lastHistoryComplete: true,
                    durableSampleIdentities: sampleIdentities
                )
        )

        let exactSnapshot = BandSessionSnapshot(
            state: .ready,
            generation: 1,
            activeOperation: nil,
            liveActive: false,
            acknowledgedHistoryCursor: composed,
            durableSampleCount: 2
        )
        #expect(
            exactSnapshot
                != BandSessionSnapshot(
                    state: .ready,
                    generation: 1,
                    activeOperation: nil,
                    liveActive: false,
                    acknowledgedHistoryCursor: decomposed,
                    durableSampleCount: 2
                )
        )
    }

    @Test("Source identity and checkpoint restore use exact UTF-8")
    func sourceIdentityAndCheckpointRestoreUseExactUTF8() async throws {
        let composed = "\u{00E9}"
        let decomposed = "e\u{0301}"
        let baseIdentity = VirtualBandFixtures.identity
        let composedIdentity = BandIdentity(
            sourceIdentity: composed,
            hardwareRevision: baseIdentity.hardwareRevision,
            firmwareVersion: baseIdentity.firmwareVersion,
            protocolVersion: baseIdentity.protocolVersion,
            wrapperRevision: baseIdentity.wrapperRevision
        )
        let (session, generation, _) = try await readySessionWithToken(
            identity: composedIdentity
        )
        let liveToken = try await session.beginLive()
        let sourceBatch = VirtualBandFixtures.liveBatch
        let mismatchedBatch = BandSampleBatch(
            sourceIdentity: decomposed,
            lane: sourceBatch.lane,
            parserRevision: sourceBatch.parserRevision,
            calibrationRevision: sourceBatch.calibrationRevision,
            samples: sourceBatch.samples
        )
        await #expect(throws: BandFailureCategory.invalidInput) {
            _ = try await session.stageLiveBatch(
                mismatchedBatch,
                token: liveToken,
                callbackGeneration: generation
            )
        }
        try await session.stopLive(token: liveToken)

        let checkpoint = BandHistoryCheckpoint(
            sourceIdentity: composed,
            acknowledgedCursor: "cursor-2",
            lastHistoryComplete: true,
            durableSampleIdentities: Set(
                VirtualBandFixtures.historyChunk.batches
                    .flatMap(\.samples)
                    .map(\.identity)
            )
        )
        let restored = BandSessionMachine(historyCheckpoint: checkpoint)
        let scanToken = try await restored.beginScan()
        let restoredGeneration = scanToken.generation
        let connectionToken = try await restored.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: scanToken
        )
        try await restored.beginConnection(
            token: connectionToken,
            callbackGeneration: restoredGeneration
        )
        try await restored.beginAuthentication(
            token: connectionToken,
            callbackGeneration: restoredGeneration
        )
        let decomposedIdentity = BandIdentity(
            sourceIdentity: decomposed,
            hardwareRevision: baseIdentity.hardwareRevision,
            firmwareVersion: baseIdentity.firmwareVersion,
            protocolVersion: baseIdentity.protocolVersion,
            wrapperRevision: baseIdentity.wrapperRevision
        )
        try await restored.completeConnection(
            decomposedIdentity,
            token: connectionToken,
            callbackGeneration: restoredGeneration
        )
        let snapshot = await restored.snapshot()
        #expect(snapshot.acknowledgedHistoryCursor == nil)
        #expect(snapshot.durableSampleCount == 0)
    }

    @Test("History cursor progression uses exact UTF-8")
    func historyCursorProgressionUsesExactUTF8() async throws {
        let composed = "\u{00E9}"
        let decomposed = "e\u{0301}"
        let base = VirtualBandFixtures.historyChunk
        let (session, generation) = try await readySession()
        let token = try await session.beginOperation(.history)
        let first = BandHistoryChunk(
            chunkIdentity: "chunk-first",
            previousCursor: nil,
            nextCursor: composed,
            complete: false,
            overflowed: base.overflowed,
            retainedRange: base.retainedRange,
            firstLostRange: base.firstLostRange,
            acknowledgementToken: "ack-first",
            batches: base.batches
        )
        let firstAcceptance = try await session.stageHistoryChunk(
            first,
            token: token,
            callbackGeneration: generation
        )
        try await session.acknowledgeHistory(
            receipt: DurableHistoryReceipt(
                acceptance: firstAcceptance,
                historyStateCommitted: true,
                committedSamples: firstAcceptance.acceptedSamples.count,
                committed: true
            ),
            token: token,
            callbackGeneration: generation
        )

        let mismatched = BandHistoryChunk(
            chunkIdentity: "chunk-second",
            previousCursor: decomposed,
            nextCursor: "cursor-3",
            complete: true,
            overflowed: base.overflowed,
            retainedRange: base.retainedRange,
            firstLostRange: base.firstLostRange,
            acknowledgementToken: "ack-second",
            batches: base.batches
        )
        await #expect(throws: BandFailureCategory.historyStalled) {
            _ = try await session.stageHistoryChunk(
                mismatched,
                token: token,
                callbackGeneration: generation
            )
        }
        #expect(
            optionalStringsHaveIdenticalUTF8(
                await session.snapshot().acknowledgedHistoryCursor,
                composed
            )
        )
    }

    @Test("History receipt identities use exact UTF-8")
    func historyReceiptIdentitiesUseExactUTF8() async throws {
        let composed = "\u{00E9}"
        let decomposed = "e\u{0301}"
        let base = VirtualBandFixtures.historyChunk
        let (session, generation) = try await readySession()
        let token = try await session.beginOperation(.history)
        let chunk = BandHistoryChunk(
            chunkIdentity: composed,
            previousCursor: nil,
            nextCursor: composed,
            complete: true,
            overflowed: base.overflowed,
            retainedRange: base.retainedRange,
            firstLostRange: base.firstLostRange,
            acknowledgementToken: composed,
            batches: base.batches
        )
        let acceptance = try await session.stageHistoryChunk(
            chunk,
            token: token,
            callbackGeneration: generation
        )

        func receipt(
            chunkIdentity: String = composed,
            acknowledgementToken: String = composed,
            nextCursor: String? = composed
        ) -> DurableHistoryReceipt {
            let alteredAcceptance = HistoryAcceptance(
                chunkIdentity: chunkIdentity,
                acknowledgementToken: acknowledgementToken,
                nextCursor: nextCursor,
                complete: acceptance.complete,
                overflowed: acceptance.overflowed,
                retainedRange: acceptance.retainedRange,
                firstLostRange: acceptance.firstLostRange,
                acceptedSamples: acceptance.acceptedSamples,
                duplicateSamples: acceptance.duplicateSamples,
                sessionNonce: acceptance.sessionNonce,
                receiptSequence: acceptance.receiptSequence
            )
            return DurableHistoryReceipt(
                acceptance: alteredAcceptance,
                historyStateCommitted: true,
                committedSamples: acceptance.acceptedSamples.count,
                committed: true
            )
        }

        for mismatched in [
            receipt(chunkIdentity: decomposed),
            receipt(acknowledgementToken: decomposed),
            receipt(nextCursor: decomposed),
        ] {
            await #expect(throws: BandFailureCategory.storage) {
                try await session.acknowledgeHistory(
                    receipt: mismatched,
                    token: token,
                    callbackGeneration: generation
                )
            }
        }
        try await session.acknowledgeHistory(
            receipt: receipt(),
            token: token,
            callbackGeneration: generation
        )
    }

    @Test("Negotiated revisions use exact UTF-8")
    func negotiatedRevisionsUseExactUTF8() async throws {
        let composed = "\u{00E9}"
        let decomposed = "e\u{0301}"
        let base = VirtualBandFixtures.capabilities
        let semantics = base.streamSemantics.map {
            BandStreamSemantics(
                lane: $0.lane,
                stream: $0.stream,
                unit: $0.unit,
                cadence: $0.cadence,
                nominalIntervalMilliseconds:
                    $0.nominalIntervalMilliseconds,
                quality: $0.quality,
                timestamp: $0.timestamp,
                parserRevision: composed,
                calibrationRevision: composed
            )
        }
        let report = BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            reportRevision: base.reportRevision,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities,
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams,
            operationsAllowedDuringLive: base.operationsAllowedDuringLive,
            streamSemantics: semantics
        )
        let (session, generation) = try await readySession(
            capabilities: report
        )
        let liveToken = try await session.beginLive()
        let source = VirtualBandFixtures.liveBatch
        let mismatches = [
            BandSampleBatch(
                sourceIdentity: source.sourceIdentity,
                lane: source.lane,
                parserRevision: decomposed,
                calibrationRevision: composed,
                samples: source.samples
            ),
            BandSampleBatch(
                sourceIdentity: source.sourceIdentity,
                lane: source.lane,
                parserRevision: composed,
                calibrationRevision: decomposed,
                samples: source.samples
            ),
        ]

        for mismatch in mismatches {
            await #expect(throws: BandFailureCategory.unsupported) {
                _ = try await session.stageLiveBatch(
                    mismatch,
                    token: liveToken,
                    callbackGeneration: generation
                )
            }
            #expect(await session.snapshot().state == .liveCollecting)
        }
        try await session.stopLive(token: liveToken)
    }

    @Test("Negotiated stream semantics reject revision drift before staging")
    func negotiatedStreamSemanticsRejectRevisionDriftBeforeStaging()
        async throws
    {
        let (session, generation) = try await readySession()
        let liveToken = try await session.beginLive()
        let mismatches = [
            BandSampleBatch(
                sourceIdentity:
                    VirtualBandFixtures.liveBatch.sourceIdentity,
                lane: .live,
                parserRevision: "parser-v2",
                calibrationRevision:
                    VirtualBandFixtures.liveBatch.calibrationRevision,
                samples: VirtualBandFixtures.liveBatch.samples
            ),
            BandSampleBatch(
                sourceIdentity:
                    VirtualBandFixtures.liveBatch.sourceIdentity,
                lane: .live,
                parserRevision:
                    VirtualBandFixtures.liveBatch.parserRevision,
                calibrationRevision: "calibration-v2",
                samples: VirtualBandFixtures.liveBatch.samples
            ),
        ]
        for mismatch in mismatches {
            await #expect(throws: BandFailureCategory.unsupported) {
                _ = try await session.stageLiveBatch(
                    mismatch,
                    token: liveToken,
                    callbackGeneration: generation
                )
            }
            #expect(await session.snapshot().state == .liveCollecting)
        }
        try await session.stopLive(token: liveToken)

        let operation = try await session.beginOperation(.history)
        let source = try #require(
            VirtualBandFixtures.historyChunk.batches.first
        )
        let mismatchedBatch = BandSampleBatch(
            sourceIdentity: source.sourceIdentity,
            lane: .history,
            parserRevision: source.parserRevision,
            calibrationRevision: "calibration-v2",
            samples: source.samples
        )
        let mismatchedChunk = BandHistoryChunk(
            chunkIdentity: VirtualBandFixtures.historyChunk.chunkIdentity,
            previousCursor:
                VirtualBandFixtures.historyChunk.previousCursor,
            nextCursor: VirtualBandFixtures.historyChunk.nextCursor,
            complete: VirtualBandFixtures.historyChunk.complete,
            overflowed: VirtualBandFixtures.historyChunk.overflowed,
            retainedRange:
                VirtualBandFixtures.historyChunk.retainedRange,
            firstLostRange:
                VirtualBandFixtures.historyChunk.firstLostRange,
            acknowledgementToken:
                VirtualBandFixtures.historyChunk.acknowledgementToken,
            batches: [mismatchedBatch]
        )
        await #expect(throws: BandFailureCategory.unsupported) {
            _ = try await session.stageHistoryChunk(
                mismatchedChunk,
                token: operation,
                callbackGeneration: generation
            )
        }
        #expect(await session.snapshot().activeOperation == .history)
        try await session.cancelOperation(operation)
    }

    @Test("Live staging precedes durable completion and reports duplicates")
    func liveStagingPrecedesDurableCompletionAndReportsDuplicates()
        async throws
    {
        let recorder = BandDiagnosticsRecorder()
        let (session, generation) = try await readySession(
            diagnostics: recorder
        )
        let liveToken = try await session.beginLive()
        let first = try await session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: liveToken,
            callbackGeneration: generation
        )
        var liveEvents = await recorder.snapshot().filter {
            $0.kind == .live
        }
        #expect(
            liveEvents.contains(
                BandDiagnosticEvent(
                    kind: .live,
                    outcome: .staged,
                    countBucket: .one
                )
            )
        )
        #expect(!liveEvents.contains { $0.outcome == .completed })
        try await session.acknowledgeLive(
            receipt: DurableLiveReceipt(
                acceptance: first,
                committedSamples: 1,
                committed: true
            ),
            callbackGeneration: generation
        )

        let duplicate = try await session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: liveToken,
            callbackGeneration: generation
        )
        #expect(duplicate.acceptedSamples.isEmpty)
        liveEvents = await recorder.snapshot().filter {
            $0.kind == .live
        }
        #expect(
            liveEvents == [
                BandDiagnosticEvent(kind: .live, outcome: .began),
                BandDiagnosticEvent(
                    kind: .live,
                    outcome: .staged,
                    countBucket: .one
                ),
                BandDiagnosticEvent(
                    kind: .live,
                    outcome: .completed,
                    countBucket: .one
                ),
                BandDiagnosticEvent(
                    kind: .live,
                    outcome: .staged,
                    countBucket: .zero
                ),
            ]
        )
        #expect(
            !liveEvents.contains(
                BandDiagnosticEvent(
                    kind: .live,
                    outcome: .completed,
                    countBucket: .zero
                )
            )
        )
        try await session.acknowledgeLive(
            receipt: DurableLiveReceipt(
                acceptance: duplicate,
                committedSamples: 0,
                committed: true
            ),
            callbackGeneration: generation
        )
        liveEvents = await recorder.snapshot().filter {
            $0.kind == .live
        }
        #expect(
            liveEvents.last == BandDiagnosticEvent(
                kind: .live,
                outcome: .completed,
                countBucket: .zero
            )
        )
        try await session.stopLive(token: liveToken)
    }

    @Test("Live completion fences the next batch until evidence is recorded")
    func liveCompletionFencesNextBatchUntilEvidenceIsRecorded()
        async throws
    {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let (session, generation) = try await readySession(
            diagnostics: recorder
        )
        let liveToken = try await session.beginLive()
        let first = try await session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: liveToken,
            callbackGeneration: generation
        )
        await recorder.requestNextRecordSuspensionForTesting()

        let completion = Task {
            try await session.acknowledgeLive(
                receipt: DurableLiveReceipt(
                    acceptance: first,
                    committedSamples: 1,
                    committed: true
                ),
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()

        let nextBatch = BandSampleBatch(
            sourceIdentity: VirtualBandFixtures.identity.sourceIdentity,
            lane: .live,
            parserRevision: "parser-v1",
            calibrationRevision: "calibration-v1",
            samples: [
                BandSample(
                    identity: BandSampleIdentity(
                        stream: .heartRate,
                        sequence: 2,
                        deviceTimeMilliseconds: 2_000
                    ),
                    value: 73,
                    unit: .beatsPerMinute,
                    quality: .accepted
                ),
            ]
        )
        await #expect(throws: BandFailureCategory.busy) {
            _ = try await session.stageLiveBatch(
                nextBatch,
                token: liveToken,
                callbackGeneration: generation
            )
        }

        await recorder.resumeSuspendedRecordForTesting()
        try await completion.value
        let second = try await session.stageLiveBatch(
            nextBatch,
            token: liveToken,
            callbackGeneration: generation
        )
        let liveEvents = await recorder.snapshot().filter {
            $0.kind == .live
        }
        let completedIndex = try #require(
            liveEvents.lastIndex {
                $0.outcome == .completed && $0.countBucket == .one
            }
        )
        let stagedIndex = try #require(
            liveEvents.lastIndex {
                $0.outcome == .staged && $0.countBucket == .one
            }
        )
        #expect(completedIndex < stagedIndex)

        try await session.acknowledgeLive(
            receipt: DurableLiveReceipt(
                acceptance: second,
                committedSamples: 1,
                committed: true
            ),
            callbackGeneration: generation
        )
        try await session.stopLive(token: liveToken)
    }

    @Test("Disconnect diagnostics preserve every reason")
    func disconnectDiagnosticsPreserveEveryReason() async throws {
        for reason in [
            BandDisconnectReason.userPaused,
            .collectorHandoff,
            .transportReplaced,
        ] {
            let recorder = BandDiagnosticsRecorder()
            let (session, generation) = try await readySession(
                diagnostics: recorder
            )
            let eventCount = await recorder.snapshot().count

            _ = try await session.disconnect(
                reason: reason,
                callbackGeneration: generation
            )

            #expect(
                Array(await recorder.snapshot().dropFirst(eventCount)) == [
                    BandDiagnosticEvent(
                        kind: .disconnect,
                        outcome: .began,
                        disconnectReason: reason
                    ),
                    BandDiagnosticEvent(
                        kind: .disconnect,
                        outcome: .completed,
                        disconnectReason: reason
                    ),
                ]
            )
        }
    }

    @Test("Connection and authentication have explicit terminals")
    func connectionAndAuthenticationHaveExplicitTerminals() async throws {
        let result = try await BandConformanceRunner.run(
            "connection_terminal_paths"
        )
        #expect(result.failure == BandFailureCategory.securityFailure.rawValue)
        #expect(result.events.contains("connection_cancelled"))
        #expect(result.events.contains("authentication_rejected"))
        #expect(result.events.contains("security_failure"))
        #expect(result.events.contains("security_failure_restart_rejected"))
        #expect(result.events.contains("replacement_session_ready"))
        #expect(result.events.contains("connection_diagnostics_bounded"))
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Capability negotiation has generation-fenced terminals")
    func capabilityNegotiationHasExplicitTerminals() async throws {
        let cancellationRecorder = BandDiagnosticsRecorder()
        let (
            cancelledSession,
            cancelledGeneration,
            cancelledConnectionToken
        ) = try await negotiatingSessionWithToken(
            diagnostics: cancellationRecorder
        )

        try await cancelledSession.cancelCapabilities(
            token: cancelledConnectionToken,
            callbackGeneration: cancelledGeneration
        )
        let cancelled = await cancelledSession.snapshot()
        #expect(cancelled.state == .idle)
        #expect(cancelled.generation == cancelledGeneration + 1)
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await cancelledSession.cancelCapabilities(
                token: cancelledConnectionToken,
                callbackGeneration: cancelledGeneration
            )
        }
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await cancelledSession.failCapabilities(
                .timeout,
                token: cancelledConnectionToken,
                callbackGeneration: cancelledGeneration
            )
        }
        let retryScanToken = try await cancelledSession.beginScan()
        let retryGeneration = retryScanToken.generation
        #expect(retryGeneration == cancelled.generation + 1)
        let retryConnectionToken = try await cancelledSession.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: retryScanToken
        )
        try await cancelledSession.beginConnection(
            token: retryConnectionToken,
            callbackGeneration: retryGeneration
        )
        try await cancelledSession.beginAuthentication(
            token: retryConnectionToken,
            callbackGeneration: retryGeneration
        )
        try await cancelledSession.completeConnection(
            VirtualBandFixtures.identity,
            token: retryConnectionToken,
            callbackGeneration: retryGeneration
        )
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await cancelledSession.cancelCapabilities(
                token: cancelledConnectionToken,
                callbackGeneration: cancelledGeneration
            )
        }
        let replacementNegotiation = await cancelledSession.snapshot()
        #expect(replacementNegotiation.state == .negotiatingCapabilities)
        #expect(replacementNegotiation.generation == retryGeneration)
        let cancellationEvents = await cancellationRecorder.snapshot()
        #expect(
            cancellationEvents.contains(
                BandDiagnosticEvent(
                    kind: .capability,
                    outcome: .cancelled
                )
            )
        )

        let timeoutRecorder = BandDiagnosticsRecorder()
        let (
            timedOutSession,
            timedOutGeneration,
            timedOutConnectionToken
        ) = try await negotiatingSessionWithToken(diagnostics: timeoutRecorder)
        try await timedOutSession.failCapabilities(
            .timeout,
            token: timedOutConnectionToken,
            callbackGeneration: timedOutGeneration
        )
        let timedOut = await timedOutSession.snapshot()
        #expect(timedOut.state == .recovering)
        #expect(timedOut.generation == timedOutGeneration + 1)
        let timeoutEvents = await timeoutRecorder.snapshot()
        #expect(
            timeoutEvents.last == BandDiagnosticEvent(
                kind: .capability,
                outcome: .timedOut,
                failureCategory: .timeout
            )
        )

        let (invalidSession, invalidGeneration, invalidConnectionToken) =
            try await negotiatingSessionWithToken()
        await #expect(throws: BandFailureCategory.invalidInput) {
            try await invalidSession.failCapabilities(
                .storage,
                token: invalidConnectionToken,
                callbackGeneration: invalidGeneration
            )
        }
        let unchanged = await invalidSession.snapshot()
        #expect(unchanged.state == .negotiatingCapabilities)
        #expect(unchanged.generation == invalidGeneration)

        let (
            authenticationSession,
            authenticationGeneration,
            authenticationConnectionToken
        ) = try await negotiatingSessionWithToken()
        try await authenticationSession.failCapabilities(
            .authentication,
            token: authenticationConnectionToken,
            callbackGeneration: authenticationGeneration
        )
        #expect(await authenticationSession.snapshot().state == .rejected)

        let (
            securitySession,
            securityGeneration,
            securityConnectionToken
        ) = try await negotiatingSessionWithToken()
        try await securitySession.failCapabilities(
            .securityFailure,
            token: securityConnectionToken,
            callbackGeneration: securityGeneration
        )
        #expect(await securitySession.snapshot().state == .securityFailure)

        let (
            disconnectedSession,
            disconnectedGeneration,
            disconnectedConnectionToken
        ) = try await negotiatingSessionWithToken()
        try await disconnectedSession.failCapabilities(
            .disconnected,
            token: disconnectedConnectionToken,
            callbackGeneration: disconnectedGeneration
        )
        #expect(await disconnectedSession.snapshot().state == .recovering)
        let recoveryScanToken = try await disconnectedSession.beginScan()
        #expect(recoveryScanToken.generation == disconnectedGeneration + 2)
        #expect(await disconnectedSession.snapshot().state == .scanning)
    }

    @Test("Authentication failure invalidates an active operation session")
    func operationAuthenticationFailureInvalidatesSession() async throws {
        let (session, generation) = try await readySession()
        let token = try await session.beginOperation(.battery)

        try await session.failOperation(token, category: .authentication)

        let failed = await session.snapshot()
        #expect(failed.state == .rejected)
        #expect(failed.generation == generation + 1)
        #expect(failed.activeOperation == nil)
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await session.cancelOperation(token)
        }
        await #expect(throws: BandFailureCategory.invalidState) {
            _ = try await session.beginOperation(.battery)
        }
        let retryScanToken = try await session.beginScan()
        let retryGeneration = retryScanToken.generation
        #expect(retryGeneration == failed.generation + 1)
    }

    @Test("Operation token is revalidated after diagnostic suspension")
    func operationTokenIsNotReturnedAfterInvalidation() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let (session, _) = try await readySession(diagnostics: recorder)
        await recorder.requestNextRecordSuspensionForTesting()

        let beginTask = Task {
            try await session.beginOperation(.battery)
        }
        await recorder.waitForRecordSuspensionForTesting()
        try await session.close()
        await recorder.resumeSuspendedRecordForTesting()

        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await beginTask.value
        }
        let snapshot = await session.snapshot()
        #expect(snapshot.state == .closed)
        #expect(snapshot.activeOperation == nil)
        let events = await recorder.snapshot()
        #expect(
            events.contains(
                BandDiagnosticEvent(
                    kind: .command,
                    outcome: .stale,
                    failureCategory: .staleCallback,
                    operationClass: .battery
                )
            )
        )
    }

    @Test("Capability completion is revalidated after diagnostic suspension")
    func capabilityCompletionIsNotReturnedAfterDisconnect() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let (session, generation, connectionToken) =
            try await negotiatingSessionWithToken(diagnostics: recorder)
        await recorder.requestNextRecordSuspensionForTesting()

        let capabilityTask = Task {
            try await session.acceptCapabilities(
                VirtualBandFixtures.capabilities,
                token: connectionToken,
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()
        let idleGeneration = try await session.disconnect(
            reason: .userPaused,
            callbackGeneration: generation
        )
        #expect(idleGeneration == generation + 1)
        #expect(await session.snapshot().state == .idle)

        await recorder.resumeSuspendedRecordForTesting()
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await capabilityTask.value
        }
        #expect(await session.snapshot().state == .idle)
        #expect(
            await recorder.snapshot().last == BandDiagnosticEvent(
                kind: .capability,
                outcome: .stale,
                failureCategory: .staleCallback
            )
        )
    }

    @Test("Capability completion permits valid live progress")
    func capabilityCompletionPermitsValidLiveProgress() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let (session, generation, connectionToken) =
            try await negotiatingSessionWithToken(diagnostics: recorder)
        await recorder.requestNextRecordSuspensionForTesting()

        let capabilityTask = Task {
            try await session.acceptCapabilities(
                VirtualBandFixtures.capabilities,
                token: connectionToken,
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()
        let liveToken = try await session.beginLive()
        #expect(await session.snapshot().state == .liveCollecting)

        await recorder.resumeSuspendedRecordForTesting()
        try await capabilityTask.value
        #expect(await session.snapshot().state == .liveCollecting)
        try await session.stopLive(token: liveToken)
    }

    @Test("Capability completion permits valid command progress")
    func capabilityCompletionPermitsValidCommandProgress() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let (session, generation, connectionToken) =
            try await negotiatingSessionWithToken(diagnostics: recorder)
        await recorder.requestNextRecordSuspensionForTesting()

        let capabilityTask = Task {
            try await session.acceptCapabilities(
                VirtualBandFixtures.capabilities,
                token: connectionToken,
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()
        let operation = try await session.beginOperation(.battery)
        #expect(await session.snapshot().activeOperation == .battery)

        await recorder.resumeSuspendedRecordForTesting()
        try await capabilityTask.value
        #expect(await session.snapshot().activeOperation == .battery)
        try await session.cancelOperation(operation)
    }

    @Test("Scan generation is revalidated after diagnostic suspension")
    func scanGenerationIsNotReturnedAfterClose() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let session = BandSessionMachine(diagnostics: recorder)
        await recorder.requestNextRecordSuspensionForTesting()

        let scanTask = Task {
            try await session.beginScan()
        }
        await recorder.waitForRecordSuspensionForTesting()
        try await session.close()
        await recorder.resumeSuspendedRecordForTesting()

        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await scanTask.value
        }
        let snapshot = await session.snapshot()
        #expect(snapshot.state == .closed)
        #expect(snapshot.generation == 2)
        let events = await recorder.snapshot()
        #expect(
            events.contains(
                BandDiagnosticEvent(
                    kind: .discovery,
                    outcome: .stale,
                    failureCategory: .staleCallback
                )
            )
        )
    }

    @Test("Disconnect generation is revalidated after diagnostic suspension")
    func disconnectGenerationIsNotReturnedAfterClose() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let (session, generation) = try await readySession(
            diagnostics: recorder
        )
        _ = try await session.beginLive()
        _ = try await session.beginOperation(.history)
        await recorder.requestNextRecordSuspensionForTesting()

        let disconnectTask = Task {
            try await session.disconnect(
                reason: .userPaused,
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()
        try await session.close()
        await recorder.resumeSuspendedRecordForTesting()

        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await disconnectTask.value
        }
        let snapshot = await session.snapshot()
        #expect(snapshot.state == .closed)
        #expect(snapshot.generation == generation + 2)
        let events = await recorder.snapshot()
        #expect(
            events.contains(
                BandDiagnosticEvent(
                    kind: .history,
                    outcome: .cancelled,
                    operationClass: .history
                )
            )
        )
        #expect(
            events.contains(
                BandDiagnosticEvent(
                    kind: .live,
                    outcome: .cancelled
                )
            )
        )
        #expect(
            events.contains(
                BandDiagnosticEvent(
                    kind: .disconnect,
                    outcome: .cancelled
                )
            )
        )
        #expect(
            events.contains(
                BandDiagnosticEvent(
                    kind: .disconnect,
                    outcome: .stale,
                    failureCategory: .staleCallback,
                    disconnectReason: .userPaused
                )
            )
        )
    }

    @Test("Disconnect invalidates staging before diagnostic suspension")
    func disconnectRejectsStagingDuringDiagnosticSuspension() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let (session, generation) = try await readySession(
            diagnostics: recorder
        )
        let historyToken = try await session.beginOperation(.history)
        await recorder.requestNextRecordSuspensionForTesting()

        let disconnectTask = Task {
            try await session.disconnect(
                reason: .collectorHandoff,
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()

        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await session.stageHistoryChunk(
                VirtualBandFixtures.historyChunk,
                token: historyToken,
                callbackGeneration: generation
            )
        }
        let disconnecting = await session.snapshot()
        #expect(disconnecting.state == .disconnecting)
        #expect(disconnecting.generation == generation + 1)
        #expect(disconnecting.activeOperation == .history)
        #expect(!disconnecting.liveActive)

        await recorder.resumeSuspendedRecordForTesting()
        let idleGeneration = try await disconnectTask.value
        #expect(idleGeneration == generation + 1)
        let idle = await session.snapshot()
        #expect(idle.state == .idle)
        #expect(idle.activeOperation == nil)
        #expect(!idle.liveActive)
        let events = await recorder.snapshot()
        #expect(
            events.contains(
                BandDiagnosticEvent(
                    kind: .history,
                    outcome: .stale,
                    failureCategory: .staleCallback
                )
            )
        )
    }

    @Test("Stopping live cannot abort a suspended disconnect")
    func stopLiveCannotAbortSuspendedDisconnect() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let (session, generation) = try await readySession(
            diagnostics: recorder
        )
        let liveToken = try await session.beginLive()
        await recorder.requestNextRecordSuspensionForTesting()

        let disconnectTask = Task {
            try await session.disconnect(
                reason: .userPaused,
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()

        await #expect(throws: BandFailureCategory.staleCallback) {
            try await session.stopLive(token: liveToken)
        }
        let disconnecting = await session.snapshot()
        #expect(disconnecting.state == .disconnecting)
        #expect(disconnecting.generation == generation + 1)
        #expect(disconnecting.liveActive)

        await recorder.resumeSuspendedRecordForTesting()
        let idleGeneration = try await disconnectTask.value
        #expect(idleGeneration == generation + 1)
        let idle = await session.snapshot()
        #expect(idle.state == .idle)
        #expect(!idle.liveActive)
        let events = await recorder.snapshot()
        #expect(
            events.contains(
                BandDiagnosticEvent(
                    kind: .live,
                    outcome: .stale,
                    failureCategory: .staleCallback
                )
            )
        )
        #expect(
            events.contains(
                BandDiagnosticEvent(
                    kind: .live,
                    outcome: .cancelled
                )
            )
        )
        #expect(
            events.contains(
                BandDiagnosticEvent(
                    kind: .disconnect,
                    outcome: .completed,
                    disconnectReason: .userPaused
                )
            )
        )
    }

    @Test("Connection callbacks are bound to the selected candidate token")
    func connectionCallbacksRejectForeignCandidateToken() async throws {
        let session = BandSessionMachine()
        let scanToken = try await session.beginScan()
        let generation = scanToken.generation
        let selectedToken = try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: scanToken
        )
        let foreignToken = BandConnectionToken(
            sessionNonce: selectedToken.sessionNonce,
            generation: selectedToken.generation,
            sequence: selectedToken.sequence,
            candidateHandle: "different-candidate"
        )

        await #expect(throws: BandFailureCategory.staleCallback) {
            try await session.beginConnection(
                token: foreignToken,
                callbackGeneration: generation
            )
        }
        #expect(await session.snapshot().state == .candidateSelected)
        try await session.beginConnection(
            token: selectedToken,
            callbackGeneration: generation
        )
        #expect(await session.snapshot().state == .connecting)
    }

    @Test("Established authentication failures terminate ready and live sessions")
    func establishedAuthenticationFailuresTerminateSession() async throws {
        let readyRecorder = BandDiagnosticsRecorder()
        let (
            readyFailureSession,
            readyGeneration,
            readyConnectionToken
        ) = try await readySessionWithToken(diagnostics: readyRecorder)
        let readyFailureEventCount = await readyRecorder.snapshot().count
        try await readyFailureSession.failEstablishedSession(
            .authentication,
            token: readyConnectionToken,
            callbackGeneration: readyGeneration
        )
        let rejected = await readyFailureSession.snapshot()
        #expect(rejected.state == .rejected)
        #expect(rejected.generation == readyGeneration + 1)
        #expect(
            Array(
                await readyRecorder.snapshot().dropFirst(
                    readyFailureEventCount
                )
            ) == [
                BandDiagnosticEvent(
                    kind: .authentication,
                    outcome: .rejected,
                    failureCategory: .authentication
                ),
            ]
        )

        let liveRecorder = BandDiagnosticsRecorder()
        let (
            liveFailureSession,
            liveGeneration,
            liveConnectionToken
        ) = try await readySessionWithToken(diagnostics: liveRecorder)
        _ = try await liveFailureSession.beginLive()
        let liveFailureEventCount = await liveRecorder.snapshot().count
        try await liveFailureSession.failEstablishedSession(
            .securityFailure,
            token: liveConnectionToken,
            callbackGeneration: liveGeneration
        )
        let secured = await liveFailureSession.snapshot()
        #expect(secured.state == .securityFailure)
        #expect(secured.generation == liveGeneration + 1)
        #expect(!secured.liveActive)
        #expect(
            Array(
                await liveRecorder.snapshot().dropFirst(
                    liveFailureEventCount
                )
            ) == [
                BandDiagnosticEvent(
                    kind: .live,
                    outcome: .interrupted,
                    failureCategory: .securityFailure
                ),
                BandDiagnosticEvent(
                    kind: .authentication,
                    outcome: .rejected,
                    failureCategory: .securityFailure
                ),
            ]
        )

        let (
            invalidFailureSession,
            invalidGeneration,
            invalidConnectionToken
        ) = try await readySessionWithToken()
        await #expect(throws: BandFailureCategory.invalidInput) {
            try await invalidFailureSession.failEstablishedSession(
                .timeout,
                token: invalidConnectionToken,
                callbackGeneration: invalidGeneration
            )
        }
        #expect(await invalidFailureSession.snapshot().state == .ready)
    }

    @Test("Established failures require the active connection token")
    func establishedFailuresRejectForeignConnectionToken() async throws {
        let (_, firstGeneration, firstToken) =
            try await readySessionWithToken()
        let (second, secondGeneration, secondToken) =
            try await readySessionWithToken()
        #expect(firstGeneration == secondGeneration)

        await #expect(throws: BandFailureCategory.staleCallback) {
            try await second.failEstablishedSession(
                .authentication,
                token: firstToken,
                callbackGeneration: secondGeneration
            )
        }
        #expect(await second.snapshot().state == .ready)

        try await second.failEstablishedSession(
            .authentication,
            token: secondToken,
            callbackGeneration: secondGeneration
        )
        #expect(await second.snapshot().state == .rejected)
    }

    @Test("Established failures terminate active operations")
    func establishedFailuresTerminateActiveOperations() async throws {
        let historyRecorder = BandDiagnosticsRecorder()
        let (
            historySession,
            historyGeneration,
            historyConnectionToken
        ) = try await readySessionWithToken(diagnostics: historyRecorder)
        let historyOperation =
            try await historySession.beginOperation(.history)
        let historyEventCount = await historyRecorder.snapshot().count

        try await historySession.failEstablishedSession(
            .authentication,
            token: historyConnectionToken,
            callbackGeneration: historyGeneration
        )

        let rejected = await historySession.snapshot()
        #expect(rejected.state == .rejected)
        #expect(rejected.generation == historyGeneration + 1)
        #expect(rejected.activeOperation == nil)
        #expect(
            Array(
                await historyRecorder.snapshot().dropFirst(
                    historyEventCount
                )
            ) == [
                BandDiagnosticEvent(
                    kind: .history,
                    outcome: .interrupted,
                    failureCategory: .authentication,
                    operationClass: .history
                ),
                BandDiagnosticEvent(
                    kind: .authentication,
                    outcome: .rejected,
                    failureCategory: .authentication
                ),
            ]
        )
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await historySession.cancelOperation(historyOperation)
        }

        let liveRecorder = BandDiagnosticsRecorder()
        let (
            liveSession,
            liveGeneration,
            liveConnectionToken
        ) = try await readySessionWithToken(diagnostics: liveRecorder)
        _ = try await liveSession.beginLive()
        _ = try await liveSession.beginOperation(.battery)
        let liveEventCount = await liveRecorder.snapshot().count

        try await liveSession.failEstablishedSession(
            .securityFailure,
            token: liveConnectionToken,
            callbackGeneration: liveGeneration
        )

        let secured = await liveSession.snapshot()
        #expect(secured.state == .securityFailure)
        #expect(secured.generation == liveGeneration + 1)
        #expect(secured.activeOperation == nil)
        #expect(!secured.liveActive)
        #expect(
            Array(
                await liveRecorder.snapshot().dropFirst(liveEventCount)
            ) == [
                BandDiagnosticEvent(
                    kind: .command,
                    outcome: .interrupted,
                    failureCategory: .securityFailure,
                    operationClass: .battery
                ),
                BandDiagnosticEvent(
                    kind: .live,
                    outcome: .interrupted,
                    failureCategory: .securityFailure
                ),
                BandDiagnosticEvent(
                    kind: .authentication,
                    outcome: .rejected,
                    failureCategory: .securityFailure
                ),
            ]
        )
    }

    @Test("Operation failures validate categories before mutation")
    func operationFailuresValidateCategoriesBeforeMutation() async throws {
        let recorder = BandDiagnosticsRecorder()
        let (session, generation) = try await readySession(
            diagnostics: recorder
        )
        let operation = try await session.beginOperation(.battery)

        for category in [
            BandFailureCategory.closed,
            .incompatible,
            .busy,
            .storage,
        ] {
            let eventCount = await recorder.snapshot().count
            await #expect(throws: BandFailureCategory.invalidInput) {
                _ = try await session.failOperation(
                    operation,
                    category: category
                )
            }
            let unchanged = await session.snapshot()
            #expect(unchanged.state == .executingCommand)
            #expect(unchanged.generation == generation)
            #expect(unchanged.activeOperation == .battery)
            #expect(
                Array(
                    await recorder.snapshot().dropFirst(eventCount)
                ) == [
                    BandDiagnosticEvent(
                        kind: .command,
                        outcome: .rejected,
                        failureCategory: .invalidInput,
                        operationClass: .battery
                    ),
                ]
            )
        }
        _ = try await session.failOperation(operation, category: .timeout)
        #expect(await session.snapshot().state == .ready)

        let (historySession, _) = try await readySession()
        let historyOperation =
            try await historySession.beginOperation(.history)
        _ = try await historySession.failOperation(
            historyOperation,
            category: .storage
        )
        #expect(await historySession.snapshot().state == .ready)
    }

    @Test("Operation auth failures terminate live before operation failure")
    func operationAuthFailuresTerminateLiveInOrder() async throws {
        for category in [
            BandFailureCategory.authentication,
            .securityFailure,
        ] {
            let recorder = BandDiagnosticsRecorder()
            let (session, generation) = try await readySession(
                diagnostics: recorder
            )
            _ = try await session.beginLive()
            let operation = try await session.beginOperation(.battery)
            let eventCount = await recorder.snapshot().count

            _ = try await session.failOperation(
                operation,
                category: category
            )

            let terminal = await session.snapshot()
            #expect(
                terminal.state == (
                    category == .securityFailure
                        ? .securityFailure
                        : .rejected
                )
            )
            #expect(terminal.generation == generation + 1)
            #expect(terminal.activeOperation == nil)
            #expect(!terminal.liveActive)
            #expect(
                Array(
                    await recorder.snapshot().dropFirst(eventCount)
                ) == [
                    BandDiagnosticEvent(
                        kind: .live,
                        outcome: .interrupted,
                        failureCategory: category
                    ),
                    BandDiagnosticEvent(
                        kind: .command,
                        outcome: .failed,
                        failureCategory: category,
                        operationClass: .battery
                    ),
                ]
            )
        }
    }

    @Test("Reconnect requires session-bound authority and issues a new connection token")
    func reconnectRequiresSessionBoundTokens() async throws {
        let (session, generation, originalToken) =
            try await readySessionWithToken()
        let (foreignSession, foreignGeneration, foreignConnectionToken) =
            try await readySessionWithToken()
        #expect(generation == foreignGeneration)

        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await session.interruptForReconnect(
                token: foreignConnectionToken,
                callbackGeneration: generation
            )
        }
        #expect(await session.snapshot().state == .ready)

        let reconnectAuthority = try await session.interruptForReconnect(
            token: originalToken,
            callbackGeneration: generation
        )
        let foreignReconnectAuthority =
            try await foreignSession.interruptForReconnect(
                token: foreignConnectionToken,
                callbackGeneration: foreignGeneration
            )
        #expect(
            reconnectAuthority.generation
                == foreignReconnectAuthority.generation
        )

        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await session.resumeAfterReconnect(
                token: foreignReconnectAuthority,
                callbackGeneration: reconnectAuthority.generation
            )
        }
        #expect(await session.snapshot().state == .recovering)

        let reconnectToken = try await session.resumeAfterReconnect(
            token: reconnectAuthority,
            callbackGeneration: reconnectAuthority.generation
        )

        await #expect(throws: BandFailureCategory.staleCallback) {
            try await session.failEstablishedSession(
                .authentication,
                token: originalToken,
                callbackGeneration: reconnectAuthority.generation
            )
        }
        #expect(await session.snapshot().state == .ready)

        try await session.failEstablishedSession(
            .authentication,
            token: reconnectToken,
            callbackGeneration: reconnectAuthority.generation
        )
        #expect(await session.snapshot().state == .rejected)
    }

    @Test("Reconnect records live interruption before clearing live state")
    func reconnectRecordsLiveInterruptionInOrder() async throws {
        let recorder = BandDiagnosticsRecorder()
        let (session, generation, connectionToken) =
            try await readySessionWithToken(diagnostics: recorder)
        _ = try await session.beginLive()
        _ = try await session.beginOperation(.battery)
        let eventCount = await recorder.snapshot().count

        _ = try await session.interruptForReconnect(
            token: connectionToken,
            callbackGeneration: generation
        )

        let interruptionEvents =
            Array(await recorder.snapshot().dropFirst(eventCount))
        #expect(interruptionEvents == [
            BandDiagnosticEvent(
                kind: .command,
                outcome: .interrupted,
                failureCategory: .disconnected,
                operationClass: .battery
            ),
            BandDiagnosticEvent(kind: .live, outcome: .interrupted),
            BandDiagnosticEvent(kind: .reconnect, outcome: .interrupted),
        ])
        let recovering = await session.snapshot()
        #expect(recovering.state == .recovering)
        #expect(!recovering.liveActive)
        #expect(recovering.activeOperation == nil)
    }

    @Test("Operation disconnect returns resumable reconnect authority")
    func operationDisconnectReturnsReconnectAuthority() async throws {
        let recorder = BandDiagnosticsRecorder()
        let (session, generation, _) =
            try await readySessionWithToken(diagnostics: recorder)
        _ = try await session.beginLive()
        let operation = try await session.beginOperation(.battery)
        let eventCount = await recorder.snapshot().count

        let authority = try await session.failOperation(
            operation,
            category: .disconnected
        )
        let reconnectAuthority = try #require(authority)

        #expect(
            Array(await recorder.snapshot().dropFirst(eventCount)) == [
                BandDiagnosticEvent(
                    kind: .command,
                    outcome: .failed,
                    failureCategory: .disconnected,
                    operationClass: .battery
                ),
                BandDiagnosticEvent(kind: .live, outcome: .interrupted),
                BandDiagnosticEvent(
                    kind: .reconnect,
                    outcome: .interrupted,
                    failureCategory: .disconnected
                ),
            ]
        )
        let recovering = await session.snapshot()
        #expect(recovering.state == .recovering)
        #expect(recovering.generation == generation + 1)
        #expect(!recovering.liveActive)

        _ = try await session.resumeAfterReconnect(
            token: reconnectAuthority,
            callbackGeneration: reconnectAuthority.generation
        )
        #expect(await session.snapshot().state == .ready)
    }

    @Test("Operation-derived reconnect authority is session-bound and single-use")
    func operationDerivedReconnectAuthorityRejectsCrossSessionAndReplay() async throws {
        let (session, _) = try await readySession()
        let (foreignSession, _) = try await readySession()
        let operation = try await session.beginOperation(.battery)
        let foreignOperation =
            try await foreignSession.beginOperation(.battery)
        let authorityResult = try await session.failOperation(
            operation,
            category: .disconnected
        )
        let foreignAuthorityResult =
            try await foreignSession.failOperation(
                foreignOperation,
                category: .disconnected
            )
        let authority = try #require(authorityResult)
        let foreignAuthority = try #require(foreignAuthorityResult)
        #expect(authority.generation == foreignAuthority.generation)

        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await session.resumeAfterReconnect(
                token: foreignAuthority,
                callbackGeneration: authority.generation
            )
        }
        #expect(await session.snapshot().state == .recovering)

        _ = try await session.resumeAfterReconnect(
            token: authority,
            callbackGeneration: authority.generation
        )
        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await session.resumeAfterReconnect(
                token: authority,
                callbackGeneration: authority.generation
            )
        }
        #expect(await session.snapshot().state == .ready)
    }

    @Test("Firmware disconnect requires full rediscovery")
    func firmwareDisconnectRequiresFullRediscovery() async throws {
        let recorder = BandDiagnosticsRecorder()
        let (session, generation, connectionToken) =
            try await readySessionWithToken(
                capabilities: firmwareCapabilities,
                diagnostics: recorder
            )
        let operation = try await session.beginOperation(.firmware)
        await #expect(throws: BandFailureCategory.invalidState) {
            _ = try await session.interruptForReconnect(
                token: connectionToken,
                callbackGeneration: generation
            )
        }
        #expect(await session.snapshot().activeOperation == .firmware)
        let eventCount = await recorder.snapshot().count

        let reconnectAuthority = try await session.failOperation(
            operation,
            category: .disconnected
        )

        #expect(reconnectAuthority == nil)
        #expect(
            Array(await recorder.snapshot().dropFirst(eventCount)) == [
                BandDiagnosticEvent(
                    kind: .firmware,
                    outcome: .interrupted,
                    failureCategory: .disconnected,
                    operationClass: .firmware
                ),
                BandDiagnosticEvent(
                    kind: .reconnect,
                    outcome: .interrupted,
                    failureCategory: .disconnected
                ),
            ]
        )
        let recovering = await session.snapshot()
        #expect(recovering.state == .recovering)
        #expect(recovering.generation == generation + 1)

        let recoveryScanToken = try await session.beginScan()
        #expect(recoveryScanToken.generation == generation + 2)
        #expect(await session.snapshot().state == .scanning)
    }

    @Test("Firmware disconnect batch revalidates after a recovery scan race")
    func firmwareDisconnectBatchRevalidatesAfterRecoveryScanRace() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let (session, generation, _) =
            try await readySessionWithToken(
                capabilities: firmwareCapabilities,
                diagnostics: recorder
            )
        let operation = try await session.beginOperation(.firmware)
        let eventCount = await recorder.snapshot().count
        await recorder.requestNextRecordSuspensionForTesting()

        let failureTask = Task {
            try await session.failOperation(
                operation,
                category: .disconnected
            )
        }
        await recorder.waitForRecordSuspensionForTesting()
        let recovering = await session.snapshot()
        #expect(recovering.state == .recovering)
        #expect(recovering.generation == generation + 1)

        let scanToken = try await session.beginScan()
        #expect(scanToken.generation == generation + 2)
        #expect(await session.snapshot().state == .scanning)

        await recorder.resumeSuspendedRecordForTesting()
        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await failureTask.value
        }

        let scanned = await session.snapshot()
        #expect(scanned.state == .scanning)
        #expect(scanned.generation == scanToken.generation)
        #expect(
            Array(await recorder.snapshot().dropFirst(eventCount)) == [
                BandDiagnosticEvent(
                    kind: .firmware,
                    outcome: .interrupted,
                    failureCategory: .disconnected,
                    operationClass: .firmware
                ),
                BandDiagnosticEvent(
                    kind: .reconnect,
                    outcome: .interrupted,
                    failureCategory: .disconnected
                ),
                BandDiagnosticEvent(
                    kind: .discovery,
                    outcome: .began
                ),
                BandDiagnosticEvent(
                    kind: .firmware,
                    outcome: .stale,
                    failureCategory: .staleCallback,
                    operationClass: .firmware
                ),
            ]
        )
    }

    @Test("Reconnect token survives live start during diagnostics")
    func reconnectTokenSurvivesLiveStartDuringDiagnostics() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let (session, generation, connectionToken) =
            try await readySessionWithToken(
            diagnostics: recorder
        )
        let reconnectAuthority = try await session.interruptForReconnect(
            token: connectionToken,
            callbackGeneration: generation
        )
        await recorder.requestNextRecordSuspensionForTesting()

        let resumeTask = Task {
            try await session.resumeAfterReconnect(
                token: reconnectAuthority,
                callbackGeneration: reconnectAuthority.generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()
        _ = try await session.beginLive()
        #expect(await session.snapshot().state == .liveCollecting)

        await recorder.resumeSuspendedRecordForTesting()
        let reconnectToken = try await resumeTask.value
        try await session.failEstablishedSession(
            .authentication,
            token: reconnectToken,
            callbackGeneration: reconnectAuthority.generation
        )
        let rejected = await session.snapshot()
        #expect(rejected.state == .rejected)
        #expect(!rejected.liveActive)
    }

    @Test("Established failure waits for a pending live receipt")
    func establishedFailureWaitsForPendingLiveReceipt() async throws {
        let recorder = BandDiagnosticsRecorder()
        let (session, generation, connectionToken) =
            try await readySessionWithToken(diagnostics: recorder)
        let liveToken = try await session.beginLive()
        let acceptance = try await session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: liveToken,
            callbackGeneration: generation
        )

        await #expect(throws: BandFailureCategory.busy) {
            try await session.failEstablishedSession(
                .authentication,
                token: connectionToken,
                callbackGeneration: generation
            )
        }
        let pending = await session.snapshot()
        #expect(pending.generation == generation)
        #expect(pending.state == .liveCollecting)
        #expect(pending.liveActive)
        #expect(
            await recorder.snapshot().last == BandDiagnosticEvent(
                kind: .authentication,
                outcome: .rejected,
                failureCategory: .busy
            )
        )

        try await session.acknowledgeLive(
            receipt: DurableLiveReceipt(
                acceptance: acceptance,
                committedSamples: acceptance.acceptedSamples.count,
                committed: true
            ),
            callbackGeneration: generation
        )
        try await session.failEstablishedSession(
            .authentication,
            token: connectionToken,
            callbackGeneration: generation
        )
        #expect(await session.snapshot().state == .rejected)
    }

    @Test("Stopping live requires the active live token")
    func stopLiveRejectsSupersededToken() async throws {
        let (session, _) = try await readySession()
        let firstToken = try await session.beginLive()
        try await session.stopLive(token: firstToken)
        let secondToken = try await session.beginLive()

        await #expect(throws: BandFailureCategory.staleCallback) {
            try await session.stopLive(token: firstToken)
        }
        #expect(await session.snapshot().state == .liveCollecting)
        #expect(await session.snapshot().liveActive)

        try await session.stopLive(token: secondToken)
        #expect(await session.snapshot().state == .ready)
    }

    @Test("Live and history streams are negotiated per lane")
    func liveAndHistoryStreamsAreNegotiatedPerLane() async throws {
        func report(
            historyDays: Int = VirtualBandFixtures.capabilities.historyDays,
            liveStreams: Set<BandStreamKind>,
            historyStreams: Set<BandStreamKind>
        ) -> BandCapabilityReport {
            let base = VirtualBandFixtures.capabilities
            return BandCapabilityReport(
                schemaVersion: base.schemaVersion,
                protocolVersion: base.protocolVersion,
                hardwareRevision: base.hardwareRevision,
                firmwareVersion: base.firmwareVersion,
                historyDays: historyDays,
                capabilities: base.capabilities,
                liveStreams: liveStreams,
                historyStreams: historyStreams
            )
        }

        #expect(throws: BandFailureCategory.incompatible) {
            try report(
                historyDays: 0,
                liveStreams: [.heartRate],
                historyStreams: [.heartRate]
            ).validate()
        }
        try report(
            historyDays: 0,
            liveStreams: [.heartRate],
            historyStreams: []
        ).validate()

        func readySession(
            capabilities: BandCapabilityReport
        ) async throws -> (BandSessionMachine, UInt64) {
            let session = BandSessionMachine()
            let scanToken = try await session.beginScan()
            let generation = scanToken.generation
            let connectionToken = try await session.selectCandidate(
                VirtualBandFixtures.candidate,
                callbackGeneration: scanToken
            )
            try await session.beginConnection(
                token: connectionToken,
                callbackGeneration: generation
            )
            try await session.beginAuthentication(
                token: connectionToken,
                callbackGeneration: generation
            )
            try await session.completeConnection(
                VirtualBandFixtures.identity,
                token: connectionToken,
                callbackGeneration: generation
            )
            try await session.acceptCapabilities(
                capabilities,
                token: connectionToken,
                callbackGeneration: generation
            )
            return (session, generation)
        }

        let historyOnly = report(
            liveStreams: [],
            historyStreams: [.heartRate]
        )
        let (historySession, historyGeneration) =
            try await readySession(capabilities: historyOnly)
        await #expect(throws: BandFailureCategory.unsupported) {
            try await historySession.beginLive()
        }

        let historyToken =
            try await historySession.beginOperation(.history)
        let historyAcceptance = try await historySession.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            token: historyToken,
            callbackGeneration: historyGeneration
        )
        try await historySession.acknowledgeHistory(
            receipt: DurableHistoryReceipt(
                acceptance: historyAcceptance,
                historyStateCommitted: true,
                committedSamples: historyAcceptance.acceptedSamples.count,
                committed: true
            ),
            token: historyToken,
            callbackGeneration: historyGeneration
        )
        try await historySession.completeOperation(historyToken)

        let liveOnly = report(
            liveStreams: [.heartRate],
            historyStreams: []
        )
        let (liveSession, liveGeneration) =
            try await readySession(capabilities: liveOnly)
        let liveToken = try await liveSession.beginLive()
        let liveAcceptance = try await liveSession.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: liveToken,
            callbackGeneration: liveGeneration
        )
        try await liveSession.acknowledgeLive(
            receipt: DurableLiveReceipt(
                acceptance: liveAcceptance,
                committedSamples: liveAcceptance.acceptedSamples.count,
                committed: true
            ),
            callbackGeneration: liveGeneration
        )
        try await liveSession.stopLive(token: liveToken)

        await #expect(throws: BandFailureCategory.unsupported) {
            _ = try await liveSession.beginOperation(.history)
        }
    }

    @Test("Overflow ranges are validated and bound to durable receipts")
    func overflowRangesAreValidatedAndBoundToDurableReceipt() async throws {
        func chunk(
            overflowed: Bool = true,
            retainedRange: BandHistoryRange?,
            firstLostRange: BandHistoryRange?,
            batches: [BandSampleBatch] =
                VirtualBandFixtures.historyChunk.batches
        ) -> BandHistoryChunk {
            BandHistoryChunk(
                chunkIdentity:
                    VirtualBandFixtures.historyChunk.chunkIdentity,
                previousCursor:
                    VirtualBandFixtures.historyChunk.previousCursor,
                nextCursor:
                    VirtualBandFixtures.historyChunk.nextCursor,
                complete: VirtualBandFixtures.historyChunk.complete,
                overflowed: overflowed,
                retainedRange: retainedRange,
                firstLostRange: firstLostRange,
                acknowledgementToken:
                    VirtualBandFixtures.historyChunk.acknowledgementToken,
                batches: batches
            )
        }

        let (session, generation) = try await readySession()
        let token = try await session.beginOperation(.history)
        let retained = BandHistoryRange(
            startDeviceTimeMilliseconds: 2_000,
            endDeviceTimeMilliseconds: 3_000
        )
        let firstLost = BandHistoryRange(
            startDeviceTimeMilliseconds: 1_000,
            endDeviceTimeMilliseconds: 1_999
        )
        let valid = chunk(
            retainedRange: retained,
            firstLostRange: firstLost
        )

        let invalidChunks = [
            chunk(retainedRange: nil, firstLostRange: firstLost),
            chunk(retainedRange: retained, firstLostRange: nil),
            chunk(
                overflowed: false,
                retainedRange: retained,
                firstLostRange: firstLost
            ),
            chunk(
                retainedRange: BandHistoryRange(
                    startDeviceTimeMilliseconds: 3_000,
                    endDeviceTimeMilliseconds: 2_000
                ),
                firstLostRange: firstLost
            ),
            chunk(
                retainedRange: retained,
                firstLostRange: BandHistoryRange(
                    startDeviceTimeMilliseconds: 1_500,
                    endDeviceTimeMilliseconds: 2_000
                )
            ),
            chunk(
                retainedRange: retained,
                firstLostRange: BandHistoryRange(
                    startDeviceTimeMilliseconds: 3_001,
                    endDeviceTimeMilliseconds: 3_500
                )
            ),
            chunk(
                retainedRange: retained,
                firstLostRange: firstLost,
                batches: [
                    BandSampleBatch(
                        sourceIdentity:
                            VirtualBandFixtures.identity.sourceIdentity,
                        lane: .history,
                        parserRevision: "parser-v1",
                        calibrationRevision: "calibration-v1",
                        samples: [VirtualBandFixtures.liveBatch.samples[0]]
                    ),
                ]
            ),
        ]
        for invalid in invalidChunks {
            await #expect(throws: BandFailureCategory.invalidInput) {
                _ = try await session.stageHistoryChunk(
                    invalid,
                    token: token,
                    callbackGeneration: generation
                )
            }
        }

        let acceptance = try await session.stageHistoryChunk(
            valid,
            token: token,
            callbackGeneration: generation
        )
        #expect(acceptance.retainedRange == retained)
        #expect(acceptance.firstLostRange == firstLost)

        let mismatchedAcceptance = HistoryAcceptance(
            chunkIdentity: acceptance.chunkIdentity,
            acknowledgementToken: acceptance.acknowledgementToken,
            nextCursor: acceptance.nextCursor,
            complete: acceptance.complete,
            overflowed: acceptance.overflowed,
            retainedRange: acceptance.retainedRange,
            firstLostRange: BandHistoryRange(
                startDeviceTimeMilliseconds: 0,
                endDeviceTimeMilliseconds: 999
            ),
            acceptedSamples: acceptance.acceptedSamples,
            duplicateSamples: acceptance.duplicateSamples,
            sessionNonce: acceptance.sessionNonce,
            receiptSequence: acceptance.receiptSequence
        )
        await #expect(throws: BandFailureCategory.storage) {
            try await session.acknowledgeHistory(
                receipt: DurableHistoryReceipt(
                    acceptance: mismatchedAcceptance,
                    historyStateCommitted: true,
                    committedSamples: acceptance.acceptedSamples.count,
                    committed: true
                ),
                token: token,
                callbackGeneration: generation
            )
        }

        let receipt = DurableHistoryReceipt(
            acceptance: acceptance,
            historyStateCommitted: true,
            committedSamples: acceptance.acceptedSamples.count,
            committed: true
        )
        #expect(receipt.retainedRange == retained)
        #expect(receipt.firstLostRange == firstLost)
        try await session.acknowledgeHistory(
            receipt: receipt,
            token: token,
            callbackGeneration: generation
        )
        try await session.completeOperation(token)
    }

    @Test("Non-overflow retained ranges bound every sample")
    func nonOverflowRetainedRangesBoundEverySample() throws {
        let retained = BandHistoryRange(
            startDeviceTimeMilliseconds: 2_000,
            endDeviceTimeMilliseconds: 3_000
        )
        func chunk(sampleTime: Int64) -> BandHistoryChunk {
            BandHistoryChunk(
                chunkIdentity: "non-overflow-range",
                previousCursor: nil,
                nextCursor: "non-overflow-cursor",
                complete: true,
                overflowed: false,
                retainedRange: retained,
                firstLostRange: nil,
                acknowledgementToken: "non-overflow-ack",
                batches: [
                    BandSampleBatch(
                        sourceIdentity:
                            VirtualBandFixtures.identity.sourceIdentity,
                        lane: .history,
                        parserRevision: "parser-v1",
                        calibrationRevision: "calibration-v1",
                        samples: [
                            BandSample(
                                identity: BandSampleIdentity(
                                    stream: .heartRate,
                                    sequence: UInt64(sampleTime),
                                    deviceTimeMilliseconds: sampleTime
                                ),
                                value: 72,
                                unit: .beatsPerMinute,
                                quality: .accepted
                            ),
                        ]
                    ),
                ]
            )
        }

        #expect(throws: BandFailureCategory.invalidInput) {
            try chunk(sampleTime: 1_999).validate()
        }
        #expect(throws: BandFailureCategory.invalidInput) {
            try chunk(sampleTime: 3_001).validate()
        }
    }

    @Test("Terminal history chunks preserve the last durable cursor")
    func terminalHistoryChunkPreservesCursor() async throws {
        let (session, generation) = try await readySession()
        let store = VirtualBandStore()

        let firstToken = try await session.beginOperation(.history)
        let firstChunk = BandHistoryChunk(
            chunkIdentity: "cursor-seed",
            previousCursor: nil,
            nextCursor: "cursor-2",
            complete: false,
            overflowed: false,
            retainedRange: BandHistoryRange(
                startDeviceTimeMilliseconds: 2_000,
                endDeviceTimeMilliseconds: 3_000
            ),
            firstLostRange: nil,
            acknowledgementToken: "cursor-seed-ack",
            batches: VirtualBandFixtures.historyChunk.batches
        )
        let firstAcceptance = try await session.stageHistoryChunk(
            firstChunk,
            token: firstToken,
            callbackGeneration: generation
        )
        try await session.acknowledgeHistory(
            receipt: await store.commit(acceptance: firstAcceptance),
            token: firstToken,
            callbackGeneration: generation
        )
        try await session.cancelOperation(firstToken)

        let terminalToken = try await session.beginOperation(.history)
        let terminalChunk = BandHistoryChunk(
            chunkIdentity: "cursor-terminal",
            previousCursor: "cursor-2",
            nextCursor: nil,
            complete: true,
            overflowed: false,
            retainedRange: firstChunk.retainedRange,
            firstLostRange: nil,
            acknowledgementToken: "cursor-terminal-ack",
            batches: []
        )
        let terminalAcceptance = try await session.stageHistoryChunk(
            terminalChunk,
            token: terminalToken,
            callbackGeneration: generation
        )
        #expect(terminalAcceptance.nextCursor == "cursor-2")
        try await session.acknowledgeHistory(
            receipt: await store.commit(acceptance: terminalAcceptance),
            token: terminalToken,
            callbackGeneration: generation
        )
        try await session.completeOperation(terminalToken)

        #expect(
            await session.snapshot().acknowledgedHistoryCursor == "cursor-2"
        )
        #expect(
            await session.historyCheckpoint()?.acknowledgedCursor == "cursor-2"
        )
    }

    @Test("Pending persistence blocks lifecycle terminals until drained")
    func pendingPersistenceBlocksLifecycleTerminals() async throws {
        let liveRecorder = BandDiagnosticsRecorder()
        let (liveSession, liveGeneration, liveConnectionToken) =
            try await readySessionWithToken(diagnostics: liveRecorder)
        let liveToken = try await liveSession.beginLive()
        let liveAcceptance = try await liveSession.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: liveToken,
            callbackGeneration: liveGeneration
        )
        let batteryToken = try await liveSession.beginOperation(.battery)

        await #expect(throws: BandFailureCategory.busy) {
            try await liveSession.failOperation(
                batteryToken,
                category: .authentication
            )
        }
        await #expect(throws: BandFailureCategory.busy) {
            _ = try await liveSession.interruptForReconnect(
                token: liveConnectionToken,
                callbackGeneration: liveGeneration
            )
        }
        await #expect(throws: BandFailureCategory.busy) {
            _ = try await liveSession.disconnect(
                reason: .userPaused,
                callbackGeneration: liveGeneration
            )
        }
        await #expect(throws: BandFailureCategory.busy) {
            try await liveSession.close()
        }
        let pendingLive = await liveSession.snapshot()
        #expect(pendingLive.generation == liveGeneration)
        #expect(pendingLive.activeOperation == .battery)
        #expect(pendingLive.liveActive)

        try await liveSession.acknowledgeLive(
            receipt: DurableLiveReceipt(
                acceptance: liveAcceptance,
                committedSamples: liveAcceptance.acceptedSamples.count,
                committed: true
            ),
            callbackGeneration: liveGeneration
        )
        try await liveSession.failOperation(
            batteryToken,
            category: .authentication
        )
        #expect(await liveSession.snapshot().state == .rejected)

        let historyRecorder = BandDiagnosticsRecorder()
        let (
            historySession,
            historyGeneration,
            historyConnectionToken
        ) = try await readySessionWithToken(diagnostics: historyRecorder)
        let historyToken = try await historySession.beginOperation(.history)
        let historyAcceptance = try await historySession.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            token: historyToken,
            callbackGeneration: historyGeneration
        )
        await #expect(throws: BandFailureCategory.busy) {
            try await historySession.cancelOperation(historyToken)
        }
        await #expect(throws: BandFailureCategory.busy) {
            try await historySession.failOperation(
                historyToken,
                category: .authentication
            )
        }
        await #expect(throws: BandFailureCategory.busy) {
            _ = try await historySession.interruptForReconnect(
                token: historyConnectionToken,
                callbackGeneration: historyGeneration
            )
        }
        await #expect(throws: BandFailureCategory.busy) {
            _ = try await historySession.disconnect(
                reason: .collectorHandoff,
                callbackGeneration: historyGeneration
            )
        }
        await #expect(throws: BandFailureCategory.busy) {
            try await historySession.close()
        }

        try await historySession.acknowledgeHistory(
            receipt: DurableHistoryReceipt(
                acceptance: historyAcceptance,
                historyStateCommitted: true,
                committedSamples: historyAcceptance.acceptedSamples.count,
                committed: true
            ),
            token: historyToken,
            callbackGeneration: historyGeneration
        )
        try await historySession.failOperation(
            historyToken,
            category: .authentication
        )
        #expect(await historySession.snapshot().state == .rejected)

        let liveEvents = await liveRecorder.snapshot()
        #expect(liveEvents.contains(BandDiagnosticEvent(
            kind: .command,
            outcome: .rejected,
            failureCategory: .busy,
            operationClass: .battery
        )))
        #expect(liveEvents.contains(BandDiagnosticEvent(
            kind: .reconnect,
            outcome: .rejected,
            failureCategory: .busy
        )))
        #expect(liveEvents.contains(BandDiagnosticEvent(
            kind: .live,
            outcome: .rejected,
            failureCategory: .busy
        )))
        let historyEvents = await historyRecorder.snapshot()
        #expect(historyEvents.contains(BandDiagnosticEvent(
            kind: .history,
            outcome: .rejected,
            failureCategory: .busy
        )))
    }

    @Test("Pending history rejection records a bounded busy event")
    func pendingHistoryRecordsBusyDiagnostics() async throws {
        let result = try await BandConformanceRunner.run(
            "history_pending_busy_diagnostics"
        )
        #expect(result.failure == BandFailureCategory.busy.rawValue)
        #expect(result.events.contains("second_chunk_rejected"))
        #expect(result.events.contains("history_busy_recorded"))
        #expect(result.events.last == "operation_cancelled")
        #expect(result.finalState == BandSessionState.ready.rawValue)
    }

    @Test("Live and history durable receipts are serialized")
    func liveAndHistoryReceiptsAreSerialized() async throws {
        let overlappingHistory = BandHistoryChunk(
            chunkIdentity: "overlap",
            previousCursor: nil,
            nextCursor: "overlap-cursor",
            complete: true,
            overflowed: false,
            retainedRange: nil,
            firstLostRange: nil,
            acknowledgementToken: "overlap-ack",
            batches: [
                BandSampleBatch(
                    sourceIdentity:
                        VirtualBandFixtures.identity.sourceIdentity,
                    lane: .history,
                    parserRevision: "parser-v1",
                    calibrationRevision: "calibration-v1",
                    samples: VirtualBandFixtures.liveBatch.samples
                ),
            ]
        )

        let (liveFirst, liveGeneration) = try await readySession()
        let liveFirstToken = try await liveFirst.beginLive()
        let liveAcceptance = try await liveFirst.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: liveFirstToken,
            callbackGeneration: liveGeneration
        )
        let liveFirstHistoryToken =
            try await liveFirst.beginOperation(.history)
        await #expect(throws: BandFailureCategory.busy) {
            _ = try await liveFirst.stageHistoryChunk(
                overlappingHistory,
                token: liveFirstHistoryToken,
                callbackGeneration: liveGeneration
            )
        }
        try await liveFirst.acknowledgeLive(
            receipt: DurableLiveReceipt(
                acceptance: liveAcceptance,
                committedSamples: liveAcceptance.acceptedSamples.count,
                committed: true
            ),
            callbackGeneration: liveGeneration
        )
        let conflictingHistory = BandHistoryChunk(
            chunkIdentity: "overlap-conflict",
            previousCursor: nil,
            nextCursor: "overlap-conflict-cursor",
            complete: true,
            overflowed: false,
            retainedRange: overlappingHistory.retainedRange,
            firstLostRange: nil,
            acknowledgementToken: "overlap-conflict-ack",
            batches: [
                BandSampleBatch(
                    sourceIdentity:
                        VirtualBandFixtures.identity.sourceIdentity,
                    lane: .history,
                    parserRevision: "parser-v1",
                    calibrationRevision: "calibration-v1",
                    samples: VirtualBandFixtures.liveBatch.samples.map {
                        BandSample(
                            identity: $0.identity,
                            value: $0.value + 1,
                            unit: $0.unit,
                            quality: $0.quality
                        )
                    }
                ),
            ]
        )
        await #expect(throws: BandFailureCategory.invalidInput) {
            _ = try await liveFirst.stageHistoryChunk(
                conflictingHistory,
                token: liveFirstHistoryToken,
                callbackGeneration: liveGeneration
            )
        }
        #expect(await liveFirst.snapshot().activeOperation == .history)
        let historyAfterLive = try await liveFirst.stageHistoryChunk(
            overlappingHistory,
            token: liveFirstHistoryToken,
            callbackGeneration: liveGeneration
        )
        #expect(historyAfterLive.acceptedSamples.isEmpty)
        #expect(historyAfterLive.duplicateSamples == 1)
        try await liveFirst.acknowledgeHistory(
            receipt: DurableHistoryReceipt(
                acceptance: historyAfterLive,
                historyStateCommitted: true,
                committedSamples: 0,
                committed: true
            ),
            token: liveFirstHistoryToken,
            callbackGeneration: liveGeneration
        )
        try await liveFirst.completeOperation(liveFirstHistoryToken)

        let (historyFirst, historyGeneration) = try await readySession()
        let historyFirstLiveToken = try await historyFirst.beginLive()
        let historyFirstToken =
            try await historyFirst.beginOperation(.history)
        let historyAcceptance = try await historyFirst.stageHistoryChunk(
            overlappingHistory,
            token: historyFirstToken,
            callbackGeneration: historyGeneration
        )
        await #expect(throws: BandFailureCategory.busy) {
            _ = try await historyFirst.stageLiveBatch(
                VirtualBandFixtures.liveBatch,
                token: historyFirstLiveToken,
                callbackGeneration: historyGeneration
            )
        }
        try await historyFirst.acknowledgeHistory(
            receipt: DurableHistoryReceipt(
                acceptance: historyAcceptance,
                historyStateCommitted: true,
                committedSamples: historyAcceptance.acceptedSamples.count,
                committed: true
            ),
            token: historyFirstToken,
            callbackGeneration: historyGeneration
        )
        let conflictingLive = BandSampleBatch(
            sourceIdentity: VirtualBandFixtures.identity.sourceIdentity,
            lane: .live,
            parserRevision: "parser-v1",
            calibrationRevision: "calibration-v1",
            samples: VirtualBandFixtures.liveBatch.samples.map {
                BandSample(
                    identity: $0.identity,
                    value: $0.value + 1,
                    unit: $0.unit,
                    quality: $0.quality
                )
            }
        )
        await #expect(throws: BandFailureCategory.invalidInput) {
            _ = try await historyFirst.stageLiveBatch(
                conflictingLive,
                token: historyFirstLiveToken,
                callbackGeneration: historyGeneration
            )
        }
        #expect(await historyFirst.snapshot().activeOperation == .history)
        let liveAfterHistory = try await historyFirst.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            token: historyFirstLiveToken,
            callbackGeneration: historyGeneration
        )
        #expect(liveAfterHistory.acceptedSamples.isEmpty)
        #expect(liveAfterHistory.duplicateSamples == 1)
        try await historyFirst.acknowledgeLive(
            receipt: DurableLiveReceipt(
                acceptance: liveAfterHistory,
                committedSamples: 0,
                committed: true
            ),
            callbackGeneration: historyGeneration
        )
        try await historyFirst.completeOperation(historyFirstToken)
    }

    @Test("Invalid history tokens record bounded rejection diagnostics")
    func invalidHistoryTokensRecordDiagnostics() async throws {
        let recorder = BandDiagnosticsRecorder()
        let (session, generation) = try await readySession(
            diagnostics: recorder
        )
        let supersededToken = try await session.beginOperation(.history)
        try await session.cancelOperation(supersededToken)
        let activeToken = try await session.beginOperation(.history)
        let (foreignSession, _) = try await readySession()
        let foreignToken = try await foreignSession.beginOperation(.history)

        var eventCount = await recorder.snapshot().count
        await #expect(throws: BandFailureCategory.invalidState) {
            _ = try await session.stageHistoryChunk(
                VirtualBandFixtures.historyChunk,
                token: supersededToken,
                callbackGeneration: generation
            )
        }
        var events = await recorder.snapshot()
        #expect(events.count == eventCount + 1)
        #expect(
            events.last == BandDiagnosticEvent(
                kind: .history,
                outcome: .rejected,
                failureCategory: .invalidState
            )
        )

        eventCount = events.count
        await #expect(throws: BandFailureCategory.staleCallback) {
            _ = try await session.stageHistoryChunk(
                VirtualBandFixtures.historyChunk,
                token: foreignToken,
                callbackGeneration: generation
            )
        }
        events = await recorder.snapshot()
        #expect(events.count == eventCount + 1)
        #expect(
            events.last == BandDiagnosticEvent(
                kind: .history,
                outcome: .rejected,
                failureCategory: .staleCallback
            )
        )

        let acceptance = try await session.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            token: activeToken,
            callbackGeneration: generation
        )
        let receipt = DurableHistoryReceipt(
            acceptance: acceptance,
            historyStateCommitted: true,
            committedSamples: acceptance.acceptedSamples.count,
            committed: true
        )

        eventCount = await recorder.snapshot().count
        await #expect(throws: BandFailureCategory.invalidState) {
            try await session.acknowledgeHistory(
                receipt: receipt,
                token: supersededToken,
                callbackGeneration: generation
            )
        }
        events = await recorder.snapshot()
        #expect(events.count == eventCount + 1)
        #expect(
            events.last == BandDiagnosticEvent(
                kind: .history,
                outcome: .rejected,
                failureCategory: .invalidState
            )
        )

        eventCount = events.count
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await session.acknowledgeHistory(
                receipt: receipt,
                token: foreignToken,
                callbackGeneration: generation
            )
        }
        events = await recorder.snapshot()
        #expect(events.count == eventCount + 1)
        #expect(
            events.last == BandDiagnosticEvent(
                kind: .history,
                outcome: .rejected,
                failureCategory: .staleCallback
            )
        )
        let unchanged = await session.snapshot()
        #expect(unchanged.state == .historyCollecting)
        #expect(unchanged.activeOperation == .history)

        try await session.acknowledgeHistory(
            receipt: receipt,
            token: activeToken,
            callbackGeneration: generation
        )
        try await session.completeOperation(activeToken)
    }

    @Test("History diagnostics distinguish staging from durable completion")
    func historyDiagnosticsDistinguishStagingFromCompletion() async throws {
        let recorder = BandDiagnosticsRecorder()
        let (session, generation) = try await readySession(
            diagnostics: recorder
        )
        let token = try await session.beginOperation(.history)

        let acceptance = try await session.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            token: token,
            callbackGeneration: generation
        )
        var events = await recorder.snapshot()
        var event = events.last
        #expect(event?.kind == .history)
        #expect(event?.outcome == .staged)
        #expect(
            events.lastIndex(where: {
                $0.kind == .history && $0.outcome == .began
            })! < events.lastIndex(where: {
                $0.kind == .history && $0.outcome == .staged
            })!
        )

        try await session.acknowledgeHistory(
            receipt: DurableHistoryReceipt(
                acceptance: acceptance,
                historyStateCommitted: true,
                committedSamples: acceptance.acceptedSamples.count,
                committed: true
            ),
            token: token,
            callbackGeneration: generation
        )
        events = await recorder.snapshot()
        event = events.last
        #expect(event?.kind == .history)
        #expect(event?.outcome == .completed)
        #expect(
            events.lastIndex(where: {
                $0.kind == .history && $0.outcome == .staged
            })! < events.lastIndex(where: {
                $0.kind == .history && $0.outcome == .completed
            })!
        )
        try await session.completeOperation(token)

        let cancellationRecorder = BandDiagnosticsRecorder()
        let (cancellationSession, cancellationGeneration) =
            try await readySession(diagnostics: cancellationRecorder)
        let cancellationToken =
            try await cancellationSession.beginOperation(.history)
        let cancellationAcceptance =
            try await cancellationSession.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            token: cancellationToken,
            callbackGeneration: cancellationGeneration
        )
        await #expect(throws: BandFailureCategory.busy) {
            try await cancellationSession.cancelOperation(cancellationToken)
        }
        try await cancellationSession.acknowledgeHistory(
            receipt: DurableHistoryReceipt(
                acceptance: cancellationAcceptance,
                historyStateCommitted: true,
                committedSamples: cancellationAcceptance.acceptedSamples.count,
                committed: true
            ),
            token: cancellationToken,
            callbackGeneration: cancellationGeneration
        )
        try await cancellationSession.cancelOperation(cancellationToken)
        let cancellationEvents = await cancellationRecorder.snapshot()
        let recordedBusy = cancellationEvents.contains(where: {
            $0.kind == .history
                && $0.outcome == .rejected
                && $0.failureCategory == .busy
        })
        #expect(recordedBusy)
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

        let coalescingRecorder = BandDiagnosticsRecorder(capacity: 4)
        await coalescingRecorder.recordCoalescingConsecutive(
            BandDiagnosticEvent(
                kind: .live,
                outcome: .completed,
                countBucket: .one
            )
        )
        await coalescingRecorder.recordCoalescingConsecutive(
            BandDiagnosticEvent(
                kind: .live,
                outcome: .completed,
                countBucket: .overHundred
            )
        )
        await coalescingRecorder.record(
            BandDiagnosticEvent(
                kind: .live,
                outcome: .failed,
                failureCategory: .storage
            )
        )
        await coalescingRecorder.recordCoalescingConsecutive(
            BandDiagnosticEvent(
                kind: .live,
                outcome: .completed,
                countBucket: .one
            )
        )
        let coalesced = await coalescingRecorder.snapshot()
        #expect(coalesced.count == 3)
        #expect(coalesced[0].countBucket == .overHundred)
        #expect(coalesced[1].failureCategory == .storage)
        #expect(coalesced[2].outcome == .completed)
    }

    @Test("Close cancels only diagnostics for actual active phases")
    func closeCancelsOnlyActualActiveDiagnostics() async throws {
        let idleRecorder = BandDiagnosticsRecorder()
        let idle = BandSessionMachine(diagnostics: idleRecorder)
        try await idle.close()
        #expect((await cancelledKinds(in: idleRecorder)).isEmpty)

        let discoveryRecorder = BandDiagnosticsRecorder()
        let discovery = BandSessionMachine(diagnostics: discoveryRecorder)
        _ = try await discovery.beginScan()
        try await discovery.close()
        #expect(await cancelledKinds(in: discoveryRecorder) == [.discovery])

        let connectionRecorder = BandDiagnosticsRecorder()
        let connection = BandSessionMachine(diagnostics: connectionRecorder)
        let connectionScanToken = try await connection.beginScan()
        let connectionGeneration = connectionScanToken.generation
        let connectionToken = try await connection.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: connectionScanToken
        )
        try await connection.beginConnection(
            token: connectionToken,
            callbackGeneration: connectionGeneration
        )
        try await connection.close()
        #expect(await cancelledKinds(in: connectionRecorder) == [.connection])

        let authenticationRecorder = BandDiagnosticsRecorder()
        let authentication =
            BandSessionMachine(diagnostics: authenticationRecorder)
        let authenticationScanToken = try await authentication.beginScan()
        let authenticationGeneration = authenticationScanToken.generation
        let authenticationToken = try await authentication.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: authenticationScanToken
        )
        try await authentication.beginConnection(
            token: authenticationToken,
            callbackGeneration: authenticationGeneration
        )
        try await authentication.beginAuthentication(
            token: authenticationToken,
            callbackGeneration: authenticationGeneration
        )
        try await authentication.close()
        #expect(
            await cancelledKinds(in: authenticationRecorder)
                == [.authentication]
        )

        let capabilityRecorder = BandDiagnosticsRecorder()
        let (capability, _) = try await negotiatingSession(
            diagnostics: capabilityRecorder
        )
        try await capability.close()
        #expect(await cancelledKinds(in: capabilityRecorder) == [.capability])

        let liveRecorder = BandDiagnosticsRecorder()
        let (live, _) = try await readySession(diagnostics: liveRecorder)
        _ = try await live.beginLive()
        try await live.close()
        #expect(await cancelledKinds(in: liveRecorder) == [.live])

        let historyRecorder = BandDiagnosticsRecorder()
        let (history, _) = try await readySession(
            diagnostics: historyRecorder
        )
        _ = try await history.beginOperation(.history)
        try await history.close()
        #expect(await cancelledKinds(in: historyRecorder) == [.history])

        let commandRecorder = BandDiagnosticsRecorder()
        let (command, _) = try await readySession(
            diagnostics: commandRecorder
        )
        _ = try await command.beginOperation(.battery)
        try await command.close()
        #expect(await cancelledKinds(in: commandRecorder) == [.command])

        let firmwareRecorder = BandDiagnosticsRecorder()
        let (firmware, _) = try await readySession(
            capabilities: firmwareCapabilities,
            diagnostics: firmwareRecorder
        )
        _ = try await firmware.beginOperation(.firmware)
        try await firmware.close()
        #expect(await cancelledKinds(in: firmwareRecorder) == [.firmware])

        let reconnectRecorder = BandDiagnosticsRecorder()
        let (
            reconnect,
            reconnectGeneration,
            reconnectConnectionToken
        ) = try await readySessionWithToken(
            diagnostics: reconnectRecorder
        )
        _ = try await reconnect.interruptForReconnect(
            token: reconnectConnectionToken,
            callbackGeneration: reconnectGeneration
        )
        try await reconnect.close()
        #expect(await cancelledKinds(in: reconnectRecorder) == [.reconnect])
    }

    @Test("Connection evidence survives sustained live persistence")
    func liveReceiptDiagnosticsAreCoalesced() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 16)
        let (session, generation) = try await readySession(
            diagnostics: recorder
        )
        let liveToken = try await session.beginLive()

        for offset in 0..<130 {
            let sample = BandSample(
                identity: BandSampleIdentity(
                    stream: .heartRate,
                    sequence: UInt64(10_000 + offset),
                    deviceTimeMilliseconds: Int64(10_000 + offset)
                ),
                value: 72,
                unit: .beatsPerMinute,
                quality: .accepted
            )
            let acceptance = try await session.stageLiveBatch(
                BandSampleBatch(
                    sourceIdentity:
                        VirtualBandFixtures.identity.sourceIdentity,
                    lane: .live,
                    parserRevision: "parser-v1",
                    calibrationRevision: "calibration-v1",
                    samples: [sample]
                ),
                token: liveToken,
                callbackGeneration: generation
            )
            try await session.acknowledgeLive(
                receipt: DurableLiveReceipt(
                    acceptance: acceptance,
                    committedSamples: acceptance.acceptedSamples.count,
                    committed: true
                ),
                callbackGeneration: generation
            )
        }
        try await session.stopLive(token: liveToken)

        let events = await recorder.snapshot()
        let connectionOutcomes = events
            .filter { $0.kind == .connection }
            .map(\.outcome)
        #expect(connectionOutcomes == [.began, .completed])
        let connectionCompletedIndex = try #require(
            events.firstIndex {
                $0.kind == .connection && $0.outcome == .completed
            }
        )
        let authenticationBeganIndex = try #require(
            events.firstIndex {
                $0.kind == .authentication && $0.outcome == .began
            }
        )
        #expect(connectionCompletedIndex < authenticationBeganIndex)
        #expect(
            events.filter {
                $0.kind == .live && $0.outcome == .completed
            }.count == 2
        )

        let restartLiveToken = try await session.beginLive()
        let restartAcceptance = try await session.stageLiveBatch(
            BandSampleBatch(
                sourceIdentity: VirtualBandFixtures.identity.sourceIdentity,
                lane: .live,
                parserRevision: "parser-v1",
                calibrationRevision: "calibration-v1",
                samples: [
                    BandSample(
                        identity: BandSampleIdentity(
                            stream: .heartRate,
                            sequence: 20_000,
                            deviceTimeMilliseconds: 20_000
                        ),
                        value: 72,
                        unit: .beatsPerMinute,
                        quality: .accepted
                    ),
                ]
            ),
            token: restartLiveToken,
            callbackGeneration: generation
        )
        try await session.acknowledgeLive(
            receipt: DurableLiveReceipt(
                acceptance: restartAcceptance,
                committedSamples: 1,
                committed: true
            ),
            callbackGeneration: generation
        )
        try await session.stopLive(token: restartLiveToken)
        let restartedEvents = await recorder.snapshot()
        #expect(
            restartedEvents.filter {
                $0.kind == .live && $0.outcome == .completed
            }.count == 4
        )
    }

    @Test("Authentication begin is actor-reentrancy safe")
    func authenticationBeginIsReentrancySafe() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let session = BandSessionMachine(diagnostics: recorder)
        let scanToken = try await session.beginScan()
        let generation = scanToken.generation
        let connectionToken = try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: scanToken
        )
        try await session.beginConnection(
            token: connectionToken,
            callbackGeneration: generation
        )

        let successes = await withTaskGroup(
            of: Bool.self,
            returning: Int.self
        ) { group in
            for _ in 0..<32 {
                group.addTask {
                    do {
                        try await session.beginAuthentication(
                            token: connectionToken,
                            callbackGeneration: generation
                        )
                        return true
                    } catch {
                        return false
                    }
                }
            }
            var count = 0
            for await success in group {
                if success {
                    count += 1
                }
            }
            return count
        }

        #expect(successes == 1)
        #expect(await session.snapshot().state == .authenticating)
        let events = await recorder.snapshot()
        #expect(
            events.filter {
                $0.kind == .connection && $0.outcome == .completed
            }.count == 1
        )
        #expect(
            events.filter {
                $0.kind == .authentication && $0.outcome == .began
            }.count == 1
        )
    }

    @Test("Authentication transition diagnostics stay ordered")
    func authenticationTransitionDiagnosticsStayOrdered() async throws {
        let recorder = BandDiagnosticsRecorder(capacity: 64)
        let session = BandSessionMachine(diagnostics: recorder)
        let scanToken = try await session.beginScan()
        let generation = scanToken.generation
        let connectionToken = try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: scanToken
        )
        try await session.beginConnection(
            token: connectionToken,
            callbackGeneration: generation
        )

        try await session.beginAuthentication(
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.completeConnection(
            VirtualBandFixtures.identity,
            token: connectionToken,
            callbackGeneration: generation
        )

        let events = await recorder.snapshot()
        let connectionCompleted = try #require(
            events.firstIndex {
                $0.kind == .connection && $0.outcome == .completed
            }
        )
        let authenticationBegan = try #require(
            events.firstIndex {
                $0.kind == .authentication && $0.outcome == .began
            }
        )
        let authenticationCompleted = try #require(
            events.firstIndex {
                $0.kind == .authentication && $0.outcome == .completed
            }
        )
        #expect(connectionCompleted < authenticationBegan)
        #expect(authenticationBegan < authenticationCompleted)
    }

    @Test("Unknown conformance scenarios fail closed")
    func unknownScenarioFailsClosed() async {
        await #expect(throws: BandFailureCategory.invalidInput) {
            try await BandConformanceRunner.run("not-a-scenario")
        }
    }

    private func readySession(
        capabilities: BandCapabilityReport =
            VirtualBandFixtures.capabilities,
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder()
    ) async throws -> (BandSessionMachine, UInt64) {
        let (session, generation, _) = try await readySessionWithToken(
            capabilities: capabilities,
            diagnostics: diagnostics
        )
        return (session, generation)
    }

    private func readySessionWithToken(
        capabilities: BandCapabilityReport =
            VirtualBandFixtures.capabilities,
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder(),
        identity: BandIdentity = VirtualBandFixtures.identity
    ) async throws -> (
        BandSessionMachine,
        UInt64,
        BandConnectionToken
    ) {
        let session = BandSessionMachine(diagnostics: diagnostics)
        let scanToken = try await session.beginScan()
        let generation = scanToken.generation
        let connectionToken = try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: scanToken
        )
        try await session.beginConnection(
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.beginAuthentication(
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.completeConnection(
            identity,
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.acceptCapabilities(
            capabilities,
            token: connectionToken,
            callbackGeneration: generation
        )
        return (session, generation, connectionToken)
    }

    private var firmwareCapabilities: BandCapabilityReport {
        let base = VirtualBandFixtures.capabilities
        return BandCapabilityReport(
            schemaVersion: base.schemaVersion,
            protocolVersion: base.protocolVersion,
            hardwareRevision: base.hardwareRevision,
            firmwareVersion: base.firmwareVersion,
            historyDays: base.historyDays,
            capabilities: base.capabilities.union([.firmwareUpdate]),
            liveStreams: base.liveStreams,
            historyStreams: base.historyStreams
        )
    }

    private func cancelledKinds(
        in recorder: BandDiagnosticsRecorder
    ) async -> [BandDiagnosticKind] {
        await recorder.snapshot()
            .filter { $0.outcome == .cancelled }
            .map(\.kind)
    }

    private func expectRedacted<T>(_ value: T, as name: String) {
        #expect(String(describing: value) == name)
        #expect(String(reflecting: value) == name)

        var dumpOutput = ""
        dump(value, to: &dumpOutput)
        #expect(dumpOutput.contains(name))
        #expect(!dumpOutput.contains("sessionNonce"))
        #expect(!dumpOutput.contains("generation"))
        #expect(!dumpOutput.contains("acceptedSamples"))
        #expect(!dumpOutput.contains("virtual-source"))
        #expect(!dumpOutput.contains("ack-1"))
        #expect(!dumpOutput.contains("72.0"))
        #expect(
            dumpOutput.range(
                of: #"[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}"#,
                options: .regularExpression
            ) == nil
        )
    }

    private func negotiatingSession(
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder()
    ) async throws -> (BandSessionMachine, UInt64) {
        let (session, generation, _) =
            try await negotiatingSessionWithToken(diagnostics: diagnostics)
        return (session, generation)
    }

    private func negotiatingSessionWithToken(
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder()
    ) async throws -> (
        BandSessionMachine,
        UInt64,
        BandConnectionToken
    ) {
        let session = BandSessionMachine(diagnostics: diagnostics)
        let scanToken = try await session.beginScan()
        let generation = scanToken.generation
        let connectionToken = try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: scanToken
        )
        try await session.beginConnection(
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.beginAuthentication(
            token: connectionToken,
            callbackGeneration: generation
        )
        try await session.completeConnection(
            VirtualBandFixtures.identity,
            token: connectionToken,
            callbackGeneration: generation
        )
        return (session, generation, connectionToken)
    }
}
