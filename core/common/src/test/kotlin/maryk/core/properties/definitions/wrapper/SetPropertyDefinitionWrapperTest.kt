package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import kotlin.test.Test

class SetPropertyDefinitionWrapperTest {
    private val def = SetPropertyDefinitionWrapper(
        index = 1,
        name = "wrapper",
        definition = SetDefinition(
            valueDefinition = StringDefinition()
        ),
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