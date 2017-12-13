package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.test.shouldBe

import kotlin.test.Test

class ListPropertyDefinitionWrapperTest {
    private val def = ListPropertyDefinitionWrapper(
            index = 1,
            name = "wrapper",
            definition = ListDefinition(
                    valueDefinition = StringDefinition()
            ),
            getter = { _: Any -> null }
    )

    @Test
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(this.def, ListPropertyDefinitionWrapper, null, ::compare)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, ListPropertyDefinitionWrapper, null, ::compare)
    }

    private fun compare(converted: ListPropertyDefinitionWrapper<*, *, *>, original: ListPropertyDefinitionWrapper<*, *, *>) {
        converted.index shouldBe original.index
        converted.name shouldBe original.name
        converted.definition shouldBe original.definition
    }
}