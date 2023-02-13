package maryk.core.models

import maryk.core.exceptions.InvalidDefinitionException
import maryk.core.models.WrongProperties.boolean
import maryk.core.models.WrongProperties.dateTime
import maryk.core.models.WrongProperties.string
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.string
import kotlin.test.Test
import kotlin.test.assertFailsWith

internal object WrongProperties : PropertyDefinitions() {
    val boolean by boolean(
        index = 1u,
        required = false,
        final = true
    )
    val dateTime by dateTime(2u)
    val string by string(3u)
}

class RootDataModelKeyTest {
    @Test
    fun notAcceptNonRequiredDefinitions() {
        assertFailsWith<IllegalArgumentException> {
            RootDataModel(
                keyDefinition = boolean.ref(),
                properties = WrongProperties
            )
        }
    }

    @Test
    fun notAcceptNonFinalDefinitions() {
        assertFailsWith<IllegalArgumentException> {
            RootDataModel(
                keyDefinition = Multiple(
                    dateTime.ref()
                ),
                properties = WrongProperties
            )
        }
    }

    @Test
    fun notAcceptFlexByteDefinitions() {
        assertFailsWith<InvalidDefinitionException> {
            RootDataModel(
                keyDefinition = Multiple(
                    string.ref()
                ),
                properties = WrongProperties
            )
        }
    }
}
