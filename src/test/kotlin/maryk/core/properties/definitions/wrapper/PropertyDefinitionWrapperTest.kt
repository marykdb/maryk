package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.StringDefinition
import maryk.test.shouldBe
import kotlin.test.Test

fun comparePropertyDefinitionWrapper(converted: IsPropertyDefinitionWrapper<*, *, *>, original: IsPropertyDefinitionWrapper<*, *, *>) {
    converted.index shouldBe original.index
    converted.name shouldBe original.name
    converted.definition shouldBe original.definition
}

class PropertyDefinitionWrapperTest {
    private val def = PropertyDefinitionWrapper(
        index = 1,
        name = "wrapper",
        definition = StringDefinition(),
        getter = { _: Any -> null }
    )

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, IsPropertyDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, IsPropertyDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }
}