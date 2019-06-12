package maryk.generator.proto3

import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals

val generatedProto3ForMarykEnum = """
enum MarykTypeEnum {
  UNKNOWN_MARYKTYPEENUM = 0;
  T1 = 1;
  T2 = 2;
  T3 = 3;
  T4 = 4;
  T5 = 5;
  T6 = 6;
  T7 = 7;
}
""".trimIndent()

class GenerateProto3ForEnumTest {
    @Test
    fun generateProto3SchemaForEnum() {
        var output = ""

        MarykTypeEnum.generateProto3Schema {
            output += it
        }

        assertEquals(generatedProto3ForMarykEnum, output)
    }
}
