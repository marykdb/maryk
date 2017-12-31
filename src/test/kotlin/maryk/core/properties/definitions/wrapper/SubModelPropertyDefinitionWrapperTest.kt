package maryk.core.properties.definitions.wrapper

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
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
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(this.def, IsPropertyDefinitionWrapper.Model, DataModelContext(), ::comparePropertyDefinitionWrapper)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, IsPropertyDefinitionWrapper.Model, DataModelContext(), ::comparePropertyDefinitionWrapper)
    }
}