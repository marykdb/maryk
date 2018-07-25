package maryk.generator.proto3

import maryk.MarykEnum
import maryk.ValueMarykObject
import maryk.generator.kotlin.GenerationContext
import maryk.test.shouldBe
import kotlin.test.Test

val generatedProto3ForValueDataModel = """
syntax = "proto3";

option java_package = "maryk";

message ValueMarykObject {
  required sint32 int = 1;
  required sint64 date = 2;
}
""".trimIndent()

class GenerateProto3ForValueDataModelTest {
    @Test
    fun testDataModelConversion() {
        var output = ""

        ValueMarykObject.generateProto3Schema(
            "maryk",
            GenerationContext(
                enums = mutableListOf(MarykEnum)
            )
        ) {
            output += it
        }

        output shouldBe generatedProto3ForValueDataModel
    }
}
