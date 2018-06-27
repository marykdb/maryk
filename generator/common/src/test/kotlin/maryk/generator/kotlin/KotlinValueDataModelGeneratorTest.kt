package maryk.generator.kotlin

import maryk.ValueMarykObject
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForValueDataModel = """
package maryk

import maryk.core.models.ValueDataModel
import maryk.core.objects.ValueMap
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32
import maryk.lib.time.Date

data class ValueMarykObject(
    val int: Int = 5,
    val date: Date = Date(2000, 5, 12)
): ValueDataObject(toBytes(int, date)) {
    object Properties: PropertyDefinitions<ValueMarykObject>() {
        val int = add(
            index = 0, name = "int",
            definition = NumberDefinition(
                type = SInt32,
                default = 5
            ),
            getter = ValueMarykObject::int
        )
        val date = add(
            index = 1, name = "date",
            definition = DateDefinition(
                default = Date(2000, 5, 12)
            ),
            getter = ValueMarykObject::date
        )
    }

    companion object: ValueDataModel<ValueMarykObject, Properties>(
        name = "ValueMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: ValueMap<ValueMarykObject>) = ValueMarykObject(
            int = map(0),
            date = map(1)
        )
    }
}
""".trimIndent()

class KotlinValueDataModelGeneratorTest {
    @Test
    fun generate_kotlin_for_simple_model(){
        var output = ""

        ValueMarykObject.generateKotlin("maryk") {
            output += it
        }

        output shouldBe generatedKotlinForValueDataModel
    }
}
