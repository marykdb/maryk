package maryk.core.properties.definitions.contextual

import maryk.core.bytes.calculateUTF8ByteLength
import maryk.core.bytes.initString
import maryk.core.bytes.writeUTF8Bytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Definition for a reference to another DataObject. */
data class ContextualModelReferenceDefinition<in CX: IsPropertyContext>(
        val contextualResolver: (context: CX?, name: String) -> RootDataModel<*, *>
): IsValueDefinition<RootDataModel<*, *>, CX>, IsSerializableFlexBytesEncodable<RootDataModel<*, *>, CX> {
    override val indexed = false
    override val searchable = false
    override val required = true
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun asString(value: RootDataModel<*, *>, context: CX?)
            = value.name

    override fun fromString(string: String, context: CX?)
            = contextualResolver(context, string)

    override fun writeJsonValue(value: RootDataModel<*, *>, writer: JsonWriter, context: CX?)
            = writer.writeString(this.asString(value, context))

    override fun readJson(reader: JsonReader, context: CX?)
            = this.fromString(reader.lastValue, context)

    override fun calculateTransportByteLength(value: RootDataModel<*, *>, cacher: WriteCacheWriter, context: CX?)
            = value.name.calculateUTF8ByteLength()

    override fun writeTransportBytes(value: RootDataModel<*, *>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?)
            = value.name.writeUTF8Bytes(writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = contextualResolver(context, initString(length, reader))
}