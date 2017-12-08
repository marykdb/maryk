package maryk.core.properties.definitions.contextual

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.types.Key
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.WireType

/** Definition for a reference to another DataObject*/
class ContextualReferenceDefinition<in CX: IsPropertyContext>(
        val contextualResolver: (context: CX?) -> RootDataModel<*, *>.KeyDefinition
): IsValueDefinition<Key<*>, CX>, IsSerializableFlexBytesEncodable<Key<*>, CX> {
    override val indexed = false
    override val searchable = false
    override val required = true
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun fromString(string: String, context: CX?)
            = contextualResolver(context).get(string)

    override fun asString(value: Key<*>, context: CX?): String = value.toString()

    override fun writeJsonValue(value: Key<*>, writer: JsonWriter, context: CX?)
            = writer.writeString(value.toString())

    override fun readJson(reader: JsonReader, context: CX?)
            = contextualResolver(context).get(reader.lastValue)

    override fun calculateTransportByteLength(value: Key<*>, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = value.size

    override fun writeTransportBytes(value: Key<*>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?)
            = value.writeBytes(writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = contextualResolver(context).get(reader)
}