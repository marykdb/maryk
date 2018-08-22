package maryk.core.properties.definitions.wrapper

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.DefinitionsContext
import kotlin.test.Test

class ObjectListPropertyDefinitionWrapperTest {
    private val def = ObjectListPropertyDefinitionWrapper(
        index = 1,
        name = "wrapper",
        properties = SimpleMarykObject.Properties,
        definition = ListDefinition(
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { SimpleMarykObject }
            )
        ),
        getter = { _: Any -> listOf<Nothing>() }
    )

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, IsPropertyDefinitionWrapper.Model, { DefinitionsContext() }, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, IsPropertyDefinitionWrapper.Model, { DefinitionsContext() }, ::comparePropertyDefinitionWrapper)
    }
}
