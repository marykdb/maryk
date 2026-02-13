package maryk.datastore.foundationdb.clusterlog

import maryk.core.clock.HLC
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.Change
import maryk.core.query.pairs.with
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ClusterUpdateLogCodecTest {
    private val modelId = 2u
    private val models = mapOf(modelId to SimpleMarykModel)

    private fun newLog() = ClusterUpdateLog(
        logPrefix = byteArrayOf(1, 2, 3),
        consumerPrefix = byteArrayOf(4, 5, 6),
        headPrefix = byteArrayOf(7, 8, 9),
        headGroupCount = 2,
        hlcPrefix = byteArrayOf(10, 11, 12),
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
}
