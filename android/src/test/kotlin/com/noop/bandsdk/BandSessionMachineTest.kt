package com.noop.bandsdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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

    private class IterationForbiddenList<T>(
        override val size: Int,
    ) : AbstractList<T>() {
        var iterationAttempted = false

        override fun get(index: Int): T =
            error("bounded snapshot must reject before indexed access")

        override fun iterator(): Iterator<T> {
            iterationAttempted = true
            error("bounded snapshot must reject before iteration")
        }
    }

    private class MisreportedList<T>(
        private val values: List<T>,
        override val size: Int,
    ) : AbstractList<T>() {
        override fun get(index: Int): T = values[index]
        override fun iterator(): Iterator<T> = values.iterator()
    }

    private class TraversalFailureList<T>(
        private val value: T,
    ) : AbstractList<T>() {
        override val size: Int = 1
        override fun get(index: Int): T = value
        override fun iterator(): Iterator<T> = object : Iterator<T> {
            override fun hasNext(): Boolean = true
            override fun next(): T = throw ConcurrentModificationException()
        }
    }

    private class SizeFailureList<T> : AbstractList<T>() {
        override val size: Int
            get() = throw ConcurrentModificationException()

        override fun get(index: Int): T =
            error("size failure must reject before indexed access")

        override fun iterator(): Iterator<T> =
            error("size failure must reject before iteration")
    }

    private class SizeFailureSet<T> : AbstractSet<T>() {
        override val size: Int
            get() = throw ConcurrentModificationException()

        override fun iterator(): Iterator<T> =
            error("size failure must reject before iteration")
    }

    private class IterationForbiddenSet<T>(
        override val size: Int,
    ) : AbstractSet<T>() {
        var iterationAttempted = false

        override fun iterator(): Iterator<T> {
            iterationAttempted = true
            error("bounded snapshot must reject before iteration")
        }
    }

    private class TraversalFailureSet<T>(
        private val value: T,
    ) : AbstractSet<T>() {
        override val size: Int = 1

        override fun iterator(): Iterator<T> = object : Iterator<T> {
            override fun hasNext(): Boolean = true
            override fun next(): T = throw ConcurrentModificationException()
        }
    }

    private class CategorizedSizeFailureList<T>(
        private val category: BandFailureCategory,
    ) : AbstractList<T>() {
        override val size: Int
            get() = throw BandException(category)

        override fun get(index: Int): T =
            error("size failure must reject before indexed access")

        override fun iterator(): Iterator<T> =
            error("size failure must reject before iteration")
    }

    private class CategorizedTraversalFailureList<T>(
        private val value: T,
        private val category: BandFailureCategory,
    ) : AbstractList<T>() {
        override val size: Int = 1
        override fun get(index: Int): T = value
        override fun iterator(): Iterator<T> = object : Iterator<T> {
            override fun hasNext(): Boolean = true
            override fun next(): T = throw BandException(category)
        }
    }

    private class CategorizedSizeFailureSet<T>(
        private val category: BandFailureCategory,
    ) : AbstractSet<T>() {
        override val size: Int
            get() = throw BandException(category)

        override fun iterator(): Iterator<T> =
            error("size failure must reject before iteration")
    }

    private class CategorizedTraversalFailureSet<T>(
        private val value: T,
        private val category: BandFailureCategory,
    ) : AbstractSet<T>() {
        override val size: Int = 1
        override fun iterator(): Iterator<T> = object : Iterator<T> {
            override fun hasNext(): Boolean = true
            override fun next(): T = throw BandException(category)
        }
    }

    private class MutationDuringTraversalSet<T>(
        private val initialValue: T,
        private val addedValue: T,
    ) : AbstractSet<T>() {
        override val size: Int = 1

        override fun iterator(): Iterator<T> =
            listOf(initialValue, addedValue).iterator()
    }

    private class RepeatingIteratorSet<T>(
        private val value: T,
        private val repeatCount: Int,
    ) : AbstractSet<T>() {
        override val size: Int = 1

        override fun iterator(): Iterator<T> = object : Iterator<T> {
            private var emitted = 0

            override fun hasNext(): Boolean = emitted < repeatCount

            override fun next(): T {
                emitted += 1
                return value
            }
        }
    }

    private class ReentrantTraversalList<T>(
        private val values: List<T>,
        private val onFirstElement: () -> Unit,
    ) : AbstractList<T>() {
        override val size: Int
            get() = values.size

        override fun get(index: Int): T = values[index]

        override fun iterator(): Iterator<T> {
            val delegate = values.iterator()
            var invoked = false
            return object : Iterator<T> {
                override fun hasNext(): Boolean = delegate.hasNext()

                override fun next(): T {
                    val value = delegate.next()
                    if (!invoked) {
                        invoked = true
                        onFirstElement()
                    }
                    return value
                }
            }
        }
    }

    private class ReentrantTraversalSet<T>(
        private val values: Set<T>,
        private val onFirstElement: () -> Unit,
    ) : AbstractSet<T>() {
        override val size: Int
            get() = values.size

        override fun iterator(): Iterator<T> {
            val delegate = values.iterator()
            var invoked = false
            return object : Iterator<T> {
                override fun hasNext(): Boolean = delegate.hasNext()

                override fun next(): T {
                    val value = delegate.next()
                    if (!invoked) {
                        invoked = true
                        onFirstElement()
                    }
                    return value
                }
            }
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
    fun liveCallbackSessionConformanceRejectsForeignToken() {
        val result = BandConformanceRunner.run(
            "live_callback_session_bound",
        )
        assertEquals(
            listOf(
                "ready_pair",
                "foreign_live_callback_rejected",
                "own_live_callback_accepted",
            ),
            result.events,
        )
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
        assertEquals(1, result.acceptedSamples)
        assertEquals(
            BandFailureCategory.STALE_CALLBACK.wireValue,
            result.failure,
        )
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
    fun restoredCheckpointSurvivesAnInterveningSource() {
        val result = BandConformanceRunner.run(
            "history_checkpoint_survives_source_mismatch",
        )
        assertNull(result.failure)
        assertEquals("cursor-2", result.acknowledgedCursor)
        assertEquals(2, result.acceptedSamples)
    }

    @Test
    fun gracefulDisconnectReturnsAReusableIdleSession() {
        val result = BandConformanceRunner.run("graceful_disconnect_to_idle")
        assertEquals(
            BandFailureCategory.STALE_CALLBACK.wireValue,
            result.failure,
        )
        assertEquals(BandSessionState.IDLE.wireValue, result.finalState)
        assertTrue("session_reusable" in result.events)
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
    fun scanCallbacksAreBoundToTheIssuingSession() {
        val result = BandConformanceRunner.run(
            "scan_callback_session_bound",
        )
        assertEquals(
            listOf(
                "scan_pair",
                "foreign_select_rejected",
                "foreign_cancel_rejected",
                "foreign_failure_rejected",
                "current_scan_preserved",
                "own_select_accepted",
                "current_session_ready",
            ),
            result.events,
        )
        assertEquals(
            BandFailureCategory.STALE_CALLBACK.wireValue,
            result.failure,
        )
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
    }

    @Test
    fun scanCallbacksAreConsumedAfterSelection() {
        val result = BandConformanceRunner.run(
            "scan_callback_consumed_after_selection",
        )
        assertEquals(
            listOf(
                "scan_started",
                "candidate_selected",
                "late_select_rejected",
                "late_cancel_rejected",
                "late_failure_rejected",
                "connection_preserved",
                "current_session_ready",
            ),
            result.events,
        )
        assertEquals(
            BandFailureCategory.STALE_CALLBACK.wireValue,
            result.failure,
        )
        assertEquals(BandSessionState.READY.wireValue, result.finalState)
    }

    @Test
    fun scanTokenStringRenderingIsRedacted() {
        val token = BandSessionMachine().beginScan()
        assertEquals("BandScanToken", token.toString())
    }

    @Test
    fun tokensAcceptancesAndReceiptsUseRedactedStrings() {
        val (session, generation, connectionToken) = readySessionWithToken()
        val liveToken = session.beginLive()
        val liveAcceptance = session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            liveToken,
            generation,
        )
        val liveReceipt = DurableLiveReceipt(
            acceptance = liveAcceptance,
            committedSamples = liveAcceptance.acceptedSamples.size,
            committed = true,
        )
        session.acknowledgeLive(liveReceipt, generation)
        session.stopLive(liveToken)

        val operationToken =
            session.beginOperation(BandOperationClass.HISTORY)
        val historyAcceptance = session.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            operationToken,
            generation,
        )
        val historyReceipt = DurableHistoryReceipt(
            acceptance = historyAcceptance,
            historyStateCommitted = true,
            committedSamples = historyAcceptance.acceptedSamples.size,
            committed = true,
        )
        val (
            reconnectSession,
            reconnectGeneration,
            reconnectConnectionToken,
        ) = readySessionWithToken()
        val reconnectToken = reconnectSession.interruptForReconnect(
            reconnectConnectionToken,
            reconnectGeneration,
        )

        assertEquals("BandConnectionToken", connectionToken.toString())
        assertEquals("BandReconnectToken", reconnectToken.toString())
        assertEquals("BandLiveToken", liveToken.toString())
        assertEquals("BandOperationToken", operationToken.toString())
        assertEquals("LiveAcceptance", liveAcceptance.toString())
        assertEquals(
            "AcceptedHistorySample",
            historyAcceptance.acceptedSamples.first().toString(),
        )
        assertEquals("DurableLiveReceipt", liveReceipt.toString())
        assertEquals("HistoryAcceptance", historyAcceptance.toString())
        assertEquals("DurableHistoryReceipt", historyReceipt.toString())
    }

    @Test
    fun scanTokenRequiresExactIssuedObject() {
        val session = BandSessionMachine()
        val issued = session.beginScan()
        val forged = BandScanToken(
            sessionNonce = issued.sessionNonce,
            generation = issued.generation,
        )

        val error = assertFailsWith<BandException> {
            session.cancelScan(forged)
        }
        assertEquals(BandFailureCategory.STALE_CALLBACK, error.category)
        assertEquals(BandSessionState.SCANNING, session.snapshot().state)

        session.cancelScan(issued)
        assertEquals(BandSessionState.IDLE, session.snapshot().state)
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
    fun reconnectCallbacksAreSessionBound() {
        val result = BandConformanceRunner.run(
            "reconnect_callback_session_bound",
        )
        assertEquals(
            listOf(
                "ready_pair",
                "foreign_interrupt_rejected",
                "current_live_preserved",
                "live_interruption_ordered",
                "foreign_resume_rejected",
                "current_recovery_preserved",
                "own_resume_accepted",
            ),
            result.events,
        )
        assertEquals(
            BandFailureCategory.STALE_CALLBACK.wireValue,
            result.failure,
        )
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
    fun unrecoverableFirmwareFailureIsTerminal() {
        val result = BandConformanceRunner.run("firmware_terminal_failure")
        assertEquals(
            BandSessionState.FIRMWARE_FAILURE.wireValue,
            result.finalState,
        )
        assertEquals(
            BandFailureCategory.INVALID_STATE.wireValue,
            result.failure,
        )
        assertEquals("replacement_scan_started", result.events.last())
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
        val replacementScanToken = replacement.beginScan()
        val replacementGeneration = replacementScanToken.generation
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
        val liveToken = session.beginLive()

        val acceptance = session.stageLiveBatch(
            batch,
            liveToken,
            generation,
        )

        assertEquals(2, acceptance.acceptedSamples.size)
        assertEquals(0, acceptance.duplicateSamples)
    }

    @Test
    fun liveCallbackTokensAreBoundToTheirSessionInstance() {
        val firstRecorder = BandDiagnosticsRecorder()
        val secondRecorder = BandDiagnosticsRecorder()
        val (first, firstGeneration) = readySession(firstRecorder)
        val (second, secondGeneration) = readySession(secondRecorder)
        assertEquals(firstGeneration, secondGeneration)

        val firstToken = first.beginLive()
        val secondToken = second.beginLive()
        val beforeFailure = secondRecorder.snapshot()
        val rejected = assertFailsWith<BandException> {
            second.stageLiveBatch(
                VirtualBandFixtures.liveBatch,
                firstToken,
                secondGeneration,
            )
        }
        assertEquals(BandFailureCategory.STALE_CALLBACK, rejected.category)
        assertEquals(
            beforeFailure + BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.STALE,
                failureCategory = BandFailureCategory.STALE_CALLBACK,
            ),
            secondRecorder.snapshot(),
        )
        assertEquals(BandSessionState.LIVE_COLLECTING, second.snapshot().state)

        val acceptance = second.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            secondToken,
            secondGeneration,
        )
        assertEquals(1, acceptance.acceptedSamples.size)
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

        assertEquals(
            listOf(first, second),
            acceptance.acceptedSamples.map(AcceptedHistorySample::sample),
        )
        assertEquals(0, acceptance.duplicateSamples)
    }

    @Test
    fun historyAcceptanceReturnsOnlyRowsStorageMayPersist() {
        val (session, generation) = readySession()
        val store = VirtualBandStore()
        val duplicate = VirtualBandFixtures.liveBatch.samples.first()
        val fresh = duplicate.copy(
            identity = duplicate.identity.copy(
                sequence = duplicate.identity.sequence + 100,
                deviceTimeMilliseconds =
                    duplicate.identity.deviceTimeMilliseconds + 1_000,
            ),
            value = duplicate.value + 1,
        )
        val secondFresh = duplicate.copy(
            identity = duplicate.identity.copy(
                sequence = duplicate.identity.sequence + 101,
                deviceTimeMilliseconds =
                    duplicate.identity.deviceTimeMilliseconds + 2_000,
            ),
            value = duplicate.value + 2,
        )
        val liveToken = session.beginLive()
        val liveAcceptance = session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            liveToken,
            generation,
        )
        session.acknowledgeLive(store.commit(liveAcceptance), generation)
        session.stopLive(liveToken)

        val token = session.beginOperation(BandOperationClass.HISTORY)
        val sourceBatch = VirtualBandFixtures.historyChunk.batches.first()
        val chunk = VirtualBandFixtures.historyChunk.copy(
            retainedRange = BandHistoryRange(
                startDeviceTimeMilliseconds =
                    duplicate.identity.deviceTimeMilliseconds,
                endDeviceTimeMilliseconds =
                    secondFresh.identity.deviceTimeMilliseconds,
            ),
            batches = listOf(
                sourceBatch.copy(
                    samples = listOf(duplicate, fresh),
                ),
                sourceBatch.copy(
                    samples = listOf(fresh, secondFresh),
                ),
            ),
        )
        val acceptance = session.stageHistoryChunk(chunk, token, generation)

        assertEquals(
            listOf(fresh, secondFresh),
            acceptance.acceptedSamples.map(AcceptedHistorySample::sample),
        )
        assertEquals(
            sourceBatch.sourceIdentity,
            acceptance.acceptedSamples[0].sourceIdentity,
        )
        assertEquals(sourceBatch.lane, acceptance.acceptedSamples[0].lane)
        assertEquals(
            sourceBatch.parserRevision,
            acceptance.acceptedSamples[0].parserRevision,
        )
        assertEquals(
            sourceBatch.calibrationRevision,
            acceptance.acceptedSamples[0].calibrationRevision,
        )
        assertEquals(
            sourceBatch.parserRevision,
            acceptance.acceptedSamples[1].parserRevision,
        )
        assertEquals(
            sourceBatch.calibrationRevision,
            acceptance.acceptedSamples[1].calibrationRevision,
        )
        assertEquals(
            VirtualBandFixtures.capabilities.reportRevision,
            acceptance.acceptedSamples[1].capabilityReportRevision,
        )
        assertEquals(2, acceptance.duplicateSamples)
        assertEquals(2, store.commit(acceptance).committedSamples)
    }

    @Test
    fun capabilitySnapshotFailuresTerminateWithOneInvalidInputRejection() {
        val malformedReports = listOf(
            VirtualBandFixtures.capabilities.copy(
                capabilities = TraversalFailureSet(BandCapability.BATTERY),
            ),
            VirtualBandFixtures.capabilities.copy(
                capabilities = MutationDuringTraversalSet(
                    BandCapability.BATTERY,
                    BandCapability.HAPTICS,
                ),
            ),
            VirtualBandFixtures.capabilities.copy(
                capabilities = nullElementSet(),
            ),
        )

        malformedReports.forEach { report ->
            val recorder = BandDiagnosticsRecorder()
            val (session, generation, connectionToken) =
                negotiatingSessionWithToken(recorder)
            val before = recorder.snapshot()

            val rejected = assertFailsWith<BandException> {
                session.acceptCapabilities(
                    report,
                    connectionToken,
                    generation,
                )
            }

            assertEquals(BandFailureCategory.INVALID_INPUT, rejected.category)
            assertEquals(BandSessionState.INCOMPATIBLE, session.snapshot().state)
            assertEquals(
                before + BandDiagnosticEvent(
                    BandDiagnosticKind.CAPABILITY,
                    BandDiagnosticOutcome.REJECTED,
                    failureCategory = BandFailureCategory.INVALID_INPUT,
                ),
                recorder.snapshot(),
            )
        }
    }

    @Test
    fun incompatibleCapabilityValidationPreservesCategory() {
        val recorder = BandDiagnosticsRecorder()
        val (session, generation, connectionToken) =
            negotiatingSessionWithToken(recorder)
        val unsupported = VirtualBandFixtures.capabilities.copy(
            schemaVersion = BandCapabilityReport.SUPPORTED_SCHEMA_VERSION + 1,
        )

        val rejected = assertFailsWith<BandException> {
            session.acceptCapabilities(
                unsupported,
                connectionToken,
                generation,
            )
        }

        assertEquals(BandFailureCategory.INCOMPATIBLE, rejected.category)
        assertEquals(BandSessionState.INCOMPATIBLE, session.snapshot().state)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.CAPABILITY,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INCOMPATIBLE,
            ),
            recorder.snapshot().last(),
        )
    }

    @Test
    fun repeatedSetElementsCountTowardTheTraversalBound() {
        val report = VirtualBandFixtures.capabilities.copy(
            capabilities = RepeatingIteratorSet(
                BandCapability.BATTERY,
                BandCapability.entries.size + 1,
            ),
        )

        val rejected = assertFailsWith<BandException> {
            report.immutableSnapshot()
        }

        assertEquals(BandFailureCategory.INVALID_INPUT, rejected.category)
    }

    @Test
    fun beginLiveBoundsAndNormalizesHostileRequestedStreams() {
        val oversized = IterationForbiddenSet<BandStreamKind>(
            BandStreamKind.entries.size + 1,
        )
        val requestedSets = listOf<Set<BandStreamKind>>(
            SizeFailureSet(),
            TraversalFailureSet(BandStreamKind.HEART_RATE),
            RepeatingIteratorSet(
                BandStreamKind.HEART_RATE,
                BandStreamKind.entries.size + 1,
            ),
            oversized,
            nullElementSet(),
        )

        requestedSets.forEach { requested ->
            val recorder = BandDiagnosticsRecorder()
            val (session, _) = readySession(recorder)
            val before = session.snapshot()

            val error = assertFailsWith<BandException> {
                session.beginLive(requested)
            }

            assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
            assertEquals(before, session.snapshot())
            assertEquals(
                BandDiagnosticEvent(
                    BandDiagnosticKind.LIVE,
                    BandDiagnosticOutcome.REJECTED,
                    failureCategory = BandFailureCategory.INVALID_INPUT,
                ),
                recorder.snapshot().last(),
            )
        }
        assertFalse(oversized.iterationAttempted)
    }

    @Test
    fun beginLiveRejectsStateMutationDuringCallerOwnedTraversal() {
        fun verifyRejectedMutation(
            nestedKind: BandDiagnosticKind,
            mutate: (BandSessionMachine, Long) -> Unit,
        ) {
            val recorder = BandDiagnosticsRecorder()
            val (session, generation) = readySession(recorder)
            val before = session.snapshot()
            val eventCount = recorder.snapshot().size
            val requested = ReentrantTraversalSet(
                setOf(BandStreamKind.HEART_RATE),
            ) {
                mutate(session, generation)
            }

            val error = assertFailsWith<BandException> {
                session.beginLive(requested)
            }

            assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
            assertEquals(before, session.snapshot())
            assertEquals(
                listOf(
                    BandDiagnosticEvent(
                        nestedKind,
                        BandDiagnosticOutcome.REJECTED,
                        failureCategory = BandFailureCategory.INVALID_INPUT,
                    ),
                    BandDiagnosticEvent(
                        BandDiagnosticKind.LIVE,
                        BandDiagnosticOutcome.REJECTED,
                        failureCategory = BandFailureCategory.INVALID_INPUT,
                    ),
                ),
                recorder.snapshot().drop(eventCount),
            )

            session.beginLive()
            assertEquals(
                BandSessionState.LIVE_COLLECTING,
                session.snapshot().state,
            )
        }

        verifyRejectedMutation(BandDiagnosticKind.DISCONNECT) { session, _ ->
            session.close()
        }
        verifyRejectedMutation(BandDiagnosticKind.DISCONNECT) {
                session, generation ->
            session.disconnect(
                BandDisconnectReason.USER_PAUSED,
                generation,
            )
        }
    }

    @Test
    fun callerOwnedSnapshotsRejectSameCallReentrancy() {
        run {
            val (session, _) = readySession()
            lateinit var requested: Set<BandStreamKind>
            requested = ReentrantTraversalSet(
                setOf(BandStreamKind.HEART_RATE),
            ) {
                session.beginLive(requested)
            }

            val error = assertFailsWith<BandException> {
                session.beginLive(requested)
            }

            assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
            assertEquals(BandSessionState.READY, session.snapshot().state)
        }

        run {
            val (session, generation, connectionToken) =
                negotiatingSessionWithToken()
            lateinit var report: BandCapabilityReport
            report = VirtualBandFixtures.capabilities.copy(
                capabilities = ReentrantTraversalSet(
                    VirtualBandFixtures.capabilities.capabilities,
                ) {
                    session.acceptCapabilities(
                        report,
                        connectionToken,
                        generation,
                    )
                },
            )

            val error = assertFailsWith<BandException> {
                session.acceptCapabilities(
                    report,
                    connectionToken,
                    generation,
                )
            }

            assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
            assertEquals(
                BandSessionState.INCOMPATIBLE,
                session.snapshot().state,
            )
        }

        run {
            val (session, generation) = readySession()
            val liveToken = session.beginLive()
            lateinit var batch: BandSampleBatch
            batch = VirtualBandFixtures.liveBatch.copy(
                samples = ReentrantTraversalList(
                    VirtualBandFixtures.liveBatch.samples,
                ) {
                    session.stageLiveBatch(batch, liveToken, generation)
                },
            )

            val error = assertFailsWith<BandException> {
                session.stageLiveBatch(batch, liveToken, generation)
            }

            assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
            assertEquals(
                BandSessionState.LIVE_COLLECTING,
                session.snapshot().state,
            )
        }

        run {
            val (session, generation) = readySession()
            val operation =
                session.beginOperation(BandOperationClass.HISTORY)
            lateinit var chunk: BandHistoryChunk
            chunk = VirtualBandFixtures.historyChunk.copy(
                batches = ReentrantTraversalList(
                    VirtualBandFixtures.historyChunk.batches,
                ) {
                    session.stageHistoryChunk(
                        chunk,
                        operation,
                        generation,
                    )
                },
            )

            val error = assertFailsWith<BandException> {
                session.stageHistoryChunk(chunk, operation, generation)
            }

            assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
            assertEquals(
                BandOperationClass.HISTORY,
                session.snapshot().activeOperation,
            )
        }
    }

    @Test
    fun otherCallerOwnedSnapshotsRejectStateMutationReentrancy() {
        fun assertInvalidInput(block: () -> Unit) {
            val error = assertFailsWith<BandException>(block = block)
            assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
        }

        run {
            val (session, generation, connectionToken) =
                negotiatingSessionWithToken()
            val report = VirtualBandFixtures.capabilities.copy(
                capabilities = ReentrantTraversalSet(
                    VirtualBandFixtures.capabilities.capabilities,
                ) {
                    session.close()
                },
            )

            assertInvalidInput {
                session.acceptCapabilities(
                    report,
                    connectionToken,
                    generation,
                )
            }
            assertEquals(
                BandSessionState.INCOMPATIBLE,
                session.snapshot().state,
            )
        }

        run {
            val (session, generation) = readySession()
            val liveToken = session.beginLive()
            val batch = VirtualBandFixtures.liveBatch.copy(
                samples = ReentrantTraversalList(
                    VirtualBandFixtures.liveBatch.samples,
                ) {
                    session.close()
                },
            )

            assertInvalidInput {
                session.stageLiveBatch(batch, liveToken, generation)
            }
            assertEquals(
                BandSessionState.LIVE_COLLECTING,
                session.snapshot().state,
            )
        }

        run {
            val (session, generation) = readySession()
            val operation =
                session.beginOperation(BandOperationClass.HISTORY)
            val chunk = VirtualBandFixtures.historyChunk.copy(
                batches = ReentrantTraversalList(
                    VirtualBandFixtures.historyChunk.batches,
                ) {
                    session.close()
                },
            )

            assertInvalidInput {
                session.stageHistoryChunk(chunk, operation, generation)
            }
            assertEquals(
                BandOperationClass.HISTORY,
                session.snapshot().activeOperation,
            )
        }
    }

    @Test
    fun supplierOwnedSnapshotsRejectJvmNullElements() {
        fun assertInvalidInput(block: () -> Unit) {
            val error = assertFailsWith<BandException>(block = block)
            assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
        }

        assertInvalidInput {
            VirtualBandFixtures.capabilities.copy(
                capabilities = nullElementSet(),
            ).immutableSnapshot()
        }
        assertInvalidInput {
            VirtualBandFixtures.capabilities.copy(
                liveStreams = nullElementSet(),
            ).immutableSnapshot()
        }
        assertInvalidInput {
            VirtualBandFixtures.capabilities.copy(
                historyStreams = nullElementSet(),
            ).immutableSnapshot()
        }
        assertInvalidInput {
            VirtualBandFixtures.liveBatch.copy(
                samples = nullElementList(),
            ).immutableSnapshot()
        }
        assertInvalidInput {
            VirtualBandFixtures.historyChunk.copy(
                batches = nullElementList(),
            ).immutableSnapshot()
        }
        assertInvalidInput {
            val nestedNullSamples =
                VirtualBandFixtures.historyChunk.batches.first().copy(
                    samples = nullElementList(),
                )
            VirtualBandFixtures.historyChunk.copy(
                batches = listOf(nestedNullSamples),
            ).immutableSnapshot()
        }
        assertInvalidInput {
            BandHistoryCheckpoint(
                sourceIdentity = VirtualBandFixtures.identity.sourceIdentity,
                acknowledgedCursor = null,
                lastHistoryComplete = null,
                durableSampleIdentities = nullElementSet(),
            ).immutableSnapshot()
        }

        assertInvalidInput {
            VirtualBandFixtures.capabilities.copy(
                capabilities = JavaNullCollections.set(),
            ).immutableSnapshot()
        }
        assertInvalidInput {
            VirtualBandFixtures.capabilities.copy(
                streamSemantics = JavaNullCollections.list(),
            ).immutableSnapshot()
        }
        assertInvalidInput {
            VirtualBandFixtures.liveBatch.copy(
                samples = JavaNullCollections.list(),
            ).immutableSnapshot()
        }
        assertInvalidInput {
            VirtualBandFixtures.historyChunk.copy(
                batches = JavaNullCollections.list(),
            ).immutableSnapshot()
        }
        assertInvalidInput {
            BandHistoryCheckpoint(
                sourceIdentity = VirtualBandFixtures.identity.sourceIdentity,
                acknowledgedCursor = null,
                lastHistoryComplete = null,
                durableSampleIdentities = JavaNullCollections.set(),
            ).immutableSnapshot()
        }
    }

    @Test
    fun liveOperationsFollowNegotiatedConcurrency() {
        val recorder = BandDiagnosticsRecorder()
        val report = VirtualBandFixtures.capabilities.copy(
            operationsAllowedDuringLive = setOf(BandOperationClass.BATTERY),
        )
        val (session, _) = readySessionWithCapabilities(report, recorder)
        val liveToken = session.beginLive()

        val battery = session.beginOperation(BandOperationClass.BATTERY)
        session.cancelOperation(battery)

        val denied = assertFailsWith<BandException> {
            session.beginOperation(BandOperationClass.HAPTIC)
        }
        assertEquals(BandFailureCategory.BUSY, denied.category)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.COMMAND,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.BUSY,
                operationClass = BandOperationClass.HAPTIC,
            ),
            recorder.snapshot().last(),
        )
        session.stopLive(liveToken)
    }

    @Test
    fun negotiatedStreamSemanticsRejectRevisionDriftBeforeStaging() {
        val recorder = BandDiagnosticsRecorder()
        val (session, generation) = readySession(recorder)
        val liveToken = session.beginLive()

        listOf(
            VirtualBandFixtures.liveBatch.copy(
                parserRevision = "parser-v2",
            ),
            VirtualBandFixtures.liveBatch.copy(
                calibrationRevision = "calibration-v2",
            ),
        ).forEach { mismatched ->
            val error = assertFailsWith<BandException> {
                session.stageLiveBatch(mismatched, liveToken, generation)
            }
            assertEquals(BandFailureCategory.UNSUPPORTED, error.category)
            assertEquals(
                BandSessionState.LIVE_COLLECTING,
                session.snapshot().state,
            )
        }
        session.stopLive(liveToken)

        val historyToken =
            session.beginOperation(BandOperationClass.HISTORY)
        val historyError = assertFailsWith<BandException> {
            session.stageHistoryChunk(
                VirtualBandFixtures.historyChunk.copy(
                    batches = listOf(
                        VirtualBandFixtures.historyChunk.batches.first().copy(
                            calibrationRevision = "calibration-v2",
                        ),
                    ),
                ),
                historyToken,
                generation,
            )
        }
        assertEquals(BandFailureCategory.UNSUPPORTED, historyError.category)
        assertEquals(
            BandOperationClass.HISTORY,
            session.snapshot().activeOperation,
        )
        session.cancelOperation(historyToken)
    }

    @Test
    fun liveStagingPrecedesDurableCompletionAndReportsZeroDuplicates() {
        val recorder = BandDiagnosticsRecorder()
        val (session, generation) = readySession(recorder)
        val liveToken = session.beginLive()
        val first = session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            liveToken,
            generation,
        )

        var liveEvents = recorder.snapshot().filter {
            it.kind == BandDiagnosticKind.LIVE
        }
        assertTrue(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.STAGED,
                countBucket = BandCountBucket.ONE,
            ) in liveEvents,
        )
        assertFalse(
            liveEvents.any {
                it.outcome == BandDiagnosticOutcome.COMPLETED
            },
        )
        session.acknowledgeLive(
            DurableLiveReceipt(
                acceptance = first,
                committedSamples = 1,
                committed = true,
            ),
            generation,
        )

        val duplicate = session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            liveToken,
            generation,
        )
        assertEquals(0, duplicate.acceptedSamples.size)
        liveEvents = recorder.snapshot().filter {
            it.kind == BandDiagnosticKind.LIVE
        }
        assertEquals(
            listOf(
                BandDiagnosticEvent(
                    BandDiagnosticKind.LIVE,
                    BandDiagnosticOutcome.BEGAN,
                ),
                BandDiagnosticEvent(
                    BandDiagnosticKind.LIVE,
                    BandDiagnosticOutcome.STAGED,
                    BandCountBucket.ONE,
                ),
                BandDiagnosticEvent(
                    BandDiagnosticKind.LIVE,
                    BandDiagnosticOutcome.COMPLETED,
                    BandCountBucket.ONE,
                ),
                BandDiagnosticEvent(
                    BandDiagnosticKind.LIVE,
                    BandDiagnosticOutcome.STAGED,
                    BandCountBucket.ZERO,
                ),
            ),
            liveEvents,
        )
        assertFalse(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.COMPLETED,
                BandCountBucket.ZERO,
            ) in liveEvents,
        )
        session.acknowledgeLive(
            DurableLiveReceipt(
                acceptance = duplicate,
                committedSamples = 0,
                committed = true,
            ),
            generation,
        )
        liveEvents = recorder.snapshot().filter {
            it.kind == BandDiagnosticKind.LIVE
        }
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.COMPLETED,
                BandCountBucket.ZERO,
            ),
            liveEvents.last(),
        )
        session.stopLive(liveToken)
    }

    @Test
    fun disconnectDiagnosticsPreserveEveryReason() {
        BandDisconnectReason.entries.forEach { reason ->
            val recorder = BandDiagnosticsRecorder()
            val (session, generation) = readySession(recorder)
            val eventCount = recorder.snapshot().size

            session.disconnect(reason, generation)

            assertEquals(
                listOf(
                    BandDiagnosticEvent(
                        BandDiagnosticKind.DISCONNECT,
                        BandDiagnosticOutcome.BEGAN,
                        disconnectReason = reason,
                    ),
                    BandDiagnosticEvent(
                        BandDiagnosticKind.DISCONNECT,
                        BandDiagnosticOutcome.COMPLETED,
                        disconnectReason = reason,
                    ),
                ),
                recorder.snapshot().drop(eventCount),
            )
        }
    }

    @Test
    fun legacyJavaDiagnosticConstructorRemainsAvailable() {
        val event = JavaNullCollections.legacyDiagnosticEvent()

        assertEquals(BandDiagnosticKind.CONNECTION, event.kind)
        assertEquals(BandDiagnosticOutcome.COMPLETED, event.outcome)
        assertNull(event.operationClass)
        assertNull(event.disconnectReason)
    }

    @Test
    fun malformedLateCapabilityCallbackPreservesReadySession() {
        val malformedReports = listOf(
            VirtualBandFixtures.capabilities.copy(
                capabilities = TraversalFailureSet(BandCapability.BATTERY),
            ),
            VirtualBandFixtures.capabilities.copy(
                capabilities = MutationDuringTraversalSet(
                    BandCapability.BATTERY,
                    BandCapability.HAPTICS,
                ),
            ),
        )

        malformedReports.forEach { report ->
            val recorder = BandDiagnosticsRecorder()
            val (session, generation, connectionToken) =
                readySessionWithToken(recorder)
            val before = session.snapshot()

            val rejected = assertFailsWith<BandException> {
                session.acceptCapabilities(
                    report,
                    connectionToken,
                    generation,
                )
            }

            assertEquals(BandFailureCategory.INVALID_INPUT, rejected.category)
            assertEquals(before, session.snapshot())
            assertEquals(
                BandDiagnosticEvent(
                    BandDiagnosticKind.CAPABILITY,
                    BandDiagnosticOutcome.REJECTED,
                    failureCategory = BandFailureCategory.INVALID_INPUT,
                ),
                recorder.snapshot().last(),
            )

            session.beginLive()
            assertEquals(BandSessionState.LIVE_COLLECTING, session.snapshot().state)
        }
    }

    @Test
    fun equivalentCapabilityReportOrderIsIdempotent() {
        val base = VirtualBandFixtures.capabilities
        assertTrue(base.streamSemantics.size > 1)
        val reordered = base.copy(
            streamSemantics = base.streamSemantics.reversed(),
        )
        assertEquals(base, reordered)
        assertEquals(base.hashCode(), reordered.hashCode())
        val changed = base.copy(
            streamSemantics = listOf(
                base.streamSemantics.first().copy(
                    parserRevision = "parser-v2",
                ),
            ) + base.streamSemantics.drop(1),
        )
        assertFalse(base == changed)
        val composed = "\u00E9"
        val decomposed = "e\u0301"
        val composedRevision = base.copy(reportRevision = composed)
        val decomposedRevision = base.copy(reportRevision = decomposed)
        composedRevision.validate()
        decomposedRevision.validate()
        assertFalse(composedRevision == decomposedRevision)

        val original = base.streamSemantics.first()
        val composedSemanticReport = base.copy(
            streamSemantics = listOf(
                original.copy(parserRevision = composed),
            ) + base.streamSemantics.drop(1),
        )
        val decomposedSemanticReport = base.copy(
            streamSemantics = listOf(
                original.copy(parserRevision = decomposed),
            ) + base.streamSemantics.drop(1),
        )
        composedSemanticReport.validate()
        decomposedSemanticReport.validate()
        assertFalse(composedSemanticReport == decomposedSemanticReport)

        val second = base.streamSemantics[1]
        val duplicateFirst = base.copy(
            streamSemantics = listOf(original, original, second),
        )
        val duplicateSecond = base.copy(
            streamSemantics = listOf(original, second, second),
        )
        assertFalse(duplicateFirst == duplicateSecond)

        val recorder = BandDiagnosticsRecorder()
        val (session, generation, connectionToken) =
            readySessionWithToken(recorder)
        session.acceptCapabilities(
            reordered,
            connectionToken,
            generation,
        )

        assertEquals(BandSessionState.READY, session.snapshot().state)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.CAPABILITY,
                BandDiagnosticOutcome.STALE,
            ),
            recorder.snapshot().last(),
        )
    }

    @Test
    fun capabilityIdentityRevisionsUseExactUtf8() {
        val composed = "\u00E9"
        val decomposed = "e\u0301"
        val baseIdentity = VirtualBandFixtures.identity
        val identity = BandIdentity(
            sourceIdentity = baseIdentity.sourceIdentity,
            hardwareRevision = composed,
            firmwareVersion = composed,
            protocolVersion = baseIdentity.protocolVersion,
            wrapperRevision = baseIdentity.wrapperRevision,
        )
        val canonicallyEquivalentIdentity = BandIdentity(
            sourceIdentity = baseIdentity.sourceIdentity,
            hardwareRevision = decomposed,
            firmwareVersion = decomposed,
            protocolVersion = baseIdentity.protocolVersion,
            wrapperRevision = baseIdentity.wrapperRevision,
        )
        assertNotEquals(identity, canonicallyEquivalentIdentity)
        val base = VirtualBandFixtures.capabilities
        val mismatches = listOf(
            decomposed to composed,
            composed to decomposed,
        )

        mismatches.forEach { (hardware, firmware) ->
            val session = BandSessionMachine()
            val scanToken = session.beginScan()
            val generation = scanToken.generation
            val connectionToken = session.selectCandidate(
                VirtualBandFixtures.candidate,
                scanToken,
            )
            session.beginConnection(connectionToken, generation)
            session.beginAuthentication(connectionToken, generation)
            session.completeConnection(
                identity,
                connectionToken,
                generation,
            )
            val report = base.copy(
                hardwareRevision = hardware,
                firmwareVersion = firmware,
            )

            val failure = assertFailsWith<BandException> {
                session.acceptCapabilities(
                    report,
                    connectionToken,
                    generation,
                )
            }
            assertEquals(
                BandFailureCategory.INCOMPATIBLE,
                failure.category,
            )
            assertEquals(
                BandSessionState.INCOMPATIBLE,
                session.snapshot().state,
            )
        }
    }

    @Test
    fun oversizedLateCapabilityReportIsBounded() {
        val base = VirtualBandFixtures.capabilities
        val oversized = base.copy(
            streamSemantics = List(
                BandCapabilityReport.MAXIMUM_STREAM_SEMANTICS + 1,
            ) {
                base.streamSemantics.first()
            },
        )
        assertNotEquals(base, oversized)
        assertEquals(oversized, oversized.copy())
        val longRevision = base.copy(
            streamSemantics = listOf(
                base.streamSemantics.first().copy(
                    parserRevision = "x".repeat(1_000_000),
                ),
            ) + base.streamSemantics.drop(1),
        )
        val misreported = base.copy(
            streamSemantics = MisreportedList(
                List(
                    BandCapabilityReport.MAXIMUM_STREAM_SEMANTICS + 1,
                ) {
                    base.streamSemantics.first()
                },
                size = 1,
            ),
        )

        val recorder = BandDiagnosticsRecorder()
        val (session, generation, connectionToken) =
            readySessionWithToken(recorder)
        val before = session.snapshot()
        listOf(oversized, longRevision, misreported).forEach { invalid ->
            val failure = assertFailsWith<BandException> {
                session.acceptCapabilities(
                    invalid,
                    connectionToken,
                    generation,
                )
            }
            assertEquals(BandFailureCategory.INVALID_INPUT, failure.category)
            assertEquals(before, session.snapshot())
            assertEquals(
                BandDiagnosticEvent(
                    BandDiagnosticKind.CAPABILITY,
                    BandDiagnosticOutcome.REJECTED,
                    failureCategory = BandFailureCategory.INVALID_INPUT,
                ),
                recorder.snapshot().last(),
            )
        }
    }

    @Test
    fun publicContractStringEqualityUsesExactUtf8() {
        val composed = "\u00E9"
        val decomposed = "e\u0301"
        val source = VirtualBandFixtures.historyChunk
        val sourceBatch = source.batches.first()
        val exactBatch = sourceBatch.copy(
            sourceIdentity = composed,
            parserRevision = composed,
            calibrationRevision = composed,
        )
        assertNotEquals(
            exactBatch,
            exactBatch.copy(sourceIdentity = decomposed),
        )
        assertNotEquals(
            exactBatch,
            exactBatch.copy(parserRevision = decomposed),
        )
        assertNotEquals(
            exactBatch,
            exactBatch.copy(calibrationRevision = decomposed),
        )

        val exactChunk = source.copy(
            chunkIdentity = composed,
            previousCursor = composed,
            nextCursor = composed,
            acknowledgementToken = composed,
            batches = listOf(exactBatch),
        )
        assertNotEquals(
            exactChunk,
            exactChunk.copy(chunkIdentity = decomposed),
        )
        assertNotEquals(
            exactChunk,
            exactChunk.copy(previousCursor = decomposed),
        )
        assertNotEquals(
            exactChunk,
            exactChunk.copy(nextCursor = decomposed),
        )
        assertNotEquals(
            exactChunk,
            exactChunk.copy(acknowledgementToken = decomposed),
        )

        val sampleIdentities = sourceBatch.samples.map { it.identity }.toSet()
        val exactCheckpoint = BandHistoryCheckpoint(
            sourceIdentity = composed,
            acknowledgedCursor = composed,
            lastHistoryComplete = true,
            durableSampleIdentities = sampleIdentities,
        )
        assertNotEquals(
            exactCheckpoint,
            exactCheckpoint.copy(sourceIdentity = decomposed),
        )
        assertNotEquals(
            exactCheckpoint,
            exactCheckpoint.copy(acknowledgedCursor = decomposed),
        )

        val exactSnapshot = BandSessionSnapshot(
            state = BandSessionState.READY,
            generation = 1,
            activeOperation = null,
            liveActive = false,
            acknowledgedHistoryCursor = composed,
            durableSampleCount = 2,
        )
        assertNotEquals(
            exactSnapshot,
            exactSnapshot.copy(acknowledgedHistoryCursor = decomposed),
        )
    }

    @Test
    fun sourceIdentityAndCheckpointRestoreUseExactUtf8() {
        val composed = "\u00E9"
        val decomposed = "e\u0301"
        val baseIdentity = VirtualBandFixtures.identity
        val composedIdentity = baseIdentity.copy(sourceIdentity = composed)
        val (session, generation) = readySessionWithToken(
            identity = composedIdentity,
        ).let { (ready, callbackGeneration, _) ->
            ready to callbackGeneration
        }
        val liveToken = session.beginLive()
        val sourceBatch = VirtualBandFixtures.liveBatch
        val failure = assertFailsWith<BandException> {
            session.stageLiveBatch(
                sourceBatch.copy(sourceIdentity = decomposed),
                liveToken,
                generation,
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, failure.category)
        session.stopLive(liveToken)

        val checkpoint = BandHistoryCheckpoint(
            sourceIdentity = composed,
            acknowledgedCursor = "cursor-2",
            lastHistoryComplete = true,
            durableSampleIdentities =
                VirtualBandFixtures.historyChunk.batches
                    .flatMap { it.samples }
                    .map { it.identity }
                    .toSet(),
        )
        val restored = BandSessionMachine(
            restoredHistoryCheckpoint = checkpoint,
        )
        val scanToken = restored.beginScan()
        val restoredGeneration = scanToken.generation
        val connectionToken = restored.selectCandidate(
            VirtualBandFixtures.candidate,
            scanToken,
        )
        restored.beginConnection(connectionToken, restoredGeneration)
        restored.beginAuthentication(connectionToken, restoredGeneration)
        restored.completeConnection(
            baseIdentity.copy(sourceIdentity = decomposed),
            connectionToken,
            restoredGeneration,
        )
        assertNull(restored.snapshot().acknowledgedHistoryCursor)
        assertEquals(0, restored.snapshot().durableSampleCount)
    }

    @Test
    fun historyCursorProgressionUsesExactUtf8() {
        val composed = "\u00E9"
        val decomposed = "e\u0301"
        val base = VirtualBandFixtures.historyChunk
        val (session, generation) = readySession()
        val token = session.beginOperation(BandOperationClass.HISTORY)
        val first = base.copy(
            chunkIdentity = "chunk-first",
            previousCursor = null,
            nextCursor = composed,
            complete = false,
            acknowledgementToken = "ack-first",
        )
        val firstAcceptance = session.stageHistoryChunk(
            first,
            token,
            generation,
        )
        session.acknowledgeHistory(
            DurableHistoryReceipt(
                firstAcceptance,
                historyStateCommitted = true,
                committedSamples = firstAcceptance.acceptedSamples.size,
                committed = true,
            ),
            token,
            generation,
        )

        val failure = assertFailsWith<BandException> {
            session.stageHistoryChunk(
                base.copy(
                    chunkIdentity = "chunk-second",
                    previousCursor = decomposed,
                    nextCursor = "cursor-3",
                    complete = true,
                    acknowledgementToken = "ack-second",
                ),
                token,
                generation,
            )
        }
        assertEquals(BandFailureCategory.HISTORY_STALLED, failure.category)
        assertEquals(composed, session.snapshot().acknowledgedHistoryCursor)
    }

    @Test
    fun historyReceiptIdentitiesUseExactUtf8() {
        val composed = "\u00E9"
        val decomposed = "e\u0301"
        val base = VirtualBandFixtures.historyChunk
        val (session, generation) = readySession()
        val token = session.beginOperation(BandOperationClass.HISTORY)
        val acceptance = session.stageHistoryChunk(
            base.copy(
                chunkIdentity = composed,
                previousCursor = null,
                nextCursor = composed,
                complete = true,
                acknowledgementToken = composed,
            ),
            token,
            generation,
        )

        fun receipt(
            chunkIdentity: String = composed,
            acknowledgementToken: String = composed,
            nextCursor: String? = composed,
        ): DurableHistoryReceipt {
            val alteredAcceptance = HistoryAcceptance(
                chunkIdentity = chunkIdentity,
                acknowledgementToken = acknowledgementToken,
                nextCursor = nextCursor,
                complete = acceptance.complete,
                overflowed = acceptance.overflowed,
                retainedRange = acceptance.retainedRange,
                firstLostRange = acceptance.firstLostRange,
                acceptedSamples = acceptance.acceptedSamples,
                duplicateSamples = acceptance.duplicateSamples,
                sessionNonce = acceptance.sessionNonce,
                receiptSequence = acceptance.receiptSequence,
            )
            return DurableHistoryReceipt(
                alteredAcceptance,
                historyStateCommitted = true,
                committedSamples = acceptance.acceptedSamples.size,
                committed = true,
            )
        }

        listOf(
            receipt(chunkIdentity = decomposed),
            receipt(acknowledgementToken = decomposed),
            receipt(nextCursor = decomposed),
        ).forEach { mismatched ->
            val failure = assertFailsWith<BandException> {
                session.acknowledgeHistory(
                    mismatched,
                    token,
                    generation,
                )
            }
            assertEquals(BandFailureCategory.STORAGE, failure.category)
        }
        session.acknowledgeHistory(receipt(), token, generation)
    }

    @Test
    fun negotiatedRevisionsUseExactUtf8() {
        val composed = "\u00E9"
        val decomposed = "e\u0301"
        val base = VirtualBandFixtures.capabilities
        val report = base.copy(
            streamSemantics = base.streamSemantics.map {
                it.copy(
                    parserRevision = composed,
                    calibrationRevision = composed,
                )
            },
        )
        val (session, generation) =
            readySessionWithCapabilities(report)
        val liveToken = session.beginLive()
        val source = VirtualBandFixtures.liveBatch
        val mismatches = listOf(
            BandSampleBatch(
                sourceIdentity = source.sourceIdentity,
                lane = source.lane,
                parserRevision = decomposed,
                calibrationRevision = composed,
                samples = source.samples,
            ),
            BandSampleBatch(
                sourceIdentity = source.sourceIdentity,
                lane = source.lane,
                parserRevision = composed,
                calibrationRevision = decomposed,
                samples = source.samples,
            ),
        )

        mismatches.forEach { mismatch ->
            val failure = assertFailsWith<BandException> {
                session.stageLiveBatch(
                    mismatch,
                    liveToken,
                    generation,
                )
            }
            assertEquals(
                BandFailureCategory.UNSUPPORTED,
                failure.category,
            )
            assertEquals(
                BandSessionState.LIVE_COLLECTING,
                session.snapshot().state,
            )
        }
        session.stopLive(liveToken)
    }

    @Test
    fun acceptanceCollectionsCannotReduceDurableReceiptRequirements() {
        val (session, generation) = readySession()
        val liveToken = session.beginLive()
        val liveAcceptance = session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            liveToken,
            generation,
        )
        @Suppress("UNCHECKED_CAST")
        val mutableLive =
            liveAcceptance.acceptedSamples as MutableList<BandSample>
        assertFailsWith<UnsupportedOperationException> {
            mutableLive.clear()
        }
        session.acknowledgeLive(
            DurableLiveReceipt(
                acceptance = liveAcceptance,
                committedSamples = liveAcceptance.acceptedSamples.size,
                committed = true,
            ),
            generation,
        )
        session.stopLive(liveToken)

        val token = session.beginOperation(BandOperationClass.HISTORY)
        val acceptance = session.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            token,
            generation,
        )
        @Suppress("UNCHECKED_CAST")
        val mutableHistory =
            acceptance.acceptedSamples as MutableList<AcceptedHistorySample>
        assertFailsWith<UnsupportedOperationException> {
            mutableHistory.clear()
        }
        val error = assertFailsWith<BandException> {
            session.acknowledgeHistory(
                DurableHistoryReceipt(
                    acceptance = acceptance,
                    historyStateCommitted = true,
                    committedSamples = 0,
                    committed = true,
                ),
                token,
                generation,
            )
        }
        assertEquals(BandFailureCategory.STORAGE, error.category)
        assertEquals(null, session.snapshot().acknowledgedHistoryCursor)
    }

    @Test
    fun restoredCheckpointSnapshotIsBoundedBeforeTraversal() {
        val oversized = IterationForbiddenSet<BandSampleIdentity>(
            BandContractLimits.HISTORY_CHECKPOINT_IDENTITIES + 1,
        )
        val oversizedError = assertFailsWith<BandException> {
            BandSessionMachine(
                restoredHistoryCheckpoint = BandHistoryCheckpoint(
                    sourceIdentity = VirtualBandFixtures.identity.sourceIdentity,
                    acknowledgedCursor = null,
                    lastHistoryComplete = null,
                    durableSampleIdentities = oversized,
                ),
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, oversizedError.category)
        assertFalse(oversized.iterationAttempted)

        val negative = IterationForbiddenSet<BandSampleIdentity>(-1)
        val negativeError = assertFailsWith<BandException> {
            BandSessionMachine(
                restoredHistoryCheckpoint = BandHistoryCheckpoint(
                    sourceIdentity = VirtualBandFixtures.identity.sourceIdentity,
                    acknowledgedCursor = null,
                    lastHistoryComplete = null,
                    durableSampleIdentities = negative,
                ),
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, negativeError.category)
        assertFalse(negative.iterationAttempted)

        val traversalError = assertFailsWith<BandException> {
            BandSessionMachine(
                restoredHistoryCheckpoint = BandHistoryCheckpoint(
                    sourceIdentity = VirtualBandFixtures.identity.sourceIdentity,
                    acknowledgedCursor = null,
                    lastHistoryComplete = null,
                    durableSampleIdentities = TraversalFailureSet(
                        VirtualBandFixtures.liveBatch.samples.first().identity,
                    ),
                ),
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, traversalError.category)
    }

    @Test
    fun immutableSnapshotsDoNotRetainMutableCallerCollections() {
        val capabilities =
            VirtualBandFixtures.capabilities.capabilities.toMutableSet()
        val liveStreams =
            VirtualBandFixtures.capabilities.liveStreams.toMutableSet()
        val historyStreams =
            VirtualBandFixtures.capabilities.historyStreams.toMutableSet()
        val reportSnapshot = VirtualBandFixtures.capabilities.copy(
            capabilities = capabilities,
            liveStreams = liveStreams,
            historyStreams = historyStreams,
        ).immutableSnapshot()
        capabilities.clear()
        liveStreams.clear()
        historyStreams.clear()
        assertEquals(
            VirtualBandFixtures.capabilities.capabilities,
            reportSnapshot.capabilities,
        )
        assertEquals(
            VirtualBandFixtures.capabilities.liveStreams,
            reportSnapshot.liveStreams,
        )
        assertEquals(
            VirtualBandFixtures.capabilities.historyStreams,
            reportSnapshot.historyStreams,
        )

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
    fun oversizedSnapshotsRejectBeforeUnboundedTraversal() {
        val oversizedStreams = IterationForbiddenSet<BandStreamKind>(
            BandStreamKind.entries.size + 1,
        )
        val reportError = assertFailsWith<BandException> {
            VirtualBandFixtures.capabilities.copy(
                liveStreams = oversizedStreams,
            ).immutableSnapshot()
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, reportError.category)
        assertFalse(oversizedStreams.iterationAttempted)

        val oversizedSamples = IterationForbiddenList<BandSample>(
            BandContractLimits.SAMPLES_PER_BATCH + 1,
        )
        val batch = VirtualBandFixtures.liveBatch.copy(
            samples = oversizedSamples,
        )
        val batchError = assertFailsWith<BandException> {
            batch.immutableSnapshot()
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, batchError.category)
        assertFalse(oversizedSamples.iterationAttempted)

        val oversizedBatches = IterationForbiddenList<BandSampleBatch>(
            BandContractLimits.BATCHES_PER_HISTORY_CHUNK + 1,
        )
        val chunk = VirtualBandFixtures.historyChunk.copy(
            batches = oversizedBatches,
        )
        val chunkError = assertFailsWith<BandException> {
            chunk.immutableSnapshot()
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, chunkError.category)
        assertFalse(oversizedBatches.iterationAttempted)

        val sample = VirtualBandFixtures.historyChunk.batches
            .first().samples.first()
        val fullBatch = VirtualBandFixtures.historyChunk.batches.first().copy(
            samples = List(BandContractLimits.SAMPLES_PER_BATCH) { sample },
        )
        val excessSamples = IterationForbiddenList<BandSample>(1)
        val excessBatch = fullBatch.copy(samples = excessSamples)
        val totalOverflow = VirtualBandFixtures.historyChunk.copy(
            batches = List(
                BandContractLimits.SAMPLES_PER_HISTORY_CHUNK /
                    BandContractLimits.SAMPLES_PER_BATCH,
            ) { fullBatch } + excessBatch,
        )
        val totalError = assertFailsWith<BandException> {
            totalOverflow.immutableSnapshot()
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, totalError.category)
        assertFalse(excessSamples.iterationAttempted)

        val traversalOverflow = MisreportedList(
            values = List(BandContractLimits.SAMPLES_PER_BATCH + 1) { sample },
            size = BandContractLimits.SAMPLES_PER_BATCH,
        )
        assertEquals(
            BandFailureCategory.INVALID_INPUT,
            assertFailsWith<BandException> {
                VirtualBandFixtures.liveBatch.copy(
                    samples = traversalOverflow,
                ).immutableSnapshot()
            }.category,
        )

        val sizeMismatch = MisreportedList(
            values = listOf(sample),
            size = 2,
        )
        assertEquals(
            BandFailureCategory.INVALID_INPUT,
            assertFailsWith<BandException> {
                VirtualBandFixtures.liveBatch.copy(
                    samples = sizeMismatch,
                ).immutableSnapshot()
            }.category,
        )

        assertEquals(
            BandFailureCategory.INVALID_INPUT,
            assertFailsWith<BandException> {
                VirtualBandFixtures.liveBatch.copy(
                    samples = TraversalFailureList(sample),
                ).immutableSnapshot()
            }.category,
        )
    }

    @Test
    fun oversizedInputsPreserveLifecycleOrderAndDiagnostics() {
        val recorder = BandDiagnosticsRecorder()
        val (session, generation) = readySession(recorder)
        val oversized = VirtualBandFixtures.liveBatch.copy(
            samples = IterationForbiddenList(
                BandContractLimits.SAMPLES_PER_BATCH + 1,
            ),
        )

        var error = assertFailsWith<BandException> {
            session.stageLiveBatch(
                oversized,
                BandLiveToken(
                    java.util.UUID.randomUUID(),
                    generation,
                    0,
                ),
                generation,
            )
        }
        assertEquals(BandFailureCategory.INVALID_STATE, error.category)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INVALID_STATE,
            ),
            recorder.snapshot().last(),
        )

        val liveToken = session.beginLive()
        error = assertFailsWith<BandException> {
            session.stageLiveBatch(
                oversized,
                liveToken,
                generation,
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INVALID_INPUT,
            ),
            recorder.snapshot().last(),
        )

        val token = session.beginOperation(BandOperationClass.HISTORY)
        val oversizedChunk = VirtualBandFixtures.historyChunk.copy(
            batches = IterationForbiddenList(
                BandContractLimits.BATCHES_PER_HISTORY_CHUNK + 1,
            ),
        )
        error = assertFailsWith<BandException> {
            session.stageHistoryChunk(oversizedChunk, token, generation)
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.HISTORY,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INVALID_INPUT,
            ),
            recorder.snapshot().last(),
        )
        session.cancelOperation(token)
    }

    @Test
    fun supplierListSizeFailuresAreNormalizedAndDiagnosed() {
        val recorder = BandDiagnosticsRecorder()
        val (session, generation) = readySession(recorder)
        val liveToken = session.beginLive()
        val liveError = assertFailsWith<BandException> {
            session.stageLiveBatch(
                VirtualBandFixtures.liveBatch.copy(
                    samples = SizeFailureList(),
                ),
                liveToken,
                generation,
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, liveError.category)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INVALID_INPUT,
            ),
            recorder.snapshot().last(),
        )
        session.stopLive(liveToken)

        val historyToken =
            session.beginOperation(BandOperationClass.HISTORY)
        val historyError = assertFailsWith<BandException> {
            session.stageHistoryChunk(
                VirtualBandFixtures.historyChunk.copy(
                    batches = SizeFailureList(),
                ),
                historyToken,
                generation,
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, historyError.category)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.HISTORY,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INVALID_INPUT,
            ),
            recorder.snapshot().last(),
        )
        session.cancelOperation(historyToken)
    }

    @Test
    fun supplierBandExceptionsAreNormalizedAndDiagnosed() {
        fun assertInvalidInput(block: () -> Unit) {
            assertEquals(
                BandFailureCategory.INVALID_INPUT,
                assertFailsWith<BandException> { block() }.category,
            )
        }

        val sample = VirtualBandFixtures.liveBatch.samples.first()
        assertInvalidInput {
            CategorizedSizeFailureList<BandSample>(
                BandFailureCategory.BUSY,
            ).boundedSnapshot(1)
        }
        assertInvalidInput {
            CategorizedTraversalFailureList(
                sample,
                BandFailureCategory.STORAGE,
            ).boundedSnapshot(1)
        }
        assertInvalidInput {
            CategorizedSizeFailureSet<BandStreamKind>(
                BandFailureCategory.BUSY,
            ).boundedSnapshot(1)
        }
        assertInvalidInput {
            CategorizedTraversalFailureSet(
                BandStreamKind.HEART_RATE,
                BandFailureCategory.STORAGE,
            ).boundedSnapshot(1)
        }

        val recorder = BandDiagnosticsRecorder()
        val (session, generation) = readySession(recorder)
        val requestedStreamsError = assertFailsWith<BandException> {
            session.beginLive(
                CategorizedTraversalFailureSet(
                    BandStreamKind.HEART_RATE,
                    BandFailureCategory.BUSY,
                ),
            )
        }
        assertEquals(
            BandFailureCategory.INVALID_INPUT,
            requestedStreamsError.category,
        )
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INVALID_INPUT,
            ),
            recorder.snapshot().last(),
        )

        val liveToken = session.beginLive()
        val liveError = assertFailsWith<BandException> {
            session.stageLiveBatch(
                VirtualBandFixtures.liveBatch.copy(
                    samples = CategorizedTraversalFailureList(
                        sample,
                        BandFailureCategory.STORAGE,
                    ),
                ),
                liveToken,
                generation,
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, liveError.category)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INVALID_INPUT,
            ),
            recorder.snapshot().last(),
        )
        session.stopLive(liveToken)

        val historyToken =
            session.beginOperation(BandOperationClass.HISTORY)
        val historyError = assertFailsWith<BandException> {
            session.stageHistoryChunk(
                VirtualBandFixtures.historyChunk.copy(
                    batches = CategorizedSizeFailureList(
                        BandFailureCategory.BUSY,
                    ),
                ),
                historyToken,
                generation,
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, historyError.category)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.HISTORY,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INVALID_INPUT,
            ),
            recorder.snapshot().last(),
        )
        session.cancelOperation(historyToken)
    }

    @Test
    fun stepSamplesRequireIntegralCounts() {
        val identity = BandSampleIdentity(
            stream = BandStreamKind.STEPS,
            sequence = 1,
            deviceTimeMilliseconds = 1,
        )
        listOf(0.0, 1.0, 1_000_000.0).forEach { value ->
            BandSample(
                identity = identity,
                value = value,
                unit = BandUnit.COUNT,
                quality = BandSampleQuality.ACCEPTED,
            ).validate()
        }

        listOf(0.5, 1.5, 999_999.5).forEach { value ->
            val error = assertFailsWith<BandException> {
                BandSample(
                    identity = identity,
                    value = value,
                    unit = BandUnit.COUNT,
                    quality = BandSampleQuality.ACCEPTED,
                ).validate()
            }
            assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
        }
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
    fun capabilityNegotiationHasGenerationFencedTerminals() {
        val cancellationRecorder = BandDiagnosticsRecorder()
        val (
            cancelledSession,
            cancelledGeneration,
            cancelledConnectionToken,
        ) = negotiatingSessionWithToken(cancellationRecorder)

        cancelledSession.cancelCapabilities(
            cancelledConnectionToken,
            cancelledGeneration,
        )
        val cancelled = cancelledSession.snapshot()
        assertEquals(BandSessionState.IDLE, cancelled.state)
        assertEquals(cancelledGeneration + 1, cancelled.generation)
        val staleCancelError = assertFailsWith<BandException> {
            cancelledSession.cancelCapabilities(
                cancelledConnectionToken,
                cancelledGeneration,
            )
        }
        assertEquals(
            BandFailureCategory.STALE_CALLBACK,
            staleCancelError.category,
        )
        val staleError = assertFailsWith<BandException> {
            cancelledSession.failCapabilities(
                BandFailureCategory.TIMEOUT,
                cancelledConnectionToken,
                cancelledGeneration,
            )
        }
        assertEquals(BandFailureCategory.STALE_CALLBACK, staleError.category)
        val retryScanToken = cancelledSession.beginScan()
        val retryGeneration = retryScanToken.generation
        assertEquals(cancelled.generation + 1, retryGeneration)
        val retryConnectionToken =
            cancelledSession.selectCandidate(
                VirtualBandFixtures.candidate,
                retryScanToken,
            )
        cancelledSession.beginConnection(retryConnectionToken, retryGeneration)
        cancelledSession.beginAuthentication(
            retryConnectionToken,
            retryGeneration,
        )
        cancelledSession.completeConnection(
            VirtualBandFixtures.identity,
            retryConnectionToken,
            retryGeneration,
        )
        val replacementStaleCancel = assertFailsWith<BandException> {
            cancelledSession.cancelCapabilities(
                cancelledConnectionToken,
                cancelledGeneration,
            )
        }
        assertEquals(
            BandFailureCategory.STALE_CALLBACK,
            replacementStaleCancel.category,
        )
        val replacementNegotiation = cancelledSession.snapshot()
        assertEquals(
            BandSessionState.NEGOTIATING_CAPABILITIES,
            replacementNegotiation.state,
        )
        assertEquals(retryGeneration, replacementNegotiation.generation)
        assertTrue(
            cancellationRecorder.snapshot().contains(
                BandDiagnosticEvent(
                    BandDiagnosticKind.CAPABILITY,
                    BandDiagnosticOutcome.CANCELLED,
                ),
            ),
        )

        val timeoutRecorder = BandDiagnosticsRecorder()
        val (
            timedOutSession,
            timedOutGeneration,
            timedOutConnectionToken,
        ) = negotiatingSessionWithToken(timeoutRecorder)
        timedOutSession.failCapabilities(
            BandFailureCategory.TIMEOUT,
            timedOutConnectionToken,
            timedOutGeneration,
        )
        val timedOut = timedOutSession.snapshot()
        assertEquals(BandSessionState.RECOVERING, timedOut.state)
        assertEquals(timedOutGeneration + 1, timedOut.generation)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.CAPABILITY,
                BandDiagnosticOutcome.TIMED_OUT,
                failureCategory = BandFailureCategory.TIMEOUT,
            ),
            timeoutRecorder.snapshot().last(),
        )

        val (invalidSession, invalidGeneration, invalidConnectionToken) =
            negotiatingSessionWithToken()
        val invalidError = assertFailsWith<BandException> {
            invalidSession.failCapabilities(
                BandFailureCategory.STORAGE,
                invalidConnectionToken,
                invalidGeneration,
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, invalidError.category)
        val unchanged = invalidSession.snapshot()
        assertEquals(BandSessionState.NEGOTIATING_CAPABILITIES, unchanged.state)
        assertEquals(invalidGeneration, unchanged.generation)

        val (
            authenticationSession,
            authenticationGeneration,
            authenticationConnectionToken,
        ) = negotiatingSessionWithToken()
        authenticationSession.failCapabilities(
            BandFailureCategory.AUTHENTICATION,
            authenticationConnectionToken,
            authenticationGeneration,
        )
        assertEquals(
            BandSessionState.REJECTED,
            authenticationSession.snapshot().state,
        )

        val (
            securitySession,
            securityGeneration,
            securityConnectionToken,
        ) = negotiatingSessionWithToken()
        securitySession.failCapabilities(
            BandFailureCategory.SECURITY_FAILURE,
            securityConnectionToken,
            securityGeneration,
        )
        assertEquals(
            BandSessionState.SECURITY_FAILURE,
            securitySession.snapshot().state,
        )

        val (
            disconnectedSession,
            disconnectedGeneration,
            disconnectedConnectionToken,
        ) = negotiatingSessionWithToken()
        disconnectedSession.failCapabilities(
            BandFailureCategory.DISCONNECTED,
            disconnectedConnectionToken,
            disconnectedGeneration,
        )
        assertEquals(
            BandSessionState.RECOVERING,
            disconnectedSession.snapshot().state,
        )
        val recoveryScanToken = disconnectedSession.beginScan()
        assertEquals(
            disconnectedGeneration + 2,
            recoveryScanToken.generation,
        )
        assertEquals(
            BandSessionState.SCANNING,
            disconnectedSession.snapshot().state,
        )
    }

    @Test
    fun operationAuthenticationFailureInvalidatesSession() {
        val (session, generation) = readySession()
        val token = session.beginOperation(BandOperationClass.BATTERY)

        session.failOperation(token, BandFailureCategory.AUTHENTICATION)

        val failed = session.snapshot()
        assertEquals(BandSessionState.REJECTED, failed.state)
        assertEquals(generation + 1, failed.generation)
        assertNull(failed.activeOperation)
        val staleError = assertFailsWith<BandException> {
            session.cancelOperation(token)
        }
        assertEquals(BandFailureCategory.STALE_CALLBACK, staleError.category)
        val operationError = assertFailsWith<BandException> {
            session.beginOperation(BandOperationClass.BATTERY)
        }
        assertEquals(BandFailureCategory.INVALID_STATE, operationError.category)
        val retryScanToken = session.beginScan()
        val retryGeneration = retryScanToken.generation
        assertEquals(failed.generation + 1, retryGeneration)
    }

    @Test
    fun connectionCallbacksRejectForeignCandidateToken() {
        val session = BandSessionMachine()
        val scanToken = session.beginScan()
        val generation = scanToken.generation
        val selectedToken =
            session.selectCandidate(VirtualBandFixtures.candidate, scanToken)
        val foreignToken = BandConnectionToken(
            sessionNonce = selectedToken.sessionNonce,
            generation = selectedToken.generation,
            sequence = selectedToken.sequence,
            candidateHandle = "different-candidate",
        )

        val staleError = assertFailsWith<BandException> {
            session.beginConnection(foreignToken, generation)
        }
        assertEquals(BandFailureCategory.STALE_CALLBACK, staleError.category)
        assertEquals(
            BandSessionState.CANDIDATE_SELECTED,
            session.snapshot().state,
        )
        session.beginConnection(selectedToken, generation)
        assertEquals(BandSessionState.CONNECTING, session.snapshot().state)
    }

    @Test
    fun establishedAuthenticationFailuresTerminateSession() {
        val readyRecorder = BandDiagnosticsRecorder()
        val (
            readyFailureSession,
            readyGeneration,
            readyConnectionToken,
        ) = readySessionWithToken(readyRecorder)
        readyFailureSession.failEstablishedSession(
            BandFailureCategory.AUTHENTICATION,
            readyConnectionToken,
            readyGeneration,
        )
        val rejected = readyFailureSession.snapshot()
        assertEquals(BandSessionState.REJECTED, rejected.state)
        assertEquals(readyGeneration + 1, rejected.generation)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.AUTHENTICATION,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.AUTHENTICATION,
            ),
            readyRecorder.snapshot().last(),
        )

        val liveRecorder = BandDiagnosticsRecorder()
        val (
            liveFailureSession,
            liveGeneration,
            liveConnectionToken,
        ) = readySessionWithToken(liveRecorder)
        liveFailureSession.beginLive()
        val liveFailureEventCount = liveRecorder.snapshot().size
        liveFailureSession.failEstablishedSession(
            BandFailureCategory.SECURITY_FAILURE,
            liveConnectionToken,
            liveGeneration,
        )
        val secured = liveFailureSession.snapshot()
        assertEquals(BandSessionState.SECURITY_FAILURE, secured.state)
        assertEquals(liveGeneration + 1, secured.generation)
        assertFalse(secured.liveActive)
        assertEquals(
            listOf(
                BandDiagnosticEvent(
                    BandDiagnosticKind.LIVE,
                    BandDiagnosticOutcome.INTERRUPTED,
                    failureCategory = BandFailureCategory.SECURITY_FAILURE,
                ),
                BandDiagnosticEvent(
                    BandDiagnosticKind.AUTHENTICATION,
                    BandDiagnosticOutcome.REJECTED,
                    failureCategory = BandFailureCategory.SECURITY_FAILURE,
                ),
            ),
            liveRecorder.snapshot().drop(liveFailureEventCount),
        )

        val (
            invalidFailureSession,
            invalidGeneration,
            invalidConnectionToken,
        ) = readySessionWithToken()
        val invalidError = assertFailsWith<BandException> {
            invalidFailureSession.failEstablishedSession(
                BandFailureCategory.TIMEOUT,
                invalidConnectionToken,
                invalidGeneration,
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, invalidError.category)
        assertEquals(
            BandSessionState.READY,
            invalidFailureSession.snapshot().state,
        )
    }

    @Test
    fun establishedFailuresRejectForeignConnectionToken() {
        val (_, firstGeneration, firstToken) = readySessionWithToken()
        val (second, secondGeneration, secondToken) =
            readySessionWithToken()
        assertEquals(firstGeneration, secondGeneration)

        val stale = assertFailsWith<BandException> {
            second.failEstablishedSession(
                BandFailureCategory.AUTHENTICATION,
                firstToken,
                secondGeneration,
            )
        }
        assertEquals(BandFailureCategory.STALE_CALLBACK, stale.category)
        assertEquals(BandSessionState.READY, second.snapshot().state)

        second.failEstablishedSession(
            BandFailureCategory.AUTHENTICATION,
            secondToken,
            secondGeneration,
        )
        assertEquals(BandSessionState.REJECTED, second.snapshot().state)
    }

    @Test
    fun reconnectRequiresSessionBoundTokens() {
        val (session, generation, originalToken) = readySessionWithToken()
        val (foreignSession, foreignGeneration, foreignConnectionToken) =
            readySessionWithToken()
        assertEquals(generation, foreignGeneration)

        val foreignInterrupt = assertFailsWith<BandException> {
            session.interruptForReconnect(
                foreignConnectionToken,
                generation,
            )
        }
        assertEquals(
            BandFailureCategory.STALE_CALLBACK,
            foreignInterrupt.category,
        )
        assertEquals(BandSessionState.READY, session.snapshot().state)

        val reconnectAuthority =
            session.interruptForReconnect(originalToken, generation)
        val foreignReconnectAuthority =
            foreignSession.interruptForReconnect(
                foreignConnectionToken,
                foreignGeneration,
            )
        assertEquals(
            reconnectAuthority.generation,
            foreignReconnectAuthority.generation,
        )

        val foreignResume = assertFailsWith<BandException> {
            session.resumeAfterReconnect(
                foreignReconnectAuthority,
                reconnectAuthority.generation,
            )
        }
        assertEquals(
            BandFailureCategory.STALE_CALLBACK,
            foreignResume.category,
        )
        assertEquals(BandSessionState.RECOVERING, session.snapshot().state)

        val reconnectToken =
            session.resumeAfterReconnect(
                reconnectAuthority,
                reconnectAuthority.generation,
            )

        val stale = assertFailsWith<BandException> {
            session.failEstablishedSession(
                BandFailureCategory.AUTHENTICATION,
                originalToken,
                reconnectAuthority.generation,
            )
        }
        assertEquals(BandFailureCategory.STALE_CALLBACK, stale.category)
        assertEquals(BandSessionState.READY, session.snapshot().state)

        session.failEstablishedSession(
            BandFailureCategory.AUTHENTICATION,
            reconnectToken,
            reconnectAuthority.generation,
        )
        assertEquals(BandSessionState.REJECTED, session.snapshot().state)
    }

    @Test
    fun reconnectRecordsLiveInterruptionInOrder() {
        val recorder = BandDiagnosticsRecorder()
        val (session, generation, connectionToken) =
            readySessionWithToken(recorder)
        session.beginLive()
        session.beginOperation(BandOperationClass.BATTERY)
        val eventCount = recorder.snapshot().size

        session.interruptForReconnect(connectionToken, generation)

        assertEquals(
            listOf(
                BandDiagnosticEvent(
                    BandDiagnosticKind.COMMAND,
                    BandDiagnosticOutcome.INTERRUPTED,
                    failureCategory = BandFailureCategory.DISCONNECTED,
                    operationClass = BandOperationClass.BATTERY,
                ),
                BandDiagnosticEvent(
                    BandDiagnosticKind.LIVE,
                    BandDiagnosticOutcome.INTERRUPTED,
                ),
                BandDiagnosticEvent(
                    BandDiagnosticKind.RECONNECT,
                    BandDiagnosticOutcome.INTERRUPTED,
                ),
            ),
            recorder.snapshot().drop(eventCount),
        )
        val recovering = session.snapshot()
        assertEquals(BandSessionState.RECOVERING, recovering.state)
        assertFalse(recovering.liveActive)
        assertNull(recovering.activeOperation)
    }

    @Test
    fun operationDisconnectReturnsReconnectAuthority() {
        val recorder = BandDiagnosticsRecorder()
        val (session, generation) = readySession(recorder)
        session.beginLive()
        val operation = session.beginOperation(BandOperationClass.BATTERY)
        val eventCount = recorder.snapshot().size

        val reconnectAuthority = session.failOperation(
            operation,
            BandFailureCategory.DISCONNECTED,
        )
        requireNotNull(reconnectAuthority)

        assertEquals(
            listOf(
                BandDiagnosticEvent(
                    BandDiagnosticKind.COMMAND,
                    BandDiagnosticOutcome.FAILED,
                    failureCategory = BandFailureCategory.DISCONNECTED,
                    operationClass = BandOperationClass.BATTERY,
                ),
                BandDiagnosticEvent(
                    BandDiagnosticKind.LIVE,
                    BandDiagnosticOutcome.INTERRUPTED,
                ),
                BandDiagnosticEvent(
                    BandDiagnosticKind.RECONNECT,
                    BandDiagnosticOutcome.INTERRUPTED,
                    failureCategory = BandFailureCategory.DISCONNECTED,
                ),
            ),
            recorder.snapshot().drop(eventCount),
        )
        val recovering = session.snapshot()
        assertEquals(BandSessionState.RECOVERING, recovering.state)
        assertEquals(generation + 1, recovering.generation)
        assertFalse(recovering.liveActive)

        session.resumeAfterReconnect(
            reconnectAuthority,
            reconnectAuthority.generation,
        )
        assertEquals(BandSessionState.READY, session.snapshot().state)
    }

    @Test
    fun operationDerivedReconnectAuthorityRejectsCrossSessionAndReplay() {
        val (session, _) = readySession()
        val (foreignSession, _) = readySession()
        val authority = requireNotNull(
            session.failOperation(
                session.beginOperation(BandOperationClass.BATTERY),
                BandFailureCategory.DISCONNECTED,
            ),
        )
        val foreignAuthority = requireNotNull(
            foreignSession.failOperation(
                foreignSession.beginOperation(BandOperationClass.BATTERY),
                BandFailureCategory.DISCONNECTED,
            ),
        )
        assertEquals(authority.generation, foreignAuthority.generation)

        val crossSession = assertFailsWith<BandException> {
            session.resumeAfterReconnect(
                foreignAuthority,
                authority.generation,
            )
        }
        assertEquals(
            BandFailureCategory.STALE_CALLBACK,
            crossSession.category,
        )
        assertEquals(BandSessionState.RECOVERING, session.snapshot().state)

        session.resumeAfterReconnect(authority, authority.generation)
        val replay = assertFailsWith<BandException> {
            session.resumeAfterReconnect(authority, authority.generation)
        }
        assertEquals(BandFailureCategory.STALE_CALLBACK, replay.category)
        assertEquals(BandSessionState.READY, session.snapshot().state)
    }

    @Test
    fun firmwareDisconnectRequiresFullRediscovery() {
        val recorder = BandDiagnosticsRecorder()
        val capabilities = VirtualBandFixtures.capabilities.copy(
            capabilities = VirtualBandFixtures.capabilities.capabilities +
                BandCapability.FIRMWARE_UPDATE,
        )
        val (session, generation, connectionToken) =
            readySessionWithToken(recorder, capabilities)
        val operation = session.beginOperation(BandOperationClass.FIRMWARE)
        val reconnectError = assertFailsWith<BandException> {
            session.interruptForReconnect(connectionToken, generation)
        }
        assertEquals(
            BandFailureCategory.INVALID_STATE,
            reconnectError.category,
        )
        assertEquals(
            BandOperationClass.FIRMWARE,
            session.snapshot().activeOperation,
        )
        val eventCount = recorder.snapshot().size

        val reconnectAuthority = session.failOperation(
            operation,
            BandFailureCategory.DISCONNECTED,
        )

        assertNull(reconnectAuthority)
        assertEquals(
            listOf(
                BandDiagnosticEvent(
                    BandDiagnosticKind.FIRMWARE,
                    BandDiagnosticOutcome.INTERRUPTED,
                    failureCategory = BandFailureCategory.DISCONNECTED,
                    operationClass = BandOperationClass.FIRMWARE,
                ),
                BandDiagnosticEvent(
                    BandDiagnosticKind.RECONNECT,
                    BandDiagnosticOutcome.INTERRUPTED,
                    failureCategory = BandFailureCategory.DISCONNECTED,
                ),
            ),
            recorder.snapshot().drop(eventCount),
        )
        val recovering = session.snapshot()
        assertEquals(BandSessionState.RECOVERING, recovering.state)
        assertEquals(generation + 1, recovering.generation)

        val recoveryScanToken = session.beginScan()
        assertEquals(generation + 2, recoveryScanToken.generation)
        assertEquals(BandSessionState.SCANNING, session.snapshot().state)
    }

    @Test
    fun establishedFailureWaitsForPendingLiveReceipt() {
        val recorder = BandDiagnosticsRecorder()
        val (session, generation, connectionToken) =
            readySessionWithToken(recorder)
        val liveToken = session.beginLive()
        val acceptance = session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            liveToken,
            generation,
        )

        val busy = assertFailsWith<BandException> {
            session.failEstablishedSession(
                BandFailureCategory.AUTHENTICATION,
                connectionToken,
                generation,
            )
        }
        assertEquals(BandFailureCategory.BUSY, busy.category)
        val pending = session.snapshot()
        assertEquals(generation, pending.generation)
        assertEquals(BandSessionState.LIVE_COLLECTING, pending.state)
        assertTrue(pending.liveActive)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.AUTHENTICATION,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.BUSY,
            ),
            recorder.snapshot().last(),
        )

        session.acknowledgeLive(
            DurableLiveReceipt(
                acceptance = acceptance,
                committedSamples = acceptance.acceptedSamples.size,
                committed = true,
            ),
            generation,
        )
        session.failEstablishedSession(
            BandFailureCategory.AUTHENTICATION,
            connectionToken,
            generation,
        )
        assertEquals(BandSessionState.REJECTED, session.snapshot().state)
    }

    @Test
    fun stopLiveRejectsSupersededToken() {
        val (session, _) = readySession()
        val firstToken = session.beginLive()
        session.stopLive(firstToken)
        val secondToken = session.beginLive()

        val stale = assertFailsWith<BandException> {
            session.stopLive(firstToken)
        }
        assertEquals(BandFailureCategory.STALE_CALLBACK, stale.category)
        assertEquals(BandSessionState.LIVE_COLLECTING, session.snapshot().state)
        assertTrue(session.snapshot().liveActive)

        session.stopLive(secondToken)
        assertEquals(BandSessionState.READY, session.snapshot().state)
    }

    @Test
    fun liveAndHistoryStreamsAreNegotiatedPerLane() {
        val invalidRetention = assertFailsWith<BandException> {
            VirtualBandFixtures.capabilities.copy(
                historyDays = 0,
                liveStreams = setOf(BandStreamKind.HEART_RATE),
                historyStreams = setOf(BandStreamKind.HEART_RATE),
                streamSemantics =
                    VirtualBandFixtures.capabilities.streamSemantics.filter {
                        it.stream == BandStreamKind.HEART_RATE
                    },
            ).validate()
        }
        assertEquals(
            BandFailureCategory.INCOMPATIBLE,
            invalidRetention.category,
        )
        VirtualBandFixtures.capabilities.copy(
            historyDays = 0,
            liveStreams = setOf(BandStreamKind.HEART_RATE),
            historyStreams = emptySet(),
            streamSemantics =
                VirtualBandFixtures.capabilities.streamSemantics.filter {
                    it.lane == BandProvenanceLane.LIVE &&
                        it.stream == BandStreamKind.HEART_RATE
                },
        ).validate()

        val historyOnly = VirtualBandFixtures.capabilities.copy(
            liveStreams = emptySet(),
            historyStreams = setOf(BandStreamKind.HEART_RATE),
            streamSemantics =
                VirtualBandFixtures.capabilities.streamSemantics.filter {
                    it.lane == BandProvenanceLane.HISTORY &&
                        it.stream == BandStreamKind.HEART_RATE
                },
        )
        val (historySession, historyGeneration) =
            readySessionWithCapabilities(historyOnly)
        val liveError = assertFailsWith<BandException> {
            historySession.beginLive()
        }
        assertEquals(BandFailureCategory.UNSUPPORTED, liveError.category)

        val historyToken =
            historySession.beginOperation(BandOperationClass.HISTORY)
        val historyAcceptance = historySession.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            historyToken,
            historyGeneration,
        )
        historySession.acknowledgeHistory(
            DurableHistoryReceipt(
                acceptance = historyAcceptance,
                historyStateCommitted = true,
                committedSamples = historyAcceptance.acceptedSamples.size,
                committed = true,
            ),
            historyToken,
            historyGeneration,
        )
        historySession.completeOperation(historyToken)

        val liveOnly = VirtualBandFixtures.capabilities.copy(
            liveStreams = setOf(BandStreamKind.HEART_RATE),
            historyStreams = emptySet(),
            streamSemantics =
                VirtualBandFixtures.capabilities.streamSemantics.filter {
                    it.lane == BandProvenanceLane.LIVE &&
                        it.stream == BandStreamKind.HEART_RATE
                },
        )
        val (liveSession, liveGeneration) =
            readySessionWithCapabilities(liveOnly)
        val liveToken = liveSession.beginLive()
        val liveAcceptance = liveSession.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            liveToken,
            liveGeneration,
        )
        liveSession.acknowledgeLive(
            DurableLiveReceipt(
                acceptance = liveAcceptance,
                committedSamples = liveAcceptance.acceptedSamples.size,
                committed = true,
            ),
            liveGeneration,
        )
        liveSession.stopLive(liveToken)

        val historyError = assertFailsWith<BandException> {
            liveSession.beginOperation(BandOperationClass.HISTORY)
        }
        assertEquals(BandFailureCategory.UNSUPPORTED, historyError.category)
    }

    @Test
    fun overflowRangesAreValidatedAndBoundToDurableReceipt() {
        val (session, generation) = readySession()
        val token = session.beginOperation(BandOperationClass.HISTORY)
        val retained = BandHistoryRange(
            startDeviceTimeMilliseconds = 2_000,
            endDeviceTimeMilliseconds = 3_000,
        )
        val firstLost = BandHistoryRange(
            startDeviceTimeMilliseconds = 1_000,
            endDeviceTimeMilliseconds = 1_999,
        )
        val valid = VirtualBandFixtures.historyChunk.copy(
            overflowed = true,
            retainedRange = retained,
            firstLostRange = firstLost,
        )

        listOf(
            valid.copy(retainedRange = null),
            valid.copy(firstLostRange = null),
            valid.copy(
                overflowed = false,
                firstLostRange = firstLost,
            ),
            valid.copy(
                retainedRange = BandHistoryRange(
                    startDeviceTimeMilliseconds = 3_000,
                    endDeviceTimeMilliseconds = 2_000,
                ),
            ),
            valid.copy(
                firstLostRange = BandHistoryRange(
                    startDeviceTimeMilliseconds = 1_500,
                    endDeviceTimeMilliseconds = 2_000,
                ),
            ),
            valid.copy(
                firstLostRange = BandHistoryRange(
                    startDeviceTimeMilliseconds = 3_001,
                    endDeviceTimeMilliseconds = 3_500,
                ),
            ),
            valid.copy(
                batches = listOf(
                    VirtualBandFixtures.historyChunk.batches.first().copy(
                        samples = listOf(
                            VirtualBandFixtures.liveBatch.samples.first(),
                        ),
                    ),
                ),
            ),
        ).forEach { invalid ->
            val error = assertFailsWith<BandException> {
                session.stageHistoryChunk(invalid, token, generation)
            }
            assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
        }

        val acceptance =
            session.stageHistoryChunk(valid, token, generation)
        assertEquals(retained, acceptance.retainedRange)
        assertEquals(firstLost, acceptance.firstLostRange)

        val mismatchedAcceptance = HistoryAcceptance(
            chunkIdentity = acceptance.chunkIdentity,
            acknowledgementToken = acceptance.acknowledgementToken,
            nextCursor = acceptance.nextCursor,
            complete = acceptance.complete,
            overflowed = acceptance.overflowed,
            retainedRange = acceptance.retainedRange,
            firstLostRange = BandHistoryRange(
                startDeviceTimeMilliseconds = 0,
                endDeviceTimeMilliseconds = 999,
            ),
            acceptedSamples = acceptance.acceptedSamples,
            duplicateSamples = acceptance.duplicateSamples,
            sessionNonce = acceptance.sessionNonce,
            receiptSequence = acceptance.receiptSequence,
        )
        val mismatch = assertFailsWith<BandException> {
            session.acknowledgeHistory(
                DurableHistoryReceipt(
                    acceptance = mismatchedAcceptance,
                    historyStateCommitted = true,
                    committedSamples = acceptance.acceptedSamples.size,
                    committed = true,
                ),
                token,
                generation,
            )
        }
        assertEquals(BandFailureCategory.STORAGE, mismatch.category)

        val receipt = DurableHistoryReceipt(
            acceptance = acceptance,
            historyStateCommitted = true,
            committedSamples = acceptance.acceptedSamples.size,
            committed = true,
        )
        assertEquals(retained, receipt.retainedRange)
        assertEquals(firstLost, receipt.firstLostRange)
        session.acknowledgeHistory(receipt, token, generation)
        session.completeOperation(token)
    }

    @Test
    fun nonOverflowRetainedRangeRejectsSamplesOutsideEitherBound() {
        val (session, generation) = readySession()
        val token = session.beginOperation(BandOperationClass.HISTORY)
        val retained = BandHistoryRange(
            startDeviceTimeMilliseconds = 2_000,
            endDeviceTimeMilliseconds = 3_000,
        )
        val sourceBatch = VirtualBandFixtures.historyChunk.batches.first()
        val boundarySample = sourceBatch.samples.first()

        listOf(1_999L, 3_001L).forEach { timestamp ->
            val chunk = VirtualBandFixtures.historyChunk.copy(
                overflowed = false,
                retainedRange = retained,
                firstLostRange = null,
                batches = listOf(
                    sourceBatch.copy(
                        samples = listOf(
                            boundarySample.copy(
                                identity = boundarySample.identity.copy(
                                    deviceTimeMilliseconds = timestamp,
                                ),
                            ),
                        ),
                    ),
                ),
            )
            val rejected = assertFailsWith<BandException> {
                session.stageHistoryChunk(chunk, token, generation)
            }
            assertEquals(BandFailureCategory.INVALID_INPUT, rejected.category)
        }

        assertEquals(
            BandSessionState.HISTORY_COLLECTING,
            session.snapshot().state,
        )
        session.cancelOperation(token)
    }

    @Test
    fun terminalHistoryChunkPreservesCursor() {
        val (session, generation) = readySession()
        val store = VirtualBandStore()

        val firstToken =
            session.beginOperation(BandOperationClass.HISTORY)
        val firstChunk = BandHistoryChunk(
            chunkIdentity = "cursor-seed",
            previousCursor = null,
            nextCursor = "cursor-2",
            complete = false,
            overflowed = false,
            retainedRange = BandHistoryRange(
                startDeviceTimeMilliseconds = 2_000,
                endDeviceTimeMilliseconds = 3_000,
            ),
            firstLostRange = null,
            acknowledgementToken = "cursor-seed-ack",
            batches = VirtualBandFixtures.historyChunk.batches,
        )
        val firstAcceptance = session.stageHistoryChunk(
            firstChunk,
            firstToken,
            generation,
        )
        session.acknowledgeHistory(
            store.commit(firstAcceptance),
            firstToken,
            generation,
        )
        session.cancelOperation(firstToken)

        val terminalToken =
            session.beginOperation(BandOperationClass.HISTORY)
        val terminalChunk = BandHistoryChunk(
            chunkIdentity = "cursor-terminal",
            previousCursor = "cursor-2",
            nextCursor = null,
            complete = true,
            overflowed = false,
            retainedRange = firstChunk.retainedRange,
            firstLostRange = null,
            acknowledgementToken = "cursor-terminal-ack",
            batches = emptyList(),
        )
        val terminalAcceptance = session.stageHistoryChunk(
            terminalChunk,
            terminalToken,
            generation,
        )
        assertEquals("cursor-2", terminalAcceptance.nextCursor)
        session.acknowledgeHistory(
            store.commit(terminalAcceptance),
            terminalToken,
            generation,
        )
        session.completeOperation(terminalToken)

        assertEquals(
            "cursor-2",
            session.snapshot().acknowledgedHistoryCursor,
        )
        assertEquals(
            "cursor-2",
            session.historyCheckpoint()?.acknowledgedCursor,
        )
    }

    @Test
    fun pendingPersistenceBlocksLifecycleTerminalsUntilDrained() {
        val liveRecorder = BandDiagnosticsRecorder()
        val (liveSession, liveGeneration, liveConnectionToken) =
            readySessionWithToken(liveRecorder)
        val liveToken = liveSession.beginLive()
        val liveAcceptance = liveSession.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            liveToken,
            liveGeneration,
        )
        val batteryToken =
            liveSession.beginOperation(BandOperationClass.BATTERY)

        val liveFailure = assertFailsWith<BandException> {
            liveSession.failOperation(
                batteryToken,
                BandFailureCategory.AUTHENTICATION,
            )
        }
        assertEquals(BandFailureCategory.BUSY, liveFailure.category)
        val reconnectFailure = assertFailsWith<BandException> {
            liveSession.interruptForReconnect(
                liveConnectionToken,
                liveGeneration,
            )
        }
        assertEquals(BandFailureCategory.BUSY, reconnectFailure.category)
        val disconnectFailure = assertFailsWith<BandException> {
            liveSession.disconnect(
                BandDisconnectReason.USER_PAUSED,
                liveGeneration,
            )
        }
        assertEquals(BandFailureCategory.BUSY, disconnectFailure.category)
        val closeFailure = assertFailsWith<BandException> {
            liveSession.close()
        }
        assertEquals(BandFailureCategory.BUSY, closeFailure.category)
        val pendingLive = liveSession.snapshot()
        assertEquals(liveGeneration, pendingLive.generation)
        assertEquals(BandOperationClass.BATTERY, pendingLive.activeOperation)
        assertTrue(pendingLive.liveActive)

        liveSession.acknowledgeLive(
            DurableLiveReceipt(
                acceptance = liveAcceptance,
                committedSamples = liveAcceptance.acceptedSamples.size,
                committed = true,
            ),
            liveGeneration,
        )
        liveSession.failOperation(
            batteryToken,
            BandFailureCategory.AUTHENTICATION,
        )
        assertEquals(BandSessionState.REJECTED, liveSession.snapshot().state)

        val historyRecorder = BandDiagnosticsRecorder()
        val (
            historySession,
            historyGeneration,
            historyConnectionToken,
        ) = readySessionWithToken(historyRecorder)
        val historyToken =
            historySession.beginOperation(BandOperationClass.HISTORY)
        val historyAcceptance = historySession.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            historyToken,
            historyGeneration,
        )
        val cancelFailure = assertFailsWith<BandException> {
            historySession.cancelOperation(historyToken)
        }
        assertEquals(BandFailureCategory.BUSY, cancelFailure.category)
        val historyFailure = assertFailsWith<BandException> {
            historySession.failOperation(
                historyToken,
                BandFailureCategory.AUTHENTICATION,
            )
        }
        assertEquals(BandFailureCategory.BUSY, historyFailure.category)
        val historyReconnectFailure = assertFailsWith<BandException> {
            historySession.interruptForReconnect(
                historyConnectionToken,
                historyGeneration,
            )
        }
        assertEquals(
            BandFailureCategory.BUSY,
            historyReconnectFailure.category,
        )
        val historyDisconnectFailure = assertFailsWith<BandException> {
            historySession.disconnect(
                BandDisconnectReason.COLLECTOR_HANDOFF,
                historyGeneration,
            )
        }
        assertEquals(
            BandFailureCategory.BUSY,
            historyDisconnectFailure.category,
        )
        val historyCloseFailure = assertFailsWith<BandException> {
            historySession.close()
        }
        assertEquals(BandFailureCategory.BUSY, historyCloseFailure.category)

        historySession.acknowledgeHistory(
            DurableHistoryReceipt(
                acceptance = historyAcceptance,
                historyStateCommitted = true,
                committedSamples = historyAcceptance.acceptedSamples.size,
                committed = true,
            ),
            historyToken,
            historyGeneration,
        )
        historySession.failOperation(
            historyToken,
            BandFailureCategory.AUTHENTICATION,
        )
        assertEquals(
            BandSessionState.REJECTED,
            historySession.snapshot().state,
        )

        assertTrue(
            liveRecorder.snapshot().contains(
                BandDiagnosticEvent(
                    BandDiagnosticKind.COMMAND,
                    BandDiagnosticOutcome.REJECTED,
                    failureCategory = BandFailureCategory.BUSY,
                    operationClass = BandOperationClass.BATTERY,
                ),
            ),
        )
        assertTrue(
            liveRecorder.snapshot().contains(
                BandDiagnosticEvent(
                    BandDiagnosticKind.RECONNECT,
                    BandDiagnosticOutcome.REJECTED,
                    failureCategory = BandFailureCategory.BUSY,
                ),
            ),
        )
        assertTrue(
            liveRecorder.snapshot().contains(
                BandDiagnosticEvent(
                    BandDiagnosticKind.LIVE,
                    BandDiagnosticOutcome.REJECTED,
                    failureCategory = BandFailureCategory.BUSY,
                ),
            ),
        )
        assertTrue(
            historyRecorder.snapshot().contains(
                BandDiagnosticEvent(
                    BandDiagnosticKind.HISTORY,
                    BandDiagnosticOutcome.REJECTED,
                    failureCategory = BandFailureCategory.BUSY,
                ),
            ),
        )
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
    fun closeConformanceReportsOnlyActualActivePhases() {
        val active = BandConformanceRunner.run(
            "close_active_phase_terminal",
        )
        assertEquals(
            listOf(
                "live_and_history_started",
                "history_cancelled",
                "live_cancelled",
                "session_closed",
            ),
            active.events,
        )
        assertEquals(BandSessionState.CLOSED.wireValue, active.finalState)
        assertNull(active.failure)

        val idle = BandConformanceRunner.run("closed_session_terminal")
        assertEquals(
            listOf(
                "session_closed",
                "no_unmatched_connection_cancellation",
                "operation_rejected",
            ),
            idle.events,
        )
        assertEquals(BandSessionState.CLOSED.wireValue, idle.finalState)
        assertEquals(BandFailureCategory.CLOSED.wireValue, idle.failure)
    }

    @Test
    fun liveAndHistoryDurableReceiptsAreSerialized() {
        val overlappingHistory = VirtualBandFixtures.historyChunk.copy(
            chunkIdentity = "overlap",
            nextCursor = "overlap-cursor",
            acknowledgementToken = "overlap-ack",
            retainedRange = BandHistoryRange(
                startDeviceTimeMilliseconds =
                    VirtualBandFixtures.liveBatch.samples.first()
                        .identity.deviceTimeMilliseconds,
                endDeviceTimeMilliseconds =
                    VirtualBandFixtures.liveBatch.samples.first()
                        .identity.deviceTimeMilliseconds,
            ),
            batches = listOf(
                VirtualBandFixtures.historyChunk.batches.first().copy(
                    samples = VirtualBandFixtures.liveBatch.samples,
                ),
            ),
        )

        val (liveFirst, liveGeneration) = readySession()
        val liveFirstToken = liveFirst.beginLive()
        val liveAcceptance = liveFirst.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            liveFirstToken,
            liveGeneration,
        )
        val liveFirstHistoryToken =
            liveFirst.beginOperation(BandOperationClass.HISTORY)
        val pendingLiveError = assertFailsWith<BandException> {
            liveFirst.stageHistoryChunk(
                overlappingHistory,
                liveFirstHistoryToken,
                liveGeneration,
            )
        }
        assertEquals(BandFailureCategory.BUSY, pendingLiveError.category)
        liveFirst.acknowledgeLive(
            DurableLiveReceipt(
                acceptance = liveAcceptance,
                committedSamples = liveAcceptance.acceptedSamples.size,
                committed = true,
            ),
            liveGeneration,
        )
        val historyAfterLive = liveFirst.stageHistoryChunk(
            overlappingHistory,
            liveFirstHistoryToken,
            liveGeneration,
        )
        assertTrue(historyAfterLive.acceptedSamples.isEmpty())
        assertEquals(1, historyAfterLive.duplicateSamples)
        liveFirst.acknowledgeHistory(
            DurableHistoryReceipt(
                acceptance = historyAfterLive,
                historyStateCommitted = true,
                committedSamples = 0,
                committed = true,
            ),
            liveFirstHistoryToken,
            liveGeneration,
        )
        liveFirst.completeOperation(liveFirstHistoryToken)

        val (historyFirst, historyGeneration) = readySession()
        val historyFirstLiveToken = historyFirst.beginLive()
        val historyFirstToken =
            historyFirst.beginOperation(BandOperationClass.HISTORY)
        val historyAcceptance = historyFirst.stageHistoryChunk(
            overlappingHistory,
            historyFirstToken,
            historyGeneration,
        )
        val pendingHistoryError = assertFailsWith<BandException> {
            historyFirst.stageLiveBatch(
                VirtualBandFixtures.liveBatch,
                historyFirstLiveToken,
                historyGeneration,
            )
        }
        assertEquals(BandFailureCategory.BUSY, pendingHistoryError.category)
        historyFirst.acknowledgeHistory(
            DurableHistoryReceipt(
                acceptance = historyAcceptance,
                historyStateCommitted = true,
                committedSamples = historyAcceptance.acceptedSamples.size,
                committed = true,
            ),
            historyFirstToken,
            historyGeneration,
        )
        val liveAfterHistory = historyFirst.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            historyFirstLiveToken,
            historyGeneration,
        )
        assertTrue(liveAfterHistory.acceptedSamples.isEmpty())
        assertEquals(1, liveAfterHistory.duplicateSamples)
        historyFirst.acknowledgeLive(
            DurableLiveReceipt(
                acceptance = liveAfterHistory,
                committedSamples = 0,
                committed = true,
            ),
            historyGeneration,
        )
        historyFirst.completeOperation(historyFirstToken)
    }

    @Test
    fun invalidHistoryTokensRecordBoundedRejectionDiagnostics() {
        val recorder = BandDiagnosticsRecorder()
        val (session, generation) = readySession(recorder)
        val supersededToken =
            session.beginOperation(BandOperationClass.HISTORY)
        session.cancelOperation(supersededToken)
        val activeToken = session.beginOperation(BandOperationClass.HISTORY)
        val (foreignSession, _) = readySession()
        val foreignToken =
            foreignSession.beginOperation(BandOperationClass.HISTORY)

        var eventCount = recorder.snapshot().size
        val stageError = assertFailsWith<BandException> {
            session.stageHistoryChunk(
                VirtualBandFixtures.historyChunk,
                supersededToken,
                generation,
            )
        }
        assertEquals(BandFailureCategory.INVALID_STATE, stageError.category)
        var events = recorder.snapshot()
        assertEquals(eventCount + 1, events.size)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.HISTORY,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INVALID_STATE,
            ),
            events.last(),
        )

        eventCount = events.size
        val foreignStageError = assertFailsWith<BandException> {
            session.stageHistoryChunk(
                VirtualBandFixtures.historyChunk,
                foreignToken,
                generation,
            )
        }
        assertEquals(
            BandFailureCategory.STALE_CALLBACK,
            foreignStageError.category,
        )
        events = recorder.snapshot()
        assertEquals(eventCount + 1, events.size)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.HISTORY,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.STALE_CALLBACK,
            ),
            events.last(),
        )

        val acceptance = session.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            activeToken,
            generation,
        )
        val receipt = DurableHistoryReceipt(
            acceptance = acceptance,
            historyStateCommitted = true,
            committedSamples = acceptance.acceptedSamples.size,
            committed = true,
        )

        eventCount = recorder.snapshot().size
        val staleAcknowledgeError = assertFailsWith<BandException> {
            session.acknowledgeHistory(
                receipt,
                supersededToken,
                generation,
            )
        }
        assertEquals(
            BandFailureCategory.INVALID_STATE,
            staleAcknowledgeError.category,
        )
        events = recorder.snapshot()
        assertEquals(eventCount + 1, events.size)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.HISTORY,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.INVALID_STATE,
            ),
            events.last(),
        )

        eventCount = events.size
        val acknowledgeError = assertFailsWith<BandException> {
            session.acknowledgeHistory(
                receipt,
                foreignToken,
                generation,
            )
        }
        assertEquals(
            BandFailureCategory.STALE_CALLBACK,
            acknowledgeError.category,
        )
        events = recorder.snapshot()
        assertEquals(eventCount + 1, events.size)
        assertEquals(
            BandDiagnosticEvent(
                BandDiagnosticKind.HISTORY,
                BandDiagnosticOutcome.REJECTED,
                failureCategory = BandFailureCategory.STALE_CALLBACK,
            ),
            events.last(),
        )
        val unchanged = session.snapshot()
        assertEquals(BandSessionState.HISTORY_COLLECTING, unchanged.state)
        assertEquals(BandOperationClass.HISTORY, unchanged.activeOperation)

        session.acknowledgeHistory(receipt, activeToken, generation)
        session.completeOperation(activeToken)
    }

    @Test
    fun historyDiagnosticsDistinguishStagingFromDurableCompletion() {
        val recorder = BandDiagnosticsRecorder()
        val (session, generation) = readySession(recorder)
        val token = session.beginOperation(BandOperationClass.HISTORY)

        val acceptance = session.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            token,
            generation,
        )
        var events = recorder.snapshot()
        var event = events.last()
        assertEquals(BandDiagnosticKind.HISTORY, event.kind)
        assertEquals(BandDiagnosticOutcome.STAGED, event.outcome)
        val beganIndex = events.indexOfLast {
            it.kind == BandDiagnosticKind.HISTORY &&
                it.outcome == BandDiagnosticOutcome.BEGAN
        }
        val stagedIndex = events.indexOfLast {
            it.kind == BandDiagnosticKind.HISTORY &&
                it.outcome == BandDiagnosticOutcome.STAGED
        }
        assertTrue(beganIndex >= 0)
        assertTrue(stagedIndex >= 0)
        assertTrue(
            beganIndex <
                stagedIndex,
        )

        session.acknowledgeHistory(
            DurableHistoryReceipt(
                acceptance = acceptance,
                historyStateCommitted = true,
                committedSamples = acceptance.acceptedSamples.size,
                committed = true,
            ),
            token,
            generation,
        )
        events = recorder.snapshot()
        event = events.last()
        assertEquals(BandDiagnosticKind.HISTORY, event.kind)
        assertEquals(BandDiagnosticOutcome.COMPLETED, event.outcome)
        val completedIndex = events.indexOfLast {
            it.kind == BandDiagnosticKind.HISTORY &&
                it.outcome == BandDiagnosticOutcome.COMPLETED
        }
        assertTrue(completedIndex >= 0)
        assertTrue(stagedIndex < completedIndex)
        session.completeOperation(token)

        val cancellationRecorder = BandDiagnosticsRecorder()
        val (cancellationSession, cancellationGeneration) =
            readySession(cancellationRecorder)
        val cancellationToken =
            cancellationSession.beginOperation(BandOperationClass.HISTORY)
        val cancellationAcceptance = cancellationSession.stageHistoryChunk(
            VirtualBandFixtures.historyChunk,
            cancellationToken,
            cancellationGeneration,
        )
        val pendingCancel = assertFailsWith<BandException> {
            cancellationSession.cancelOperation(cancellationToken)
        }
        assertEquals(BandFailureCategory.BUSY, pendingCancel.category)
        cancellationSession.acknowledgeHistory(
            DurableHistoryReceipt(
                acceptance = cancellationAcceptance,
                historyStateCommitted = true,
                committedSamples = cancellationAcceptance.acceptedSamples.size,
                committed = true,
            ),
            cancellationToken,
            cancellationGeneration,
        )
        cancellationSession.cancelOperation(cancellationToken)
        assertTrue(
            cancellationRecorder.snapshot().any {
                it.kind == BandDiagnosticKind.HISTORY &&
                    it.outcome == BandDiagnosticOutcome.REJECTED &&
                    it.failureCategory == BandFailureCategory.BUSY
            },
        )
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

        val coalescingRecorder = BandDiagnosticsRecorder(4)
        coalescingRecorder.recordCoalescingConsecutive(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.COMPLETED,
                countBucket = BandCountBucket.ONE,
            ),
        )
        coalescingRecorder.recordCoalescingConsecutive(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.COMPLETED,
                countBucket = BandCountBucket.OVER_HUNDRED,
            ),
        )
        coalescingRecorder.record(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.FAILED,
                failureCategory = BandFailureCategory.STORAGE,
            ),
        )
        coalescingRecorder.recordCoalescingConsecutive(
            BandDiagnosticEvent(
                BandDiagnosticKind.LIVE,
                BandDiagnosticOutcome.COMPLETED,
                countBucket = BandCountBucket.ONE,
            ),
        )
        val coalesced = coalescingRecorder.snapshot()
        assertEquals(3, coalesced.size)
        assertEquals(BandCountBucket.OVER_HUNDRED, coalesced[0].countBucket)
        assertEquals(BandFailureCategory.STORAGE, coalesced[1].failureCategory)
        assertEquals(BandDiagnosticOutcome.COMPLETED, coalesced[2].outcome)
    }

    @Test
    fun connectionEvidenceSurvivesSustainedLivePersistence() {
        val recorder = BandDiagnosticsRecorder(16)
        val (session, generation) = readySession(recorder)
        val liveToken = session.beginLive()

        repeat(130) { offset ->
            val sample = BandSample(
                identity = BandSampleIdentity(
                    stream = BandStreamKind.HEART_RATE,
                    sequence = 10_000L + offset,
                    deviceTimeMilliseconds = 10_000L + offset,
                ),
                value = 72.0,
                unit = BandUnit.BEATS_PER_MINUTE,
                quality = BandSampleQuality.ACCEPTED,
            )
            val acceptance = session.stageLiveBatch(
                BandSampleBatch(
                    sourceIdentity =
                        VirtualBandFixtures.identity.sourceIdentity,
                    lane = BandProvenanceLane.LIVE,
                    parserRevision = "parser-v1",
                    calibrationRevision = "calibration-v1",
                    samples = listOf(sample),
                ),
                liveToken,
                generation,
            )
            session.acknowledgeLive(
                DurableLiveReceipt(
                    acceptance = acceptance,
                    committedSamples = acceptance.acceptedSamples.size,
                    committed = true,
                ),
                generation,
            )
        }
        session.stopLive(liveToken)

        val events = recorder.snapshot()
        assertEquals(
            listOf(
                BandDiagnosticOutcome.BEGAN,
                BandDiagnosticOutcome.COMPLETED,
            ),
            events
                .filter { it.kind == BandDiagnosticKind.CONNECTION }
                .map(BandDiagnosticEvent::outcome),
        )
        val connectionCompletedIndex = events.indexOfFirst {
            it.kind == BandDiagnosticKind.CONNECTION &&
                it.outcome == BandDiagnosticOutcome.COMPLETED
        }
        val authenticationBeganIndex = events.indexOfFirst {
            it.kind == BandDiagnosticKind.AUTHENTICATION &&
                it.outcome == BandDiagnosticOutcome.BEGAN
        }
        assertTrue(connectionCompletedIndex >= 0)
        assertTrue(connectionCompletedIndex < authenticationBeganIndex)
        assertEquals(
            2,
            events.count {
                it.kind == BandDiagnosticKind.LIVE &&
                    it.outcome == BandDiagnosticOutcome.COMPLETED
            },
        )

        val restartLiveToken = session.beginLive()
        val restartAcceptance = session.stageLiveBatch(
            BandSampleBatch(
                sourceIdentity = VirtualBandFixtures.identity.sourceIdentity,
                lane = BandProvenanceLane.LIVE,
                parserRevision = "parser-v1",
                calibrationRevision = "calibration-v1",
                samples = listOf(
                    BandSample(
                        identity = BandSampleIdentity(
                            stream = BandStreamKind.HEART_RATE,
                            sequence = 20_000,
                            deviceTimeMilliseconds = 20_000,
                        ),
                        value = 72.0,
                        unit = BandUnit.BEATS_PER_MINUTE,
                        quality = BandSampleQuality.ACCEPTED,
                    ),
                ),
            ),
            restartLiveToken,
            generation,
        )
        session.acknowledgeLive(
            DurableLiveReceipt(
                acceptance = restartAcceptance,
                committedSamples = 1,
                committed = true,
            ),
            generation,
        )
        session.stopLive(restartLiveToken)
        assertEquals(
            4,
            recorder.snapshot().count {
                it.kind == BandDiagnosticKind.LIVE &&
                    it.outcome == BandDiagnosticOutcome.COMPLETED
            },
        )
    }

    @Test
    fun authenticationTransitionDiagnosticsStayOrdered() {
        val recorder = BandDiagnosticsRecorder(64)
        val session = BandSessionMachine(recorder)
        val scanToken = session.beginScan()
        val generation = scanToken.generation
        val connectionToken =
            session.selectCandidate(VirtualBandFixtures.candidate, scanToken)
        session.beginConnection(connectionToken, generation)
        session.beginAuthentication(connectionToken, generation)
        session.completeConnection(
            VirtualBandFixtures.identity,
            connectionToken,
            generation,
        )

        val events = recorder.snapshot()
        val connectionCompleted = events.indexOfFirst {
            it.kind == BandDiagnosticKind.CONNECTION &&
                it.outcome == BandDiagnosticOutcome.COMPLETED
        }
        val authenticationBegan = events.indexOfFirst {
            it.kind == BandDiagnosticKind.AUTHENTICATION &&
                it.outcome == BandDiagnosticOutcome.BEGAN
        }
        val authenticationCompleted = events.indexOfFirst {
            it.kind == BandDiagnosticKind.AUTHENTICATION &&
                it.outcome == BandDiagnosticOutcome.COMPLETED
        }
        assertTrue(connectionCompleted >= 0)
        assertTrue(connectionCompleted < authenticationBegan)
        assertTrue(authenticationBegan < authenticationCompleted)
    }

    @Test
    fun closeCancelsOnlyActualActiveLifecycleAndOperationPhases() {
        fun assertCloseAdds(
            expectedKinds: List<BandDiagnosticKind>,
            operationClasses: Map<
                BandDiagnosticKind,
                BandOperationClass
                > = emptyMap(),
            prepare: (BandDiagnosticsRecorder) -> BandSessionMachine,
        ) {
            val recorder = BandDiagnosticsRecorder()
            val session = prepare(recorder)
            val before = recorder.snapshot()

            session.close()

            assertEquals(BandSessionState.CLOSED, session.snapshot().state)
            assertEquals(
                expectedKinds.map {
                    BandDiagnosticEvent(
                        it,
                        BandDiagnosticOutcome.CANCELLED,
                        operationClass = operationClasses[it],
                    )
                },
                recorder.snapshot().drop(before.size),
            )
        }

        assertCloseAdds(emptyList()) { BandSessionMachine(it) }
        assertCloseAdds(listOf(BandDiagnosticKind.DISCOVERY)) { recorder ->
            BandSessionMachine(recorder).also {
                it.beginScan()
            }
        }
        assertCloseAdds(listOf(BandDiagnosticKind.CONNECTION)) { recorder ->
            BandSessionMachine(recorder).also { session ->
                val scanToken = session.beginScan()
                val generation = scanToken.generation
                val token = session.selectCandidate(
                    VirtualBandFixtures.candidate,
                    scanToken,
                )
                session.beginConnection(token, generation)
            }
        }
        assertCloseAdds(listOf(BandDiagnosticKind.AUTHENTICATION)) { recorder ->
            BandSessionMachine(recorder).also { session ->
                val scanToken = session.beginScan()
                val generation = scanToken.generation
                val token = session.selectCandidate(
                    VirtualBandFixtures.candidate,
                    scanToken,
                )
                session.beginConnection(token, generation)
                session.beginAuthentication(token, generation)
            }
        }
        assertCloseAdds(listOf(BandDiagnosticKind.CAPABILITY)) { recorder ->
            negotiatingSession(recorder).first
        }
        assertCloseAdds(listOf(BandDiagnosticKind.RECONNECT)) { recorder ->
            val (session, generation, connectionToken) =
                readySessionWithToken(recorder)
            session.interruptForReconnect(connectionToken, generation)
            session
        }
        assertCloseAdds(listOf(BandDiagnosticKind.LIVE)) { recorder ->
            readySession(recorder).first.also {
                it.beginLive()
            }
        }
        listOf(
            BandOperationClass.BATTERY to BandDiagnosticKind.COMMAND,
            BandOperationClass.HISTORY to BandDiagnosticKind.HISTORY,
        ).forEach { (operationClass, diagnosticKind) ->
            assertCloseAdds(
                listOf(diagnosticKind),
                mapOf(diagnosticKind to operationClass),
            ) { recorder ->
                readySession(recorder).first.also {
                    it.beginOperation(operationClass)
                }
            }
        }
        assertCloseAdds(
            listOf(BandDiagnosticKind.FIRMWARE),
            mapOf(
                BandDiagnosticKind.FIRMWARE to
                    BandOperationClass.FIRMWARE,
            ),
        ) { recorder ->
            readySessionWithCapabilities(
                VirtualBandFixtures.capabilities.copy(
                    capabilities =
                        VirtualBandFixtures.capabilities.capabilities +
                            BandCapability.FIRMWARE_UPDATE,
                ),
                recorder,
            ).first.also {
                it.beginOperation(BandOperationClass.FIRMWARE)
            }
        }
        assertCloseAdds(
            listOf(BandDiagnosticKind.COMMAND, BandDiagnosticKind.LIVE),
            mapOf(
                BandDiagnosticKind.COMMAND to
                    BandOperationClass.BATTERY,
            ),
        ) { recorder ->
            readySession(recorder).first.also {
                it.beginLive()
                it.beginOperation(BandOperationClass.BATTERY)
            }
        }
    }

    @Test
    fun unknownScenarioFailsClosed() {
        val error = assertFailsWith<BandException> {
            BandConformanceRunner.run("not-a-scenario")
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
    }

    private fun readySession(
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder(),
    ): Pair<BandSessionMachine, Long> {
        val (session, generation) = readySessionWithToken(diagnostics)
        return session to generation
    }

    private fun readySessionWithToken(
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder(),
        capabilities: BandCapabilityReport =
            VirtualBandFixtures.capabilities,
        identity: BandIdentity = VirtualBandFixtures.identity,
    ): Triple<BandSessionMachine, Long, BandConnectionToken> {
        val session = BandSessionMachine(diagnostics)
        val scanToken = session.beginScan()
        val generation = scanToken.generation
        val connectionToken =
            session.selectCandidate(VirtualBandFixtures.candidate, scanToken)
        session.beginConnection(connectionToken, generation)
        session.beginAuthentication(connectionToken, generation)
        session.completeConnection(
            identity,
            connectionToken,
            generation,
        )
        session.acceptCapabilities(
            capabilities,
            connectionToken,
            generation,
        )
        return Triple(session, generation, connectionToken)
    }

    private fun readySessionWithCapabilities(
        capabilities: BandCapabilityReport,
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder(),
    ): Pair<BandSessionMachine, Long> {
        val session = BandSessionMachine(diagnostics)
        val scanToken = session.beginScan()
        val generation = scanToken.generation
        val connectionToken =
            session.selectCandidate(VirtualBandFixtures.candidate, scanToken)
        session.beginConnection(connectionToken, generation)
        session.beginAuthentication(connectionToken, generation)
        session.completeConnection(
            VirtualBandFixtures.identity,
            connectionToken,
            generation,
        )
        session.acceptCapabilities(
            capabilities,
            connectionToken,
            generation,
        )
        return session to generation
    }

    private fun negotiatingSession(
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder(),
    ): Pair<BandSessionMachine, Long> {
        val (session, generation) = negotiatingSessionWithToken(diagnostics)
        return session to generation
    }

    private fun negotiatingSessionWithToken(
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder(),
    ): Triple<BandSessionMachine, Long, BandConnectionToken> {
        val session = BandSessionMachine(diagnostics)
        val scanToken = session.beginScan()
        val generation = scanToken.generation
        val connectionToken =
            session.selectCandidate(VirtualBandFixtures.candidate, scanToken)
        session.beginConnection(connectionToken, generation)
        session.beginAuthentication(connectionToken, generation)
        session.completeConnection(
            VirtualBandFixtures.identity,
            connectionToken,
            generation,
        )
        return Triple(session, generation, connectionToken)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> nullElementList(): List<T> =
        listOf(null) as List<T>

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> nullElementSet(): Set<T> =
        setOf(null) as Set<T>
}
