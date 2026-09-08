package maryk.datastore.rocksdb

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.models.key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.createTestDBFolder
import maryk.datastore.test.updateListenerTester
import maryk.deleteFolder
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReplicatedSoftDeleteHistoryTest {
    @Test
    fun initiallyDeletedReplicationDoesNotEnterLiveFilteredScan() = runTest {
        val folder = createTestDBFolder("replicated-initial-soft-delete-live-scan")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = mapOf(1u to Log),
        )
        val values = Log("replicated deleted", timestamp = LocalDateTime(2026, 9, 5, 0, 0))
        val key = Log.key(values)

        try {
            updateListenerTester(store, Log.scan(allowTableScan = true), 2) { responses ->
                assertIs<InitialValuesUpdate<Log>>(responses[0].await()).also {
                    assertTrue(it.values.isEmpty())
                }

                store.processUpdate(
                    UpdateResponse(
                        Log,
                        AdditionUpdate(key, 10uL, 10uL, 0, true, values),
                    )
                )

                delay(250)
                assertFalse(responses[1].isCompleted)
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun initiallyDeletedReplicationEmitsDeletedAdditionToLiveUnfilteredScan() = runTest {
        val folder = createTestDBFolder("replicated-initial-soft-delete-unfiltered-live-scan")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = mapOf(1u to Log),
        )
        val values = Log("replicated deleted", timestamp = LocalDateTime(2026, 9, 5, 0, 0))
        val key = Log.key(values)

        try {
            updateListenerTester(store, Log.scan(filterSoftDeleted = false, allowTableScan = true), 2) { responses ->
                assertIs<InitialValuesUpdate<Log>>(responses[0].await()).also {
                    assertTrue(it.values.isEmpty())
                }

                store.processUpdate(
                    UpdateResponse(
                        Log,
                        AdditionUpdate(key, 10uL, 10uL, 0, true, values),
                    )
                )

                assertIs<AdditionUpdate<Log>>(responses[1].await()).also {
                    assertTrue(it.isDeleted)
                }
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun initiallyDeletedReplicationRemainsDeletedBeforeRestoreAfterReopen() = runTest {
        val folder = createTestDBFolder("replicated-initial-soft-delete-history")
        val values = Log("replicated deleted", timestamp = LocalDateTime(2026, 9, 5, 0, 0))
        val key = Log.key(values)
        var store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = mapOf(1u to Log),
        )

        try {
            store.processUpdate(
                UpdateResponse(
                    Log,
                    AdditionUpdate(key, 10uL, 10uL, 0, true, values),
                )
            )
            store.processUpdate(
                UpdateResponse(
                    Log,
                    ChangeUpdate(key, 20uL, 0, listOf(ObjectSoftDeleteChange(false))),
                )
            )
            store.close()

            store = RocksDBDataStore.open(
                relativePath = folder,
                dataModelsById = mapOf(1u to Log),
            )

            val beforeRestore = store.execute(
                Log.get(key, toVersion = 10uL, filterSoftDeleted = false)
            ).values.single()
            assertEquals(values, beforeRestore.values)
            assertTrue(beforeRestore.isDeleted)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}
