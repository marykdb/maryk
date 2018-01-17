package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.StringDefinition
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
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, IsPropertyDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, IsPropertyDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }
}