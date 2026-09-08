package io.maryk.app.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.enum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl

private sealed class SharedExportProtoEnum(index: UInt) : IndexedEnumImpl<SharedExportProtoEnum>(index) {
    object A : SharedExportProtoEnum(1u)

    class UnknownSharedExportProtoEnum(index: UInt, override val name: String) : SharedExportProtoEnum(index)

    companion object : IndexedEnumDefinition<SharedExportProtoEnum>(
        SharedExportProtoEnum::class,
        values = { listOf(A) },
        unknownCreator = ::UnknownSharedExportProtoEnum,
    )
}

private object FirstSharedExportProtoModel : RootDataModel<FirstSharedExportProtoModel>() {
    val kind by enum(index = 1u, enum = SharedExportProtoEnum)
}

private object SecondSharedExportProtoModel : RootDataModel<SecondSharedExportProtoModel>() {
    val kind by enum(index = 1u, enum = SharedExportProtoEnum)
}

private sealed class OtherExportProtoEnum(index: UInt) : IndexedEnumImpl<OtherExportProtoEnum>(index) {
    object A : OtherExportProtoEnum(1u)

    class UnknownOtherExportProtoEnum(index: UInt, override val name: String) : OtherExportProtoEnum(index)

    companion object : IndexedEnumDefinition<OtherExportProtoEnum>(
        OtherExportProtoEnum::class,
        values = { listOf(A) },
        unknownCreator = ::UnknownOtherExportProtoEnum,
    )
}

private object ThirdExportProtoModel : RootDataModel<ThirdExportProtoModel>() {
    val kind by enum(index = 1u, enum = OtherExportProtoEnum)
}

private object FourthExportProtoModel : RootDataModel<FourthExportProtoModel>() {
    val kind by enum(index = 1u, enum = OtherExportProtoEnum)
}

class ModelExportProtoCompilationTest {
    @Test
    fun exportAllProtoCompilesSharedEnumInBothModelOrders() {
        val models = listOf(FirstSharedExportProtoModel, SecondSharedExportProtoModel)
        assertEquals(3, serializeProtoModels(models).size)
        assertCompilesInBothOrders(models)
    }

    @Test
    fun exportAllProtoKeepsCollidingEnumCasesInTheirModelScope() {
        val models = listOf(FirstSharedExportProtoModel, SecondSharedExportProtoModel, ThirdExportProtoModel, FourthExportProtoModel)
        assertEquals(4, serializeProtoModels(models).size)
        assertCompilesInBothOrders(models)
    }

    @Test
    fun exportAllProtoDoesNotConfuseSharedAndLocalEnumsWithEqualCases() {
        val models = listOf(FirstSharedExportProtoModel, SecondSharedExportProtoModel, ThirdExportProtoModel)
        assertCompilesInBothOrders(models)
    }

    private fun assertCompilesInBothOrders(models: List<IsRootDataModel>) {
        listOf(models, models.reversed()).forEach { orderedModels ->
            val directory = Files.createTempDirectory("maryk-proto-export-")
            try {
                val outputs = serializeProtoModels(orderedModels)
                val sourceFiles = outputs.map { (fileName, content) ->
                    directory.resolve(fileName).also { Files.writeString(it, content) }
                }
                val process = ProcessBuilder(
                    System.getProperty("maryk.protoc.path"),
                    "--proto_path=$directory",
                    "--descriptor_set_out=${directory.resolve("models.pb")}",
                    *sourceFiles.map { it.toString() }.toTypedArray(),
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()

                assertEquals(0, process.waitFor(), output)
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
    }
}
