package maryk.core.properties.definitions.contextual

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.UInt64
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.SInt64
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Definition for Number properties */
class ContextualNumberDefinition(
        override val required: Boolean = true
): IsSubDefinition<Comparable<Any>, ContextualNumberDefinition.NumericContext>, IsSerializableFlexBytesEncodable<Comparable<Any>, ContextualNumberDefinition.NumericContext> {
    override val indexed = false
    override val searchable = false
    override val final = true

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *>? = null
    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun calculateTransportByteLengthWithKey(index: Int, value: Comparable<Any>, cacher: WriteCacheWriter, context: NumericContext?)
            = ProtoBuf.calculateKeyLength(index) + context!!.numberType!!.calculateTransportByteLength(value)

    override fun writeTransportBytesWithKey(index: Int, value: Comparable<Any>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: NumericContext?) {
        ProtoBuf.writeKey(index, context!!.numberType!!.wireType, writer)
        context.numberType!!.writeTransportBytes(value, writer)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: NumericContext?)
            = context!!.numberType!!.readTransportBytes(reader)

    override fun readJson(reader: JsonReader, context: NumericContext?)= try {
        context!!.numberType!!.ofString(reader.lastValue)
    } catch (e: Throwable) { throw ParseException(reader.lastValue, e) }

    override fun writeJsonValue(value: Comparable<Any>, writer: JsonWriter, context: NumericContext?) = when {
        context!!.numberType!! !in arrayOf(UInt64, SInt64, Float64, Float32) -> {
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

    class NumericContext : IsPropertyContext {
        var numberType: NumberDescriptor<Comparable<Any>>? = null
    }
}