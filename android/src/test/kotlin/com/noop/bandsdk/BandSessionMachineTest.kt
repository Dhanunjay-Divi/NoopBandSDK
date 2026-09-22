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
        session.beginLive()
        val liveAcceptance = session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
            generation,
        )
        session.acknowledgeLive(store.commit(liveAcceptance), generation)
        session.stopLive()

        val token = session.beginOperation(BandOperationClass.HISTORY)
        val sourceBatch = VirtualBandFixtures.historyChunk.batches.first()
        val chunk = VirtualBandFixtures.historyChunk.copy(
            batches = listOf(
                sourceBatch.copy(
                    samples = listOf(duplicate, fresh),
                ),
                sourceBatch.copy(
                    parserRevision = "parser-v2",
                    calibrationRevision = "calibration-v2",
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
        assertEquals("parser-v2", acceptance.acceptedSamples[1].parserRevision)
        assertEquals(
            "calibration-v2",
            acceptance.acceptedSamples[1].calibrationRevision,
        )
        assertEquals(2, acceptance.duplicateSamples)
        assertEquals(2, store.commit(acceptance).committedSamples)
    }

    @Test
    fun acceptanceCollectionsCannotReduceDurableReceiptRequirements() {
        val (session, generation) = readySession()
        session.beginLive()
        val liveAcceptance = session.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
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
        session.stopLive()

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
            session.stageLiveBatch(oversized, generation)
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

        session.beginLive()
        error = assertFailsWith<BandException> {
            session.stageLiveBatch(oversized, generation)
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
        val (cancelledSession, cancelledGeneration) =
            negotiatingSession(cancellationRecorder)

        cancelledSession.cancelCapabilities(cancelledGeneration)
        val cancelled = cancelledSession.snapshot()
        assertEquals(BandSessionState.IDLE, cancelled.state)
        assertEquals(cancelledGeneration + 1, cancelled.generation)
        val staleCancelError = assertFailsWith<BandException> {
            cancelledSession.cancelCapabilities(cancelledGeneration)
        }
        assertEquals(
            BandFailureCategory.STALE_CALLBACK,
            staleCancelError.category,
        )
        val staleError = assertFailsWith<BandException> {
            cancelledSession.failCapabilities(
                BandFailureCategory.TIMEOUT,
                cancelledGeneration,
            )
        }
        assertEquals(BandFailureCategory.STALE_CALLBACK, staleError.category)
        val retryGeneration = cancelledSession.beginScan()
        assertEquals(cancelled.generation + 1, retryGeneration)
        cancelledSession.selectCandidate(
            VirtualBandFixtures.candidate,
            retryGeneration,
        )
        cancelledSession.beginConnection(retryGeneration)
        cancelledSession.beginAuthentication(retryGeneration)
        cancelledSession.completeConnection(
            VirtualBandFixtures.identity,
            retryGeneration,
        )
        val replacementStaleCancel = assertFailsWith<BandException> {
            cancelledSession.cancelCapabilities(cancelledGeneration)
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
        val (timedOutSession, timedOutGeneration) =
            negotiatingSession(timeoutRecorder)
        timedOutSession.failCapabilities(
            BandFailureCategory.TIMEOUT,
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

        val (invalidSession, invalidGeneration) = negotiatingSession()
        val invalidError = assertFailsWith<BandException> {
            invalidSession.failCapabilities(
                BandFailureCategory.STORAGE,
                invalidGeneration,
            )
        }
        assertEquals(BandFailureCategory.INVALID_INPUT, invalidError.category)
        val unchanged = invalidSession.snapshot()
        assertEquals(BandSessionState.NEGOTIATING_CAPABILITIES, unchanged.state)
        assertEquals(invalidGeneration, unchanged.generation)

        val (authenticationSession, authenticationGeneration) =
            negotiatingSession()
        authenticationSession.failCapabilities(
            BandFailureCategory.AUTHENTICATION,
            authenticationGeneration,
        )
        assertEquals(
            BandSessionState.REJECTED,
            authenticationSession.snapshot().state,
        )

        val (securitySession, securityGeneration) = negotiatingSession()
        securitySession.failCapabilities(
            BandFailureCategory.SECURITY_FAILURE,
            securityGeneration,
        )
        assertEquals(
            BandSessionState.SECURITY_FAILURE,
            securitySession.snapshot().state,
        )

        val (disconnectedSession, disconnectedGeneration) =
            negotiatingSession()
        disconnectedSession.failCapabilities(
            BandFailureCategory.DISCONNECTED,
            disconnectedGeneration,
        )
        assertEquals(
            BandSessionState.RECOVERING,
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
        val retryGeneration = session.beginScan()
        assertEquals(failed.generation + 1, retryGeneration)
    }

    @Test
    fun pendingPersistenceBlocksLifecycleTerminalsUntilDrained() {
        val liveRecorder = BandDiagnosticsRecorder()
        val (liveSession, liveGeneration) = readySession(liveRecorder)
        liveSession.beginLive()
        val liveAcceptance = liveSession.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
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
            liveSession.interruptForReconnect(liveGeneration)
        }
        assertEquals(BandFailureCategory.BUSY, reconnectFailure.category)
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
        val (historySession, historyGeneration) =
            readySession(historyRecorder)
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
            historySession.interruptForReconnect(historyGeneration)
        }
        assertEquals(
            BandFailureCategory.BUSY,
            historyReconnectFailure.category,
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
                    BandDiagnosticKind.CONNECTION,
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
    fun liveAndHistoryDurableReceiptsAreSerialized() {
        val overlappingHistory = VirtualBandFixtures.historyChunk.copy(
            chunkIdentity = "overlap",
            nextCursor = "overlap-cursor",
            acknowledgementToken = "overlap-ack",
            batches = listOf(
                VirtualBandFixtures.historyChunk.batches.first().copy(
                    samples = VirtualBandFixtures.liveBatch.samples,
                ),
            ),
        )

        val (liveFirst, liveGeneration) = readySession()
        liveFirst.beginLive()
        val liveAcceptance = liveFirst.stageLiveBatch(
            VirtualBandFixtures.liveBatch,
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
        historyFirst.beginLive()
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
        val session = BandSessionMachine(diagnostics)
        val generation = session.beginScan()
        session.selectCandidate(VirtualBandFixtures.candidate, generation)
        session.beginConnection(generation)
        session.beginAuthentication(generation)
        session.completeConnection(VirtualBandFixtures.identity, generation)
        session.acceptCapabilities(VirtualBandFixtures.capabilities, generation)
        return session to generation
    }

    private fun negotiatingSession(
        diagnostics: BandDiagnosticsRecorder = BandDiagnosticsRecorder(),
    ): Pair<BandSessionMachine, Long> {
        val session = BandSessionMachine(diagnostics)
        val generation = session.beginScan()
        session.selectCandidate(VirtualBandFixtures.candidate, generation)
        session.beginConnection(generation)
        session.beginAuthentication(generation)
        session.completeConnection(VirtualBandFixtures.identity, generation)
        return session to generation
    }
}
