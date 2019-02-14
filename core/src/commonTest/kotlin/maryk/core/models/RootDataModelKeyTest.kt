package maryk.core.models

import maryk.core.models.WrongProperties.boolean
import maryk.core.models.WrongProperties.dateTime
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.key.Multiple
import maryk.lib.time.DateTime
import maryk.test.shouldThrow
import kotlin.test.Test

internal object WrongProperties : PropertyDefinitions() {
    val boolean = add(
        1, "bool", BooleanDefinition(
            required = false,
            final = true
        )
    )
    val dateTime = add(
        2, "dateTime", DateTimeDefinition()
    )
}

class RootDataModelKeyTest {
    @Test
    fun missingRequiredTest() {
        shouldThrow<IllegalArgumentException> {
            object : RootDataModel<IsRootValuesDataModel<WrongProperties>, WrongProperties>(
                name = "MarykModel",
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
    fun missingFinalTest() {
        shouldThrow<IllegalArgumentException> {
            object : RootDataModel<IsRootValuesDataModel<WrongProperties>, WrongProperties>(
                name = "MarykModel",
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
}
