package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.numeric.UInt32
import kotlin.test.Test

class FixedBytesPropertyDefinitionWrapperTest {
    private val type = UInt32

    private val def = FixedBytesPropertyDefinitionWrapper(
        index = 1,
        name = "wrapper",
        definition = NumberDefinition(type = type),
        getter = { _: Any -> null }
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
