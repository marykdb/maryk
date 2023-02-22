package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.fromNativeType
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt64
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException

/**
 * Definition for Number properties which are based on a context from [contextualResolver] which can be set by a property
 * which defines the number type
 */
internal class ContextualNumberDefinition<in CX : IsPropertyContext>(
    override val required: Boolean = true,
    val contextualResolver: Unit.(context: CX?) -> NumberDescriptor<Comparable<Any>>
) : IsSubDefinition<Comparable<Any>, CX>, IsContextualEncodable<Comparable<Any>, CX> {
    override val final = true

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = null

    override fun calculateTransportByteLengthWithKey(
        index: Int,
        value: Comparable<Any>,
        cacher: WriteCacheWriter,
        context: CX?
    ) =
        ProtoBuf.calculateKeyLength(index.toUInt()) + contextualResolver(Unit, context).calculateTransportByteLength(value)

    override fun writeTransportBytesWithKey(
        index: Int,
        value: Comparable<Any>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        val numType = contextualResolver(Unit, context)
        ProtoBuf.writeKey(index.toUInt(), numType.wireType, writer)
        numType.writeTransportBytes(value, writer)
    }

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: Comparable<Any>?
    ) =
        contextualResolver(Unit, context).readTransportBytes(reader)

    override fun readJson(reader: IsJsonLikeReader, context: CX?): Comparable<Any> = reader.currentToken.let {
        when (it) {
            is Value<*> -> {
                when (val jsonValue = it.value) {
                    null -> throw ParseException("Contextual number cannot be null in JSON")
                    is String -> contextualResolver(Unit, context).ofString(jsonValue)
                    else -> {
                        fromNativeType(contextualResolver(Unit, context), jsonValue)
                            ?: throw ParseException("Contextual number was not defined as a number or string")
                    }
                }
            }
            else -> throw ParseException("Contextual number has to be a value")
        }
    }

    override fun writeJsonValue(value: Comparable<Any>, writer: IsJsonLikeWriter, context: CX?) = when {
        contextualResolver(Unit, context) !in arrayOf(UInt64, SInt64, Float64, Float32) -> {
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
