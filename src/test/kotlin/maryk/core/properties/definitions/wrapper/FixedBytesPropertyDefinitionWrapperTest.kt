package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.numeric.UInt32
import maryk.test.shouldBe
import kotlin.test.Test

class FixedBytesPropertyDefinitionWrapperTest {
    private val def = FixedBytesPropertyDefinitionWrapper(
            index = 1,
            name = "wrapper",
            definition = NumberDefinition(type = UInt32),
            getter = { _: Any -> null }
    )

    @Test
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(this.def, FixedBytesPropertyDefinitionWrapper, null, ::compare)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, FixedBytesPropertyDefinitionWrapper, null, ::compare)
    }

    private fun compare(converted: FixedBytesPropertyDefinitionWrapper<*, *, *, *>, original: FixedBytesPropertyDefinitionWrapper<*, *, *, *>) {
        converted.index shouldBe original.index
        converted.name shouldBe original.name
        converted.definition shouldBe original.definition
    }
}