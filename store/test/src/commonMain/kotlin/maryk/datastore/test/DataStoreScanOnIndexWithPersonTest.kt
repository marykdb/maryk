package maryk.datastore.test

import maryk.core.properties.types.Key
import maryk.core.query.filters.Equals
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.Person
import kotlin.test.expect

class DataStoreScanOnIndexWithPersonTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<Person>>()
    private var highestCreationVersion = ULong.MIN_VALUE

    override val allTests = mapOf(
        "executeIndexScanRequestWithPerson" to ::executeIndexScanRequestWithPerson,
        "executeIndexScanFilterRequestWithPerson" to ::executeIndexScanFilterRequestWithPerson,
        "executeIndexScanFilterSpecificRequestWithPerson" to ::executeIndexScanFilterSpecificRequestWithPerson,
    )

    private val persons = arrayOf(
        Person.run { create(firstName with "Jurriaan", surname with "Mous") },
        Person.run { create(firstName with "Karel", surname with "Kastens") },
        Person.run { create(firstName with "Ariël", surname with "Kastens") },
        Person.run { create(firstName with "Ti", surname with "Tockle") },
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            Person.add(*persons)
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<Person>>(status)
            keys.add(response.key)
            if (response.version > highestCreationVersion) {
                // Add lowest version for scan test
                highestCreationVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            Person.delete(*keys.toTypedArray(), hardDelete = true)
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        highestCreationVersion = ULong.MIN_VALUE
    }

    private suspend fun executeIndexScanRequestWithPerson() {
        val scanResponse = dataStore.execute(
            Person.scan(
                order = Orders(Person { surname::ref }.ascending(), Person { firstName::ref }.ascending())
            )
        )

        expect(4) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = byteArrayOf(4, 2, 10, 17, 2, 10, 9),
            startKey = byteArrayOf(),
            stopKey = byteArrayOf(),
        )) { scanResponse.dataFetchType }

        // Sorted on name
        scanResponse.values[0].let {
            expect(persons[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[1].let {
            expect(persons[1]) { it.values }
            expect(keys[1]) { it.key }
        }
        scanResponse.values[2].let {
            expect(persons[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[3].let {
            expect(persons[3]) { it.values }
            expect(keys[3]) { it.key }
        }
    }

    private suspend fun executeIndexScanFilterRequestWithPerson() {
        val scanResponse = dataStore.execute(
            Person.scan(
                where = Equals(
                    Person { surname::ref } with "Kastens",
                ),
                order = Orders(Person { surname::ref }.ascending(), Person { firstName::ref }.ascending()),
            )
        )

        expect(2) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = byteArrayOf(4, 2, 10, 17, 2, 10, 9),
            startKey = byteArrayOf(75, 97, 115, 116, 101, 110, 115),
            stopKey = byteArrayOf(75, 97, 115, 116, 101, 110, 116),
        )) { scanResponse.dataFetchType }

        // Sorted on name
        scanResponse.values[0].let {
            expect(persons[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[1].let {
            expect(persons[1]) { it.values }
            expect(keys[1]) { it.key }
        }
    }

    private suspend fun executeIndexScanFilterSpecificRequestWithPerson() {
        val scanResponse = dataStore.execute(
            Person.scan(
                where = Equals(
                    Person { firstName::ref } with "Karel",
                    Person { surname::ref } with "Kastens",
                ),
            )
        )

        expect(1) { scanResponse.values.size }
        expect(FetchByIndexScan(
            direction = Direction.ASC,
            index = byteArrayOf(4, 2, 10, 17, 2, 10, 9),
            startKey = byteArrayOf(75, 97, 115, 116, 101, 110, 115, 75, 97, 114, 101, 108),
            stopKey = byteArrayOf(75, 97, 115, 116, 101, 110, 115, 75, 97, 114, 101, 109),
        )) { scanResponse.dataFetchType }

        // Sorted on name
        scanResponse.values[0].let {
            expect(persons[1]) { it.values }
            expect(keys[1]) { it.key }
        }
    }
}
