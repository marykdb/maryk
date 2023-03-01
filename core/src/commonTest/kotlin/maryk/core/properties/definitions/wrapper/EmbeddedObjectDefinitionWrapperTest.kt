package maryk.core.properties.definitions.wrapper

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.references.EmbeddedObjectPropertyRef
import maryk.test.models.EmbeddedMarykObject
import kotlin.test.Test
import kotlin.test.expect

class EmbeddedObjectDefinitionWrapperTest {
    private val def = EmbeddedObjectDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = EmbeddedObjectDefinition(
            dataModel = { EmbeddedMarykObject.Model }
        ),
        getter = { _: Any -> null }
    )

    @Test
    fun getReference() {
        expect(EmbeddedObjectPropertyRef(def, null)) {
            def.ref()
        }
    }
}
