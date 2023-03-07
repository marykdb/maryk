package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.query.DefinitionsContext
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test

class MultiTypePropertyDefinitionWrapperTest {
    @Suppress("UNCHECKED_CAST")
    private val def = MultiTypeDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = MultiTypeDefinition(
            typeEnum = MarykTypeEnum
        ),
        getter = { _: Any -> null }
    ) as IsDefinitionWrapper<out Any, out Any, IsPropertyContext, Any>

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            this.def,
            IsDefinitionWrapper.Model.Model,
            { DefinitionsContext() },
            ::comparePropertyDefinitionWrapper
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            this.def,
            IsDefinitionWrapper.Model.Model,
            { DefinitionsContext() },
            ::comparePropertyDefinitionWrapper
        )
    }
}
