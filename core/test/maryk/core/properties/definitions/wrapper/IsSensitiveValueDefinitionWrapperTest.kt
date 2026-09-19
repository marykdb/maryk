package maryk.core.properties.definitions.wrapper

import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse

class IsSensitiveValueDefinitionWrapperTest {
    @Test
    fun sensitivePropertyIsIncompatibleWithLegacyPlaintextLayout() {
        val encrypted = FlexBytesDefinitionWrapper(
            index = 1u,
            name = "secret",
            definition = StringDefinition(),
            sensitive = true,
            getter = { _: Any -> null },
        )
        val legacyPlaintext = encrypted.copy(sensitive = false)

        assertSensitivityTransitionIsIncompatible(encrypted, legacyPlaintext)
    }

    @Test
    fun sensitivityTransitionIsIncompatibleForFixedBytesProperties() {
        val encrypted = FixedBytesDefinitionWrapper(
            index = 1u,
            name = "secret",
            definition = BooleanDefinition(),
            sensitive = true,
            getter = { _: Any -> null },
        )

        assertSensitivityTransitionIsIncompatible(encrypted, encrypted.copy(sensitive = false))
    }

    @Test
    fun sensitivityTransitionIsIncompatibleForReferenceProperties() {
        val plaintext = TestMarykModel.reference
        val encrypted = plaintext.copy(sensitive = true)

        assertSensitivityTransitionIsIncompatible(encrypted, plaintext)
    }

    private fun assertSensitivityTransitionIsIncompatible(
        encrypted: IsDefinitionWrapper<*, *, *, *>,
        plaintext: IsDefinitionWrapper<*, *, *, *>,
    ) {
        assertFalse(encrypted.compatibleWith(plaintext))
        assertFalse(plaintext.compatibleWith(encrypted))
    }
}
