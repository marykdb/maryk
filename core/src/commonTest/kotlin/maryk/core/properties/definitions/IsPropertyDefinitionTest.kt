package maryk.core.properties.definitions

import kotlinx.datetime.LocalDate
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
            StringDefinition(maxValue = "a").compatibleWith(DateDefinition(maxValue = LocalDate(2010, 1, 1)))
        }
    }
}
