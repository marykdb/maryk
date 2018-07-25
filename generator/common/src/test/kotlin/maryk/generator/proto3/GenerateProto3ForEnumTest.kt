package maryk.generator.proto3

import maryk.MarykEnum
import maryk.test.shouldBe
import kotlin.test.Test

val generatedProto3ForMarykEnum = """
enum MarykEnum {
  UNKNOWN = 0;
  O1 = 1;
  O2 = 2;
  O3 = 3;
}
""".trimIndent()

class GenerateProto3ForEnumTest {
    @Test
    fun generateProto3SchemaForEnum(){
        var output = ""

        MarykEnum.generateProto3Schema {
            output += it
        }

        output shouldBe generatedProto3ForMarykEnum
    }
}
