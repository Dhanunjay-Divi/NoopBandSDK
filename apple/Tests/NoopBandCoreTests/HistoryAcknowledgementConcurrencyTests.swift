import Foundation
import Testing
@testable import NoopBandCore

@Suite("History acknowledgement concurrency")
struct HistoryAcknowledgementConcurrencyTests {
    private struct SuspendedAcknowledgement {
        let session: BandSessionMachine
        let recorder: BandDiagnosticsRecorder
        let generation: UInt64
        let connectionToken: BandConnectionToken
        let operationToken: BandOperationToken
        let receipt: DurableHistoryReceipt
        let task: Task<Void, Error>
    }

    @Test("Reconnect remains busy during history acknowledgement completion")
    func reconnectRemainsBusyDuringCompletion() async throws {
        let context = try await suspendedAcknowledgement()

        await #expect(throws: BandFailureCategory.busy) {
            _ = try await context.session.interruptForReconnect(
                token: context.connectionToken,
                callbackGeneration: context.generation
            )
        }
        await expectPendingAuthority(context)
        #expect(
            await context.recorder.snapshot().last
                == BandDiagnosticEvent(
                    kind: .reconnect,
                    outcome: .rejected,
                    failureCategory: .busy
                )
        )

        try await resumeAndComplete(context)
        let reconnectToken = try await context.session.interruptForReconnect(
            token: context.connectionToken,
            callbackGeneration: context.generation
        )
        #expect(reconnectToken.generation == context.generation + 1)
    }

    @Test("Close remains busy during history acknowledgement completion")
    func closeRemainsBusyDuringCompletion() async throws {
        let context = try await suspendedAcknowledgement()

        await #expect(throws: BandFailureCategory.busy) {
            try await context.session.close()
        }
        await expectPendingAuthority(context)
        #expect(
            await context.recorder.snapshot().last
                == BandDiagnosticEvent(
                    kind: .history,
                    outcome: .rejected,
                    failureCategory: .busy
                )
        )

        try await resumeAndComplete(context)
        try await context.session.close()
        #expect(await context.session.snapshot().state == .closed)
    }

    @Test("Duplicate acknowledgement remains busy during completion")
    func duplicateAcknowledgementRemainsBusyDuringCompletion() async throws {
        let context = try await suspendedAcknowledgement()

        await #expect(throws: BandFailureCategory.busy) {
            try await context.session.acknowledgeHistory(
                receipt: context.receipt,
                token: context.operationToken,
                callbackGeneration: context.generation
            )
        }
        await expectPendingAuthority(context)
        #expect(
            await context.recorder.snapshot().last
                == BandDiagnosticEvent(
                    kind: .history,
                    outcome: .rejected,
                    failureCategory: .busy
                )
        )

        try await resumeAndComplete(context)
    }

    @Test("History acknowledgement completes after diagnostic suspension")
    func acknowledgementCompletesAfterDiagnosticSuspension() async throws {
        let context = try await suspendedAcknowledgement()

        await expectPendingAuthority(context)
        try await resumeAndComplete(context)
    }

    private func suspendedAcknowledgement()
        async throws -> SuspendedAcknowledgement
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
        let operationToken = try await session.beginOperation(.history)
        let acceptance = try await session.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            token: operationToken,
            callbackGeneration: generation
        )
        let receipt = DurableHistoryReceipt(
            acceptance: acceptance,
            historyStateCommitted: true,
            committedSamples: acceptance.acceptedSamples.count,
            committed: true
        )

        await recorder.requestNextRecordSuspensionForTesting()
        let task = Task {
            try await session.acknowledgeHistory(
                receipt: receipt,
                token: operationToken,
                callbackGeneration: generation
            )
        }
        await recorder.waitForRecordSuspensionForTesting()

        return SuspendedAcknowledgement(
            session: session,
            recorder: recorder,
            generation: generation,
            connectionToken: connectionToken,
            operationToken: operationToken,
            receipt: receipt,
            task: task
        )
    }

    private func expectPendingAuthority(
        _ context: SuspendedAcknowledgement
    ) async {
        let snapshot = await context.session.snapshot()
        #expect(snapshot.generation == context.generation)
        #expect(snapshot.state == .historyCollecting)
        #expect(snapshot.activeOperation == .history)
        #expect(
            snapshot.acknowledgedHistoryCursor
                == VirtualBandFixtures.historyChunk.nextCursor
        )
        #expect(
            snapshot.durableSampleCount == context.receipt.committedSamples
        )

        let checkpoint = await context.session.historyCheckpoint()
        #expect(
            checkpoint?.acknowledgedCursor
                == VirtualBandFixtures.historyChunk.nextCursor
        )
        #expect(checkpoint?.lastHistoryComplete == true)
        #expect(
            checkpoint?.durableSampleIdentities.count
                == context.receipt.committedSamples
        )
    }

    private func resumeAndComplete(
        _ context: SuspendedAcknowledgement
    ) async throws {
        await context.recorder.resumeSuspendedRecordForTesting()
        try await context.task.value

        let acknowledged = await context.session.snapshot()
        #expect(acknowledged.generation == context.generation)
        #expect(acknowledged.state == .historyCollecting)
        #expect(acknowledged.activeOperation == .history)
        #expect(
            acknowledged.acknowledgedHistoryCursor
                == VirtualBandFixtures.historyChunk.nextCursor
        )

        try await context.session.completeOperation(context.operationToken)
        let completed = await context.session.snapshot()
        #expect(completed.generation == context.generation)
        #expect(completed.state == .ready)
        #expect(completed.activeOperation == nil)
        #expect(
            completed.acknowledgedHistoryCursor
                == VirtualBandFixtures.historyChunk.nextCursor
        )
    }
}
