package maryk.datastore.indexeddb

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt16
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.indexeddb.processors.hardDeleteHistoryRowReadObserver

class HardDeleteBoundaryTest {
    @Test
    fun maximumHardDeleteKeyIsReturnedOnce() = runTest {
        verifyMaximumHardDelete(excludeByVersion = false)
    }

    @Test
    fun excludedMaximumHardDeleteKeyTerminates() = runTest {
        verifyMaximumHardDelete(excludeByVersion = true)
    }

    private suspend fun verifyMaximumHardDelete(excludeByVersion: Boolean) = withContext(Dispatchers.Default) {
        installIndexedDbForTests()
        val databaseName = "maryk-hard-delete-boundary-${Random.nextInt()}"
        val store = IndexedDbDataStore.open(
            databaseName = databaseName,
            dataModelsById = mapOf(1u to HardDeleteRecord),
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
        )
        var rowsRead = 0
        try {
            val added = assertIs<AddSuccess<HardDeleteRecord>>(store.execute(HardDeleteRecord.add(HardDeleteRecord.create {
                id with 65535u.toUShort()
            })).statuses.single())
            val key = added.key
            val deleteVersion = assertIs<DeleteSuccess<HardDeleteRecord>>(
                store.execute(HardDeleteRecord.delete(key, hardDelete = true)).statuses.single()
            ).version
            hardDeleteHistoryRowReadObserver = {
                check(++rowsRead <= 20) { "Hard-delete scan exceeded its row budget (possible loop)" }
            }
            val updates = withTimeout(10.seconds) {
                store.execute(HardDeleteRecord.scanUpdates(
                    fromVersion = if (excludeByVersion) deleteVersion + 1u else 1uL,
                    toVersion = if (excludeByVersion) deleteVersion + 1u else deleteVersion,
                    limit = 10u,
                ))
            }
            assertTrue(updates.updates.filterIsInstance<OrderedKeysUpdate<HardDeleteRecord>>().single().keys.isEmpty())
            val removals = updates.updates.filterIsInstance<RemovalUpdate<HardDeleteRecord>>()
            assertEquals(if (excludeByVersion) emptyList() else listOf(key), removals.map { it.key })
            removals.forEach {
                assertEquals(deleteVersion, it.version)
                assertEquals(HardDelete, it.reason)
            }
            assertTrue(rowsRead <= 2, "Expected at most two history reads, got $rowsRead")
        } finally {
            hardDeleteHistoryRowReadObserver = null
            store.close()
        }
    }
}

private object HardDeleteRecord : RootDataModel<HardDeleteRecord>(
    keyDefinition = { HardDeleteRecord.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by number(1u, type = UInt16, final = true)
}
