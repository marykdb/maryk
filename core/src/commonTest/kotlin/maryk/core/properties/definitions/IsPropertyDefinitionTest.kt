package maryk.core.properties.definitions

import maryk.lib.time.Date
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class IsPropertyDefinitionTest {
    @Test
    fun isCompatible() {
        assertTrue {
            StringDefinition(
                required = false
            ).compatibleWith(StringDefinition())
        }

        assertFalse {
            StringDefinition().compatibleWith(StringDefinition(required = false))
        }

        assertFalse {
            StringDefinition().compatibleWith(BooleanDefinition())
        }

        assertFalse {
            StringDefinition(maxValue = "a").compatibleWith(DateDefinition(maxValue = Date(2010)))
        }
    }
}
