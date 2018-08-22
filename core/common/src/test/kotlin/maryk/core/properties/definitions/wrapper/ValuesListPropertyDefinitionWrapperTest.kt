package maryk.core.properties.definitions.wrapper

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.DefinitionsContext
import kotlin.test.Test

class ValuesListPropertyDefinitionWrapperTest {
    private val def = ValuesListPropertyDefinitionWrapper(
        index = 1,
        name = "wrapper",
        definition = ListDefinition(
            valueDefinition = EmbeddedValuesDefinition(
                dataModel = { SimpleMarykModel }
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
