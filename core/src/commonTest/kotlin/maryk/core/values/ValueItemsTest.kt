package maryk.core.values

import maryk.core.properties.types.MutableTypedValue
import maryk.core.properties.types.TypedValue
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SimpleMarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    @Test
    fun absentChangeToUnitCreatesDeletionMarker() {
        val source = ValueItems(ValueItem(1u, "old"))
        val changes = MutableValueItems()

        changes.copyFromOriginalAndChange(source, 1u) { _, _ -> Unit }

        assertEquals(Unit, changes[1u])
    }

    @Test
    fun existingChangeToUnitRemovesPendingChange() {
        val changes = MutableValueItems(ValueItem(1u, "new"))

        changes.copyFromOriginalAndChange(ValueItems(ValueItem(1u, "old")), 1u) { _, _ -> Unit }

        assertFalse(changes.contains(1u))
    }

    @Test
    fun copiesNestedValuesInsideCollectionsWhenChangingFromOriginal() {
        val embedded = Values(EmbeddedMarykModel, ValueItems(ValueItem(1u, "old")))
        val source = ValueItems(ValueItem(1u, listOf(embedded)))
        val changes = MutableValueItems()
        var copiedList: MutableList<*>? = null

        changes.copyFromOriginalAndChange(source, 1u) { _, newValue ->
            copiedList = newValue as MutableList<*>
            null
        }

        val list = copiedList ?: error("Expected copied list")
        val copiedEmbedded = list.single() as Values<*>
        assertEquals("old", copiedEmbedded.values[1u])
        assertTrue(copiedEmbedded.values !== embedded.values)
        assertEquals(list, changes[1u])
    }

    @Test
    fun copiesTypedValueNestedValueWhenChangingFromOriginal() {
        val embedded = Values(EmbeddedMarykModel, ValueItems(ValueItem(1u, "typed")))
        val typed = TypedValue(SimpleMarykTypeEnum.S3, embedded)
        val source = ValueItems(ValueItem(1u, typed))
        val changes = MutableValueItems()
        var copiedTyped: MutableTypedValue<*, *>? = null

        changes.copyFromOriginalAndChange(source, 1u) { _, newValue ->
            copiedTyped = newValue as MutableTypedValue<*, *>
            null
        }

        val copied = copiedTyped ?: error("Expected copied typed value")
        assertSame(SimpleMarykTypeEnum.S3, copied.type)
        val copiedEmbedded = copied.value as Values<*>
        assertEquals("typed", copiedEmbedded.values[1u])
        assertTrue(copiedEmbedded.values !== embedded.values)
        assertEquals(copied, changes[1u])
    }
}
