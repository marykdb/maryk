package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class ConcurrentLocalMutationVersionTest {
    @Test
    fun localChangeAdvancesBeyondAnotherOpenWritersVersion() = runTest(timeout = 3.minutes) {
        withTwoWriters { first, second, key, future ->
            val changed = assertIs<ChangeSuccess<SimpleMarykModel>>(
                second.execute(SimpleMarykModel.change(key.change(Change(SimpleMarykModel.ref { value } with "happy changed"))))
                    .statuses.single()
            )
            assertTrue(changed.version > future, "Local change must advance beyond the committed record version")
            val latest = first.execute(SimpleMarykModel.get(key)).values.single()
            assertEquals(changed.version, latest.lastVersion)
            assertEquals("happy changed", latest.values { value })
            val historic = first.execute(SimpleMarykModel.get(key, toVersion = changed.version)).values.single()
            assertEquals("happy changed", historic.values { value })
        }
    }

    @Test
    fun localDeleteAdvancesBeyondAnotherOpenWritersVersion() = runTest(timeout = 3.minutes) {
        withTwoWriters { first, second, key, future ->
            val deleted = assertIs<DeleteSuccess<SimpleMarykModel>>(
                second.execute(SimpleMarykModel.delete(key)).statuses.single()
            )
            assertTrue(deleted.version > future, "Local delete must advance beyond the committed record version")
            assertTrue(first.execute(SimpleMarykModel.get(key)).values.isEmpty())
            assertEquals(1, first.execute(SimpleMarykModel.get(key, toVersion = future)).values.size)
        }
    }

    @Test
    fun localRecreationAdvancesBeyondAnotherWritersTombstone() = runTest(timeout = 3.minutes) {
        withTwoWriters { first, second, key, _ ->
            val deleted = assertIs<DeleteSuccess<SimpleMarykModel>>(
                first.execute(SimpleMarykModel.delete(key, hardDelete = true)).statuses.single()
            )
            val added = assertIs<AddSuccess<SimpleMarykModel>>(
                second.execute(SimpleMarykModel.add(key to SimpleMarykModel.create { value with "happy recreated" }))
                    .statuses.single()
            )
            assertTrue(added.version > deleted.version, "Recreation must advance beyond the durable tombstone")
        }
    }

    @Test
    fun localDeleteRecomputesVersionAfterTransactionConflict() = runTest(timeout = 3.minutes) {
        withTwoWriters { first, second, key, future ->
            val competingVersion = HLC(future).increment().increment().timestamp
            val conflictOnce = atomic(true)
            second.afterDeleteUpdatePrepared.value = {
                if (conflictOnce.getAndSet(false)) {
                    first.runTransaction { transaction ->
                        transaction.set(
                            packKey(first.getTableDirs(SimpleMarykModel).tablePrefix, key.bytes),
                            HLC.toStorageBytes(HLC(competingVersion)),
                        )
                    }
                }
            }
            try {
                val deleted = assertIs<DeleteSuccess<SimpleMarykModel>>(
                    second.execute(SimpleMarykModel.delete(key)).statuses.single()
                )
                assertTrue(deleted.version > competingVersion, "Retry must derive its version from the new transaction snapshot")
                assertEquals(deleted.version, first.execute(SimpleMarykModel.get(key, filterSoftDeleted = false)).values.single().lastVersion)
            } finally {
                second.afterDeleteUpdatePrepared.value = null
            }
        }
    }

    private suspend fun withTwoWriters(
        block: suspend (FoundationDBDataStore, FoundationDBDataStore, Key<SimpleMarykModel>, ULong) -> Unit,
    ) {
        val root = listOf("maryk", "test", "concurrent-local-version", Uuid.random().toString())
        val first = FoundationDBDataStore.open(directoryPath = root, dataModelsById = mapOf(1u to SimpleMarykModel), keepAllVersions = true)
        try {
            val second = FoundationDBDataStore.open(directoryPath = root, dataModelsById = mapOf(1u to SimpleMarykModel), keepAllVersions = true)
            try {
                val future = HLC(HLC().toPhysicalUnixTime() + 60_000uL, 0u).timestamp
                val key = Key<SimpleMarykModel>(ByteArray(16) { 12 })
                first.processUpdate(UpdateResponse(SimpleMarykModel, AdditionUpdate(
                    key, future, future, 0, false, SimpleMarykModel.create { value with "happy original" },
                )))
                block(first, second, key, future)
            } finally {
                second.close()
            }
        } finally {
            first.close()
        }
    }
}
