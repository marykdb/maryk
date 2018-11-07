package maryk.generator.proto3

import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykEnum
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
            "ValueMarykObject" to Writer(),
            "CompleteMarykModel" to Writer()
        )

        val setOfNames = mutableSetOf<String>()

        Definitions(
            MarykEnum,
            ValueMarykObject,
            CompleteMarykModel
        ).generateProto3 { name ->
            setOfNames.add(name)
            val writer = mapOfWriters[name]
                    ?: fail("Called for not known writer $name")
            writer::writer
        }

        setOfNames.size shouldBe 3

        mapOfWriters["MarykEnum"]!!.output shouldBe generatedProto3ForMarykEnum
        mapOfWriters["ValueMarykObject"]!!.output shouldBe generatedProto3ForValueDataModel
        mapOfWriters["CompleteMarykModel"]!!.output shouldBe generatedProto3ForCompleteMarykModel
    }
}

private class Writer(
    var output: String = ""
) {
    fun writer(input: String) {
        this.output += input
    }
}
