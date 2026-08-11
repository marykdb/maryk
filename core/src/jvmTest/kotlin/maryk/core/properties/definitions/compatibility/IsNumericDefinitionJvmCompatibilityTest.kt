package maryk.core.properties.definitions.compatibility

import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.NumberDefinition
import kotlin.test.Test
import kotlin.test.assertTrue

internal class IsNumericDefinitionJvmCompatibilityTest {
    @Test
    fun historicComparableCreateRandomDescriptorIsAvailable() {
        val definitions = listOf(
            NumberDefinition::class.java,
            FixedBytesDefinition::class.java,
        )

        definitions.forEach { definition ->
            assertTrue(
                definition.declaredMethods.any {
                    it.name == "createRandom" &&
                        it.parameterCount == 0 &&
                        it.returnType == Comparable::class.java
                },
                "${definition.name} no longer exposes createRandom(): Comparable",
            )
        }
    }
}
