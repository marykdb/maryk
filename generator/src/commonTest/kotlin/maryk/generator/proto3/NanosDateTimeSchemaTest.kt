package maryk.generator.proto3

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.generator.kotlin.GenerationContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.yaml.MarykYamlModelReader
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object DateTimeKeySchema : RootDataModel<DateTimeKeySchema>() {
    val events by map(1u, keyDefinition = DateTimeDefinition(precision = NANOS), valueDefinition = StringDefinition())
}

private object DateTimePairSchema : RootDataModel<DateTimePairSchema>() {
    val events by map(1u, keyDefinition = DateTimeDefinition(precision = NANOS), valueDefinition = DateTimeDefinition(precision = NANOS))
}

class NanosDateTimeSchemaTest {
    @Test
    fun nanosDateTimeMapKeysUseAnEntryMessage() {
        var schema = ""
        DateTimeKeySchema.generateProto3Schema(GenerationContext()) { schema = it }
        assertTrue(schema.contains("repeated EventsEntry events = 1;"), schema)
        assertTrue(schema.contains("EventsNanosDateTime key = 1;"), schema)
    }

    @Test
    fun nanosHelpersParticipateInNamespaceValidation() {
        val model = RootDataModel.Model.Serializer.readJson(MarykYamlModelReader("""
            name: DateTimeSymbolCollision
            ? 1: whenAt
            : !DateTime { precision: NANOS }
            ? 2: kind
            : !Enum
              enum:
                name: WhenAtNanosDateTime
                cases:
                  1: Option
        """.trimIndent()), DefinitionsConversionContext()).toDataObject()
        val error = assertFailsWith<IllegalArgumentException> {
            model.generateProto3Schema(GenerationContext()) {}
        }
        assertTrue(error.message.orEmpty().contains("WhenAtNanosDateTime"))
    }

    @Test
    fun equivalentNanosHelpersCanBeShared() {
        var schema = ""
        DateTimePairSchema.generateProto3Schema(GenerationContext()) { schema = it }
        assertTrue(schema.split("message EventsNanosDateTime {").size == 2, schema)
    }
}
