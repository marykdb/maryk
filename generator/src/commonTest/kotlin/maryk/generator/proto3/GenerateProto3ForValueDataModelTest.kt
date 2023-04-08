package maryk.generator.proto3

import maryk.generator.kotlin.GenerationContext
import maryk.test.models.MarykTypeEnum
import maryk.test.models.ValueMarykObject
import kotlin.test.Test
import kotlin.test.assertEquals

val generatedProto3ForValueDataModel = """
message ValueMarykObject {
  sint32 int = 1;
  sint32 date = 2;
}
""".trimIndent()

class GenerateProto3ForValueDataModelTest {
    @Test
    fun testDataModelConversion() {
        val output = buildString {
            ValueMarykObject.generateProto3Schema(
                GenerationContext(
                    enums = mutableListOf(MarykTypeEnum)
                )
            ) {
                append(it)
            }
        }


        assertEquals(generatedProto3ForValueDataModel, output)
    }
}
