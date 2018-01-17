package maryk.core.properties.definitions.contextual

import maryk.core.json.IsJsonLikeReader
import maryk.core.json.IsJsonLikeWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Definition for a reference to another property */
data class ContextualPropertyReferenceDefinition<in CX: IsPropertyContext>(
        override val required: Boolean = true,
        val contextualResolver: (context: CX?) -> PropertyDefinitions<*>
): IsValueDefinition<IsPropertyReference<*, *>, CX>, IsSerializableFlexBytesEncodable<IsPropertyReference<*, *>, CX> {
    override val indexed = false
    override val searchable = false
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun asString(value: IsPropertyReference<*, *>, context: CX?)
            = value.completeName!!

    override fun fromString(string: String, context: CX?)
            = contextualResolver(context).getPropertyReferenceByName(string)

    override fun writeJsonValue(value: IsPropertyReference<*, *>, writer: IsJsonLikeWriter, context: CX?) {
        writer.writeString(value.completeName!!)
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?)
            = fromString(reader.lastValue, context)

    override fun calculateTransportByteLength(value: IsPropertyReference<*, *>, cacher: WriteCacheWriter, context: CX?)
            = value.calculateTransportByteLength(cacher)

    override fun writeTransportBytes(value: IsPropertyReference<*, *>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        value.writeTransportBytes(cacheGetter, writer)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?): IsPropertyReference<*, *>
            = contextualResolver(context).getPropertyReferenceByBytes(length, reader)
}

