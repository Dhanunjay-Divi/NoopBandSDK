package com.noop.bandsdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelRenderingPrivacyTest {
    @Test
    fun pairingCandidateRendersOnlyNonSensitiveDisplayState() {
        val candidate = BandPairingCandidate(
            handle = CANDIDATE_HANDLE_SENTINEL,
            compatible = true,
            identifyEligible = false,
        )
        val expected =
            "BandPairingCandidate(" +
                "compatible=true, " +
                "identifyEligible=false" +
                ")"
        val rendered = listOf(
            candidate.toString(),
            "$candidate",
            listOf(candidate).toString(),
        )

        rendered.forEach {
            assertEquals(
                if (it.startsWith("[")) "[$expected]" else expected,
                it,
            )
            assertTrue(it.contains("compatible=true"))
            assertTrue(it.contains("identifyEligible=false"))
            assertPrivacySafe(it)
        }
    }

    @Test
    fun modelsRenderTypeNamesWithoutSensitiveContent() {
        modelFixtures().forEach { (name, value) ->
            val direct = value.toString()
            val interpolated = "$value"

            assertEquals(name, direct)
            assertEquals(name, interpolated)
            assertPrivacySafe(direct)
            assertPrivacySafe(interpolated)
        }
    }

    @Test
    fun nestedCollectionsRenderModelsWithoutSensitiveContent() {
        val fixtures = modelFixtures()
        val rendered = listOf(
            mapOf("models" to fixtures.map { it.second }),
        ).toString()

        fixtures.forEach { (name, _) ->
            assertTrue(rendered.contains(name))
        }
        assertPrivacySafe(rendered)
    }

    private fun modelFixtures(): List<Pair<String, Any>> {
        val sampleIdentity = BandSampleIdentity(
            stream = BandStreamKind.HEART_RATE,
            sequence = SAMPLE_SEQUENCE_SENTINEL,
            deviceTimeMilliseconds = SAMPLE_TIME_SENTINEL,
        )
        val sample = BandSample(
            identity = sampleIdentity,
            value = SAMPLE_VALUE_SENTINEL,
            unit = BandUnit.BEATS_PER_MINUTE,
            quality = BandSampleQuality.DEGRADED,
        )
        val batch = BandSampleBatch(
            sourceIdentity = SOURCE_SENTINEL,
            lane = BandProvenanceLane.HISTORY,
            parserRevision = PARSER_SENTINEL,
            calibrationRevision = CALIBRATION_SENTINEL,
            samples = listOf(sample),
        )
        val retainedRange = BandHistoryRange(
            startDeviceTimeMilliseconds = RETAINED_START_SENTINEL,
            endDeviceTimeMilliseconds = RETAINED_END_SENTINEL,
        )
        val chunk = BandHistoryChunk(
            chunkIdentity = CHUNK_SENTINEL,
            previousCursor = PREVIOUS_CURSOR_SENTINEL,
            nextCursor = NEXT_CURSOR_SENTINEL,
            complete = false,
            overflowed = true,
            retainedRange = retainedRange,
            firstLostRange = BandHistoryRange(
                startDeviceTimeMilliseconds = LOST_START_SENTINEL,
                endDeviceTimeMilliseconds = LOST_END_SENTINEL,
            ),
            acknowledgementToken = ACKNOWLEDGEMENT_SENTINEL,
            batches = listOf(batch),
        )

        return listOf(
            "BandIdentity" to BandIdentity(
                sourceIdentity = SOURCE_SENTINEL,
                hardwareRevision = HARDWARE_SENTINEL,
                firmwareVersion = FIRMWARE_SENTINEL,
                protocolVersion = PROTOCOL_SENTINEL,
                wrapperRevision = WRAPPER_SENTINEL,
            ),
            "BandSampleIdentity" to sampleIdentity,
            "BandSample" to sample,
            "BandSampleBatch" to batch,
            "BandHistoryRange" to retainedRange,
            "BandHistoryChunk" to chunk,
            "BandHistoryCheckpoint" to BandHistoryCheckpoint(
                sourceIdentity = SOURCE_SENTINEL,
                acknowledgedCursor = CHECKPOINT_CURSOR_SENTINEL,
                lastHistoryComplete = false,
                durableSampleIdentities = setOf(sampleIdentity),
            ),
            "BandSessionSnapshot" to BandSessionSnapshot(
                state = BandSessionState.HISTORY_COLLECTING,
                generation = 42,
                activeOperation = BandOperationClass.HISTORY,
                liveActive = false,
                acknowledgedHistoryCursor = CHECKPOINT_CURSOR_SENTINEL,
                durableSampleCount = 1,
            ),
        )
    }

    private fun assertPrivacySafe(rendered: String) {
        sensitiveSentinels.forEach { sentinel ->
            assertFalse(rendered.contains(sentinel))
        }
        sensitiveLabels.forEach { label ->
            assertFalse(rendered.contains(label))
        }
    }

    private companion object {
        const val CANDIDATE_HANDLE_SENTINEL =
            "AA:BB:CC:DD:EE:FF/vendor-sentinel-5a27"
        const val SOURCE_SENTINEL = "sentinel-source-4f91"
        const val HARDWARE_SENTINEL = "sentinel-hardware-2d73"
        const val FIRMWARE_SENTINEL = "sentinel-firmware-8a15"
        const val PROTOCOL_SENTINEL = "sentinel-protocol-6c24"
        const val WRAPPER_SENTINEL = "sentinel-wrapper-0b82"
        const val PARSER_SENTINEL = "sentinel-parser-7e36"
        const val CALIBRATION_SENTINEL = "sentinel-calibration-5d47"
        const val CHUNK_SENTINEL = "sentinel-chunk-3a59"
        const val PREVIOUS_CURSOR_SENTINEL = "sentinel-cursor-previous-1c68"
        const val NEXT_CURSOR_SENTINEL = "sentinel-cursor-next-9b04"
        const val ACKNOWLEDGEMENT_SENTINEL = "sentinel-ack-2f76"
        const val CHECKPOINT_CURSOR_SENTINEL = "sentinel-cursor-checkpoint-8d13"
        const val SAMPLE_SEQUENCE_SENTINEL = 9_876_543_210L
        const val SAMPLE_TIME_SENTINEL = 1_977_777_777_777L
        const val SAMPLE_VALUE_SENTINEL = 173.625
        const val LOST_START_SENTINEL = 1_977_777_770_000L
        const val LOST_END_SENTINEL = 1_977_777_771_000L
        const val RETAINED_START_SENTINEL = 1_977_777_772_000L
        const val RETAINED_END_SENTINEL = 1_977_777_773_000L

        val sensitiveSentinels = listOf(
            CANDIDATE_HANDLE_SENTINEL,
            SOURCE_SENTINEL,
            HARDWARE_SENTINEL,
            FIRMWARE_SENTINEL,
            PROTOCOL_SENTINEL,
            WRAPPER_SENTINEL,
            PARSER_SENTINEL,
            CALIBRATION_SENTINEL,
            CHUNK_SENTINEL,
            PREVIOUS_CURSOR_SENTINEL,
            NEXT_CURSOR_SENTINEL,
            ACKNOWLEDGEMENT_SENTINEL,
            CHECKPOINT_CURSOR_SENTINEL,
            SAMPLE_SEQUENCE_SENTINEL.toString(),
            SAMPLE_TIME_SENTINEL.toString(),
            SAMPLE_VALUE_SENTINEL.toString(),
            LOST_START_SENTINEL.toString(),
            LOST_END_SENTINEL.toString(),
            RETAINED_START_SENTINEL.toString(),
            RETAINED_END_SENTINEL.toString(),
        )

        val sensitiveLabels = listOf(
            "handle=",
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
        )
    }
}
