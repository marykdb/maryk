package maryk.core.properties.definitions

import maryk.core.models.RootDataModel
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.invoke
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object NestedValidationRecord : RootDataModel<NestedValidationRecord>() {
    val lists by map(1u, keyDefinition = StringDefinition(),
        valueDefinition = ListDefinition(valueDefinition = StringDefinition(minSize = 3u)))
    val sets by map(2u, keyDefinition = StringDefinition(),
        valueDefinition = SetDefinition(valueDefinition = StringDefinition(minSize = 3u)))
    val nested by map(3u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(
        keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition(minSize = 3u)),
    ))
    val typed by multiType(4u, typeEnum = MarykTypeEnum)
}

class NestedValidationReferenceTest {
    @Test
    fun invalidNestedMembersIdentifyTheirActualParents() {
        val values = NestedValidationRecord.create {
            lists with mapOf("key" to listOf("x"))
            sets with mapOf("key" to setOf("x"))
            nested with mapOf("outer" to mapOf("inner" to listOf("x")))
            typed with MarykTypeEnum.T4(listOf("invalid&"))
        }
        val failure = assertFailsWith<ValidationUmbrellaException> { values.validate() }
        fun leaves(error: ValidationException): List<ValidationException> =
            if (error is ValidationUmbrellaException) error.exceptions.flatMap(::leaves) else listOf(error)
        val references = leaves(failure).map { exception -> requireNotNull(when (exception) {
            is InvalidSizeException -> exception.reference
            is InvalidValueException -> exception.reference
            else -> error("Unexpected validation failure: $exception")
        }) }
        assertEquals(listOf("lists.@key.@0", "sets.@key.#x", "nested.@outer.@inner.@0", "typed.*T4.@0"),
            references.map { it.completeName })
        for (reference in references) {
            assertEquals(reference, NestedValidationRecord.getPropertyReferenceByName(reference.completeName))
        }
    }
}
