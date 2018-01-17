package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.numeric.UInt32
import kotlin.test.Test

class FixedBytesPropertyDefinitionWrapperTest {
    private val def = FixedBytesPropertyDefinitionWrapper(
            index = 1,
            name = "wrapper",
            definition = NumberDefinition(type = UInt32),
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