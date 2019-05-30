package maryk.generator.proto3

import maryk.core.definitions.Definitions
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.ValueMarykObject
import kotlin.test.Test
import kotlin.test.expect
import kotlin.test.fail

class MixedKotlinGenerationTest {
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
            CompleteMarykModel
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

private class Writer(
    var output: String = ""
) {
    fun writer(input: String) {
        this.output += input
    }
}
