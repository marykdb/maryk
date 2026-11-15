package maryk.core.values

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import kotlin.test.Test
import kotlin.test.assertEquals

private object OwnedValuesCopyModel : RootDataModel<OwnedValuesCopyModel>() {
    val label by string(1u)
    val entries by list(2u, valueDefinition = StringDefinition())
}

class OwnedValuesCopyTest {
    @Test
    fun copiedContainersAndMutablePayloadsRemainIndependent() {
        val callerEntries = mutableListOf("original")
        val original = Values(OwnedValuesCopyModel, MutableValueItems(mutableListOf(
            ValueItem(1u, "original"), ValueItem(2u, callerEntries),
        )))
        val copied = original.copyWithOwnedValues()
        callerEntries += "caller"
        assertEquals(listOf("original"), copied[OwnedValuesCopyModel.entries.ref()])
        (copied.values as MutableValueItems)[1u] = "copy"
        assertEquals("original", original[OwnedValuesCopyModel.label.ref()])
        assertEquals("copy", copied[OwnedValuesCopyModel.label.ref()])
        val copiedEntries = copied[OwnedValuesCopyModel.entries.ref()] as MutableList<String>
        copiedEntries += "copy"
        assertEquals(listOf("original", "caller"), original[OwnedValuesCopyModel.entries.ref()])
        assertEquals(listOf("original", "copy"), copiedEntries)
    }
}
