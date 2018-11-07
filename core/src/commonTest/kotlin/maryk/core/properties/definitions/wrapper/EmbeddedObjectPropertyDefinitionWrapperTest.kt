package maryk.core.properties.definitions.wrapper

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.query.DefinitionsConversionContext
import maryk.test.models.EmbeddedMarykObject
import kotlin.test.Test

class EmbeddedObjectPropertyDefinitionWrapperTest {
    private val def = EmbeddedObjectPropertyDefinitionWrapper(
        index = 1,
        name = "wrapper",
        definition = EmbeddedObjectDefinition(
            dataModel = { EmbeddedMarykObject }
        ),
        getter = { _: Any -> null }
    )

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, IsPropertyDefinitionWrapper.Model, { DefinitionsConversionContext() }, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, IsPropertyDefinitionWrapper.Model, { DefinitionsConversionContext() }, ::comparePropertyDefinitionWrapper)
    }
}
