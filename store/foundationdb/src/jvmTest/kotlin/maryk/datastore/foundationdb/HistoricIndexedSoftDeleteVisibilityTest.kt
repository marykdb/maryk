package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class HistoricIndexedSoftDeleteVisibilityTest {
    @Test
    fun historicIndexScanCanIncludeSoftDeletedObject() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-soft-delete-visibility", Uuid.random().toString()),
            dataModelsById = mapOf(1u to TestMarykModel),
            keepAllVersions = true,
        )

        try {
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

            val deleteStatus = assertIs<DeleteSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.delete(addStatus.key)
                ).statuses.single()
            )

            val scanAtDelete = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = deleteStatus.version,
                    filterSoftDeleted = false,
                )
            )

            assertEquals(1, scanAtDelete.values.size)
            assertTrue(scanAtDelete.values.single().isDeleted)
        } finally {
            store.close()
        }
    }

    @Test
    fun historicIndexScanCanIncludeObjectSoftDeletedByChange() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-change-soft-delete-visibility", Uuid.random().toString()),
            dataModelsById = mapOf(1u to TestMarykModel),
            keepAllVersions = true,
        )

        try {
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

            val changeStatus = assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(
                        addStatus.key.change(ObjectSoftDeleteChange(true))
                    )
                ).statuses.single()
            )

            val filteredScanAtDelete = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = changeStatus.version,
                )
            )

            assertEquals(0, filteredScanAtDelete.values.size)

            val scanAtDelete = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = changeStatus.version,
                    filterSoftDeleted = false,
                )
            )

            assertEquals(1, scanAtDelete.values.size)
            assertTrue(scanAtDelete.values.single().isDeleted)
        } finally {
            store.close()
        }
    }

    @Test
    fun softDeleteSucceedsWithMalformedCurrentIndexedValueRow() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-soft-delete-malformed-current-value", Uuid.random().toString()),
            dataModelsById = mapOf(1u to TestMarykModel),
            keepAllVersions = true,
        )

        try {
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

            val tableDirs = store.getTableDirs(TestMarykModel)
            val valueKey = packKey(
                tableDirs.tablePrefix,
                addStatus.key.bytes + TestMarykModel { int::ref }.toStorageByteArray()
            )

            store.runTransaction { tr ->
                val current = tr.get(valueKey).awaitResult()!!
                tr.set(valueKey, current + byteArrayOf(1))
            }

            assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(
                        addStatus.key.change(ObjectSoftDeleteChange(true))
                    )
                ).statuses.single()
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun unrelatedChangeSucceedsWithMalformedCurrentIndexedValueRow() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-change-malformed-current-value", Uuid.random().toString()),
            dataModelsById = mapOf(1u to TestMarykModel),
            keepAllVersions = true,
        )

        try {
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

            val tableDirs = store.getTableDirs(TestMarykModel)
            val valueKey = packKey(
                tableDirs.tablePrefix,
                addStatus.key.bytes + TestMarykModel { int::ref }.toStorageByteArray()
            )

            store.runTransaction { tr ->
                val current = tr.get(valueKey).awaitResult()!!
                tr.set(valueKey, current + byteArrayOf(1))
            }

            assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(
                        addStatus.key.change(
                            Change(TestMarykModel { double::ref } with 2.4)
                        )
                    )
                ).statuses.single()
            )
        } finally {
            store.close()
        }
    }
}
