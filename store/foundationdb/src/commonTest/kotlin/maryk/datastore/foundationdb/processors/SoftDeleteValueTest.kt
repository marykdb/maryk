package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.clock.HLC
import maryk.core.models.key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class SoftDeleteValueTest {
    @Test
    fun currentIsSoftDeletedIgnoresOverlongValue() {
        runBlocking {
            val store = FoundationDBDataStore.open(
                directoryPath = listOf("maryk", "test", "soft-delete-current-overlong", Uuid.random().toString()),
                dataModelsById = dataModelsForTests,
                keepAllVersions = true,
            )

            try {
                val tableDirs = store.getTableDirs(SimpleMarykModel)
                val key = SimpleMarykModel.key(
                    SimpleMarykModel.create {
                        value with "haha"
                    }
                )
                store.runTransaction { tr ->
                    tr.set(
                        packKey(tableDirs.tablePrefix, key.bytes + SOFT_DELETE_INDICATOR),
                        HLC.toStorageBytes(HLC(5uL)) + byteArrayOf(TRUE, 0)
                    )
                }

                assertFalse(store.runTransaction { tr ->
                    isSoftDeleted(tr, tableDirs, null, key.bytes)
                })
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun softDeleteFallbackIgnoresValueWithoutDeleteFlag() {
        runBlocking {
            val store = FoundationDBDataStore.open(
                directoryPath = listOf("maryk", "test", "soft-delete-fallback-missing-flag", Uuid.random().toString()),
                dataModelsById = dataModelsForTests,
                keepAllVersions = true,
            )

            try {
                val tableDirs = store.getTableDirs(SimpleMarykModel)
                val key = SimpleMarykModel.key(
                    SimpleMarykModel.create {
                        value with "haha"
                    }
                )
                val version = 5uL
                store.runTransaction { tr ->
                    tr.set(
                        packKey(tableDirs.tablePrefix, key.bytes + SOFT_DELETE_INDICATOR),
                        HLC.toStorageBytes(HLC(version))
                    )
                }

                val objectChange = DataObjectVersionedChange(key, changes = emptyList())
                assertEquals(
                    objectChange,
                    store.runTransaction { tr ->
                        addSoftDeleteChangeIfMissing(
                            tr = tr,
                            tableDirs = tableDirs,
                            key = key,
                            fromVersion = version,
                            objectChange = objectChange,
                        )
                    }
                )
            } finally {
                store.close()
            }
        }
    }
}
