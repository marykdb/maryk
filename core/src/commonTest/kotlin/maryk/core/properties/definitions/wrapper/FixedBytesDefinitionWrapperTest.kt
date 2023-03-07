package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.numeric.UInt32
import kotlin.test.Test

class FixedBytesDefinitionWrapperTest {
    private val type = UInt32

    private val def = FixedBytesDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = NumberDefinition(type = type),
        getter = { _: Any -> null }
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, IsDefinitionWrapper.Model.Model, null, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, IsDefinitionWrapper.Model.Model, null, ::comparePropertyDefinitionWrapper)
    }
}
