package maryk.datastore.shared

import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class RequestRecordCacheTest {
    private val key = Key<SimpleMarykModel>(byteArrayOf(1))
    private fun record() = ValuesWithMetaData(key, SimpleMarykModel.create { value with "original" }, 1uL, 2uL, false)

    @Test fun duplicateResultsHaveIndependentValueContainers() {
        val cache = assertNotNull(RequestRecordCache.create(listOf(key, key)))
        val original = record()
        cache.put(key, original)
        val copy = assertNotNull(cache.get(key))
        assertEquals(original, copy)
        assertNotSame(original.values, copy.values)
        val changed = copy.values.copyWithValue(SimpleMarykModel.value.index, "changed")
        assertEquals("changed", changed { value })
        assertEquals("original", original.values { value })
        assertEquals("original", cache.get(key)?.values { value })
    }

    @Test fun negativeResultsAreMemoizedOnlyForRepeatedKeys() {
        val other = Key<SimpleMarykModel>(byteArrayOf(2))
        val cache = assertNotNull(RequestRecordCache.create(listOf(key, other, key)))
        assertFalse(cache.contains(key))
        cache.put(key, null)
        assertTrue(cache.contains(key))
        assertNull(cache.get(key))
        cache.put(other, null)
        assertFalse(cache.contains(other))
    }

    @Test fun rejectedGraphsAreNotRevisitedOrTreatedAsMissingRecords() {
        val cache = assertNotNull(RequestRecordCache.create(listOf(key, key), maxValueNodes = 8))
        var visited = 0
        val oversized = object : AbstractList<String>() {
            override val size = 100
            override fun get(index: Int): String = "value".also { visited++ }
        }
        val value = record().let { it.copy(values = it.values.copyWithValue(1u, oversized)) }
        cache.put(key, value)
        val firstWalk = visited
        assertTrue(firstWalk > 0)
        cache.put(key, value)
        assertEquals(firstWalk, visited)
        assertFalse(cache.contains(key))
        val other = Key<SimpleMarykModel>(byteArrayOf(2))
        val fresh = assertNotNull(RequestRecordCache.create(listOf(other, other)))
        fresh.put(other, null)
        assertTrue(fresh.contains(other))
    }

    @Test fun mutableCollectionsAndByteArraysAreCopiedOnReuse() {
        val cache = assertNotNull(RequestRecordCache.create(listOf(key, key)))
        val bytes = byteArrayOf(1, 2)
        val collection = mutableListOf(mapOf("bytes" to bytes))
        cache.put(key, record().let { it.copy(values = it.values.copyWithValue(1u, collection)) })
        @Suppress("UNCHECKED_CAST")
        val copied = assertNotNull(cache.get(key)).values.original(1u) as MutableList<Map<String, ByteArray>>
        copied[0].getValue("bytes")[0] = 9
        copied.clear()
        assertEquals(1, collection.size)
        assertEquals(1, bytes[0].toInt())
    }

    @Test fun largerCopyBudgetsAreExplicitBoundedAndKeepOwnedContainers() {
        val original = record().let { it.copy(values = it.values.copyWithValue(1u, List(8192) { "value-$it" })) }
        val defaults = assertNotNull(RequestRecordCache.create(listOf(key, key)))
        defaults.put(key, original)
        assertFalse(defaults.contains(key))
        val expanded = assertNotNull(RequestRecordCache.create(listOf(key, key), maxValueNodes = 16_384))
        expanded.put(key, original)
        assertTrue(expanded.contains(key))
        @Suppress("UNCHECKED_CAST")
        val copy = assertNotNull(expanded.get(key)).values.original(1u) as MutableList<String>
        copy[0] = "changed"
        assertEquals("value-0", (original.values.original(1u) as List<*>).first())
        assertEquals("value-0", (assertNotNull(expanded.get(key)).values.original(1u) as List<*>).first())
        val tooLarge = assertNotNull(RequestRecordCache.create(listOf(key, key), maxValueNodes = 16_384))
        tooLarge.put(key, original.copy(values = original.values.copyWithValue(1u, List(16_384) { "value" })))
        assertFalse(tooLarge.contains(key))
        val cyclic = mutableListOf<Any>().also { it.add(it) }
        tooLarge.put(key, original.copy(values = original.values.copyWithValue(1u, cyclic)))
        assertFalse(tooLarge.contains(key))
        assertFailsWith<IllegalArgumentException> { RequestRecordCache.create(listOf(key, key), maxValueNodes = 0) }
        assertFailsWith<IllegalArgumentException> { RequestRecordCache.create(listOf(key, key), maxValueNodes = 16_385) }
    }

    @Test fun planningAndCopyWorkAreBoundedAndDoNotAcceptUnknownGraphs() {
        assertNull(RequestRecordCache.create(listOf(key)))
        assertNull(RequestRecordCache.create(List(4097) { key }))
        val cache = assertNotNull(RequestRecordCache.create(listOf(key, key)))
        val cyclic = mutableListOf<Any>().also { it.add(it) }
        cache.put(key, record().let { it.copy(values = it.values.copyWithValue(1u, cyclic)) })
        assertFalse(cache.contains(key))
        cache.put(key, record().let { it.copy(values = it.values.copyWithValue(1u, Any())) })
        assertFalse(cache.contains(key))
    }
}
