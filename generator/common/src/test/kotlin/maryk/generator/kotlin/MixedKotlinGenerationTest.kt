package maryk.generator.kotlin

import maryk.CompleteMarykModel
import maryk.CompleteMarykObject
import maryk.EmbeddedModel
import maryk.EmbeddedObject
import maryk.MarykEnum
import maryk.SimpleMarykModel
import maryk.SimpleMarykObject
import maryk.ValueMarykObject
import maryk.core.definitions.Definitions
import maryk.test.shouldBe
import kotlin.test.Test
import kotlin.test.fail

class MixedKotlinGenerationTest {
    @Test
    fun generate_mixed_maryk_primitives() {
        val mapOfWriters = mutableMapOf(
            "MarykEnum" to Writer(),
            "SimpleMarykObject" to Writer(),
            "EmbeddedObject" to Writer(),
            "CompleteMarykObject" to Writer(),
            "ValueMarykObject" to Writer(),
            "EmbeddedModel" to Writer(),
            "CompleteMarykModel" to Writer(),
            "SimpleMarykModel" to Writer()
        )

        val setOfNames = mutableSetOf<String>()

        Definitions(
            MarykEnum,
            SimpleMarykObject,
            EmbeddedObject,
            CompleteMarykObject,
            ValueMarykObject,
            EmbeddedModel,
            CompleteMarykModel,
            SimpleMarykModel
        ).generateKotlin("maryk") { name ->
            setOfNames.add(name)
            val writer = mapOfWriters[name]
                    ?: fail("Called for not known writer $name")
            writer::writer
        }

        setOfNames.size shouldBe 8

        mapOfWriters["MarykEnum"]!!.output shouldBe generatedKotlinForEnum
        mapOfWriters["SimpleMarykObject"]!!.output shouldBe generatedKotlinForSimpleObjectDataModel
        mapOfWriters["EmbeddedObject"]!!.output shouldBe generatedKotlinForEmbeddedObjectDataModel
        mapOfWriters["CompleteMarykObject"]!!.output shouldBe generatedKotlinForCompleteObjectDataModel
        mapOfWriters["ValueMarykObject"]!!.output shouldBe generatedKotlinForValueDataModel

        mapOfWriters["EmbeddedModel"]!!.output shouldBe generatedKotlinForEmbeddedDataModel
        mapOfWriters["CompleteMarykModel"]!!.output shouldBe generatedKotlinForCompleteDataModel
        mapOfWriters["SimpleMarykModel"]!!.output shouldBe generatedKotlinForSimpleDataModel
    }
}

private class Writer(
    var output: String = ""
) {
    fun writer(input: String) {
        this.output += input
    }
}
