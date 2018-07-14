package maryk.generator.kotlin

import maryk.EmbeddedObject
import maryk.test.shouldBe
import kotlin.test.Test

val generatedKotlinForEmbeddedDataModel = """
package maryk

import maryk.core.models.ObjectDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

data class EmbeddedObject(
    val value: String = "haha"
) {
    object Properties: ObjectPropertyDefinitions<EmbeddedObject>() {
        val value = add(
            index = 0, name = "value",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            ),
            getter = EmbeddedObject::value
        )
    }

    companion object: ObjectDataModel<EmbeddedObject, Properties>(
        name = "EmbeddedObject",
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<EmbeddedObject, Properties>) = EmbeddedObject(
            value = map(0)
        )
    }
}
""".trimIndent()

class KotlinEmbeddedObjectDataModelGeneratorTest {
    @Test
    fun generate_kotlin_for_simple_model(){
        var output = ""

        EmbeddedObject.generateKotlin("maryk") {
            output += it
        }

        output shouldBe generatedKotlinForEmbeddedDataModel
    }
}
