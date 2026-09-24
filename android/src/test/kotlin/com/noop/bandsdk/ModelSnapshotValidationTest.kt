package com.noop.bandsdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

    @Test
    fun publicValidationNormalizesRawJavaCollectionFailures() {
        val wrongCapabilities =
            JavaNullCollections.setWithWrongType<BandCapability>(
                BandStreamKind.HEART_RATE,
            )
        val wrongLiveStreams =
            JavaNullCollections.setWithWrongType<BandStreamKind>(
                BandCapability.HEART_RATE,
            )
        val wrongOperations =
            JavaNullCollections.setWithWrongType<BandOperationClass>(
                BandCapability.HEART_RATE,
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
                ).validate()
            },
            {
                VirtualBandFixtures.capabilities.copy(
                    liveStreams = wrongLiveStreams,
                ).validate()
            },
            {
                VirtualBandFixtures.capabilities.copy(
                    operationsAllowedDuringLive = wrongOperations,
                ).validate()
            },
            {
                VirtualBandFixtures.capabilities.copy(
                    streamSemantics = wrongSemantics,
                ).validate()
            },
            {
                VirtualBandFixtures.liveBatch.copy(
                    samples = wrongSamples,
                ).validate(BandProvenanceLane.LIVE)
            },
            {
                VirtualBandFixtures.historyChunk.copy(
                    batches = wrongBatches,
                ).validate()
            },
            {
                VirtualBandFixtures.historyChunk.copy(
                    batches = listOf(
                        VirtualBandFixtures.historyChunk.batches.first().copy(
                            samples = wrongSamples,
                        ),
                    ),
                ).validate()
            },
            {
                BandHistoryCheckpoint(
                    sourceIdentity =
                        VirtualBandFixtures.identity.sourceIdentity,
                    acknowledgedCursor = null,
                    lastHistoryComplete = null,
                    durableSampleIdentities = wrongIdentities,
                ).validate()
            },
        ).forEach(::assertInvalidInput)
    }

    @Test
    fun payloadFingerprintIsStableAndNormalizesSignedZero() {
        val identity = BandSampleIdentity(
            stream = BandStreamKind.ACCELERATION,
            sequence = 1,
            deviceTimeMilliseconds = 1,
        )
        val positiveZero = BandSample(
            identity = identity,
            value = 0.0,
            unit = BandUnit.GRAVITY,
            quality = BandSampleQuality.ACCEPTED,
        )
        val negativeZero = positiveZero.copy(value = -0.0)

        assertTrue(positiveZero.hasEquivalentPayload(negativeZero))
        assertEquals(
            "a10e64de6afbbee0a9f4c4da6ab1e54" +
                "a3c5a77adc94ba6c76092b1cf419a0046",
            BandSampleFingerprint(positiveZero).payloadFingerprint,
        )
        assertEquals(positiveZero, negativeZero)
        assertEquals(positiveZero.hashCode(), negativeZero.hashCode())
        assertEquals(
            BandSampleFingerprint(positiveZero),
            BandSampleFingerprint(negativeZero),
        )
    }

    @Test
    fun checkpointRetainsThePreviousFourArgumentJvmConstructor() {
        val constructor = BandHistoryCheckpoint::class.java.getConstructor(
            String::class.java,
            String::class.java,
            java.lang.Boolean::class.java,
            Set::class.java,
        )
        val checkpoint = constructor.newInstance(
            VirtualBandFixtures.identity.sourceIdentity,
            null,
            null,
            emptySet<BandSampleIdentity>(),
        )
        assertTrue(checkpoint.durableSampleFingerprints.isEmpty())
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
