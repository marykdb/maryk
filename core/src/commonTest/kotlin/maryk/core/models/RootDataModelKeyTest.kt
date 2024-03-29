package maryk.core.models

import maryk.core.exceptions.InvalidDefinitionException
import maryk.core.models.WrongProperties.boolean
import maryk.core.models.WrongProperties.dateTime
import maryk.core.models.WrongProperties.string
import maryk.core.models.definitions.RootDataModelDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.string
import kotlin.test.Test
import kotlin.test.assertFailsWith

internal object WrongProperties : DataModel<WrongProperties>() {
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
            RootDataModelDefinition(
                name = "WrongModel",
                keyDefinition = boolean.ref(),
            )
        }
    }

    @Test
    fun notAcceptNonFinalDefinitions() {
        assertFailsWith<IllegalArgumentException> {
            RootDataModelDefinition(
                name = "WrongModel",
                keyDefinition = Multiple(
                    dateTime.ref()
                ),
            )
        }
    }

    @Test
    fun notAcceptFlexByteDefinitions() {
        assertFailsWith<InvalidDefinitionException> {
            RootDataModelDefinition(
                name = "WrongModel",
                keyDefinition = Multiple(
                    string.ref()
                ),
            )
        }
    }
}
