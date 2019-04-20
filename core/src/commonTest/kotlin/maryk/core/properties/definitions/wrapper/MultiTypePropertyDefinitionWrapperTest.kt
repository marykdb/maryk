package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.DefinitionsContext
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.Option
import kotlin.test.Test

class MultiTypePropertyDefinitionWrapperTest {
    private val def = MultiTypeDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = MultiTypeDefinition<Option, IsPropertyContext>(
            typeEnum = Option,
            definitionMap = mapOf(
                Option.V1 to StringDefinition(),
                Option.V2 to NumberDefinition(type = SInt32),
                Option.V3 to EmbeddedValuesDefinition(
                    dataModel = { EmbeddedMarykModel }
                )
            )
        ),
        getter = { _: Any -> null }
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            this.def,
            IsPropertyDefinitionWrapper.Model,
            { DefinitionsContext() },
            ::comparePropertyDefinitionWrapper
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            this.def,
            IsPropertyDefinitionWrapper.Model,
            { DefinitionsContext() },
            ::comparePropertyDefinitionWrapper
        )
    }
}
