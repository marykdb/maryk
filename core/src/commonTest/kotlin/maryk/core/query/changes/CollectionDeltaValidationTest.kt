package maryk.core.query.changes

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.set
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object DeltaValidationRecord : RootDataModel<DeltaValidationRecord>() {
    val items by list(1u, minSize = 3u, valueDefinition = StringDefinition(minSize = 2u))
    val members by set(2u, minSize = 3u, valueDefinition = StringDefinition(minSize = 2u))
}

class CollectionDeltaValidationTest {
    @Test
    fun setDeltasValidateOperandsWithoutAssumingTheCurrentCollectionSize() {
        val errors = mutableListOf<ValidationException>()
        SetChange(DeltaValidationRecord.ref { members }.change()).validate(errors::add)
        assertEquals(emptyList(), errors, "An empty delta is valid")
        SetChange(DeltaValidationRecord.ref { members }.change(addValues = setOf("ok"))).validate(errors::add)
        assertEquals(emptyList(), errors, "One added member does not determine the final set size")
        ListChange(DeltaValidationRecord.ref { items }.change(addValuesToEnd = listOf("ok"))).validate(errors::add)
        assertEquals(emptyList(), errors)
        SetChange(DeltaValidationRecord.ref { members }.change(addValues = setOf("x"))).validate(errors::add)
        assertEquals("members.#x", assertIs<InvalidSizeException>(errors.single()).reference?.completeName)
    }
}
