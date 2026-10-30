package maryk.core.properties.definitions

import maryk.core.models.RootDataModel
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.query.pairs.with
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

private object ComplexChangeContractModel : RootDataModel<ComplexChangeContractModel>() {
    val allocated by incrementingMap(1u, required = false, keyNumberDescriptor = UInt32,
        valueDefinition = StringDefinition())
    val maps by map(2u, keyDefinition = StringDefinition(),
        valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
    val sets by map(3u, keyDefinition = StringDefinition(),
        valueDefinition = SetDefinition(valueDefinition = StringDefinition()))
    val types by map(4u, keyDefinition = StringDefinition(),
        valueDefinition = MultiTypeDefinition(typeEnum = MarykTypeEnum))
    val lists by map(5u, keyDefinition = StringDefinition(),
        valueDefinition = ListDefinition(valueDefinition = StringDefinition()))
    val embedded by map(6u, keyDefinition = StringDefinition(),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { EmbeddedMarykModel }))
}

class ChangeableComplexDefinitionTest {
    private fun assertContract(pair: IsReferenceValueOrNullPair<*>, definition: IsPropertyDefinition<*>) {
        val actual = pair.reference.propertyDefinition
        assertSame(definition, actual)
        assertIs<IsChangeableValueDefinition<*, *>>(actual)
    }

    @Test
    fun incrementingAndNestedMapsImplementTheChangePairContract() {
        val model = ComplexChangeContractModel
        assertIs<IsChangeableValueDefinition<*, *>>(model.allocated.definition)
        assertContract(model.ref { allocated } with mapOf(1u to "value"), model.allocated)
        assertContract(model.ref { allocated } with null, model.allocated)
        assertContract(model.ref { maps at "nested" } with mapOf("key" to "value"), model.maps.definition.valueDefinition)
        assertContract(model.ref { maps at "nested" } with null, model.maps.definition.valueDefinition)
    }

    @Test
    fun nestedSetsAndTypesImplementTheChangePairContract() {
        val model = ComplexChangeContractModel
        assertContract(model.ref { sets at "nested" } with setOf("value"), model.sets.definition.valueDefinition)
        assertContract(model.ref { sets at "nested" } with null, model.sets.definition.valueDefinition)
        assertContract(model.ref { types at "nested" } with MarykTypeEnum.T1("value"), model.types.definition.valueDefinition)
        assertContract(model.ref { types at "nested" } with null, model.types.definition.valueDefinition)
    }

    @Test
    fun nestedListsAndEmbeddedValuesRetainTheirChangePairContract() {
        val model = ComplexChangeContractModel
        assertContract(model.ref { lists at "nested" } with listOf("value"), model.lists.definition.valueDefinition)
        assertContract(model.ref { embedded at "nested" } with EmbeddedMarykModel.create { value with "value" },
            model.embedded.definition.valueDefinition)
    }
}
