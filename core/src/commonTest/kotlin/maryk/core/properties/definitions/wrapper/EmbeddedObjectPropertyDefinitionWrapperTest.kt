package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.query.DefinitionsConversionContext
import maryk.test.models.EmbeddedMarykObject
import kotlin.test.Test

class EmbeddedObjectPropertyDefinitionWrapperTest {
    private val def = EmbeddedObjectPropertyDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = EmbeddedObjectDefinition(
            dataModel = { EmbeddedMarykObject }
        ),
        getter = { _: Any -> null }
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            this.def,
            IsPropertyDefinitionWrapper.Model,
            { DefinitionsConversionContext() },
            ::comparePropertyDefinitionWrapper
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            this.def,
            IsPropertyDefinitionWrapper.Model,
            { DefinitionsConversionContext() },
            ::comparePropertyDefinitionWrapper
        )
    }
}
