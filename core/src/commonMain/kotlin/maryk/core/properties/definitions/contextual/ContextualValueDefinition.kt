package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/** Definition which refers to specific property value definition based on context from [contextualResolver] */
data class ContextualValueDefinition<CX : IsPropertyContext, CXI : IsPropertyContext, T : Any, out D : IsValueDefinition<T, CXI>>(
    val contextualResolver: Unit.(context: CX?) -> D,
    val contextTransformer: Unit.(context: CX?) -> CXI? = {
        @Suppress("UNCHECKED_CAST")
        it as CXI?
    },
    override val required: Boolean = true
) : IsValueDefinition<T, CX>, IsContextualEncodable<T, CX> {
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun asString(value: T, context: CX?) =
        contextualResolver(Unit, context).asString(value, contextTransformer(Unit, context))

    override fun fromString(string: String, context: CX?) =
        contextualResolver(Unit, context).fromString(string, contextTransformer(Unit, context))

    override fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX?) =
        contextualResolver(Unit, context).writeJsonValue(value, writer, contextTransformer(Unit, context))

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?, earlierValue: T?) =
        contextualResolver(Unit, context).readTransportBytes(length, reader, contextTransformer(Unit, context), null)

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        contextualResolver(Unit, context).readJson(reader, contextTransformer(Unit, context))

    override fun calculateTransportByteLengthWithKey(index: UInt, value: T, cacher: WriteCacheWriter, context: CX?) =
        contextualResolver(Unit, context).calculateTransportByteLengthWithKey(index, value, cacher, contextTransformer(Unit, context))

    override fun writeTransportBytesWithKey(
        index: UInt,
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        contextualResolver(Unit, context).writeTransportBytesWithKey(index, value, cacheGetter, writer, contextTransformer(Unit, context))
    }

    override fun writeTransportBytes(
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) =
        contextualResolver(Unit, context).writeTransportBytes(value, cacheGetter, writer, contextTransformer(Unit, context))

    override fun calculateTransportByteLength(value: T, cacher: WriteCacheWriter, context: CX?) =
        contextualResolver(Unit, context).calculateTransportByteLength(value, cacher, contextTransformer(Unit, context))
}
