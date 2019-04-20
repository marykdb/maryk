package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.StringDefinition
import kotlin.test.Test

class ListPropertyDefinitionWrapperTest {
    private val def = ListPropertyDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = ListDefinition(
            valueDefinition = StringDefinition()
        ),
        getter = { _: Any -> listOf<Nothing>() }
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, IsPropertyDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, IsPropertyDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }
}
