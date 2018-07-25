package maryk.generator.kotlin

import maryk.CompleteMarykModel
import maryk.EmbeddedModel
import maryk.MarykEnum
import maryk.SimpleMarykModel
import maryk.ValueMarykObject
import maryk.core.definitions.Definitions
import maryk.test.shouldBe
import kotlin.test.Test
import kotlin.test.fail

class GenerateKotlinForDefinitionsTest {
    @Test
    fun generate_mixed_maryk_primitives() {
        val mapOfWriters = mutableMapOf(
            "MarykEnum" to Writer(),
            "ValueMarykObject" to Writer(),
            "EmbeddedModel" to Writer(),
            "CompleteMarykModel" to Writer(),
            "SimpleMarykModel" to Writer()
        )

        val setOfNames = mutableSetOf<String>()

        Definitions(
            MarykEnum,
            ValueMarykObject,
            EmbeddedModel,
            CompleteMarykModel,
            SimpleMarykModel
        ).generateKotlins("maryk") { name ->
            setOfNames.add(name)
            val writer = mapOfWriters[name]
                    ?: fail("Called for not known writer $name")
            writer::writer
        }

        setOfNames.size shouldBe 5

        mapOfWriters["MarykEnum"]!!.output shouldBe generatedKotlinForEnum
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
