package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.checkExistence
import maryk.deleteFolder
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CheckExistenceBehaviorTest {
    @Test
    fun checkExistenceAcceptsStoredCurrentAndHistoricRows() = runTest {
        val folder = createTestDBFolder("check-existence-behavior")

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
                            string with "ha-probe"
                            int with 5
                            uint with 1u
                            double with 1.0
                            dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                            bool with true
                            enum with Option.V1
                        }
                    )
                ).statuses.single()
            )

            val columnFamilies = store.getColumnFamilies(TestMarykModel) as HistoricTableColumnFamilies

            DBAccessor(store).use { accessor ->
                accessor.getIterator(store.defaultReadOptions, columnFamilies.table).use { iterator ->
                    iterator.seek(addStatus.key.bytes)
                    assertTrue(iterator.isValid())
                    assertTrue(iterator.key().copyOfRange(0, addStatus.key.size).contentEquals(addStatus.key.bytes))
                    checkExistence(iterator, addStatus.key)
                }

                accessor.getIterator(store.defaultReadOptions, columnFamilies.historic.table).use { iterator ->
                    iterator.seek(addStatus.key.bytes)
                    assertTrue(iterator.isValid())
                    assertTrue(iterator.key().copyOfRange(0, addStatus.key.size).contentEquals(addStatus.key.bytes))
                    checkExistence(iterator, addStatus.key)
                }
            }

            val response = store.execute(
                TestMarykModel.scan(
                    where = Equals(TestMarykModel { int::ref } with 5),
                    toVersion = addStatus.version
                )
            )
            assertEquals(1, response.values.size)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
