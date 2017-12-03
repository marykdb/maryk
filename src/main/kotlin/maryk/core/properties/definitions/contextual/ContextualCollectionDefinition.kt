package maryk.core.properties.definitions.contextual

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractPropertyDefinition
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.WireType

/** Definition which refers to specific property value definition based on context */
class ContextualCollectionDefinition<in CX: IsPropertyContext>(
        name: String? = null,
        index: Int = -1,
        private val contextualResolver: (context: CX?) -> IsByteTransportableCollection<Any, Collection<Any>, CX>
): AbstractPropertyDefinition<Collection<Any>>(
        name, index,
        indexed = false,
        searchable = false,
        required = true,
        final = true
), IsByteTransportableCollection<Any, Collection<Any>, CX>, IsSerializableFlexBytesEncodable<Collection<Any>, CX> {
    override fun isPacked(context: CX?, encodedWireType: WireType)
            = contextualResolver(context).isPacked(context, encodedWireType)

    override fun readCollectionTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = contextualResolver(context).readCollectionTransportBytes(length, reader, context)

    override fun readPackedCollectionTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = contextualResolver(context).readPackedCollectionTransportBytes(length, reader, context)

    override fun getEmbeddedByName(name: String): IsPropertyDefinition<out Any>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinition<out Any>? = null

    override fun newMutableCollection(context: CX?)
            = contextualResolver(context).newMutableCollection(context)

    override fun calculateTransportByteLengthWithKey(value: Collection<Any>, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = contextualResolver(context).calculateTransportByteLengthWithKey(value, lengthCacher, context)

    override fun writeJsonValue(value: Collection<Any>, writer: JsonWriter, context: CX?)
            = contextualResolver(context).writeJsonValue(value, writer, context)

    override fun readJson(reader: JsonReader, context: CX?)
            = contextualResolver(context).readJson(reader, context)

    override fun writeTransportBytesWithIndexKey(index: Int, value: Collection<Any>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?)
            = contextualResolver(context).writeTransportBytesWithIndexKey(index, value, lengthCacheGetter, writer, context)
}