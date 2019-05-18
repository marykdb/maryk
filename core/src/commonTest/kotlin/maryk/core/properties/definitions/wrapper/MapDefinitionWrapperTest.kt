package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.numeric.Float32
import kotlin.test.Test

class MapDefinitionWrapperTest {
    private val def = MapDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = MapDefinition(
            keyDefinition = NumberDefinition(type = Float32),
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
        checkJsonConversion(this.def, IsDefinitionWrapper.Model, null, ::comparePropertyDefinitionWrapper)
    }
}
