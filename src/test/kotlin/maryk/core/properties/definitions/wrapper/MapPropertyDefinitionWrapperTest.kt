package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.numeric.Float32
import maryk.test.shouldBe
import kotlin.test.Test

class MapPropertyDefinitionWrapperTest {
    private val def = MapPropertyDefinitionWrapper(
            index = 1,
            name = "wrapper",
            definition = MapDefinition(
                    keyDefinition = NumberDefinition(type = Float32),
                    valueDefinition = StringDefinition()
            ),
            getter = { _: Any -> null }
    )

    @Test
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(this.def, MapPropertyDefinitionWrapper, null, ::compare)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, MapPropertyDefinitionWrapper, null, ::compare)
    }

    private fun compare(converted: MapPropertyDefinitionWrapper<*, *, *, *>, original: MapPropertyDefinitionWrapper<*, *, *, *>) {
        converted.index shouldBe original.index
        converted.name shouldBe original.name
        converted.definition shouldBe original.definition
    }
}