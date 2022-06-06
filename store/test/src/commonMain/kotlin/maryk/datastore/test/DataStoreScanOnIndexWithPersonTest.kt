package maryk.datastore.test

import maryk.core.properties.types.Key
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.assertType
import maryk.test.models.Person
import kotlin.test.expect

class DataStoreScanOnIndexWithPersonTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<Person>>()
    private var highestCreationVersion = ULong.MIN_VALUE

    override val allTests = mapOf(
        "executeIndexScanRequestWithPerson" to ::executeIndexScanRequestWithPerson
    )

    private val persons = arrayOf(
        Person("Jurriaan", "Mous"),
        Person("Myra", "Mous"),
        Person("Desiderio", "Espinosa"),
        Person("Muffin", "Espinosa")
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            Person.add(*persons)
        )
        addResponse.statuses.forEach { status ->
            val response = assertType<AddSuccess<Person>>(status)
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
        )
        keys.clear()
        highestCreationVersion = ULong.MIN_VALUE
    }

    private suspend fun executeIndexScanRequestWithPerson() {
        val scanResponse = dataStore.execute(
            Person.scan(
                order = Orders(Person { firstName::ref }.ascending(), Person { surname::ref }.ascending())
            )
        )

        expect(4) { scanResponse.values.size }

        // Sorted on severity
        scanResponse.values[0].let {
            expect(persons[2]) { it.values }
            expect(keys[2]) { it.key }
        }
        scanResponse.values[1].let {
            expect(persons[0]) { it.values }
            expect(keys[0]) { it.key }
        }
        scanResponse.values[2].let {
            expect(persons[3]) { it.values }
            expect(keys[3]) { it.key }
        }
        scanResponse.values[3].let {
            expect(persons[1]) { it.values }
            expect(keys[1]) { it.key }
        }
    }
}
