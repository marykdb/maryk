package maryk.generator.proto3

import maryk.generator.kotlin.GenerationContext
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.shouldBe
import kotlin.test.Test

class GenerateProto3FileHeaderTest {
    @Test
    fun testFileHeaderConversion() {
        var output = ""

        generateProto3FileHeader("maryk") {
            output += it
        }

        CompleteMarykModel.generateProto3Schema(
            GenerationContext(
                enums = mutableListOf(MarykTypeEnum)
            )
        ) {
            output += it
        }

        output shouldBe """
        syntax = "proto3";

        option java_package = "maryk";

        ${generatedProto3ForCompleteMarykModel.prependIndent().prependIndent().trimStart()}
        """.trimIndent()
    }

    @Test
    fun testFileHeaderWithImportsConversion() {
        var output = ""

        generateProto3FileHeader(
            "maryk",
            protosToImport = listOf("SimpleMarykModel", "MarykEnumEmbedded")
        ) {
            output += it
        }

        CompleteMarykModel.generateProto3Schema(
            GenerationContext(
                enums = mutableListOf(MarykTypeEnum)
            )
        ) {
            output += it
        }

        output shouldBe """
        syntax = "proto3";

        import "SimpleMarykModel.proto";
        import "MarykEnumEmbedded.proto";

        option java_package = "maryk";

        ${generatedProto3ForCompleteMarykModel.prependIndent().prependIndent().trimStart()}
        """.trimIndent()
    }
}
