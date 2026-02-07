package io.maryk.app.data

import maryk.core.models.RootDataModel
import maryk.core.models.asValues
import maryk.core.models.key
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.pairs.with
import maryk.core.protobuf.WriteCache
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DataImportProtoScopeTest {
    @Test
    fun detectSingleProtoScope() {
        val values = ProtoScopeModel.create {
            id with 7u
            number with 42u
        }
        val record = ValuesWithMetaData(
            key = ProtoScopeModel.key(values),
            values = values,
            firstVersion = 1uL,
            lastVersion = 1uL,
            isDeleted = false,
        )
        val requestContext = buildRequestContext(ProtoScopeModel)
        val bytes = serializeValuesWithMetaDataProto(record, requestContext)

        val path = Files.createTempFile("maryk-import-single-", ".proto")
        try {
            Files.write(path, bytes)
            assertEquals(
                DataImportScope.SINGLE,
                detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO),
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun detectMultiProtoScope() {
        val values = ProtoScopeModel.create {
            id with 7u
            number with 42u
        }
        val record = ValuesWithMetaData(
            key = ProtoScopeModel.key(values),
            values = values,
            firstVersion = 1uL,
            lastVersion = 1uL,
            isDeleted = false,
        )
        val requestContext = buildRequestContext(ProtoScopeModel)
        val single = serializeValuesWithMetaDataProto(record, requestContext)
        val framed = single.size.toVarBytes() + single + single.size.toVarBytes() + single

        val path = Files.createTempFile("maryk-import-multi-", ".proto")
        try {
            Files.write(path, framed)
            assertEquals(
                DataImportScope.MULTIPLE,
                detectImportScopeFromPath(path.toString(), DataExportFormat.PROTO),
            )
        } finally {
            Files.deleteIfExists(path)
        }
    }
}

private fun serializeValuesWithMetaDataProto(
    record: ValuesWithMetaData<ProtoScopeModel>,
    requestContext: RequestContext,
): ByteArray {
    val values = ValuesWithMetaData.asValues(record, requestContext)
    val cache = WriteCache()
    val length = ValuesWithMetaData.Serializer.calculateProtoBufLength(values, cache, requestContext)
    val bytes = ByteArray(length)
    var index = 0
    ValuesWithMetaData.Serializer.writeProtoBuf(values, cache, { byte ->
        bytes[index++] = byte
    }, requestContext)
    return bytes
}

private object ProtoScopeModel : RootDataModel<ProtoScopeModel>(
    keyDefinition = {
        ProtoScopeModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val number by number(index = 2u, type = UInt32)
}
