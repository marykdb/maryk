package maryk.core.properties.definitions.wrapper

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.ListReference
import maryk.test.models.SimpleMarykObject
import kotlin.test.Test
import kotlin.test.expect

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
        getter = { _: Any -> listOf<Any>() }
    )

    @Test
    fun getReference() {
        expect(ListReference(def, null)) {
            def.ref()
        }
    }
}
