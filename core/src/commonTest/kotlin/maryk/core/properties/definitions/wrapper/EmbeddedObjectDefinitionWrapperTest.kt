package maryk.core.properties.definitions.wrapper

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.references.EmbeddedObjectPropertyRef
import maryk.test.models.EmbeddedMarykObject
import maryk.test.shouldBe
import kotlin.test.Test

class EmbeddedObjectDefinitionWrapperTest {
    private val def = EmbeddedObjectDefinitionWrapper(
        index = 1u,
        name = "wrapper",
        definition = EmbeddedObjectDefinition(
            dataModel = { EmbeddedMarykObject }
        ),
        getter = { _: Any -> null }
    )

    @Test
    fun getReference() {
        def.ref() shouldBe EmbeddedObjectPropertyRef(def, null)
    }
}
