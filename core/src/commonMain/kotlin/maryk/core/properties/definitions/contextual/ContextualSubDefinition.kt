package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/** Definition which refers to sub definitions based on context from [contextualResolver] */
data class ContextualSubDefinition<CX : IsPropertyContext, CXI : IsPropertyContext, T : Any, out D : IsSubDefinition<T, CXI>>(
    val contextualResolver: Unit.(context: CX?) -> D,
    val contextTransformer: Unit.(context: CX?) -> CXI? = {
        @Suppress("UNCHECKED_CAST")
        it as CXI?
    },
    override val required: Boolean = true
) : IsSubDefinition<T, CX>, IsContextualEncodable<T, CX> {
    override val final = true

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = null

    override fun calculateTransportByteLengthWithKey(
        index: Int,
        value: T,
        cacher: WriteCacheWriter,
        context: CX?
    ) =
        contextualResolver(Unit, context).calculateTransportByteLengthWithKey(index, value, cacher, contextTransformer(Unit, context))

    override fun writeTransportBytesWithKey(
        index: Int,
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        contextualResolver(Unit, context).writeTransportBytesWithKey(index, value, cacheGetter, writer, contextTransformer(Unit, context))
    }

    override fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX?) =
        contextualResolver(Unit, context).writeJsonValue(value, writer, contextTransformer(Unit, context))

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?, earlierValue: T?) =
        contextualResolver(Unit, context).readTransportBytes(length, reader, contextTransformer(Unit, context), null)

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        contextualResolver(Unit, context).readJson(reader, contextTransformer(Unit, context))
}
