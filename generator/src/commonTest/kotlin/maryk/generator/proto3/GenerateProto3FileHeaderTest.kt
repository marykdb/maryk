package maryk.generator.proto3

import maryk.generator.kotlin.GenerationContext
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals

class GenerateProto3FileHeaderTest {
    @Test
    fun testFileHeaderConversion() {
        val output = buildString {
            generateProto3FileHeader("maryk") {
                append(it)
            }
            CompleteMarykModel.generateProto3Schema(
                GenerationContext(
                    enums = mutableListOf(MarykTypeEnum)
                )
            ) {
                append(it)
            }
        }

        assertEquals(
            """
            syntax = "proto3";

            option java_package = "maryk";

            ${generatedProto3ForCompleteMarykModel.prependIndent().prependIndent().prependIndent().trimStart()}
            """.trimIndent(),
            output
        )
    }

    @Test
    fun testFileHeaderWithImportsConversion() {
        val output = buildString {
            generateProto3FileHeader(
                "maryk",
                protosToImport = listOf("SimpleMarykModel", "MarykEnumEmbedded")
            ) {
                append(it)
            }
            CompleteMarykModel.generateProto3Schema(
                GenerationContext(
                    enums = mutableListOf(MarykTypeEnum)
                )
            ) {
                append(it)
            }
        }

        assertEquals(
            """
            syntax = "proto3";

            import "SimpleMarykModel.proto";
            import "MarykEnumEmbedded.proto";

            option java_package = "maryk";

            ${generatedProto3ForCompleteMarykModel.prependIndent().prependIndent().prependIndent().trimStart()}
            """.trimIndent(),
            output
        )
    }
}
