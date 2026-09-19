package maryk.core.properties.definitions.compatibility

import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.IsNumericDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.numeric.UInt32
import kotlin.test.Test
import kotlin.test.assertIs

@Suppress("DEPRECATION")
internal class IsNumericDefinitionCompatibilityTest {
    @Test
    fun historicNumericDefinitionContractIsImplemented() {
        val numberDefinition: IsNumericDefinition<UInt> = NumberDefinition(type = UInt32)
        val fixedBytesDefinition: IsNumericDefinition<Bytes> = FixedBytesDefinition(byteSize = 2)

        assertIs<UInt>(numberDefinition.createRandom())
        assertIs<Bytes>(fixedBytesDefinition.createRandom())
    }
}
