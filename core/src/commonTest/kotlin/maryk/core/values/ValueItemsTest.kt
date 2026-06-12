package maryk.core.values

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ValueItemsTest {
    @Test
    fun oversizedIndexDoesNotWrapInSearch() {
        val items = ValueItems(
            ValueItem(1u, "one"),
            ValueItem(2u, "two")
        )

        assertNull(items[UInt.MAX_VALUE])
        assertNull(items.getValueItem(UInt.MAX_VALUE))
        assertFalse(items.contains(UInt.MAX_VALUE))
    }
}
