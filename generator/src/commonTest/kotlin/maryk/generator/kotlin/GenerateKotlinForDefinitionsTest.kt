package maryk.generator.kotlin

import maryk.core.definitions.Definitions
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.Option
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum
import maryk.test.models.ValueMarykObject
import kotlin.test.Test
import kotlin.test.expect
import kotlin.test.fail

class GenerateKotlinForDefinitionsTest {
    @Test
    fun generateMixedMarykPrimitives() {
        val mapOfWriters = mutableMapOf(
            "Option" to Writer(),
            "SimpleMarykTypeEnum" to Writer(),
            "MarykTypeEnum" to Writer(),
            "ValueMarykObject" to Writer(),
            "EmbeddedModel" to Writer(),
            "CompleteMarykModel" to Writer(),
            "SimpleMarykModel" to Writer()
        )

        val setOfNames = mutableSetOf<String>()

        Definitions(
            Option,
            SimpleMarykTypeEnum,
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

        expect(7) { setOfNames.size }

        expect(generatedKotlinForIndexedEnum) { mapOfWriters["Option"]!!.output }
        expect(generatedKotlinForTypeEnum) { mapOfWriters["MarykTypeEnum"]!!.output }
        expect(generatedKotlinForValueDataModel) { mapOfWriters["ValueMarykObject"]!!.output }
        expect(generatedKotlinForEmbeddedDataModel) { mapOfWriters["EmbeddedModel"]!!.output }
        expect(generatedKotlinForCompleteDataModel) { mapOfWriters["CompleteMarykModel"]!!.output }
        expect(generatedKotlinForSimpleDataModel) { mapOfWriters["SimpleMarykModel"]!!.output }
    }
}

private class Writer(
    var output: String = ""
) {
    fun writer(input: String) {
        this.output += input
    }
}
