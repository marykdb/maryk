package maryk.generator.proto3

import maryk.MarykEnum
import maryk.test.shouldBe
import kotlin.test.Test

val generatedProto3ForMarykEnum = """
syntax = "proto3";

option java_package = "maryk";

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

        MarykEnum.generateProto3SchemaFile("maryk") {
            output += it
        }

        output shouldBe generatedProto3ForMarykEnum
    }
}
