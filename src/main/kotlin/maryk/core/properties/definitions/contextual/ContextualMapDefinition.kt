package maryk.core.properties.definitions.contextual

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractPropertyDefinition
import maryk.core.properties.definitions.IsByteTransportableMap
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.ByteLengthContainer

/** Definition which refers to specific map property value definition based on context */
class ContextualMapDefinition<K: Any, V: Any, in CX: IsPropertyContext>(
        name: String? = null,
        index: Int = -1,
        private val contextualResolver: (context: CX?) -> IsByteTransportableMap<K, V, CX>
): AbstractPropertyDefinition<Map<K, V>>(
        name, index,
        indexed = false,
        searchable = false,
        required = true,
        final = true
), IsByteTransportableMap<K, V, CX> {
    override fun getEmbeddedByName(name: String): IsPropertyDefinition<out Any>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinition<out Any>? = null

    override fun writeJsonValue(value: Map<K, V>, writer: JsonWriter, context: CX?)
            = contextualResolver(context).writeJsonValue(value, writer, context)

    override fun readJson(reader: JsonReader, context: CX?)
            = contextualResolver(context).readJson(reader, context)

    override fun calculateTransportByteLengthWithKey(value: Map<K, V>, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = contextualResolver(context).calculateTransportByteLengthWithKey(value, lengthCacher, context)

    override fun readMapTransportBytes(reader: () -> Byte, context: CX?)
            = contextualResolver(context).readMapTransportBytes(reader, context)

    override fun writeTransportBytesWithIndexKey(index: Int, value: Map<K, V>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?)
            = contextualResolver(context).writeTransportBytesWithIndexKey(index, value, lengthCacheGetter, writer, context)
}