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
        let generation = try await session.beginScan()
        try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: generation
        )
        try await session.beginConnection(callbackGeneration: generation)
        try await session.beginAuthentication(callbackGeneration: generation)
        try await session.completeConnection(
            VirtualBandFixtures.identity,
            callbackGeneration: generation
        )
        try await session.acceptCapabilities(
            VirtualBandFixtures.capabilities,
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
        let replacementGeneration = try await replacement.beginScan()
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
            acknowledgementToken: "value-semantics",
            batches: batches
        )
        batches.removeAll()
        #expect(chunk.batches.count == 1)
        #expect(chunk.batches.first?.samples.count == 1)
        try chunk.validate()
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
        let (cancelledSession, cancelledGeneration) =
            try await negotiatingSession(diagnostics: cancellationRecorder)

        try await cancelledSession.cancelCapabilities(
            callbackGeneration: cancelledGeneration
        )
        let cancelled = await cancelledSession.snapshot()
        #expect(cancelled.state == .idle)
        #expect(cancelled.generation == cancelledGeneration + 1)
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await cancelledSession.cancelCapabilities(
                callbackGeneration: cancelledGeneration
            )
        }
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await cancelledSession.failCapabilities(
                .timeout,
                callbackGeneration: cancelledGeneration
            )
        }
        let retryGeneration = try await cancelledSession.beginScan()
        #expect(retryGeneration == cancelled.generation + 1)
        try await cancelledSession.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: retryGeneration
        )
        try await cancelledSession.beginConnection(
            callbackGeneration: retryGeneration
        )
        try await cancelledSession.beginAuthentication(
            callbackGeneration: retryGeneration
        )
        try await cancelledSession.completeConnection(
            VirtualBandFixtures.identity,
            callbackGeneration: retryGeneration
        )
        await #expect(throws: BandFailureCategory.staleCallback) {
            try await cancelledSession.cancelCapabilities(
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
        let (timedOutSession, timedOutGeneration) =
            try await negotiatingSession(diagnostics: timeoutRecorder)
        try await timedOutSession.failCapabilities(
            .timeout,
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

        let (invalidSession, invalidGeneration) =
            try await negotiatingSession()
        await #expect(throws: BandFailureCategory.invalidInput) {
            try await invalidSession.failCapabilities(
                .storage,
                callbackGeneration: invalidGeneration
            )
        }
        let unchanged = await invalidSession.snapshot()
        #expect(unchanged.state == .negotiatingCapabilities)
        #expect(unchanged.generation == invalidGeneration)

        let (authenticationSession, authenticationGeneration) =
            try await negotiatingSession()
        try await authenticationSession.failCapabilities(
            .authentication,
            callbackGeneration: authenticationGeneration
        )
        #expect(await authenticationSession.snapshot().state == .rejected)

        let (securitySession, securityGeneration) =
            try await negotiatingSession()
        try await securitySession.failCapabilities(
            .securityFailure,
            callbackGeneration: securityGeneration
        )
        #expect(await securitySession.snapshot().state == .securityFailure)

        let (disconnectedSession, disconnectedGeneration) =
            try await negotiatingSession()
        try await disconnectedSession.failCapabilities(
            .disconnected,
            callbackGeneration: disconnectedGeneration
        )
        #expect(await disconnectedSession.snapshot().state == .recovering)
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
        let retryGeneration = try await session.beginScan()
        #expect(retryGeneration == failed.generation + 1)
    }

    @Test("Pending persistence blocks lifecycle terminals until drained")
    func pendingPersistenceBlocksLifecycleTerminals() async throws {
        let liveRecorder = BandDiagnosticsRecorder()
        let (liveSession, liveGeneration) =
            try await readySession(diagnostics: liveRecorder)
        try await liveSession.beginLive()
        let liveAcceptance = try await liveSession.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
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
        let (historySession, historyGeneration) =
            try await readySession(diagnostics: historyRecorder)
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
                committedSamples: historyAcceptance.acceptedSamples,
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
            failureCategory: .busy
        )))
        #expect(liveEvents.contains(BandDiagnosticEvent(
            kind: .reconnect,
            outcome: .rejected,
            failureCategory: .busy
        )))
        #expect(liveEvents.contains(BandDiagnosticEvent(
            kind: .connection,
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
        try await liveFirst.beginLive()
        let liveAcceptance = try await liveFirst.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
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
        let historyAfterLive = try await liveFirst.stageHistoryChunk(
            overlappingHistory,
            token: liveFirstHistoryToken,
            callbackGeneration: liveGeneration
        )
        #expect(historyAfterLive.acceptedSamples == 0)
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
        try await historyFirst.beginLive()
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
                callbackGeneration: historyGeneration
            )
        }
        try await historyFirst.acknowledgeHistory(
            receipt: DurableHistoryReceipt(
                acceptance: historyAcceptance,
                historyStateCommitted: true,
                committedSamples: historyAcceptance.acceptedSamples,
                committed: true
            ),
            token: historyFirstToken,
            callbackGeneration: historyGeneration
        )
        let liveAfterHistory = try await historyFirst.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
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
            committedSamples: acceptance.acceptedSamples,
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
                committedSamples: acceptance.acceptedSamples,
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
                committedSamples: cancellationAcceptance.acceptedSamples,
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
    }

    @Test("Unknown conformance scenarios fail closed")
    func unknownScenarioFailsClosed() async {
        await #expect(throws: BandFailureCategory.invalidInput) {
            try await BandConformanceRunner.run("not-a-scenario")
        }
    }

    private func readySession(
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder()
    ) async throws -> (BandSessionMachine, UInt64) {
        let session = BandSessionMachine(diagnostics: diagnostics)
        let generation = try await session.beginScan()
        try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: generation
        )
        try await session.beginConnection(callbackGeneration: generation)
        try await session.beginAuthentication(callbackGeneration: generation)
        try await session.completeConnection(
            VirtualBandFixtures.identity,
            callbackGeneration: generation
        )
        try await session.acceptCapabilities(
            VirtualBandFixtures.capabilities,
            callbackGeneration: generation
        )
        return (session, generation)
    }

    private func negotiatingSession(
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder()
    ) async throws -> (BandSessionMachine, UInt64) {
        let session = BandSessionMachine(diagnostics: diagnostics)
        let generation = try await session.beginScan()
        try await session.selectCandidate(
            VirtualBandFixtures.candidate,
            callbackGeneration: generation
        )
        try await session.beginConnection(callbackGeneration: generation)
        try await session.beginAuthentication(callbackGeneration: generation)
        try await session.completeConnection(
            VirtualBandFixtures.identity,
            callbackGeneration: generation
        )
        return (session, generation)
    }
}
