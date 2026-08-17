package maryk.generator.kotlin

import maryk.core.definitions.Definitions
import maryk.core.models.RootDataModel
import maryk.core.query.DefinitionsConversionContext
import maryk.core.yaml.MarykYamlModelReader
import maryk.test.models.CompleteMarykModel
import maryk.test.models.EmbeddedModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.Option
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum
import maryk.test.models.ValueMarykObject
import kotlin.test.Test
import kotlin.test.expect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class GenerateKotlinForDefinitionsTest {
    @Test
    fun rejectsDuplicateOutputNamesBeforeCreatingWriters() {
        val duplicate = RootDataModel.Model.Serializer.readJson(
            MarykYamlModelReader(
                """
                name: Option
                ? 1: value
                : !String
                """.trimIndent(),
            ),
            DefinitionsConversionContext(),
        ).toDataObject()
        var writersCreated = 0

        val exception = assertFailsWith<IllegalArgumentException> {
            Definitions(Option, duplicate).generateKotlin("maryk.test.models") {
                writersCreated++
                {}
            }
        }

        assertEquals("Kotlin definitions generate duplicate output name Option", exception.message)
        assertEquals(0, writersCreated)
    }

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
            SimpleMarykModel,
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

private class Writer {
    val builder: StringBuilder = StringBuilder()
    val output get() = builder.toString()

    fun writer(input: String) {
        builder.append(input)
    }
}
