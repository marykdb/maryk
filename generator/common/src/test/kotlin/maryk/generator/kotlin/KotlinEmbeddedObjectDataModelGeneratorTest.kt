package maryk.generator.kotlin

import maryk.EmbeddedMarykObject
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForEmbeddedDataModel = """
package maryk

import maryk.core.models.ObjectDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

data class EmbeddedMarykObject(
    val value: String = "haha"
) {
    object Properties: ObjectPropertyDefinitions<EmbeddedMarykObject>() {
        val value = add(
            index = 0, name = "value",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            ),
            getter = EmbeddedMarykObject::value
        )
    }

    companion object: ObjectDataModel<EmbeddedMarykObject, Properties>(
        name = "EmbeddedMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<EmbeddedMarykObject, Properties>) = EmbeddedMarykObject(
            value = map(0)
        )
    }
}
""".trimIndent()

class KotlinEmbeddedObjectDataModelGeneratorTest {
    @Test
    fun generate_kotlin_for_simple_model(){
        var output = ""

        EmbeddedMarykObject.generateKotlin("maryk") {
            output += it
        }

        output shouldBe generatedKotlinForEmbeddedDataModel
    }
}
