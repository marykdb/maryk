package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.createTestDBFolder
import maryk.datastore.test.UniqueModel
import maryk.datastore.test.assertStatusIs
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import kotlin.test.Test

class ReopenUniqueIndexCleanupTest {
    @Test
    fun reopenedStoreDeletesPersistedUniqueIndexEntries() = runTest {
        val folder = createTestDBFolder("reopen-unique-index-cleanup")

        val key = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        ).let { dataStore ->
            try {
                assertStatusIs<AddSuccess<UniqueModel>>(
                    dataStore.execute(
                        UniqueModel.add(
                            UniqueModel.create { email with "reopen@test.com" }
                        )
                    ).statuses.first()
                ).key
            } finally {
                dataStore.close()
            }
        }

        RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = dataModelsForTests,
            keepAllVersions = false,
        ).let { dataStore ->
            try {
                assertStatusIs<DeleteSuccess<UniqueModel>>(
                    dataStore.execute(
                        UniqueModel.delete(key, hardDelete = true)
                    ).statuses.first()
                )

                assertStatusIs<AddSuccess<UniqueModel>>(
                    dataStore.execute(
                        UniqueModel.add(
                            UniqueModel.create { email with "reopen@test.com" }
                        )
                    ).statuses.first()
                )
            } finally {
                dataStore.close()
            }
        }

        deleteFolder(folder)
    }
}
