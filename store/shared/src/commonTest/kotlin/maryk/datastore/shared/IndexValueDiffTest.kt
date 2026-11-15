package maryk.datastore.shared

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame

class IndexValueDiffTest {
    @Test
    fun tinyDifferencesPreserveOrderDuplicatesAndArrayOwnership() {
        val first = byteArrayOf(1)
        val second = byteArrayOf(2)
        val third = byteArrayOf(3)
        val result = diffIndexValues(listOf(first, first.copyOf(), second), listOf(first.copyOf(), third, third.copyOf()))
        assertContentEquals(listOf(second.toList()), result.removed.map(ByteArray::toList))
        assertContentEquals(listOf(third.toList(), third.toList()), result.added.map(ByteArray::toList))
        assertSame(second, result.removed.single())
        assertSame(third, result.added.first())
    }

    @Test
    fun emptySideResultsDoNotAliasMutableInputLists() {
        val values = mutableListOf(byteArrayOf(1), byteArrayOf(1))
        val added = diffIndexValues(emptyList(), values)
        val removed = diffIndexValues(values, emptyList())
        values.clear()
        assertContentEquals(listOf(listOf<Byte>(1), listOf<Byte>(1)), added.added.map(ByteArray::toList))
        assertContentEquals(listOf(listOf<Byte>(1), listOf<Byte>(1)), removed.removed.map(ByteArray::toList))
        assertContentEquals(emptyList(), diffIndexValues(emptyList(), emptyList()).added)
    }
    @Test
    fun findsContentBasedDifferencesForLargeIndexValueLists() {
        val oldValues = List(2_000) { index -> byteArrayOf(0, (index % 127).toByte(), (index / 127).toByte()) }
        val newValues = oldValues.drop(1_000).map(ByteArray::copyOf) +
            List(1_000) { index -> byteArrayOf(1, (index % 127).toByte(), (index / 127).toByte()) }

        val diff = diffIndexValues(oldValues, newValues)

        assertContentEquals(oldValues.take(1_000).map(ByteArray::toList), diff.removed.map(ByteArray::toList))
        assertContentEquals(newValues.takeLast(1_000).map(ByteArray::toList), diff.added.map(ByteArray::toList))
    }

    @Test
    fun preservesExistingDuplicateMembershipSemantics() {
        val value = byteArrayOf(1)

        val diff = diffIndexValues(listOf(value, value.copyOf()), listOf(value.copyOf()))

        assertContentEquals(emptyList(), diff.removed)
        assertContentEquals(emptyList(), diff.added)
    }
}
