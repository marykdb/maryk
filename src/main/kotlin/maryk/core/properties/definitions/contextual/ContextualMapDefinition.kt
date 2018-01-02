package maryk.core.properties.definitions.contextual

import maryk.core.json.IsJsonLikeReader
import maryk.core.json.IsJsonLikeWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableMap
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Definition which refers to specific map property value definition based on context */
class ContextualMapDefinition<K: Any, V: Any, in CX: IsPropertyContext>(
        private val contextualResolver: (context: CX?) -> IsByteTransportableMap<K, V, CX>,
        override val required: Boolean = true
) : IsByteTransportableMap<K, V, CX>, IsSerializableFlexBytesEncodable<Map<K, V>, CX> {
    override val indexed = false
    override val searchable = false
    override val final = true

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun writeJsonValue(value: Map<K, V>, writer: IsJsonLikeWriter, context: CX?)
            = contextualResolver(context).writeJsonValue(value, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: CX?)
            = contextualResolver(context).readJson(reader, context)

    override fun calculateTransportByteLengthWithKey(index: Int, value: Map<K, V>, cacher: WriteCacheWriter, context: CX?)
            = contextualResolver(context).calculateTransportByteLengthWithKey(index, value, cacher, context)

    override fun readMapTransportBytes(reader: () -> Byte, context: CX?)
            = contextualResolver(context).readMapTransportBytes(reader, context)

    override fun writeTransportBytesWithKey(index: Int, value: Map<K, V>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?)
            = contextualResolver(context).writeTransportBytesWithKey(index, value, cacheGetter, writer, context)
}