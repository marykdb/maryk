package maryk.core.models

import maryk.core.exceptions.InvalidDefinitionException
import maryk.core.models.WrongProperties.boolean
import maryk.core.models.WrongProperties.dateTime
import maryk.core.models.WrongProperties.string
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.index.Multiple
import maryk.lib.time.DateTime
import maryk.test.shouldThrow
import kotlin.test.Test

internal object WrongProperties : PropertyDefinitions() {
    val boolean = add(
        1u, "bool", BooleanDefinition(
            required = false,
            final = true
        )
    )
    val dateTime = add(
        2u, "dateTime", DateTimeDefinition()
    )
    val string = add(
        3u, "string", StringDefinition()
    )
}

class RootDataModelKeyTest {
    @Test
    fun notAcceptNonRequiredDefinitions() {
        shouldThrow<IllegalArgumentException> {
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
        shouldThrow<IllegalArgumentException> {
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
        shouldThrow<InvalidDefinitionException> {
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
