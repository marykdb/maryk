package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HistoricIndexHardDeleteCleanupTest {
    @Test
    fun hardDeleteRemovesHistoricIndexLookupsBeforeDeletion() = runTest {
        val folder = createTestDBFolder("historic-index-hard-delete-cleanup")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to TestMarykModel),
            )

            val original = TestMarykModel.create {
                int with 5
                uint with 1u
                double with 1.2
                dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                bool with true
                enum with Option.V1
            }

            val addStatus = assertIs<AddSuccess<TestMarykModel>>(
                store.execute(TestMarykModel.add(original)).statuses.single()
            )

            val changeStatus = assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(
                        addStatus.key.change(
                            Change(TestMarykModel.int.ref() with 6)
                        )
                    )
                ).statuses.single()
            )

            val deleteStatus = assertIs<DeleteSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.delete(addStatus.key, hardDelete = true)
                ).statuses.single()
            )

            assertEquals(0, store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel.int.ref() with 5),
                    toVersion = addStatus.version
                )
            ).values.size)

            assertEquals(0, store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel.int.ref() with 6),
                    toVersion = changeStatus.version
                )
            ).values.size)

            assertEquals(0, store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel.int.ref() with 6),
                    toVersion = deleteStatus.version
                )
            ).values.size)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
