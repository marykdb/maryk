package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScanUpdateHistorySoftDeleteFilterTest {
    @Test
    fun olderChangeRemainsVisibleWhenToVersionIsSoftDeleteVersion() = runTest {
        val folder = createTestDBFolder("scan-update-history-soft-delete-filter")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                keepUpdateHistoryIndex = true,
                dataModelsById = mapOf(1u to TestMarykModel),
            )

            val addStatus = assertIs<AddSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.add(
                        TestMarykModel.create {
                            int with 5
                            uint with 1u
                            double with 1.2
                            dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                            bool with true
                            enum with Option.V1
                        }
                    )
                ).statuses.single()
            )

            val change = Change(TestMarykModel { int::ref } with 6)
            val changeStatus = assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(addStatus.key.change(change))
                ).statuses.single()
            )

            val deleteStatus = assertIs<DeleteSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.delete(addStatus.key, hardDelete = false)
                ).statuses.single()
            )

            val response = store.execute(
                TestMarykModel.scanUpdateHistory(
                    fromVersion = changeStatus.version,
                    toVersion = deleteStatus.version,
                    limit = 10u,
                    filterSoftDeleted = true
                )
            )

            assertIs<FetchByUpdateHistoryIndex>(response.dataFetchType)
            val updates = response.updates.map { assertIs<ChangeUpdate<TestMarykModel>>(it) }
            assertEquals(1, updates.size)
            assertEquals(changeStatus.version, updates.single().version)
            assertEquals(addStatus.key, updates.single().key)
            assertEquals(listOf(change), updates.single().changes)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
