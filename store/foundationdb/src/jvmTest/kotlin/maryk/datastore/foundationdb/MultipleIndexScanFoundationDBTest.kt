package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.AnyValueIncMapIndexModel
import maryk.test.models.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class MultipleIndexScanFoundationDBTest {
    @Test
    fun scansCompositePersonIndexInOrderAndByPrefixFilter() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "multiple-index-scan", Uuid.random().toString()),
            dataModelsById = mapOf(1u to Person),
            keepAllVersions = true,
        )

        try {
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
        } finally {
            store.close()
        }
    }

    @Test
    fun historicDescendingAnyValueScanMatchesCurrentAnyValueOrderPerKey() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-desc-any-value-scan", Uuid.random().toString()),
            dataModelsById = mapOf(1u to AnyValueIncMapIndexModel),
            keepAllVersions = true,
        )

        try {
            val statuses = store.execute(
                AnyValueIncMapIndexModel.add(
                    AnyValueIncMapIndexModel.create {
                        name with "a"
                        incMapValues with mapOf(
                            1u to "i1",
                            4u to "i4"
                        )
                    },
                    AnyValueIncMapIndexModel.create {
                        name with "b"
                        incMapValues with mapOf(3u to "i3")
                    },
                    AnyValueIncMapIndexModel.create {
                        name with "c"
                        incMapValues with mapOf(2u to "i2")
                    },
                )
            ).statuses.map { assertIs<AddSuccess<AnyValueIncMapIndexModel>>(it) }

            val toVersion = statuses.maxOf { it.version }
            val scanResponse = store.execute(
                AnyValueIncMapIndexModel.scan(
                    toVersion = toVersion,
                    order = AnyValueIncMapIndexModel { incMapValues.refToAnyKey() }.descending()
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(
                listOf("a", "c", "b"),
                scanResponse.values.map { it.values { name } }.distinct()
            )
        } finally {
            store.close()
        }
    }
}
