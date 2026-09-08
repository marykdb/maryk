package maryk.core.models

import maryk.core.models.definitions.DataModelDefinition
import maryk.core.properties.references.dsl.ref
import kotlin.test.Test
import kotlin.test.assertSame

class InheritedReferenceCompatibilityTest {
    @Test
    fun definitionModelKeepsConcreteExtensionReceiver() {
        assertSame<Any>(
            DataModelDefinition.Model.reservedIndices.ref(),
            DataModelDefinition.Model.ref { reservedIndices }
        )
    }
}
