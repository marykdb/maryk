package maryk.generator.kotlin

import maryk.test.models.ValueMarykObject
import kotlin.test.Test
import kotlin.test.assertEquals

val generatedKotlinForValueDataModel = """
package maryk.test.models

import maryk.core.models.ValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32
import maryk.core.values.ObjectValues
import maryk.lib.time.Date

data class ValueMarykObject(
    val int: Int = 5,
    val date: Date = Date(2000, 5, 12)
) : ValueDataObject(toBytes(int, date)) {
    object Properties : ObjectPropertyDefinitions<ValueMarykObject>() {
        val int = add(
            index = 1u, name = "int",
            definition = NumberDefinition(
                type = SInt32,
                default = 5
            ),
            getter = ValueMarykObject::int
        )
        val date = add(
            index = 2u, name = "date",
            definition = DateDefinition(
                default = Date(2000, 5, 12)
            ),
            getter = ValueMarykObject::date
        )
    }

    companion object : ValueDataModel<ValueMarykObject, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ValueMarykObject, Properties>) = ValueMarykObject(
            int = values(1u),
            date = values(2u)
        )
    }
}
""".trimIndent()

class GenerateKotlinForValueDataModelTest {
    @Test
    fun generateKotlinForSimpleModel() {
        val output = buildString {
            ValueMarykObject.generateKotlin("maryk.test.models") {
                append(it)
            }
        }

        assertEquals(generatedKotlinForValueDataModel, output)
    }
}
