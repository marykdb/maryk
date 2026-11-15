package maryk.datastore.shared

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.models.graph
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.test.models.SimpleMarykModel
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RequestValueCacheTest {
    private fun key(index: Int) = Key<IsValuesDataModel>(byteArrayOf(index.toByte()))
    private fun reference() = object : IsPropertyReferenceForCache<String, StringDefinition> {
        override val propertyDefinition = StringDefinition()
    }

    @Test fun budgetIsSharedAcrossKeysAndModelsAndHitsProtectRecentValues() {
        val cache = Cache.forRequest(6000)
        val reference = reference()
        cache.readValue(1u, key(1), reference, 1uL) { "a".repeat(1000) }
        cache.readValue(2u, key(2), reference, 1uL) { "b".repeat(1000) }
        cache.readValue(1u, key(1), reference, 1uL) { error("Expected hit") }
        cache.readValue(3u, key(3), reference, 1uL) { "c".repeat(1000) }
        assertEquals("a".repeat(1000), cache.readValue(1u, key(1), reference, 1uL) { error("Recent value evicted") })
        assertEquals("evicted", cache.readValue(2u, key(2), reference, 1uL) { "evicted" })
    }

    @Test fun largeValueCanBorrowTheRequestBudget() {
        val cache = Cache.forRequest()
        val reference = reference()
        val value = "x".repeat(98_304)
        cache.readValue(1u, key(1), reference, 1uL) { value }
        assertEquals(value, cache.readValue(1u, key(1), reference, 1uL) { error("Large value decoded twice") })
    }

    @Test fun replacementsDeletionAndOlderReadsKeepAccountingConsistent() {
        val cache = Cache.forRequest(5000)
        val reference = reference()
        cache.readValue(1u, key(1), reference, 2uL) { "new" }
        cache.readValue(1u, key(1), reference, 1uL) { "x".repeat(4000) }
        assertEquals("new", cache.readValue(1u, key(1), reference, 2uL) { error("Older read lost newer value") })
        cache.readValue(1u, key(1), reference, 3uL) { "x".repeat(4000) }
        assertEquals("reload", cache.readValue(1u, key(1), reference, 2uL) { "reload" })
        cache.delete(1u, key(1))
        repeat(20) { index ->
            cache.readValue(1u, key(index), reference, 1uL) { "x".repeat(2000) }
            cache.delete(1u, key(index))
        }
        cache.readValue(1u, key(1), reference, 4uL) { "small" }
        assertEquals("small", cache.readValue(1u, key(1), reference, 4uL) { error("Accounting not released") })
    }

    @Test fun failedAndUncacheableReadsDoNotEvictUsefulValues() {
        val cache = Cache.forRequest(5000)
        val reference = reference()
        cache.readValue(1u, key(1), reference, 1uL) { "useful" }
        assertFailsWith<IllegalStateException> { cache.readValue(1u, key(1), reference, 2uL) { error("failed") } }
        cache.readValue(1u, key(2), reference, 1uL) { "x".repeat(4000) }
        cache.readValue(1u, key(3), reference, 1uL) { mutableListOf<Any>() }
        assertEquals("useful", cache.readValue(1u, key(1), reference, 1uL) { error("Unrelated value evicted") })
    }

    @Test fun perKeyEntryAndPerModelKeySafeguardsRemainBounded() {
        val cache = Cache.forRequest()
        val references = List(129) { reference() }
        references.forEach { cache.readValue(1u, key(1), it, 1uL) { "small" } }
        assertEquals("evicted", cache.readValue(1u, key(1), references.first(), 1uL) { "evicted" })
        for (index in 0..1024) {
            val record = Key<IsValuesDataModel>(byteArrayOf((index shr 8).toByte(), index.toByte()))
            cache.readValue(2u, record, references[0], 1uL) { "small" }
        }
        assertEquals("evicted", cache.readValue(2u, Key<IsValuesDataModel>(byteArrayOf(0, 0)), references[0], 1uL) { "evicted" })
    }

    @Test fun separateRequestsNeverShareValues() {
        val reference = reference()
        Cache.forRequest().readValue(1u, key(1), reference, 1uL) { "one" }
        assertEquals("two", Cache.forRequest().readValue(1u, key(1), reference, 1uL) { "two" })
        assertFailsWith<IllegalArgumentException> { Cache.forRequest(-1) }
    }

    @Test fun recordIndexesAreIncludedInTheBudget() {
        val reference = reference()
        val cache = Cache.forRequest(2048)
        for (index in 1..3) cache.readValue(1u, key(index), reference, 1uL) { "small" }
        assertEquals("evicted", cache.readValue(1u, key(1), reference, 1uL) { "evicted" })
        val tiny = Cache.forRequest(512)
        var reads = 0
        repeat(2) { tiny.readValue(1u, key(1), reference, 1uL) { reads++; "small" } }
        assertEquals(2, reads)
    }

    @Test fun reentrantDecodingCannotUnlinkAnAlreadyEvictedEntryOrOverwriteANewerVersion() {
        val cache = Cache.forRequest(2048)
        val reference = reference()
        cache.readValue(1u, key(1), reference, 1uL) { "old" }
        cache.readValue(1u, key(1), reference, 2uL) {
            cache.readValue(1u, key(2), reference, 1uL) { "x".repeat(500) }
            "new"
        }
        assertEquals("new", cache.readValue(1u, key(1), reference, 2uL) { error("Entry lost after nested eviction") })
        cache.readValue(1u, key(1), reference, 3uL) {
            cache.readValue(1u, key(1), reference, 4uL) { "newest" }
            "older result"
        }
        assertEquals("newest", cache.readValue(1u, key(1), reference, 4uL) { error("Nested newer version overwritten") })
    }

    @Test fun disabledPoolDoesNotRetainValues() {
        val cache = Cache.forRequest(0)
        val reference = reference()
        var reads = 0
        repeat(2) { cache.readValue(1u, key(1), reference, 1uL) { reads++; "value" } }
        assertEquals(2, reads)
    }

    @Test fun singleRecordAndScalarReadsAvoidAdmissionWhileComplexBatchesKeepThePool() {
        val reference = reference()
        val simpleKey = Key<SimpleMarykModel>(ByteArray(SimpleMarykModel.Meta.keyByteSize))
        val completeKey = Key<CompleteMarykModel>(ByteArray(CompleteMarykModel.Meta.keyByteSize))
        val requests = listOf(
            SimpleMarykModel.get(simpleKey) to false,
            SimpleMarykModel.scan() to false,
            CompleteMarykModel.get(completeKey, select = CompleteMarykModel.graph { listOf(string) }) to false,
            CompleteMarykModel.get(completeKey) to false,
            CompleteMarykModel.get(completeKey, completeKey) to true,
            CompleteMarykModel.scan() to true,
            SimpleMarykModel.get(*Array(4097) { simpleKey }) to true,
        )
        for ((request, caches) in requests) {
            val cache = Cache.forRequest(request)
            var reads = 0
            repeat(2) { cache.readValue(1u, key(1), reference, 1uL) { reads++; "value" } }
            assertEquals(if (caches) 1 else 2, reads)
        }
    }
}
