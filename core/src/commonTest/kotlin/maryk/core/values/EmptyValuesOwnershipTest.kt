package maryk.core.values

import maryk.core.models.emptyValues
import maryk.core.properties.types.Version
import maryk.test.models.ComplexModel
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

class EmptyValuesOwnershipTest {
    @Test
    fun emptyValuesOwnTheirMutableItemsAcrossRecordsAndModels() {
        val first = SimpleMarykModel.emptyValues()
        val sibling = SimpleMarykModel.emptyValues()
        val other = ComplexModel.emptyValues()
        first.mutate { arrayOf(value asValueItem "owned by first") }

        assertEquals("owned by first", first { value })
        assertEquals(0, sibling.size)
        assertEquals(0, other.size)
        assertEquals(0, SimpleMarykModel.emptyValues().size)
        assertEquals(0, ComplexModel.emptyValues().size)
    }

    @Test
    fun emptyObjectValuesDoNotShareItemsWithOtherObjectsOrValues() {
        val first = Version.emptyValues()
        val sibling = Version.emptyValues()
        val root = SimpleMarykModel.emptyValues()
        first.mutate { arrayOf(major asValueItem 12.toUShort()) }

        assertEquals(12.toUShort(), first { major })
        assertEquals(0, sibling.size)
        assertEquals(0, root.size)
        assertEquals(0, Version.emptyValues().size)
        assertEquals(0, EmptyValueItems.size)
    }
}
