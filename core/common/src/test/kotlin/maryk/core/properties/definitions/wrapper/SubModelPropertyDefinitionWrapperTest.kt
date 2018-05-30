package maryk.core.properties.definitions.wrapper

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.query.DataModelContext
import kotlin.test.Test

class SubModelPropertyDefinitionWrapperTest {
    private val def = SubModelPropertyDefinitionWrapper(
        index = 1,
        name = "wrapper",
        definition = SubModelDefinition(
            dataModel = { SubMarykObject }
        ),
        getter = { _: Any -> null }
    )

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, IsPropertyDefinitionWrapper.Model, { DataModelContext() }, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, IsPropertyDefinitionWrapper.Model, { DataModelContext() }, ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(this.def, IsPropertyDefinitionWrapper.Model, { DataModelContext() }, ::comparePropertyDefinitionWrapper)
    }
}
