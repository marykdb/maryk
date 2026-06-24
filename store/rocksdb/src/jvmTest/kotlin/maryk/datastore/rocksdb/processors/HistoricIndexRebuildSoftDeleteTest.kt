package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.test.runTest
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.walkDataRecordsAndFillIndex
import maryk.deleteFolder
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HistoricIndexRebuildSoftDeleteTest {
    @Test
    fun rebuildSkipsCurrentIndexRowsForMalformedKeyMetadata() = runTest {
        val folder = createTestDBFolder("historic-index-rebuild-malformed-key-metadata")

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

            val indexable = TestMarykModel { int::ref }
            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(TestMarykModel)
            )
            val currentKeyRow = store.db.get(columnFamilies.keys, addStatus.key.bytes)!!
            store.db.put(columnFamilies.keys, addStatus.key.bytes, currentKeyRow + byteArrayOf(1))

            Transaction(store).use { transaction ->
                deleteCompleteIndexContents(transaction, columnFamilies, indexable)
                transaction.commit()
            }
            walkDataRecordsAndFillIndex(store, columnFamilies, listOf(indexable))

            val currentScan = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5)
                )
            )
            assertEquals(0, currentScan.values.size)
            assertIs<FetchByTableScan>(
                store.execute(
                    TestMarykModel.scan(
                        where = Equals(TestMarykModel { int::ref } with 5),
                        allowTableScan = true
                    )
                ).dataFetchType
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun rebuildSkipsCurrentIndexRowsForMalformedCurrentValueVersion() = runTest {
        val folder = createTestDBFolder("historic-index-rebuild-malformed-current-value")

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

            val indexable = TestMarykModel { int::ref }
            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(TestMarykModel)
            )
            val intReference = TestMarykModel { int::ref }.toStorageByteArray()
            val valueKey = addStatus.key.bytes + intReference
            val currentValue = store.db.get(columnFamilies.table, valueKey)!!
            store.db.put(columnFamilies.table, valueKey, currentValue + byteArrayOf(1))

            Transaction(store).use { transaction ->
                deleteCompleteIndexContents(transaction, columnFamilies, indexable)
                transaction.commit()
            }
            walkDataRecordsAndFillIndex(store, columnFamilies, listOf(indexable))

            val currentScan = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5)
                )
            )
            assertEquals(0, currentScan.values.size)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun rebuildPreservesSoftDeleteRemovalVersion() = runTest {
        val folder = createTestDBFolder("historic-index-rebuild-soft-delete")

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

            val baselinePostDelete = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = deleteStatus.version
                )
            )
            assertEquals(0, baselinePostDelete.values.size)

            val baselinePreDelete = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = addStatus.version
                )
            )
            assertEquals(1, baselinePreDelete.values.size)

            val indexable = TestMarykModel { int::ref }
            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(TestMarykModel)
            )

            Transaction(store).use { transaction ->
                deleteCompleteIndexContents(transaction, columnFamilies, indexable)
                transaction.commit()
            }
            walkDataRecordsAndFillIndex(store, columnFamilies, listOf(indexable))

            val postDelete = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = deleteStatus.version
                )
            )
            assertEquals(0, postDelete.values.size)

            val preDelete = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = addStatus.version
                )
            )
            assertEquals(1, preDelete.values.size)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
