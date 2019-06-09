package maryk.datastore.memory

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.ValueCount
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.properties.types.Key
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.assertType
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import kotlin.test.Test
import kotlin.test.expect

class InMemoryDataStoreGetTest {
    private val dataStore = InMemoryDataStore()
    private val keys = mutableListOf<Key<SimpleMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    init {
        runSuspendingTest {
            val addResponse = dataStore.execute(
                addRequest
            )
            addResponse.statuses.forEach { status ->
                val response = assertType<AddSuccess<SimpleMarykModel>>(status)
                keys.add(response.key)
                if (response.version < lowestVersion) {
                    // Add lowest version for scan test
                    lowestVersion = response.version
                }
            }
        }
    }

    @Test
    fun executeSimpleGetRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(*keys.toTypedArray())
        )

        expect(2) { getResponse.values.size }

        getResponse.values.forEachIndexed { index, value ->
            expect(addRequest.objects[index]) { value.values }
        }
    }

    @Test
    fun executeSimpleGetWithAggregationRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(
                *keys.toTypedArray(),
                aggregations = Aggregations(
                    "count" to ValueCount(
                        SimpleMarykModel { value::ref }
                    )
                )
            )
        )

        expect(2) { getResponse.values.size }

        expect(
            AggregationsResponse(
                "count" to ValueCountResponse(
                    SimpleMarykModel { value::ref }, 2uL
                )
            )
        ) {
            getResponse.aggregations
        }
    }

    @Test
    fun executeToVersionGetRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
        )

        expect(0) { getResponse.values.size }
    }

    @Test
    fun executeGetRequestWithSelect() = runSuspendingTest {
        val scanResponse = dataStore.execute(
            SimpleMarykModel.get(
                *keys.toTypedArray(),
                select = SimpleMarykModel.graph {
                    listOf(value)
                }
            )
        )

        expect(2) { scanResponse.values.size }

        scanResponse.values[0].let {
            expect(SimpleMarykModel(value = "haha1")) { it.values }
            expect(keys[0]) { it.key }
        }
    }
}
