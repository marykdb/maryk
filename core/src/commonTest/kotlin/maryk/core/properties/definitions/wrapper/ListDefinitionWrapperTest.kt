package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.StringDefinition
import kotlin.test.Test

class ListDefinitionWrapperTest {
    private val def = ListDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = ListDefinition(
            valueDefinition = StringDefinition()
        ),
        getter = { _: Any -> listOf<Nothing>() }
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, IsDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, IsDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }
}
