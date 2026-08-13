package maryk.generator.proto3

import maryk.core.definitions.Definitions
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.ValueMarykObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect
import kotlin.test.fail

class MixedKotlinGenerationTest {
    @Test
    fun validatesDefinitionNamesBeforeSelectingAnOutputWriter() {
        var writerSelections = 0

        val exception = assertFailsWith<IllegalArgumentException> {
            Definitions(`invalid-output-name`).generateProto3 {
                writerSelections++
                Writer()::writer
            }
        }

        assertEquals("Proto3 identifier is invalid: invalid-output-name", exception.message)
        assertEquals(0, writerSelections)
    }

    @Test
    fun generateMixedMarykPrimitives() {
        val mapOfWriters = mutableMapOf(
            "MarykTypeEnum" to Writer(),
            "ValueMarykObject" to Writer(),
            "CompleteMarykModel" to Writer()
        )

        val setOfNames = mutableSetOf<String>()

        Definitions(
            MarykTypeEnum,
            ValueMarykObject,
            CompleteMarykModel,
        ).generateProto3 { name ->
            setOfNames.add(name)
            val writer = mapOfWriters[name]
                ?: fail("Called for not known writer $name")
            writer::writer
        }

        expect(3) { setOfNames.size }

        expect(generatedProto3ForMarykEnum) { mapOfWriters["MarykTypeEnum"]!!.output }
        expect(generatedProto3ForValueDataModel) { mapOfWriters["ValueMarykObject"]!!.output }
        expect(generatedProto3ForCompleteMarykModel) { mapOfWriters["CompleteMarykModel"]!!.output }
    }
}

private sealed class `invalid-output-name`(index: UInt) : IndexedEnumImpl<`invalid-output-name`>(index) {
    object Value : `invalid-output-name`(1u)

    companion object : IndexedEnumDefinition<`invalid-output-name`>(
        `invalid-output-name`::class,
        values = { listOf(Value) },
    )
}

private class Writer {
    val builder: StringBuilder = StringBuilder()
    val output get() = builder.toString()

    fun writer(input: String) {
        builder.append(input)
    }
}
