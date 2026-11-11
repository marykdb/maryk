package maryk.datastore.shared

import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
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
