package maryk.datastore.shared.updates

import maryk.core.models.graph
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.ValuesResponse
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListenerMembershipTest {
    private fun key(value: Int) = Key<SimpleMarykModel>(ByteArray(16).also {
        it[14] = (value ushr 8).toByte()
        it[15] = value.toByte()
    })
    private val values = SimpleMarykModel.create { value with "record" }

    @Test
    fun scanMembershipTracksReplacementRemovalDuplicatesAndColdLists() {
        val keys = (1..128).map(::key)
        val request = SimpleMarykModel.scan(
            select = SimpleMarykModel.graph { listOf(value) }, limit = 200u, allowTableScan = true,
        )
        val listener = UpdateListenerForScan(request, SimpleMarykModel.createScanRange(null, null, true),
            ValuesResponse(SimpleMarykModel, keys.map { ValuesWithMetaData(it, values, 1uL, 1uL, false) }))
        repeat(64) {
            assertTrue(listener.containsKey(keys.last()))
            assertFalse(listener.containsKey(key(999)))
        }
        listener.removeKey(keys.last())
        assertFalse(listener.containsKey(keys.last()))
        val duplicates = listOf(key(200), key(200)) + keys.take(64)
        listener.matchingKeys.value = duplicates
        repeat(64) { assertTrue(listener.containsKey(key(200))) }
        assertEquals(duplicates, listener.matchingKeys.value)
        var previous = key(200)
        for (value in 201..240) {
            val next = key(value)
            listener.matchingKeys.value = keys.take(64) + next
            assertFalse(listener.containsKey(previous))
            assertTrue(listener.containsKey(next))
            previous = next
        }
        listener.matchingKeys.value = listOf(key(400))
        assertTrue(listener.containsKey(key(400)))
        assertFalse(listener.containsKey(previous))
    }

    @Test
    fun getPresenceIndexTracksRemovalAndReinsertionWithoutChangingRequestOrder() {
        val keys = (1..128).map(::key)
        val listener = UpdateListenerForGet(SimpleMarykModel.get(*keys.toTypedArray()),
            ValuesResponse(SimpleMarykModel, keys.map { ValuesWithMetaData(it, values, 1uL, 1uL, false) }))
        assertTrue(listener.containsKey(keys.last()))
        assertEquals(127, listener.removeKey(keys.last()))
        assertFalse(listener.containsKey(keys.last()))
        assertEquals(127, listener.addValues(keys.last(), values))
        assertTrue(listener.containsKey(keys.last()))
        assertEquals(keys, listener.matchingKeys.value)
    }
}
