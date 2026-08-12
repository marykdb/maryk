package maryk.datastore.shared

import kotlin.test.Test
import kotlin.test.assertContentEquals

class IndexValueDiffTest {
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
