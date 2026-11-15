package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.properties.types.Key
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DataStore
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HistoricalIndexLookupTest {
    @Test
    fun coldAndPreparedLookupsAgreeForShuffledOwnerTransitions() {
        val random = Random(8324)
        repeat(40) { iteration ->
            val index = UniqueIndexValues<SimpleMarykModel, String>(byteArrayOf(1))
            repeat(30) { position ->
                val at = random.nextInt(1, 100).toULong()
                val owner = record(position, at)
                assertTrue(index.addToIndex(owner, "shared", HLC(at)))
                assertTrue(index.removeFromIndex(owner, "shared", HLC(random.nextInt(1, 130).toULong()), true))
            }
            val item = index.indexValues.single() as HistoricalIndexValue<SimpleMarykModel, String>
            val ordered = HistoricalIndexValue("shared", item.records.toMutableList()).historyByVersion
            repeat(20) {
                val at = HLC(random.nextInt(0, 140).toULong())
                val expected = ordered.lastOrNull { it.version <= at }?.record
                assertSame(expected, item.recordAtVersion(at), "iteration=$iteration at=$at")
            }
        }
    }

    @Test
    fun publishedReleaseEntriesAreReplacedWhenLateClaimsChangeTheirOwner() {
        val index = UniqueIndexValues<SimpleMarykModel, String>(byteArrayOf(1))
        val first = record(1, 1uL)
        val second = record(2, 2uL)
        val third = record(3, 3uL)
        assertTrue(index.addToIndex(first, "shared", HLC(1uL)))
        assertTrue(index.removeFromIndex(first, "shared", HLC(4uL), true))
        assertTrue(index.addToIndex(second, "shared", HLC(3uL)))
        val item = index.indexValues.single() as HistoricalIndexValue<SimpleMarykModel, String>
        val published = item.historyByVersion.single { it.version == HLC(4uL) }
        assertSame(second, published.record)

        assertTrue(index.removeFromIndex(second, "shared", HLC(7uL), true))
        assertTrue(index.addToIndex(third, "shared", HLC(3uL)))
        assertSame(third, index["shared", HLC(5uL)])
        assertSame(third, item.historyByVersion.single { it.version == HLC(4uL) }.record)
        assertSame(second, published.record)
    }

    @Test
    fun outOfOrderOwnershipIsSortedAcrossAtomicCopies() {
        val store = DataStore<SimpleMarykModel>(true, false)
        val reference = byteArrayOf(1)
        @Suppress("UNCHECKED_CAST")
        fun index(data: DataStore<SimpleMarykModel>) = data.getOrCreateUniqueIndex(reference) as UniqueIndexValues<SimpleMarykModel, String>
        val first = DataRecord<SimpleMarykModel>(Key(byteArrayOf(1)), emptyList(), HLC(1uL), HLC(4uL))
        val second = DataRecord<SimpleMarykModel>(Key(byteArrayOf(2)), emptyList(), HLC(3uL), HLC(6uL))
        store.records.addAll(listOf(first, second))
        val history = index(store)
        history.addToIndex(first, "shared", HLC(1uL))
        history.removeFromIndex(first, "shared", HLC(4uL), keepAllVersions = true)
        history.addToIndex(second, "shared", HLC(3uL))
        history.removeFromIndex(second, "shared", HLC(6uL), keepAllVersions = true)
        val item = history.indexValues.single() as HistoricalIndexValue<SimpleMarykModel, String>
        assertEquals(listOf(1uL, 3uL, 4uL, 6uL), item.historyByVersion.map { it.version.timestamp })
        assertSame(first, history["shared", HLC(2uL)])
        assertSame(second, history["shared", HLC(3uL)])
        assertSame(second, history["shared", HLC(5uL)])
        assertNull(history["shared", HLC(6uL)])

        val staged = store.copyForAtomic(setOf(second.key)) {}
        val stagedRecord = staged.getByKey(second.key)!!
        assertSame(stagedRecord, index(staged)["shared", HLC(3uL)])
        staged.finishAtomicCopy()
        assertSame(stagedRecord, index(staged)["shared", HLC(3uL)])
        assertSame(second, history["shared", HLC(3uL)])
    }

    private fun record(key: Int, firstVersion: ULong) = DataRecord<SimpleMarykModel>(
        Key(byteArrayOf(key.toByte())), emptyList(), HLC(firstVersion), HLC(firstVersion),
    )

    @Test
    fun lateClaimUsesVersionOrderWithoutChangingCurrentAdmission() {
        val index = UniqueIndexValues<SimpleMarykModel, String>(byteArrayOf(1))
        val newer = record(1, 10uL)
        val older = record(2, 3uL)
        assertTrue(index.addToIndex(newer, "shared", HLC(10uL)))
        assertTrue(index.removeFromIndex(newer, "shared", HLC(20uL), true))
        assertTrue(index.addToIndex(older, "shared", HLC(3uL)))
        assertSame(older, index["shared"])
        assertNull(index["shared", HLC(2uL)])
        assertSame(older, index["shared", HLC(5uL)])
        assertSame(newer, index["shared", HLC(15uL)])
        assertNull(index["shared", HLC(25uL)])
        assertTrue(index.removeFromIndex(older, "shared", HLC(6uL), true))
        assertNull(index["shared"])
        assertNull(index["shared", HLC(7uL)])
        assertSame(newer, index["shared", HLC(15uL)])
    }

    @Test
    fun equalVersionsRetainAppliedOrder() {
        val index = UniqueIndexValues<SimpleMarykModel, String>(byteArrayOf(1))
        val first = record(1, 1uL)
        val second = record(2, 2uL)
        index.addToIndex(first, "shared", HLC(1uL))
        index.removeFromIndex(first, "shared", HLC(5uL), true)
        index.addToIndex(second, "shared", HLC(5uL))
        assertSame(second, index["shared", HLC(5uL)])
        index.removeFromIndex(second, "shared", HLC(5uL), true)
        assertNull(index["shared", HLC(5uL)])
    }

    @Test
    fun lateReleaseDoesNotEraseAnotherIncarnationOfTheSameKey() {
        val index = UniqueIndexValues<SimpleMarykModel, String>(byteArrayOf(1))
        val first = record(1, 1uL)
        val replacement = record(1, 3uL)
        index.addToIndex(first, "shared", HLC(1uL))
        index.removeFromIndex(first, "shared", HLC(8uL), true)
        index.addToIndex(replacement, "shared", HLC(3uL))
        assertSame(replacement, index["shared", HLC(8uL)])
    }

    @Test
    fun reorderedHistoryEditsDetachAnUntouchedAtomicList() {
        val store = DataStore<SimpleMarykModel>(true, false)
        val first = record(1, 1uL)
        val second = record(2, 3uL)
        store.records.addAll(listOf(first, second))
        @Suppress("UNCHECKED_CAST")
        val source = store.getOrCreateUniqueIndex(byteArrayOf(1)) as UniqueIndexValues<SimpleMarykModel, String>
        source.addToIndex(first, "shared", HLC(1uL))
        source.removeFromIndex(first, "shared", HLC(8uL), true)
        var copiedNodes = 0
        val staged = store.copyForAtomic(emptySet()) { copiedNodes += it }
        @Suppress("UNCHECKED_CAST")
        val index = staged.getOrCreateUniqueIndex(byteArrayOf(1)) as UniqueIndexValues<SimpleMarykModel, String>
        index.addToIndex(second, "shared", HLC(3uL))
        staged.finishAtomicCopy()
        assertTrue(copiedNodes > 0)
        assertSame(second, index["shared", HLC(8uL)])
        assertNull(source["shared", HLC(8uL)])
        assertNull(source["shared"])
    }

    @Test
    fun hardErasureReconcilesRemainingOwnerTransitions() {
        val index = UniqueIndexValues<SimpleMarykModel, String>(byteArrayOf(1))
        val first = record(1, 1uL)
        val second = record(2, 3uL)
        val third = record(3, 2uL)
        index.addToIndex(first, "shared", HLC(1uL))
        index.removeFromIndex(first, "shared", HLC(4uL), true)
        index.addToIndex(second, "shared", HLC(3uL))
        assertSame(second, index["shared", HLC(3uL)])
        assertTrue(index.deleteHardFromIndex(second.copy(), "shared"))
        assertNull(index["shared"])
        assertSame(first, index["shared", HLC(3uL)])
        assertNull(index["shared", HLC(4uL)])
        index.addToIndex(third, "shared", HLC(2uL))
        assertSame(third, index["shared", HLC(4uL)])
    }

    @Test
    fun firstReleaseCanPrecedeTheClaimWithoutBreakingSortedHistory() {
        val index = UniqueIndexValues<SimpleMarykModel, String>(byteArrayOf(1))
        val owner = record(1, 10uL)
        index.addToIndex(owner, "shared", HLC(10uL))
        index.removeFromIndex(owner, "shared", HLC(5uL), true)
        assertNull(index["shared", HLC(7uL)])
        assertSame(owner, index["shared", HLC(12uL)])
        assertNull(index["shared"])
    }

    @Test
    fun shuffledTransitionsMatchVersionOrderedOwnershipReplay() {
        data class Event(val version: ULong, val owner: DataRecord<SimpleMarykModel>, val release: Boolean)
        val random = Random(4231)
        val index = UniqueIndexValues<SimpleMarykModel, String>(byteArrayOf(1))
        val events = mutableListOf<Event>()
        repeat(80) { iteration ->
            val version = random.nextInt(1, 100).toULong()
            val owner = record(iteration + 1, version)
            val release = version + random.nextInt(0, 30).toULong()
            assertTrue(index.addToIndex(owner, "shared", HLC(version)))
            assertTrue(index.removeFromIndex(owner, "shared", HLC(release), true))
            events += Event(version, owner, false)
            events += Event(release, owner, true)
            for (at in 0uL..130uL) {
                var expected: DataRecord<SimpleMarykModel>? = null
                for (event in events.filter { it.version <= at }.sortedBy { it.version }) {
                    if (!event.release) expected = event.owner
                    else if (expected === event.owner) expected = null
                }
                assertSame(expected, index["shared", HLC(at)], "iteration=$iteration version=$at")
            }
        }
    }
}
