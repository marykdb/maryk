package io.maryk.cli

import maryk.core.query.createRequestContext
import maryk.core.models.IsRootDataModel
import maryk.core.models.asValues
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WriteCache
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.yaml.YamlWriter

internal fun buildSaveContext(
    dataModel: IsRootDataModel,
    keyToken: String,
    values: Values<IsRootDataModel>,
    valuesWithMetaData: ValuesWithMetaData<IsRootDataModel>,
): SaveContext {
    @Suppress("UNCHECKED_CAST")
    val serializer = dataModel.Serializer as IsDataModelSerializer<
        Values<IsRootDataModel>,
        IsRootDataModel,
        IsPropertyContext
        >

    val dataYaml = buildString {
        val writer = YamlWriter { append(it) }
        serializer.writeJson(values, writer)
    }
    val dataJson = serializer.writeJson(values)
    val dataProto = writeDataProto(serializer, values)

    val requestContext = dataModel.createRequestContext()
    val metaValues = ValuesWithMetaData.asValues(valuesWithMetaData, requestContext)

    val metaYaml = buildString {
        val writer = YamlWriter { append(it) }
        ValuesWithMetaData.Serializer.writeJson(metaValues, writer, requestContext)
    }
    val metaJson = ValuesWithMetaData.Serializer.writeJson(metaValues, requestContext)
    val metaProto = writeMetaProto(metaValues, requestContext)

    return SaveContext(
        key = keyToken,
        dataYaml = dataYaml,
        dataJson = dataJson,
        dataProto = dataProto,
        metaYaml = metaYaml,
        metaJson = metaJson,
        metaProto = metaProto,
    )
}

private fun writeDataProto(
    serializer: IsDataModelSerializer<Values<IsRootDataModel>, IsRootDataModel, IsPropertyContext>,
    values: Values<IsRootDataModel>,
): ByteArray {
    val cache = WriteCache()
    val length = serializer.calculateProtoBufLength(values, cache, null)
    val bytes = ByteArray(length)
    var index = 0
    serializer.writeProtoBuf(values, cache, { bytes[index++] = it }, null)
    check(index == bytes.size) { "Proto length mismatch: wrote $index of ${bytes.size} bytes." }
    return bytes
}

private fun writeMetaProto(
    valuesWithMetaData: ObjectValues<ValuesWithMetaData<*>, ValuesWithMetaData.Companion>,
    context: RequestContext,
): ByteArray {
    val cache = WriteCache()
    val length = ValuesWithMetaData.Serializer.calculateProtoBufLength(valuesWithMetaData, cache, context)
    val bytes = ByteArray(length)
    var index = 0
    ValuesWithMetaData.Serializer.writeProtoBuf(valuesWithMetaData, cache, { bytes[index++] = it }, context)
    check(index == bytes.size) { "Proto length mismatch: wrote $index of ${bytes.size} bytes." }
    return bytes
}
