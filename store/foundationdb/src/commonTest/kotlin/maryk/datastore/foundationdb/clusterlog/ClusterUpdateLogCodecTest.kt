package maryk.datastore.foundationdb.clusterlog

import maryk.core.clock.HLC
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.Change
import maryk.core.query.pairs.with
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.Versionstamp
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareTo
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClusterUpdateLogCodecTest {
    private val modelId = 2u
    private val models = mapOf(modelId to SimpleMarykModel)

    private fun newLog() = ClusterUpdateLog(
        logPrefix = byteArrayOf(1, 2, 3),
        consumerPrefix = byteArrayOf(4, 5, 6),
        headPrefix = byteArrayOf(7, 8, 9),
        headGroupCount = 2,
        hlcPrefix = byteArrayOf(10, 11, 12),
        hlcMaxPrefix = byteArrayOf(13, 14, 15),
        shardCount = 4,
        originId = "node-a",
        dataModelsById = models,
        consumerId = "consumer-a",
        retention = ClusterUpdateLog.retentionDefault(),
    )

    @Test
    fun roundTripAddition() {
        val log = newLog()
        val key = ByteArray(16) { 7 }
        val version = HLC().timestamp
        val values = SimpleMarykModel.create { value with "hello" }
        val update = ClusterLogAddition(Bytes(key), version, values)

        val encoded = log.encodeValue(modelId, update, SimpleMarykModel)
        val decoded = log.decodeValue(encoded)

        assertNotNull(decoded)
        assertEquals("node-a", decoded.header.origin)
        assertEquals(modelId, decoded.header.modelId)
        assertEquals(update, decoded.update)
    }

    @Test
    fun roundTripChange() {
        val log = newLog()
        val key = ByteArray(16) { 9 }
        val version = HLC().timestamp
        val changes = listOf(Change(SimpleMarykModel { value::ref } with "changed"))
        val update = ClusterLogChange(Bytes(key), version, changes)

        val encoded = log.encodeValue(modelId, update, SimpleMarykModel)
        val decoded = log.decodeValue(encoded)

        assertNotNull(decoded)
        assertEquals(update, decoded.update)
    }

    @Test
    fun roundTripDeletion() {
        val log = newLog()
        val key = ByteArray(16) { 1 }
        val version = HLC().timestamp
        val update = ClusterLogDeletion(Bytes(key), version, hardDelete = true)

        val encoded = log.encodeValue(modelId, update, SimpleMarykModel)
        val decoded = log.decodeValue(encoded)

        assertNotNull(decoded)
        assertEquals(update, decoded.update)
    }

    @Test
    fun v2LogKeysFollowCommitOrderWhenHlcDecreases() {
        val log = newLog()
        val key = Bytes(ByteArray(16) { 1 })
        val earlierCommit = log.buildLogKey(
            modelId = modelId,
            update = ClusterLogDeletion(key, version = 20uL, hardDelete = false),
            versionstamp = Versionstamp.complete(transactionVersion(41L)),
        )
        val laterCommit = log.buildLogKey(
            modelId = modelId,
            update = ClusterLogDeletion(key, version = 10uL, hardDelete = false),
            versionstamp = Versionstamp.complete(transactionVersion(42L)),
        )

        assertTrue(earlierCommit < laterCommit)
    }

    @Test
    fun v2EntryExposesCommitVersionAndCursorCanAdvanceToLaterCommit() {
        val log = newLog()
        val key = Bytes(ByteArray(16) { 2 })
        val update = ClusterLogDeletion(key, version = 10uL, hardDelete = true)
        val firstKey = log.buildLogKey(
            modelId = modelId,
            update = update,
            versionstamp = Versionstamp.complete(transactionVersion(41L)),
        )
        val laterKey = log.buildLogKey(
            modelId = modelId,
            update = update,
            versionstamp = Versionstamp.complete(transactionVersion(42L)),
        )
        val decoded = log.decodeEntry(
            key = firstKey,
            value = log.encodeValue(modelId, update, SimpleMarykModel),
        )

        assertNotNull(decoded)
        assertEquals(41L, decoded.commitVersion)
        assertTrue(combineCursorSuccessor(firstKey) < laterKey)
    }

    @Test
    fun legacyEntryExposesCommitVersionForMixedFormatOrdering() {
        val log = newLog()
        val key = Bytes(ByteArray(16) { 3 })
        val update = ClusterLogDeletion(key, version = 10uL, hardDelete = false)
        val legacyKey = combineToByteArray(
            byteArrayOf(1, 2, 3),
            Tuple.from(
                0,
                modelId.toLong(),
                ByteArray(8),
                key.bytes,
                "node-a".encodeToByteArray(),
                Versionstamp.complete(transactionVersion(1L)),
            ).pack(),
        )
        val decoded = log.decodeEntry(
            key = legacyKey,
            value = log.encodeValue(modelId, update, SimpleMarykModel),
        )

        assertNotNull(decoded)
        assertEquals(1L, decoded.commitVersion)
    }

    @Test
    fun malformedV2CursorIsRejectedInsteadOfRewound() {
        val malformedCursor = Tuple.from(
            "cluster-log-cursor-v2",
            "not-a-key",
            null,
            byteArrayOf(1),
            false,
        ).pack()

        assertFailsWith<IllegalArgumentException> {
            newLog().cursorIsBeforeCutoff(0, modelId, malformedCursor, cutoff = 10uL)
        }
    }

    @Test
    fun foreignShardCursorIsRejectedInsteadOfScanningAnotherRange() {
        val foreignLegacyKey = combineToByteArray(
            byteArrayOf(1, 2, 3),
            Tuple.from(1, modelId.toLong(), ByteArray(8)).pack(),
        )
        val foreignCursor = Tuple.from(
            "cluster-log-cursor-v2",
            foreignLegacyKey,
            null,
            null,
            false,
        ).pack()

        assertFailsWith<IllegalArgumentException> {
            newLog().cursorIsBeforeCutoff(0, modelId, foreignCursor, cutoff = 10uL)
        }
    }

    @Test
    fun corruptPayloadLengthDoesNotOverflowBoundsCheck() {
        val encoded = byteArrayOf(
            0, 0, // origin length
            0, 0, 0, modelId.toByte(),
            ClusterLogUpdate.TYPE_DELETION,
            0, 0, 0, 0, 0, 0, 0, 1, // version
            0, 0, // key length
            0x7f, -1, -1, -1 // payload length
        )

        assertNull(newLog().decodeValue(encoded))
    }

    @Test
    fun trailingBytesAfterPayloadAreRejected() {
        val update = ClusterLogDeletion(Bytes(ByteArray(16) { 1 }), HLC().timestamp, hardDelete = false)
        val encoded = newLog().encodeValue(modelId, update, SimpleMarykModel)

        assertNull(newLog().decodeValue(encoded + byteArrayOf(0)))
    }

    @Test
    fun truncatedAdditionPayloadIsRejectedWithoutThrowing() {
        val encoded = byteArrayOf(
            0, 0, // origin length
            0, 0, 0, modelId.toByte(),
            ClusterLogUpdate.TYPE_ADDITION,
            0, 0, 0, 0, 0, 0, 0, 1, // version
            0, 0, // key length
            0, 0, 0, 2, // payload length
            10, -128 // string field tag with truncated varint length
        )

        assertNull(newLog().decodeValue(encoded))
    }

    @Test
    fun oversizedKeyDoesNotEncodeWithTruncatedLength() {
        val update = ClusterLogDeletion(Bytes(ByteArray(0x1_0000)), HLC().timestamp, hardDelete = false)

        assertFailsWith<IllegalArgumentException> {
            newLog().encodeValue(modelId, update, SimpleMarykModel)
        }
    }

    @Test
    fun clusterLogByteLengthRejectsOverflow() {
        assertFailsWith<IllegalArgumentException> {
            Int.MAX_VALUE.checkedClusterLogByteLengthPlus(1)
        }
    }

    @Test
    fun versionstampOffsetRejectsNegativeTrailer() {
        assertFailsWith<IllegalArgumentException> {
            packWithAdjustedVersionstampOffset(
                prefix = byteArrayOf(1),
                packedWithVersionstamp = byteArrayOf(0, 0, 0, 0, -1, -1, -1, -1)
            )
        }
    }

    @Test
    fun invalidClusterLogConfigurationFailsEarly() {
        assertFailsWith<IllegalArgumentException> {
            ClusterUpdateLog(
                logPrefix = byteArrayOf(1, 2, 3),
                consumerPrefix = byteArrayOf(4, 5, 6),
                headPrefix = byteArrayOf(7, 8, 9),
                headGroupCount = 2,
                hlcPrefix = byteArrayOf(10, 11, 12),
                hlcMaxPrefix = byteArrayOf(13, 14, 15),
                shardCount = 0,
                originId = "node-a",
                dataModelsById = models,
                consumerId = "consumer-a",
                retention = ClusterUpdateLog.retentionDefault(),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ClusterUpdateLog(
                logPrefix = byteArrayOf(1, 2, 3),
                consumerPrefix = byteArrayOf(4, 5, 6),
                headPrefix = byteArrayOf(7, 8, 9),
                headGroupCount = 2,
                hlcPrefix = byteArrayOf(10, 11, 12),
                hlcMaxPrefix = byteArrayOf(13, 14, 15),
                shardCount = 4,
                originId = "a".repeat(0x1_0000),
                dataModelsById = models,
                consumerId = "consumer-a",
                retention = ClusterUpdateLog.retentionDefault(),
            )
        }
    }

    private fun transactionVersion(version: Long): ByteArray = ByteArray(10).also { bytes ->
        for (index in 0 until Long.SIZE_BYTES) {
            bytes[index] = (version ushr ((Long.SIZE_BYTES - index - 1) * Byte.SIZE_BITS)).toByte()
        }
    }

    private fun combineCursorSuccessor(cursor: ByteArray): ByteArray = cursor + byteArrayOf(0)
}
