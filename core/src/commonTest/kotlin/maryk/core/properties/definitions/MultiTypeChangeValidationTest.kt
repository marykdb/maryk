package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.enum.invoke
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.SInt16
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.MarykTypeEnum.T4
import maryk.test.models.SimpleMarykTypeEnum
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.SimpleMarykTypeEnum.S2
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MultiTypeChangeValidationTest {
    @Test
    fun changingAComplexStoredPlaceholderToAnotherCaseHasNoPreviousCaseValue() {
        val definition = MultiTypeDefinition(typeEnum = MarykTypeEnum, typeIsFinal = false)
        val placeholder = TypedValue<MarykTypeEnum<out Any>, Any>(T3, Unit)
        definition.validateWithRef(previousValue = placeholder, newValue = T1("new")) { null }
        definition.validateWithRef(previousValue = placeholder, newValue = T4(listOf("new"))) { null }
        definition.validateWithRef(previousValue = T3 { value with "old" }, newValue = T1("new")) { null }
        definition.validateWithRef(previousValue = placeholder, newValue = T3 { value with "same case" }) { null }
        assertFailsWith<InvalidValueException> {
            definition.validateWithRef(previousValue = placeholder, newValue = T1("&invalid")) { null }
        }
    }

    @Test
    fun aNewFinalCaseCanBeInitializedButItsExistingValueAndFinalTypesStayProtected() {
        val definition = InternalMultiTypeDefinition<SimpleMarykTypeEnum<out Any>, Any, IsPropertyContext>(
            typeEnum = IndexedEnumDefinition(SimpleMarykTypeEnum::class, values = { listOf(S1, S2) }), typeIsFinal = false,
            definitionMap = mapOf(S1 to StringDefinition(final = true), S2 to NumberDefinition(type = SInt16)),
        )
        definition.validateWithRef(previousValue = S2(1), newValue = S1("first")) { null }
        assertFailsWith<AlreadySetException> {
            definition.validateWithRef(previousValue = S1("first"), newValue = S1("changed")) { null }
        }
        assertFailsWith<AlreadySetException> {
            definition.copy(typeIsFinal = true).validateWithRef(previousValue = S2(1), newValue = S1("first")) { null }
        }
        assertFailsWith<AlreadySetException> {
            definition.copy(final = true, typeIsFinal = true).validateWithRef(previousValue = S2(1), newValue = S2(2)) { null }
        }
    }
}
