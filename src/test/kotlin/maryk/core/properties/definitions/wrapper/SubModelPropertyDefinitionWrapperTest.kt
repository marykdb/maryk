package maryk.core.properties.definitions.wrapper

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.DataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.query.DataModelContext
import maryk.test.shouldBe
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
        checkProtoBufConversion(this.def, SubModelPropertyDefinitionWrapper, DataModelContext(), ::compare)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(this.def, SubModelPropertyDefinitionWrapper, DataModelContext(), ::compare)
    }

    private fun compare(converted: SubModelPropertyDefinitionWrapper<*, *, *, *, *, *>, original: SubModelPropertyDefinitionWrapper<*, *, *, *, *, *>) {
        converted.index shouldBe original.index
        converted.name shouldBe original.name
        @Suppress("UNCHECKED_CAST")
        (converted as SubModelPropertyDefinitionWrapper<*, *, DataModel<Any, PropertyDefinitions<Any>>, *, *, *>).definition shouldBe (original as SubModelPropertyDefinitionWrapper<*, *, DataModel<Any, PropertyDefinitions<Any>>, *, *, *>).definition
    }
}