package maryk.datastore.foundationdb.processors.helpers

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.StorageException
import maryk.core.models.key
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class GetLastVersionTest {
    @Test
    fun rejectsStoredLatestVersionWithInvalidLength() = runTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "last-version-invalid-length", Uuid.random().toString()),
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
                tr.set(packKey(tableDirs.tablePrefix, key.bytes), byteArrayOf(1))
            }

            assertFailsWith<StorageException> {
                store.runTransaction { tr ->
                    getLastVersion(tr, tableDirs, key)
                }
            }
        } finally {
            store.close()
        }
    }
}
