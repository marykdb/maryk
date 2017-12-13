package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.test.shouldBe
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
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(this.def, SetPropertyDefinitionWrapper, null, ::compare)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, SetPropertyDefinitionWrapper, null, ::compare)
    }

    private fun compare(converted: SetPropertyDefinitionWrapper<*, *, *>, original: SetPropertyDefinitionWrapper<*, *, *>) {
        converted.index shouldBe original.index
        converted.name shouldBe original.name
        converted.definition shouldBe original.definition
    }
}