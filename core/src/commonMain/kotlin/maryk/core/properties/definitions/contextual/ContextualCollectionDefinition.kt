package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/** Definition which refers to specific collection definition based on context from [contextualResolver] */
internal class ContextualCollectionDefinition<in CX : IsPropertyContext>(
    private val contextualResolver: (context: CX?) -> IsSerializablePropertyDefinition<Collection<Any>, CX>,
    override val required: Boolean = true
) : IsSerializablePropertyDefinition<Collection<Any>, CX>, IsContextualEncodable<Collection<Any>, CX> {
    override val final = true

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsPropertyDefinitionWrapper<*, *, *, *>? = null

    override fun calculateTransportByteLengthWithKey(
        index: UInt,
        value: Collection<Any>,
        cacher: WriteCacheWriter,
        context: CX?
    ) =
        contextualResolver(context).calculateTransportByteLengthWithKey(index, value, cacher, context)

    override fun writeJsonValue(value: Collection<Any>, writer: IsJsonLikeWriter, context: CX?) =
        contextualResolver(context).writeJsonValue(value, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        contextualResolver(context).readJson(reader, context)

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: Collection<Any>?
    ) = contextualResolver(context).readTransportBytes(length, reader, context, earlierValue)

    override fun writeTransportBytesWithKey(
        index: UInt,
        value: Collection<Any>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) =
        contextualResolver(context).writeTransportBytesWithKey(index, value, cacheGetter, writer, context)
}
