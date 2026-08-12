package maryk.generator.kotlin

import maryk.test.models.ValueMarykObject
import maryk.core.models.ValueDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32
import maryk.core.values.ObjectValues
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

private data class ValueMarykObjectWithAlternativeName(
    val renamedValue: Int = 5,
) : ValueDataObject(toBytes(renamedValue)) {
    companion object : ValueDataModel<ValueMarykObjectWithAlternativeName, Companion>(ValueMarykObjectWithAlternativeName::class) {
        val renamedValue by number(
            index = 1u,
            getter = ValueMarykObjectWithAlternativeName::renamedValue,
            type = SInt32,
            default = 5,
            alternativeNames = setOf("oldValue"),
        )

        override fun invoke(values: ObjectValues<ValueMarykObjectWithAlternativeName, Companion>) = ValueMarykObjectWithAlternativeName(
            renamedValue = values(1u),
        )
    }
}

val generatedKotlinForValueDataModel = """
package maryk.test.models

import kotlinx.datetime.LocalDate
import maryk.core.properties.ValueModel
import maryk.core.properties.definitions.date
import maryk.core.properties.definitions.number
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32
import maryk.core.values.ObjectValues

data class ValueMarykObject(
    val int: Int = 5,
    val date: LocalDate = LocalDate(2000, 5, 12)
) : ValueDataObject(toBytes(int, date)) {
    companion object : ValueDataModel<ValueMarykObject, Companion>(ValueMarykObject::class) {
        val int by number(
            index = 1u,
            getter = ValueMarykObject::int,
            type = SInt32,
            default = 5
        )
        val date by date(
            index = 2u,
            getter = ValueMarykObject::date,
            default = LocalDate(2000, 5, 12)
        )

        override fun invoke(values: ObjectValues<ValueMarykObject, Companion>) = ValueMarykObject(
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

    @Test
    fun generatesAlternativeNamesForValueModelProperties() {
        val output = buildString {
            ValueMarykObjectWithAlternativeName.generateKotlin("maryk.test.models") {
                append(it)
            }
        }

        assertContains(
            output,
            """alternativeNames = setOf("oldValue"),""",
        )
    }
}
