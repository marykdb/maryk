package maryk.core.properties.definitions.contextual

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.numeric.UInt64
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.SInt64
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Definition for Number properties which are based on a context which can be set by a property which defines
 * the number type
 */
class ContextualNumberDefinition<in CX: IsPropertyContext>(
        override val required: Boolean = true,
        val contextualResolver: (context: CX?) -> NumberDescriptor<Comparable<Any>>
): IsSubDefinition<Comparable<Any>, CX>, IsSerializableFlexBytesEncodable<Comparable<Any>, CX> {
    override val indexed = false
    override val searchable = false
    override val final = true

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *>? = null
    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun calculateTransportByteLengthWithKey(index: Int, value: Comparable<Any>, cacher: WriteCacheWriter, context: CX?)
            = ProtoBuf.calculateKeyLength(index) + contextualResolver(context).calculateTransportByteLength(value)

    override fun writeTransportBytesWithKey(index: Int, value: Comparable<Any>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        val numType = contextualResolver(context)
        ProtoBuf.writeKey(index, numType.wireType, writer)
        numType.writeTransportBytes(value, writer)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = contextualResolver(context).readTransportBytes(reader)

    override fun readJson(reader: JsonReader, context: CX?)= try {
        contextualResolver(context).ofString(reader.lastValue)
    } catch (e: Throwable) { throw ParseException(reader.lastValue, e) }

    override fun writeJsonValue(value: Comparable<Any>, writer: JsonWriter, context: CX?) = when {
        contextualResolver(context) !in arrayOf(UInt64, SInt64, Float64, Float32) -> {
            writer.writeValue(
                    value.toString()
            )
        }
        else -> {
            writer.writeString(
                    value.toString()
            )
        }
    }
}