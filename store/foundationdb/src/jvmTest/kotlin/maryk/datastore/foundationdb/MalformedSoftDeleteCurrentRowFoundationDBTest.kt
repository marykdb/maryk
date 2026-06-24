
package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.foundationdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MalformedSoftDeleteCurrentRowFoundationDBTest {
    @Test
    fun malformedOverlongCurrentSoftDeleteRowIsIgnoredByGetAndGetChanges() = runTest {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-soft-delete-current-row", Uuid.random().toString()),
            keepAllVersions = true,
            dataModelsById = mapOf(1u to Log),
        )

        try {
            val values = Log("live-log")
            val addStatus = assertIs<AddSuccess<Log>>(store.execute(Log.add(values)).statuses.single())
            val key = addStatus.key

            val tableDirs = store.getTableDirs(Log)
            store.runTransaction { tr ->
                tr.set(
                    packKey(tableDirs.tablePrefix, key.bytes + SOFT_DELETE_INDICATOR),
                    HLC.toStorageBytes(HLC(addStatus.version + 1u)) + byteArrayOf(1, 0)
                )
            }

            val getResponse = store.execute(
                Log.get(
                    key,
                    filterSoftDeleted = false,
                )
            )
            assertEquals(1, getResponse.values.size)
            assertFalse(getResponse.values.single().isDeleted)

            val scanResponse = store.execute(
                Log.scan(
                    filterSoftDeleted = false,
                    allowTableScan = true,
                )
            )
            assertEquals(1, scanResponse.values.size)
            assertFalse(scanResponse.values.single().isDeleted)

            val changesResponse = store.execute(
                Log.getChanges(
                    key,
                    maxVersions = 1u,
                    filterSoftDeleted = false,
                )
            )
            assertFalse(
                changesResponse.changes.single().changes.any { versioned ->
                    versioned.changes.any { it is ObjectSoftDeleteChange }
                }
            )

            val changeStatus = assertIs<ChangeSuccess<Log>>(
                store.execute(
                    Log.change(
                        key.change(ObjectSoftDeleteChange(true))
                    )
                ).statuses.single()
            )

            val deletedResponse = store.execute(
                Log.get(
                    key,
                    filterSoftDeleted = false,
                )
            )
            assertEquals(1, deletedResponse.values.size)
            assertFalse(changeStatus.version <= addStatus.version)
            assertTrue(deletedResponse.values.single().isDeleted)
        } finally {
            store.close()
        }
    }
}
