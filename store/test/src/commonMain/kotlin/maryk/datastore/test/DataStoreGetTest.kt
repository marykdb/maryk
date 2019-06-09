package maryk.datastore.test

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.ValueCount
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.processors.datastore.IsDataStore
import maryk.core.properties.types.Key
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.assertType
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.runSuspendingTest
import kotlin.test.expect

class DataStoreGetTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<SimpleMarykModel>>()
    private var lowestVersion = ULong.MAX_VALUE

    override val allTests = mapOf(
        "executeSimpleGetRequest" to ::executeSimpleGetRequest,
        "executeSimpleGetWithAggregationRequest" to ::executeSimpleGetWithAggregationRequest,
        "executeToVersionGetRequest" to ::executeToVersionGetRequest,
        "executeGetRequestWithSelect" to ::executeGetRequestWithSelect
    )

    override fun initData() {
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

    override fun resetData() {
        runSuspendingTest {
            dataStore.execute(
                SimpleMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
            )
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private fun executeSimpleGetRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(*keys.toTypedArray())
        )

        expect(2) { getResponse.values.size }

        getResponse.values.forEachIndexed { index, value ->
            expect(addRequest.objects[index]) { value.values }
        }
    }

    private fun executeSimpleGetWithAggregationRequest() = runSuspendingTest {
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

    private fun executeToVersionGetRequest() = runSuspendingTest {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
        )

        expect(0) { getResponse.values.size }
    }

    private fun executeGetRequestWithSelect() = runSuspendingTest {
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
