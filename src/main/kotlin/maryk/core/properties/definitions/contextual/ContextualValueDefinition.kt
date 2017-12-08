package maryk.core.properties.definitions.contextual

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.WireType

/** Definition which refers to specific property value definition based on context */
internal data class ContextualValueDefinition<in CX: IsPropertyContext>(
        val contextualResolver: (context: CX?) -> IsValueDefinition<Any, IsPropertyContext>,
        override val required: Boolean = true
): IsValueDefinition<Any, CX>, IsSerializableFlexBytesEncodable<Any, CX> {
    override val indexed = false
    override val searchable = false
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun asString(value: Any, context: CX?)
            = contextualResolver(context).asString(value, context)

    override fun fromString(string: String, context: CX?)
            = contextualResolver(context).fromString(string, context)

    override fun writeJsonValue(value: Any, writer: JsonWriter, context: CX?)
            = contextualResolver(context).writeJsonValue(value, writer, context)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = contextualResolver(context).readTransportBytes(length, reader)

    override fun readJson(reader: JsonReader, context: CX?)
            = contextualResolver(context).readJson(reader, context)

    override fun writeTransportBytes(value: Any, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?)
            = contextualResolver(context).writeTransportBytes(value, lengthCacheGetter, writer, context)

    override fun calculateTransportByteLength(value: Any, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = contextualResolver(context).calculateTransportByteLength(value, lengthCacher, context)
}