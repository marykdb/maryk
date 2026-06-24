package maryk.datastore.rocksdb

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
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HistoricIndexedSoftDeleteVisibilityTest {
    @Test
    fun historicIndexScanCanIncludeSoftDeletedObject() = runTest {
        val folder = createTestDBFolder("historic-index-soft-delete-visibility")

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

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun historicIndexScanSkipsBucketsExitedByChange() = runTest {
        val folder = createTestDBFolder("historic-index-scan-skips-exited-bucket")

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

            val changeStatus = assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(
                        addStatus.key.change(
                            Change(TestMarykModel { int::ref } with 6)
                        )
                    )
                ).statuses.single()
            )

            val scanOldValue = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = changeStatus.version,
                )
            )
            assertEquals(0, scanOldValue.values.size)

            val scanNewValue = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 6),
                    toVersion = changeStatus.version,
                )
            )
            assertEquals(1, scanNewValue.values.size)
            assertEquals(addStatus.key, scanNewValue.values.single().key)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun undeleteRestoresSecondaryIndexEntries() = runTest {
        val folder = createTestDBFolder("historic-index-soft-delete-undelete")

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
                    TestMarykModel.change(
                        addStatus.key.change(ObjectSoftDeleteChange(true))
                    )
                ).statuses.single()
            )
            assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(
                        addStatus.key.change(ObjectSoftDeleteChange(false))
                    )
                ).statuses.single()
            )

            val response = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5)
                )
            )

            assertEquals(1, response.values.size)
            assertEquals(addStatus.key, response.values.single().key)
            assertEquals(false, response.values.single().isDeleted)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun softDeleteSucceedsWithMalformedCurrentIndexedValueRow() = runTest {
        val folder = createTestDBFolder("historic-index-soft-delete-malformed-current-value")

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

            val columnFamilies = store.getColumnFamilies(TestMarykModel)
            val valueKey = addStatus.key.bytes + TestMarykModel { int::ref }.toStorageByteArray()
            val currentValue = store.db.get(columnFamilies.table, valueKey)!!
            store.db.put(columnFamilies.table, valueKey, currentValue + byteArrayOf(1))

            assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(
                        addStatus.key.change(ObjectSoftDeleteChange(true))
                    )
                ).statuses.single()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun unrelatedChangeSucceedsWithMalformedCurrentIndexedValueRow() = runTest {
        val folder = createTestDBFolder("historic-index-change-malformed-current-value")

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

            val columnFamilies = store.getColumnFamilies(TestMarykModel)
            val valueKey = addStatus.key.bytes + TestMarykModel { int::ref }.toStorageByteArray()
            val currentValue = store.db.get(columnFamilies.table, valueKey)!!
            store.db.put(columnFamilies.table, valueKey, currentValue + byteArrayOf(1))

            assertIs<ChangeSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.change(
                        addStatus.key.change(
                            Change(TestMarykModel { double::ref } with 2.4)
                        )
                    )
                ).statuses.single()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
