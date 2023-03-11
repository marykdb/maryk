package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/** Definition which refers to specific collection definition based on context from [contextualResolver] */
class ContextualCollectionDefinition<in CX : IsPropertyContext>(
    private val contextualResolver: Unit.(context: CX?) -> IsSerializablePropertyDefinition<Collection<Any>, CX>,
    override val required: Boolean = true
) : IsSerializablePropertyDefinition<Collection<Any>, CX>, IsContextualEncodable<Collection<Any>, CX> {
    override val final = true

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = null

    override fun calculateTransportByteLengthWithKey(
        index: Int,
        value: Collection<Any>,
        cacher: WriteCacheWriter,
        context: CX?
    ) =
        contextualResolver(Unit, context).calculateTransportByteLengthWithKey(index, value, cacher, context)

    override fun writeJsonValue(value: Collection<Any>, writer: IsJsonLikeWriter, context: CX?) =
        contextualResolver(Unit, context).writeJsonValue(value, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        contextualResolver(Unit, context).readJson(reader, context)

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: Collection<Any>?
    ) = contextualResolver(Unit, context).readTransportBytes(length, reader, context, earlierValue)

    override fun writeTransportBytesWithKey(
        index: Int,
        value: Collection<Any>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) =
        contextualResolver(Unit, context).writeTransportBytesWithKey(index, value, cacheGetter, writer, context)
}
