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
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import kotlin.test.assertFailsWith
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
        "executeGetRequestWithSelect" to ::executeGetRequestWithSelect,
        "repeatedKeysPreserveOrderAndAggregationMultiplicity" to ::repeatedKeysPreserveOrderAndAggregationMultiplicity,
        "repeatedKeysWithProjectionKeepFallbackAggregations" to ::repeatedKeysWithProjectionKeepFallbackAggregations,
        "repeatedKeysKeepHistoricSnapshot" to ::repeatedKeysKeepHistoricSnapshot,
        "repeatedKeysRespectFiltering" to ::repeatedKeysRespectFiltering
    )

    override suspend fun initData() {
        val addResponse = dataStore.execute(
            addRequest
        )
        addResponse.statuses.forEach { status ->
            val response = assertStatusIs<AddSuccess<SimpleMarykModel>>(status)
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
        ).statuses.forEach {
            assertStatusIs<DeleteSuccess<*>>(it)
        }
        keys.clear()
        lowestVersion = ULong.MAX_VALUE
    }

    private suspend fun executeSimpleGetRequest() {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(*keys.toTypedArray())
        )

        expect(2) { getResponse.values.size }
        expect(FetchByKey) {getResponse.dataFetchType}

        getResponse.values.forEachIndexed { index, value ->
            expect(addRequest.objects[index]) { value.values }
        }
    }

    private suspend fun repeatedKeysPreserveOrderAndAggregationMultiplicity() {
        val requested = listOf(keys[0], keys[1], keys[0], keys[0], keys[1])
        val result = dataStore.execute(SimpleMarykModel.get(*requested.toTypedArray(),
            aggregations = Aggregations("count" to ValueCount(SimpleMarykModel.ref { value }))))
        expect(requested) { result.values.map { it.key } }
        expect(AggregationsResponse("count" to ValueCountResponse(SimpleMarykModel.ref { value }, 5uL))) { result.aggregations }
        result.values.forEachIndexed { index, row -> expect(addRequest.objects[if (requested[index] == keys[0]) 0 else 1]) { row.values } }
    }

    private suspend fun repeatedKeysWithProjectionKeepFallbackAggregations() {
        val result = dataStore.execute(SimpleMarykModel.get(keys[0], keys[0], keys[1],
            select = SimpleMarykModel.graph { emptyList() },
            aggregations = Aggregations("count" to ValueCount(SimpleMarykModel.ref { value }))))
        expect(listOf(keys[0], keys[0], keys[1])) { result.values.map { it.key } }
        expect(true) { result.values.all { it.values.size == 0 } }
        expect(AggregationsResponse("count" to ValueCountResponse(SimpleMarykModel.ref { value }, 3uL))) { result.aggregations }
    }

    private suspend fun repeatedKeysKeepHistoricSnapshot() {
        if (!dataStore.keepAllVersions) return
        val before = dataStore.execute(SimpleMarykModel.get(keys[0])).values.single()
        val result = dataStore.execute(SimpleMarykModel.get(keys[0], keys[0], toVersion = before.lastVersion))
        expect(listOf(before, before)) { result.values }
    }

    private suspend fun repeatedKeysRespectFiltering() {
        val result = dataStore.execute(SimpleMarykModel.get(keys[1], keys[0], keys[1], keys[0],
            where = Equals(SimpleMarykModel.ref { value } with "haha1")))
        expect(listOf(keys[0], keys[0])) { result.values.map { it.key } }
    }

    private suspend fun executeSimpleGetWithAggregationRequest() {
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(
                *keys.toTypedArray(),
                aggregations = Aggregations(
                    "count" to ValueCount(
                        SimpleMarykModel.ref { value }
                    )
                )
            )
        )

        expect(2) { getResponse.values.size }
        expect(FetchByKey) {getResponse.dataFetchType}

        expect(
            AggregationsResponse(
                "count" to ValueCountResponse(
                    SimpleMarykModel.ref { value }, 2uL
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
        val getResponse = dataStore.execute(
            SimpleMarykModel.get(
                *keys.toTypedArray(),
                select = SimpleMarykModel.graph {
                    listOf(value)
                }
            )
        )

        expect(2) { getResponse.values.size }
        expect(FetchByKey) {getResponse.dataFetchType}

        getResponse.values[0].let {
            expect(SimpleMarykModel.create { value with "haha1" }) { it.values }
            expect(keys[0]) { it.key }
        }
    }
}
