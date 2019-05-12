package maryk.generator.kotlin

import maryk.core.definitions.Definitions
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.Option
import maryk.test.models.SimpleMarykModel
import maryk.test.models.ValueMarykObject
import maryk.test.shouldBe
import kotlin.test.Test
import kotlin.test.fail

class GenerateKotlinForDefinitionsTest {
    @Test
    fun generateMixedMarykPrimitives() {
        val mapOfWriters = mutableMapOf(
            "Option" to Writer(),
            "MarykTypeEnum" to Writer(),
            "ValueMarykObject" to Writer(),
            "EmbeddedModel" to Writer(),
            "CompleteMarykModel" to Writer(),
            "SimpleMarykModel" to Writer()
        )

        val setOfNames = mutableSetOf<String>()

        Definitions(
            Option,
            MarykTypeEnum,
            ValueMarykObject,
            EmbeddedModel,
            CompleteMarykModel,
            SimpleMarykModel
        ).generateKotlin("maryk.test.models") { name ->
            setOfNames.add(name)
            val writer = mapOfWriters[name]
                ?: fail("Called for not known writer $name")
            writer::writer
        }

        setOfNames.size shouldBe 6

        mapOfWriters["MarykTypeEnum"]!!.output shouldBe generatedKotlinForEnum
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
