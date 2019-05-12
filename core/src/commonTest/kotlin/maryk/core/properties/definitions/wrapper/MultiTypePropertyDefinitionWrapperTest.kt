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
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.MarykTypeEnum.T3
import kotlin.test.Test

class MultiTypePropertyDefinitionWrapperTest {
    private val def = MultiTypeDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = MultiTypeDefinition<MarykTypeEnum<*>, Any, IsPropertyContext>(
            typeEnum = MarykTypeEnum,
            definitionMap = mapOf(
                T1 to StringDefinition(),
                T2 to NumberDefinition(type = SInt32),
                T3 to EmbeddedValuesDefinition(
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
            IsDefinitionWrapper.Model,
            { DefinitionsContext() },
            ::comparePropertyDefinitionWrapper
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            this.def,
            IsDefinitionWrapper.Model,
            { DefinitionsContext() },
            ::comparePropertyDefinitionWrapper
        )
    }
}
