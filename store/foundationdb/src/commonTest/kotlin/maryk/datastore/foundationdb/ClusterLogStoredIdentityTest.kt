package maryk.datastore.foundationdb

import maryk.core.properties.types.Bytes
import maryk.datastore.foundationdb.clusterlog.ClusterLogDeletion
import maryk.datastore.foundationdb.clusterlog.ClusterUpdateLog
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.Versionstamp
import maryk.lib.bytes.combineToByteArray
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class ClusterLogStoredIdentityTest {
    @Test
    fun malformedIdentityDoesNotEscapeItsScopeOrStallContinuation() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "cluster-log-identity", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )
        try {
            for (legacy in listOf(false, true)) {
                val prefix = combineToByteArray(store.getTableDirs(1u).modelPrefix, "identity-$legacy".encodeToByteArray())
                val log = ClusterUpdateLog(
                    logPrefix = prefix,
                    consumerPrefix = prefix + byteArrayOf(1),
                    headPrefix = null,
                    headGroupCount = 0,
                    hlcMaxPrefix = null,
                    shardCount = 1,
                    originId = "reader",
                    dataModelsById = mapOf(1u to SimpleMarykModel),
                    consumerId = "reader",
                    retention = ClusterUpdateLog.retentionDefault(),
                )
                val writer = ClusterUpdateLog(
                    logPrefix = prefix,
                    consumerPrefix = prefix + byteArrayOf(1),
                    headPrefix = null,
                    headGroupCount = 0,
                    hlcMaxPrefix = null,
                    shardCount = 1,
                    originId = "writer",
                    dataModelsById = mapOf(1u to SimpleMarykModel),
                    consumerId = "writer",
                    retention = ClusterUpdateLog.retentionDefault(),
                )
                val recordKey = Bytes(ByteArray(16) { 3 })
                val corrupt = writer.encodeValue(1u, ClusterLogDeletion(recordKey, 99uL, false), SimpleMarykModel)
                val valid = ClusterLogDeletion(recordKey, 11uL, false)
                fun rowKey(version: ULong, commit: Long): ByteArray {
                    val clock = ByteArray(8).also { it[7] = version.toByte() }
                    val stamp = Versionstamp.complete(ByteArray(10).also { it[7] = commit.toByte() })
                    val fields = if (legacy) {
                        listOf(0, 1L, clock, recordKey.bytes, "writer".encodeToByteArray(), stamp)
                    } else {
                        listOf(0, 1L, stamp, clock, recordKey.bytes, "writer".encodeToByteArray())
                    }
                    return combineToByteArray(prefix, Tuple.from(*fields.toTypedArray()).pack())
                }
                val corruptKey = rowKey(10uL, 41L)
                store.runTransaction { tr ->
                    tr.set(corruptKey, corrupt)
                    tr.set(rowKey(11uL, 42L), writer.encodeValue(1u, valid, SimpleMarykModel))
                }
                var cursor: ByteArray? = null
                if (!legacy) {
                    val activation = store.runTransaction { log.tailOnce(it, 0, 1u, cursor, 1) }
                    assertEquals(0, activation.decoded.size)
                    cursor = assertNotNull(activation.lastKey)
                }
                val skipped = store.runTransaction { log.tailOnce(it, 0, 1u, cursor, 1) }
                assertEquals(0, skipped.decoded.size)
                cursor = assertNotNull(skipped.lastKey)
                val next = store.runTransaction { log.tailOnce(it, 0, 1u, cursor, 1) }
                assertEquals(valid, next.decoded.single().update)
                assertEquals(42L, next.decoded.single().commitVersion)
                val end = store.runTransaction { log.tailOnce(it, 0, 1u, assertNotNull(next.lastKey), 1) }
                assertEquals(0, end.decoded.size)
                if (!legacy) assertNull(end.lastKey)
                assertContentEquals(corrupt, store.runTransaction { it.get(corruptKey).awaitResult() })
            }
        } finally {
            store.close()
        }
    }
}
