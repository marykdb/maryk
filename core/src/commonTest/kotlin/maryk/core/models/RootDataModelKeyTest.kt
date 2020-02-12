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
import maryk.lib.time.DateTime
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
            object : RootDataModel<IsRootValuesDataModel<WrongProperties>, WrongProperties>(
                keyDefinition = boolean.ref(),
                properties = WrongProperties
            ) {
                operator fun invoke(boolean: Boolean) = this.values {
                    mapNonNulls(this.boolean with boolean)
                }
            }
        }
    }

    @Test
    fun notAcceptNonFinalDefinitions() {
        assertFailsWith<IllegalArgumentException> {
            object : RootDataModel<IsRootValuesDataModel<WrongProperties>, WrongProperties>(
                keyDefinition = Multiple(
                    dateTime.ref()
                ),
                properties = WrongProperties
            ) {
                operator fun invoke(dateTime: DateTime) = this.values {
                    mapNonNulls(this.dateTime with dateTime)
                }
            }
        }
    }

    @Test
    fun notAcceptFlexByteDefinitions() {
        assertFailsWith<InvalidDefinitionException> {
            object : RootDataModel<IsRootValuesDataModel<WrongProperties>, WrongProperties>(
                keyDefinition = Multiple(
                    string.ref()
                ),
                properties = WrongProperties
            ) {
                operator fun invoke(string: String) = this.values {
                    mapNonNulls(this.string with string)
                }
            }
        }
    }
}
