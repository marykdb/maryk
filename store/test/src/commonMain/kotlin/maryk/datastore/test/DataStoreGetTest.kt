package maryk.datastore.test

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.ValueCount
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.exceptions.RequestException
import maryk.core.models.graph
import maryk.core.properties.types.Key
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            addRequest
        )
        addResponse.statuses.forEach { status ->
            val response = assertIs<AddSuccess<SimpleMarykModel>>(status)
            keys.add(response.key)
            if (response.version < lowestVersion) {
                // Add lowest version for scan test
                lowestVersion = response.version
            }
        }
    }

    override suspend fun resetData() {
        dataStore.execute(
            SimpleMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
        )
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeSimpleGetRequest() {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(*keys.toTypedArray())
        )

        expect(2) { getResponse.values.size }

        getResponse.values.forEachIndexed { index, value ->
            expect(addRequest.objects[index]) { value.values }
        }
    }

    private suspend fun executeSimpleGetWithAggregationRequest() {
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

    private suspend fun executeToVersionGetRequest() {
        if (dataStore.keepAllVersions) {
            val getResponse = dataStore.execute(
                SimpleMarykModel.get(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
            )

            expect(0) { getResponse.values.size }
        } else {
            assertFailsWith<RequestException> {
                dataStore.execute(
                    SimpleMarykModel.get(*keys.toTypedArray(), toVersion = lowestVersion - 1uL)
                )
            }
        }
    }

    private suspend fun executeGetRequestWithSelect() {
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
            expect(SimpleMarykModel.run { create(value with "haha1") }) { it.values }
            expect(keys[0]) { it.key }
        }
    }
}
