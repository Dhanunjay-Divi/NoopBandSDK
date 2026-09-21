package com.noop.bandsdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BandSessionMachineTest {
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
}
