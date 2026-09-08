package io.maryk.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.enum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl

private sealed class ExportSharedEnum(index: UInt) : IndexedEnumImpl<ExportSharedEnum>(index) {
    object A : ExportSharedEnum(1u)

    class UnknownExportSharedEnum(index: UInt, override val name: String) : ExportSharedEnum(index)

    companion object : IndexedEnumDefinition<ExportSharedEnum>(
        ExportSharedEnum::class,
        values = { listOf(A) },
        unknownCreator = ::UnknownExportSharedEnum,
    )
}

private object FirstExportSharedEnumModel : RootDataModel<FirstExportSharedEnumModel>() {
    val kind by enum(index = 1u, enum = ExportSharedEnum)
}

private object SecondExportSharedEnumModel : RootDataModel<SecondExportSharedEnumModel>() {
    val kind by enum(index = 1u, enum = ExportSharedEnum)
}

class ModelExportKotlinTest {
    @Test
    fun exportAllWritesSharedInlineEnumOnce() {
        val models = listOf(FirstExportSharedEnumModel, SecondExportSharedEnumModel)
        val allModels = models.associateBy { it.Meta.name }

        val generated = serializeModels(models, ModelExportFormat.KOTLIN, allModels)
            .joinToString { it.second }

        assertEquals(1, Regex("sealed class ExportSharedEnum").findAll(generated).count())
    }
}
