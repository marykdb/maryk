package maryk.core.values

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.test.models.EmbeddedMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

private object InsertionOwnershipRecord : RootDataModel<InsertionOwnershipRecord>() {
    val items by list(1u, required = false,
        valueDefinition = EmbeddedValuesDefinition(dataModel = { EmbeddedMarykModel }))
    val allocated by incrementingMap(2u, required = false, keyNumberDescriptor = UInt32,
        valueDefinition = EmbeddedValuesDefinition(dataModel = { EmbeddedMarykModel }))
}

class ValuesInsertionOwnershipTest {
    @Test
    fun automaticallyAllocatedValuesAreCopiedBeforeLaterChildEdits() {
        assertOwnedInsertion("automatic")
    }

    @Test
    fun assignedIncrementingMapValuesAreCopiedBeforeLaterChildEdits() {
        assertOwnedInsertion("assigned")
    }

    @Test
    fun appendedListValuesAreCopiedBeforeLaterChildEdits() {
        assertOwnedInsertion("append")
    }

    @Test
    fun indexedListValuesAreCopiedBeforeLaterChildEdits() {
        assertOwnedInsertion("index")
    }

    private fun assertOwnedInsertion(mode: String) {
        val supplied = EmbeddedMarykModel.create(setDefaults = false) { value with "caller" }
        val original = InsertionOwnershipRecord.create(setDefaults = false) {
            items with emptyList<Values<EmbeddedMarykModel>>()
            allocated with emptyMap<UInt, Values<EmbeddedMarykModel>>()
        }
        val addition = when (mode) {
            "automatic" -> IncMapChange(InsertionOwnershipRecord.ref { allocated }.change(addValues = listOf(supplied)))
            "assigned" -> IncMapAddition(IncMapKeyAdditions(InsertionOwnershipRecord.ref { allocated }, listOf(1u), listOf(supplied)))
            "append" -> ListChange(InsertionOwnershipRecord.ref { items }.change(addValuesToEnd = listOf(supplied)))
            "index" -> ListChange(InsertionOwnershipRecord.ref { items }.change(addValuesAtIndex = mapOf(0u to supplied)))
            else -> error("Unknown insertion")
        }
        val child = if (mode == "automatic" || mode == "assigned") {
            EmbeddedMarykModel.ref(InsertionOwnershipRecord.ref { allocated at 1u }) { value }
        } else EmbeddedMarykModel.ref(InsertionOwnershipRecord.ref { items at 0u }) { value }
        val changed = original.change(addition, Change(child with "edited"))

        assertEquals("edited", changed[child])
        assertEquals("caller", supplied { value })
        assertEquals(emptyList(), original { items })
        assertEquals(emptyMap(), original { allocated })
        // Reusing a request input must start with its original content too.
        val repeated = original.change(addition)
        assertEquals("caller", repeated[child])
        assertEquals("caller", supplied { value })
    }
}
