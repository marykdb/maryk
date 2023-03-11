package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import kotlin.test.Test

class SetPropertyDefinitionWrapperTest {
    private val def = SetDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = SetDefinition(
            valueDefinition = StringDefinition()
        ),
        getter = { _: Any -> null }
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, IsDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, IsDefinitionWrapper.Model.Model, null, ::comparePropertyDefinitionWrapper)
    }
}
