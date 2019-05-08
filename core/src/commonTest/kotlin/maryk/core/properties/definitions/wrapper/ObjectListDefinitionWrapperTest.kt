package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.ListReference
import maryk.test.models.SimpleMarykObject
import maryk.test.shouldBe
import kotlin.test.Test

class ObjectListDefinitionWrapperTest {
    private val def = ObjectListDefinitionWrapper(
        index = 1u,
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
    fun getReference() {
        @Suppress("UNCHECKED_CAST")
        def.ref() shouldBe ListReference(
            def as IsListDefinitionWrapper<SimpleMarykObject, Any, ListDefinition<SimpleMarykObject, IsPropertyContext>, IsPropertyContext, *>,
            null
        )
    }
}
