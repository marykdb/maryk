package maryk.core.properties.definitions.contextual

import maryk.core.bytes.calculateUTF8ByteLength
import maryk.core.bytes.initString
import maryk.core.bytes.writeUTF8Bytes
import maryk.core.json.IsJsonLikeReader
import maryk.core.json.IsJsonLikeWriter
import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Definition for a reference to another DataObject resolved from context by [contextualResolver]. */
internal data class ContextualModelReferenceDefinition<in CX: IsPropertyContext>(
    val contextualResolver: (context: CX?, name: String) -> DataModel<*, *>
): IsValueDefinition<DataModel<*, *>, CX>, IsSerializableFlexBytesEncodable<DataModel<*, *>, CX> {
    override val indexed = false
    override val searchable = false
    override val required = true
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun asString(value: DataModel<*, *>, context: CX?) =
        value.name

    override fun fromString(string: String, context: CX?) =
        contextualResolver(context, string)

    override fun writeJsonValue(value: DataModel<*, *>, writer: IsJsonLikeWriter, context: CX?) =
        writer.writeString(this.asString(value, context))

    override fun readJson(reader: IsJsonLikeReader, context: CX?) = reader.lastValue?.let {
        this.fromString(it, context)
    } ?: throw ParseException("Model reference cannot be null in JSON")


    override fun calculateTransportByteLength(value: DataModel<*, *>, cacher: WriteCacheWriter, context: CX?) =
        value.name.calculateUTF8ByteLength()

    override fun writeTransportBytes(value: DataModel<*, *>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) =
        value.name.writeUTF8Bytes(writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?) =
        contextualResolver(context, initString(length, reader))
}