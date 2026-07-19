package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.FetchByTableScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.test.models.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class MultipleIndexScanRocksDBTest {
    @Test
    fun cursorStaysOnTableFallbackWhenOrderedIndexReturns() = runTest {
        val folder = createTestDBFolder("cursor-table-fallback")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                dataModelsById = mapOf(1u to Person),
            )
            val order = Orders(
                Person { surname::ref }.ascending(),
                Person { firstName::ref }.ascending(),
            )
            Person.create {
                firstName with "Jurriaan"
                surname with "Mous"
            }.also { person ->
                assertIs<AddSuccess<Person>>(store.execute(Person.add(person)).statuses.single())
            }
            Person.create {
                firstName with "Karel"
                surname with "Kastens"
            }.also { person ->
                assertIs<AddSuccess<Person>>(store.execute(Person.add(person)).statuses.single())
            }

            val columnFamilies = store.getColumnFamilies(Person)
            val indexRows = mutableListOf<Pair<ByteArray, ByteArray>>()
            store.db.newIterator(columnFamilies.index).use { iterator ->
                iterator.seekToFirst()
                while (iterator.isValid()) {
                    indexRows += iterator.key() to iterator.value()
                    iterator.next()
                }
            }
            indexRows.forEach { (key, _) -> store.db.delete(columnFamilies.index, key) }

            val firstPage = store.execute(Person.scan(order = order, limit = 1u, allowTableScan = true))
            assertIs<FetchByTableScan>(firstPage.dataFetchType)

            indexRows.forEach { (key, value) -> store.db.put(columnFamilies.index, key, value) }

            val secondPage = store.execute(
                Person.scan(
                    order = order,
                    limit = 1u,
                    allowTableScan = true,
                    cursor = assertNotNull(firstPage.nextCursor),
                )
            )
            assertEquals(1, secondPage.values.size)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun scansCompositePersonIndexInOrderAndByPrefixFilter() = runTest {
        val folder = createTestDBFolder("multiple-index-scan")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to Person),
            )

            val persons = arrayOf(
                Person.create {
                    firstName with "Jurriaan"
                    surname with "Mous"
                },
                Person.create {
                    firstName with "Karel"
                    surname with "Kastens"
                },
                Person.create {
                    firstName with "Ariel"
                    surname with "Kastens"
                },
                Person.create {
                    firstName with "Ti"
                    surname with "Tockle"
                },
            )

            persons.forEach { person ->
                assertIs<AddSuccess<Person>>(store.execute(Person.add(person)).statuses.single())
            }

            val orderedScan = store.execute(
                Person.scan(
                    order = Orders(
                        Person { surname::ref }.ascending(),
                        Person { firstName::ref }.ascending()
                    )
                )
            )

            assertIs<FetchByIndexScan>(orderedScan.dataFetchType)
            assertEquals(
                listOf("Ariel", "Karel", "Jurriaan", "Ti"),
                orderedScan.values.map { it.values { firstName } }
            )

            val filteredScan = store.execute(
                Person.scan(
                    where = Equals(Person { surname::ref } with "Kastens"),
                    order = Orders(
                        Person { surname::ref }.ascending(),
                        Person { firstName::ref }.ascending()
                    )
                )
            )

            assertIs<FetchByIndexScan>(filteredScan.dataFetchType)
            assertEquals(
                listOf("Ariel", "Karel"),
                filteredScan.values.map { it.values { firstName } }
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
