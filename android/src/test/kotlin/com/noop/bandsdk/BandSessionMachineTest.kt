package com.noop.bandsdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BandSessionMachineTest {
    private class SingleTraversalList<T>(
        private val values: List<T>,
    ) : AbstractList<T>() {
        private var traversals = 0

        override val size: Int
            get() = values.size

        override fun get(index: Int): T = values[index]

        override fun iterator(): Iterator<T> {
            check(traversals++ == 0) {
                "caller-owned collection was traversed more than once"
            }
            return values.iterator()
        }
    }

    @Test
    fun deterministicScenariosAreStable() {
        BandConformanceRunner.automatedScenarios.forEach { scenario ->
            val first = BandConformanceRunner.run(scenario)
            val second = BandConformanceRunner.run(scenario)
            assertEquals(first, second)
            assertEquals(scenario, first.scenario)
        }
    }

    @Test
    fun historyRequiresDurableReceiptBeforeCursorAdvance() {
        val result = BandConformanceRunner.run(
            "history_requires_durable_receipt",
        )
        assertEquals(BandFailureCategory.STORAGE.wireValue, result.failure)
        assertEquals("cursor-2", result.acknowledgedCursor)
        assertTrue(
            result.events.indexOf("ack_rejected") <
                result.events.indexOf("history_committed"),
        )
        assertTrue(
            result.events.indexOf("history_committed") <
                result.events.indexOf("history_acknowledged"),
        )
    }

    @Test
    fun liveDeliveryDoesNotAdvanceHistory() {
        val result = BandConformanceRunner.run(
            "live_does_not_advance_history",
        )
        assertNull(result.acknowledgedCursor)
        assertEquals(1, result.acceptedSamples)
    }

    @Test
    fun duplicateLiveIdentitiesAreAcceptedOnce() {
        val result = BandConformanceRunner.run("live_batch_deduplicated")
        assertEquals(1, result.acceptedSamples)
    }

    @Test
    fun historyCursorChainsFailClosed() {
        val result = BandConformanceRunner.run(
            "history_cursor_chain_rejected",
        )
        assertEquals(
            BandFailureCategory.HISTORY_STALLED.wireValue,
            result.failure,
        )
        assertEquals("cursor-2", result.acknowledgedCursor)
    }

    @Test
    fun operationCapabilitiesAreOwnedByTheCore() {
        val result = BandConformanceRunner.run(
            "operation_capability_fail_closed",
        )
        assertEquals(
            BandFailureCategory.UNSUPPORTED.wireValue,
            result.failure,
        )
    }

    @Test
    fun oversizedMetadataIsRejected() {
        val result = BandConformanceRunner.run("oversized_metadata_rejected")
        assertEquals(
            BandFailureCategory.INVALID_INPUT.wireValue,
            result.failure,
        )
        assertEquals(0, result.acceptedSamples)
    }

    @Test
    fun durableHistoryCheckpointRestores() {
        val result = BandConformanceRunner.run("history_checkpoint_restored")
        assertNull(result.failure)
        assertEquals("cursor-3", result.acknowledgedCursor)
        assertEquals(1, result.acceptedSamples)
    }

    @Test
    fun firmwareEligibilityIsSpecific() {
        val result = BandConformanceRunner.run("firmware_eligibility_specific")
        assertEquals(
            BandFailureCategory.UPDATE_NOT_ELIGIBLE.wireValue,
            result.failure,
        )
    }

    @Test
    fun unnegotiatedStreamsFailClosed() {
        val result = BandConformanceRunner.run("unnegotiated_stream_rejected")
        assertEquals(BandFailureCategory.UNSUPPORTED.wireValue, result.failure)
        assertEquals(0, result.acceptedSamples)
    }

    @Test
    fun firmwareCannotOverlapLiveCollection() {
        val result = BandConformanceRunner.run("firmware_blocked_during_live")
        assertEquals(BandFailureCategory.BUSY.wireValue, result.failure)
    }

    @Test
    fun historyStateRequiresDurableReceipt() {
        val result = BandConformanceRunner.run(
            "history_state_requires_durable_receipt",
        )
        assertEquals(
            BandFailureCategory.HISTORY_STALLED.wireValue,
            result.failure,
        )
        assertEquals("cursor-3", result.acknowledgedCursor)
        assertTrue("receipt_rejected" in result.events)
    }

    @Test
    fun maximumSignedSequenceIsAccepted() {
        val sample = VirtualBandFixtures.liveBatch.samples.first().copy(
            identity = VirtualBandFixtures.liveBatch.samples.first().identity.copy(
                sequence = BandContractLimits.MAXIMUM_SAMPLE_SEQUENCE,
            ),
        )
        sample.validate()
    }

    @Test
    fun negativeSequencesAreRejected() {
        val sample = VirtualBandFixtures.liveBatch.samples.first().copy(
            identity = VirtualBandFixtures.liveBatch.samples.first().identity.copy(
                sequence = -1,
            ),
        )
        val error = assertFailsWith<BandException> {
            sample.validate()
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
    }

    @Test
    fun capabilityCallbacksAreGenerationFenced() {
        val result = BandConformanceRunner.run(
            "stale_capability_callback_rejected",
        )
        assertEquals(
            BandFailureCategory.STALE_CALLBACK.wireValue,
            result.failure,
        )
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
    }

    @Test
    fun receiptsAndTokensAreSessionBound() {
        val result = BandConformanceRunner.run(
            "cross_session_credentials_rejected",
        )
        assertEquals(
            BandFailureCategory.STALE_CALLBACK.wireValue,
            result.failure,
        )
        assertTrue("foreign_live_receipt_rejected" in result.events)
        assertTrue("foreign_history_receipt_rejected" in result.events)
        assertTrue("foreign_operation_token_rejected" in result.events)
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
    }

    @Test
    fun credentialIssuanceCannotReplay() {
        val result = BandConformanceRunner.run(
            "same_session_replay_rejected",
        )
        assertEquals(BandFailureCategory.STORAGE.wireValue, result.failure)
        assertTrue("stale_live_receipt_rejected" in result.events)
        assertTrue("stale_operation_token_rejected" in result.events)
        assertTrue("stale_history_receipt_rejected" in result.events)
        assertEquals("cursor-3", result.acknowledgedCursor)
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
    }

    @Test
    fun terminalCallbacksAreGenerationFenced() {
        val result = BandConformanceRunner.run(
            "stale_terminal_callbacks_rejected",
        )
        assertEquals(
            BandFailureCategory.STALE_CALLBACK.wireValue,
            result.failure,
        )
        assertTrue("stale_scan_cancel_rejected" in result.events)
        assertTrue("stale_scan_failure_rejected" in result.events)
        assertTrue("stale_reconnect_interrupt_rejected" in result.events)
        assertTrue("stale_reconnect_completion_rejected" in result.events)
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
    }

    @Test
    fun deviceTimeRejectsNegativeSamplesAndCheckpoints() {
        val result = BandConformanceRunner.run(
            "invalid_device_time_rejected",
        )
        assertEquals(
            BandFailureCategory.INVALID_INPUT.wireValue,
            result.failure,
        )

        val error = assertFailsWith<BandException> {
            BandHistoryCheckpoint(
                sourceIdentity = "source",
                acknowledgedCursor = null,
                lastHistoryComplete = null,
                durableSampleIdentities = setOf(
                    BandSampleIdentity(
                        stream = BandStreamKind.HEART_RATE,
                        sequence = 1,
                        deviceTimeMilliseconds = -1,
                    ),
                ),
            ).validate()
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
    }

    @Test
    fun eachHistoryOperationRequiresItsOwnDurableReceipt() {
        val result = BandConformanceRunner.run(
            "history_operation_requires_own_receipt",
        )
        assertEquals(BandFailureCategory.STORAGE.wireValue, result.failure)
        assertEquals("operation_cancelled", result.events.last())
    }

    @Test
    fun firmwareLifecycleUsesFirmwareDiagnostics() {
        val result = BandConformanceRunner.run(
            "firmware_diagnostics_specific",
        )
        assertEquals("firmware_diagnostics_specific", result.events.last())
    }

    @Test
    fun incompleteHistoryRequiresCursorProgress() {
        val result = BandConformanceRunner.run(
            "history_nonadvancing_cursor_rejected",
        )
        assertEquals(
            BandFailureCategory.HISTORY_STALLED.wireValue,
            result.failure,
        )
    }

    @Test
    fun boundedStringsUseUtf8ByteLength() {
        val result = BandConformanceRunner.run("utf8_length_cross_platform")
        assertEquals(
            BandFailureCategory.INVALID_INPUT.wireValue,
            result.failure,
        )
        val malformed = assertFailsWith<BandException> {
            BandPairingCandidate(
                handle = "\uD800",
                compatible = true,
                identifyEligible = true,
            ).validate()
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, malformed.category)
    }

    @Test
    fun samplingRequiresNegotiatedSensorCapability() {
        val result = BandConformanceRunner.run(
            "sampling_requires_sensor_capability",
        )
        assertEquals(
            BandFailureCategory.UNSUPPORTED.wireValue,
            result.failure,
        )
    }

    @Test
    fun recentDurableIdentitiesRemainBoundedInMemory() {
        val result = BandConformanceRunner.run(
            "durable_identity_cache_bounded",
        )
        assertTrue("identity_cache_bounded" in result.events)
        assertEquals(2, result.acceptedSamples)
    }

    @Test
    fun activeOperationsSupportCancellationAndFailureTerminals() {
        val result = BandConformanceRunner.run("operation_terminal_paths")
        assertTrue("operation_cancelled" in result.events)
        assertTrue("operation_failed" in result.events)
        assertTrue("disconnect_recovery" in result.events)
        assertTrue("stale_callback_rejected" in result.events)
        assertTrue("security_failure_terminal" in result.events)
        assertTrue("security_failure_stale_token_rejected" in result.events)
        assertTrue("security_failure_restart_rejected" in result.events)
        assertEquals("replacement_session_ready", result.events.last())
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
    }

    @Test
    fun securityFailureRequiresReplacementSessionObject() {
        val (session, _) = readySession()
        val token = session.beginOperation(BandOperationClass.BATTERY)
        session.failOperation(token, BandFailureCategory.SECURITY_FAILURE)
        val terminal = session.snapshot()

        assertEquals(BandSessionState.SECURITY_FAILURE, terminal.state)
        val error = assertFailsWith<BandException> {
            session.beginScan()
        }
        assertEquals(BandFailureCategory.INVALID_STATE, error.category)
        val unchanged = session.snapshot()
        assertEquals(BandSessionState.SECURITY_FAILURE, unchanged.state)
        assertEquals(terminal.generation, unchanged.generation)

        val replacement = BandSessionMachine()
        val replacementGeneration = replacement.beginScan()
        val replacementSnapshot = replacement.snapshot()
        assertEquals(1, replacementGeneration)
        assertEquals(BandSessionState.SCANNING, replacementSnapshot.state)
        assertEquals(replacementGeneration, replacementSnapshot.generation)
    }

    @Test
    fun liveBatchSnapshotsCallerOwnedSamplesBeforeProcessing() {
        val (session, generation) = readySession()
        val first = VirtualBandFixtures.liveBatch.samples.first()
        val second = first.copy(
            identity = first.identity.copy(sequence = first.identity.sequence + 1),
        )
        val batch = VirtualBandFixtures.liveBatch.copy(
            samples = SingleTraversalList(listOf(first, second)),
        )
        session.beginLive()

        val acceptance = session.stageLiveBatch(batch, generation)

        assertEquals(2, acceptance.acceptedSamples.size)
        assertEquals(0, acceptance.duplicateSamples)
    }

    @Test
    fun historyChunkDeepSnapshotsCallerOwnedCollectionsBeforeProcessing() {
        val (session, generation) = readySession()
        val token = session.beginOperation(BandOperationClass.HISTORY)
        val originalBatch = VirtualBandFixtures.historyChunk.batches.first()
        val first = originalBatch.samples.first()
        val second = first.copy(
            identity = first.identity.copy(sequence = first.identity.sequence + 1),
        )
        val batch = originalBatch.copy(
            samples = SingleTraversalList(listOf(first, second)),
        )
        val chunk = VirtualBandFixtures.historyChunk.copy(
            batches = SingleTraversalList(listOf(batch)),
        )

        val acceptance = session.stageHistoryChunk(chunk, token, generation)

        assertEquals(2, acceptance.acceptedSamples)
        assertEquals(0, acceptance.duplicateSamples)
    }

    @Test
    fun immutableSnapshotsDoNotRetainMutableCallerCollections() {
        val samples = VirtualBandFixtures.liveBatch.samples.toMutableList()
        val expectedSampleCount = samples.size
        val batch = VirtualBandFixtures.liveBatch.copy(samples = samples)
        val batchSnapshot = batch.immutableSnapshot()
        samples.clear()
        assertEquals(expectedSampleCount, batchSnapshot.samples.size)

        val nestedSamples =
            VirtualBandFixtures.historyChunk.batches.first().samples.toMutableList()
        val expectedNestedSampleCount = nestedSamples.size
        val batches = mutableListOf(
            VirtualBandFixtures.historyChunk.batches.first().copy(
                samples = nestedSamples,
            ),
        )
        val expectedBatchCount = batches.size
        val chunkSnapshot =
            VirtualBandFixtures.historyChunk.copy(batches = batches)
                .immutableSnapshot()
        nestedSamples.clear()
        batches.clear()
        assertEquals(expectedBatchCount, chunkSnapshot.batches.size)
        assertEquals(
            expectedNestedSampleCount,
            chunkSnapshot.batches.first().samples.size,
        )
    }

    @Test
    fun connectionCompletionCallbacksAreGenerationFenced() {
        val result = BandConformanceRunner.run(
            "connection_callbacks_generation_fenced",
        )
        assertEquals(
            BandFailureCategory.STALE_CALLBACK.wireValue,
            result.failure,
        )
        assertTrue("connection_failed" in result.events)
        assertTrue("stale_cancel_rejected" in result.events)
        assertTrue("stale_failure_rejected" in result.events)
        assertTrue("stale_terminals_preserved_state" in result.events)
        assertTrue("stale_connection_rejected" in result.events)
        assertTrue("stale_phases_preserved" in result.events)
        assertTrue("connection_diagnostics_bounded" in result.events)
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
    }

    @Test
    fun connectionAndAuthenticationHaveExplicitTerminals() {
        val result = BandConformanceRunner.run("connection_terminal_paths")
        assertEquals(
            BandFailureCategory.SECURITY_FAILURE.wireValue,
            result.failure,
        )
        assertTrue("connection_cancelled" in result.events)
        assertTrue("authentication_rejected" in result.events)
        assertTrue("security_failure" in result.events)
        assertTrue("security_failure_restart_rejected" in result.events)
        assertTrue("replacement_session_ready" in result.events)
        assertTrue("connection_diagnostics_bounded" in result.events)
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
    }

    @Test
    fun pendingHistoryRejectionRecordsBoundedBusyEvent() {
        val result = BandConformanceRunner.run(
            "history_pending_busy_diagnostics",
        )
        assertEquals(BandFailureCategory.BUSY.wireValue, result.failure)
        assertTrue("second_chunk_rejected" in result.events)
        assertTrue("history_busy_recorded" in result.events)
        assertEquals("operation_cancelled", result.events.last())
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
    }

    @Test
    fun diagnosticsAreBoundedAndStructurallyRedacted() {
        val recorder = BandDiagnosticsRecorder(2)
        recorder.record(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.COMPLETED,
            ),
        )
        recorder.record(
            BandDiagnosticEvent(
                BandDiagnosticKind.HISTORY,
                BandDiagnosticOutcome.FAILED,
            ),
        )
        recorder.record(
            BandDiagnosticEvent(
                BandDiagnosticKind.COMMAND,
                BandDiagnosticOutcome.REJECTED,
            ),
        )
        val events = recorder.snapshot()
        assertEquals(2, events.size)
        assertEquals(BandDiagnosticKind.HISTORY, events.first().kind)
        val text = events.toString()
        assertFalse(text.contains("virtual-source"))
        assertFalse(text.contains("72"))
        assertFalse(text.contains("ack-1"))
    }

    @Test
    fun unknownScenarioFailsClosed() {
        val error = assertFailsWith<BandException> {
            BandConformanceRunner.run("not-a-scenario")
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
    }

    private fun readySession(): Pair<BandSessionMachine, Long> {
        val session = BandSessionMachine()
        val generation = session.beginScan()
        session.selectCandidate(VirtualBandFixtures.candidate, generation)
        session.beginConnection(generation)
        session.beginAuthentication(generation)
        session.completeConnection(VirtualBandFixtures.identity, generation)
        session.acceptCapabilities(VirtualBandFixtures.capabilities, generation)
        return session to generation
    }
}
