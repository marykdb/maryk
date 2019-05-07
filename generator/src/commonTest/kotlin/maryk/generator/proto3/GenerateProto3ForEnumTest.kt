package maryk.generator.proto3

import maryk.test.models.MarykTypeEnum
import maryk.test.shouldBe
import kotlin.test.Test

val generatedProto3ForMarykEnum = """
enum MarykTypeEnum {
  UNKNOWN = 0;
  O1 = 1;
  O2 = 2;
  O3 = 3;
  O4 = 4;
}
""".trimIndent()

class GenerateProto3ForEnumTest {
    @Test
    fun generateProto3SchemaForEnum() {
        var output = ""

        MarykTypeEnum.generateProto3Schema {
            output += it
        }

        output shouldBe generatedProto3ForMarykEnum
    }
}
