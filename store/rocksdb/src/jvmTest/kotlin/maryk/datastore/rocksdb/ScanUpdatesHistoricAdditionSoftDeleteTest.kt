package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ScanUpdatesHistoricAdditionSoftDeleteTest {
    @Test
    fun historicCreateStaysNonDeletedBeforeLaterSoftDelete() = runTest {
        val folder = createTestDBFolder("scan-updates-historic-addition-soft-delete")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
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

            assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(addStatus.key.change(Change(TestMarykModel { int::ref } with 6)))
                ).statuses.single()
            )

            val deleteStatus = assertIs<DeleteSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.delete(addStatus.key, hardDelete = false)
                ).statuses.single()
            )

            val response = store.execute(
                TestMarykModel.scanUpdates(
                    startKey = addStatus.key,
                    includeStart = true,
                    limit = 1u,
                    toVersion = deleteStatus.version,
                    maxVersions = 10u,
                    filterSoftDeleted = false
                )
            )

            val addition = response.updates.filterIsInstance<AdditionUpdate<TestMarykModel>>().single()
            assertEquals(addStatus.version, addition.version)
            assertEquals(addStatus.key, addition.key)
            assertFalse(addition.isDeleted)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun getUpdatesHistoricCreateStaysNonDeletedBeforeLaterSoftDelete() = runTest {
        val folder = createTestDBFolder("get-updates-historic-addition-soft-delete")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
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

            assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(addStatus.key.change(Change(TestMarykModel { int::ref } with 6)))
                ).statuses.single()
            )

            val deleteStatus = assertIs<DeleteSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.delete(addStatus.key, hardDelete = false)
                ).statuses.single()
            )

            val response = store.execute(
                TestMarykModel.getUpdates(
                    addStatus.key,
                    toVersion = deleteStatus.version,
                    maxVersions = 10u,
                    filterSoftDeleted = false
                )
            )

            val addition = response.updates.filterIsInstance<AdditionUpdate<TestMarykModel>>().single()
            assertEquals(addStatus.version, addition.version)
            assertEquals(addStatus.key, addition.key)
            assertFalse(addition.isDeleted)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
