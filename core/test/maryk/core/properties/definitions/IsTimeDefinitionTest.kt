package maryk.core.properties.definitions

import maryk.core.properties.types.TimePrecision.MILLIS
import maryk.core.properties.types.TimePrecision.SECONDS
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class IsTimeDefinitionTest {
    @Test
    fun isCompatible() {
        assertTrue {
            TimeDefinition(precision = SECONDS).compatibleWith(TimeDefinition(precision = SECONDS))
        }

        assertFalse {
            TimeDefinition(precision = MILLIS).compatibleWith(TimeDefinition(precision = SECONDS))
        }
    }
}
