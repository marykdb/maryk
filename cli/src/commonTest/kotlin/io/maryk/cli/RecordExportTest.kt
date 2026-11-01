package io.maryk.cli

import maryk.core.models.key
import maryk.core.models.RootDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.Values
import maryk.core.yaml.MarykYamlReader
import kotlin.test.Test
import kotlin.test.assertEquals

private object ExportRecord : RootDataModel<ExportRecord>() {
    val text by string(1u)
}

class RecordExportTest {
    @Test
    @Suppress("UNCHECKED_CAST")
    fun allExportFormatsRoundTripValuesAndMetadata() {
        val model: IsRootDataModel = ExportRecord
        val values = ExportRecord.create { text with "a: 'quoted'\nsecond line" } as Values<IsRootDataModel>
        val key = model.key(ByteArray(model.Meta.keyByteSize) { it.toByte() })
        val metadata = ValuesWithMetaData(key, values, 10uL, 20uL, true)
        val export = buildSaveContext(model, "test-key", values, metadata)
        assertEquals("test-key", export.key)
        assertEquals(values, model.Serializer.readJson(export.dataJson))
        assertEquals(values, model.Serializer.readJson(MarykYamlReader(export.dataYaml)))
        var offset = 0
        assertEquals(values, model.Serializer.readProtoBuf(export.dataProto.size, { export.dataProto[offset++] }))
        fun context() = RequestContext(DefinitionsContext(mutableMapOf(model.Meta.name to DataModelReference(model))), dataModel = model)
        assertEquals(metadata, ValuesWithMetaData.Serializer.readJson(export.metaJson, context()).toDataObject())
        assertEquals(metadata, ValuesWithMetaData.Serializer.readJson(MarykYamlReader(export.metaYaml), context()).toDataObject())
        offset = 0
        assertEquals(metadata, ValuesWithMetaData.Serializer.readProtoBuf(export.metaProto.size, { export.metaProto[offset++] }, context()).toDataObject())
    }
}
