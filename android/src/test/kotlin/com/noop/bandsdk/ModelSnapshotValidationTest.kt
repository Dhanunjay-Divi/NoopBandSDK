package com.noop.bandsdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelSnapshotValidationTest {
    @Test
    fun rawJavaListsNormalizeWrongTypeNullAndTraversalFailures() {
        val inputs = listOf(
            JavaNullCollections.listWithWrongType<BandSample>(
                BandStreamKind.HEART_RATE,
            ),
            JavaNullCollections.list<BandSample>(),
            JavaNullCollections.classCastTraversalList<BandSample>(),
        )

        inputs.forEach { samples ->
            assertInvalidInput {
                VirtualBandFixtures.liveBatch.copy(
                    samples = samples,
                ).immutableSnapshot()
            }
        }
    }

    @Test
    fun rawJavaSetsNormalizeWrongTypeNullAndTraversalFailures() {
        val inputs = listOf(
            JavaNullCollections.setWithWrongType<BandSampleIdentity>(
                BandStreamKind.HEART_RATE,
            ),
            JavaNullCollections.set<BandSampleIdentity>(),
            JavaNullCollections.classCastTraversalSet<BandSampleIdentity>(),
        )

        inputs.forEach { identities ->
            assertInvalidInput {
                BandHistoryCheckpoint(
                    sourceIdentity =
                        VirtualBandFixtures.identity.sourceIdentity,
                    acknowledgedCursor = null,
                    lastHistoryComplete = null,
                    durableSampleIdentities = identities,
                ).immutableSnapshot()
            }
        }
    }

    @Test
    fun everyModelSnapshotChecksItsConcreteElementType() {
        val wrongCapabilities =
            JavaNullCollections.setWithWrongType<BandCapability>(
                BandStreamKind.HEART_RATE,
            )
        val wrongSemantics =
            JavaNullCollections.listWithWrongType<BandStreamSemantics>(
                BandSampleQuality.ACCEPTED,
            )
        val wrongSamples =
            JavaNullCollections.listWithWrongType<BandSample>(
                BandSampleQuality.ACCEPTED,
            )
        val wrongBatches =
            JavaNullCollections.listWithWrongType<BandSampleBatch>(
                BandSampleQuality.ACCEPTED,
            )
        val wrongIdentities =
            JavaNullCollections.setWithWrongType<BandSampleIdentity>(
                BandSampleQuality.ACCEPTED,
            )

        listOf<() -> Unit>(
            {
                VirtualBandFixtures.capabilities.copy(
                    capabilities = wrongCapabilities,
                ).immutableSnapshot()
            },
            {
                VirtualBandFixtures.capabilities.copy(
                    streamSemantics = wrongSemantics,
                ).immutableSnapshot()
            },
            {
                VirtualBandFixtures.liveBatch.copy(
                    samples = wrongSamples,
                ).immutableSnapshot()
            },
            {
                VirtualBandFixtures.historyChunk.copy(
                    batches = wrongBatches,
                ).immutableSnapshot()
            },
            {
                BandHistoryCheckpoint(
                    sourceIdentity =
                        VirtualBandFixtures.identity.sourceIdentity,
                    acknowledgedCursor = null,
                    lastHistoryComplete = null,
                    durableSampleIdentities = wrongIdentities,
                ).immutableSnapshot()
            },
        ).forEach(::assertInvalidInput)
    }

    private fun assertInvalidInput(block: () -> Unit) {
        val error = assertFailsWith<BandException>(block = block)
        assertEquals(BandFailureCategory.INVALID_INPUT, error.category)
        assertEquals(
            BandFailureCategory.INVALID_INPUT.wireValue,
            error.message,
        )
    }
}
