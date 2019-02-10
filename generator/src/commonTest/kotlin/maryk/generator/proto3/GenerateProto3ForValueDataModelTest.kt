package maryk.generator.proto3

import maryk.generator.kotlin.GenerationContext
import maryk.test.models.MarykEnum
import maryk.test.models.ValueMarykObject
import maryk.test.shouldBe
import kotlin.test.Test

val generatedProto3ForValueDataModel = """
message ValueMarykObject {
  sint32 int = 1;
  sint32 date = 2;
}
""".trimIndent()

class GenerateProto3ForValueDataModelTest {
    @Test
    fun testDataModelConversion() {
        var output = ""

        ValueMarykObject.generateProto3Schema(
            GenerationContext(
                enums = mutableListOf(MarykEnum)
            )
        ) {
            output += it
        }

        output shouldBe generatedProto3ForValueDataModel
    }
}
