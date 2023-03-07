package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.query.DefinitionsContext
import maryk.test.models.EmbeddedMarykModel
import kotlin.test.Test

class EmbeddedValuesDefinitionWrapperTest {
    private val def = EmbeddedValuesDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = EmbeddedValuesDefinition(
            dataModel = { EmbeddedMarykModel.Model }
        ),
        getter = { null }
    )

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
