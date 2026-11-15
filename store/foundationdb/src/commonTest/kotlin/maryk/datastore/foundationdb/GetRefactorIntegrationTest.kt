package maryk.datastore.foundationdb

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.metric.Average
import maryk.core.aggregations.metric.Stats
import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.SumResponse
import maryk.core.aggregations.metric.ValueCount
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.processors.processGetRequest
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.foundationdb.FDBException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private object RefactorRecord : RootDataModel<RefactorRecord>() {
    val title by string(1u)
    val entries by list(2u, valueDefinition = StringDefinition())
    val amount by number(3u, type = SInt32, sensitive = true)
}

private class CountingRefactorProvider : ContextualFieldEncryptionProvider {
    val decryptions = atomic(0)
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int) = transform(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int) = transform(value, offset, length)
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int) =
        transform(value, offset, length)

    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray {
        assertEquals(1u, context.modelId)
        assertContentEquals(RefactorRecord.amount.ref().toStorageByteArray(), context.reference)
        decryptions.incrementAndGet()
        return transform(value, offset, length)
    }

    private fun transform(value: ByteArray, offset: Int, length: Int) =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5a).toByte() }
}

class GetRefactorIntegrationTest {
    @Test
    fun metricReuseCrossesWindowsWithoutExposingFieldsOrLosingDuplicateContributions() = runBoundedIntegrationTest {
        val provider = CountingRefactorProvider()
        val store = openStore(provider)
        try {
            var historical = 0uL
            val keys = (1..4).map { amount ->
                val status = assertIs<AddSuccess<RefactorRecord>>(store.execute(RefactorRecord.add(RefactorRecord.create {
                    title with "record"; entries with listOf("entry"); RefactorRecord.amount with amount
                })).statuses.single())
                historical = maxOf(historical, status.version)
                status.key
            }
            val requested = Array(24) { keys[it % 4] }
            val metrics = Aggregations(
                "sum" to Sum(RefactorRecord.amount.ref()), "average" to Average(RefactorRecord.amount.ref()),
                "stats" to Stats(RefactorRecord.amount.ref()), "count" to ValueCount(RefactorRecord.amount.ref()),
            )
            for (version in listOf(null, historical)) {
                provider.decryptions.value = 0
                val response = store.unpooled(RefactorRecord.get(*requested,
                    select = RefactorRecord.graph { listOf(title) }, aggregations = metrics, toVersion = version))
                assertEquals(requested.toList(), response.values.map { it.key })
                assertEquals(60, assertIs<SumResponse<*>>(response.aggregations!!.namedAggregations["sum"]).value)
                assertEquals(24uL, assertIs<ValueCountResponse<*>>(response.aggregations!!.namedAggregations["count"]).value)
                response.values.forEach { assertNull(it.values { amount }); assertNull(it.values { entries }) }
                assertEquals(24, provider.decryptions.value, "Each logical row fetches the repeated metric dependency once")
            }
            provider.decryptions.value = 0
            val selected = store.unpooled(RefactorRecord.get(*requested,
                select = RefactorRecord.graph { listOf(title, amount) }, aggregations = metrics))
            assertEquals(60, assertIs<SumResponse<*>>(selected.aggregations!!.namedAggregations["sum"]).value)
            assertEquals(4, provider.decryptions.value, "Selected scalar rows use the existing whole-record reuse")
        } finally { store.close() }
    }

    @Test
    fun largeGraphReuseRetainsOwnershipAndFilteredHistoricalAndOversizedFallbacks() = runBoundedIntegrationTest {
        val provider = CountingRefactorProvider()
        val store = openStore(provider)
        try {
            for (size in listOf(8192, 17000)) {
                val status = assertIs<AddSuccess<RefactorRecord>>(phase("create $size entries") { store.execute(RefactorRecord.add(RefactorRecord.create {
                    title with "record"; entries with List(size) { "entry-$it" }; amount with 7
                })).statuses.single() })
                // Reuse must cross an eight-key window; fallback only needs two
                // independently decoded responses to prove its ownership contract.
                // Repeating a rejected 17k-node graph sixteen times makes this
                // correctness test depend on the runner's native transaction speed.
                val repeated = Array(if (size == 8192) 16 else 2) { status.key }
                provider.decryptions.value = 0
                val current = phase("current $size entries") { store.unpooled(RefactorRecord.get(*repeated)) }
                assertEquals(repeated.size, current.values.size)
                assertEquals(if (size == 8192) 1 else repeated.size, provider.decryptions.value)
                @Suppress("UNCHECKED_CAST")
                (current.values[0].values { entries } as MutableList<String>)[0] = "changed"
                assertEquals("entry-0", current.values[1].values { entries }!!.first())
                assertEquals("entry-0", store.execute(RefactorRecord.get(status.key)).values.single().values { entries }!!.first())
                if (size == 8192) {
                    val fallbackKeys = arrayOf(status.key, status.key)
                    provider.decryptions.value = 0
                    val filtered = phase("filtered $size entries") {
                        store.unpooled(RefactorRecord.get(*fallbackKeys, where = Equals(RefactorRecord.title.ref() with "record")))
                    }
                    assertEquals(fallbackKeys.size, filtered.values.size)
                    assertEquals(fallbackKeys.size, provider.decryptions.value, "Filtered requests retain the default graph bound")
                    provider.decryptions.value = 0
                    val historical = phase("historical $size entries") {
                        store.unpooled(RefactorRecord.get(*fallbackKeys, toVersion = status.version))
                    }
                    assertEquals(fallbackKeys.size, historical.values.size)
                    assertEquals(fallbackKeys.size, provider.decryptions.value, "Historical requests retain the default graph bound")
                }
            }
        } finally { store.close() }
    }

    private suspend fun <T> phase(name: String, block: suspend () -> T): T = try {
        block()
    } catch (error: FDBException) {
        throw AssertionError("FoundationDB large-graph phase failed: $name", error)
    }

    private suspend fun openStore(provider: CountingRefactorProvider) = FoundationDBDataStore.open(
        directoryPath = listOf("maryk", "get-refactor-integration", Uuid.random().toString()),
        dataModelsById = mapOf(1u to RefactorRecord), keepAllVersions = true, fieldEncryptionProvider = provider,
    )

    private suspend fun FoundationDBDataStore.unpooled(request: GetRequest<RefactorRecord>): ValuesResponse<RefactorRecord> {
        // Isolate record and metric reuse from the independent scalar value pool.
        val response = CompletableDeferred<ValuesResponse<RefactorRecord>>()
        processGetRequest(StoreAction(request, response), Cache.forRequest(0), createReadContext())
        return response.await()
    }
}
