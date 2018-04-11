package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Definition for a reference to another property from a context resolved from [contextualResolver]  */
internal data class ContextualPropertyReferenceDefinition<in CX: IsPropertyContext>(
    override val required: Boolean = true,
    val contextualResolver: (context: CX?) -> PropertyDefinitions<*>
): IsValueDefinition<IsPropertyReference<*, *>, CX>, IsSerializableFlexBytesEncodable<IsPropertyReference<*, *>, CX> {
    override val indexed = false
    override val searchable = false
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun asString(value: IsPropertyReference<*, *>, context: CX?) =
        value.completeName

    override fun fromString(string: String, context: CX?) =
        contextualResolver(context).getPropertyReferenceByName(string)

    override fun writeJsonValue(value: IsPropertyReference<*, *>, writer: IsJsonLikeWriter, context: CX?) {
        writer.writeString(value.completeName)
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?) = reader.currentToken.let {
        when(it) {
            is JsonToken.Value<*> -> {
                val jsonValue = it.value
                when (jsonValue) {
                    null -> throw ParseException("Property reference cannot be null in JSON")
                    is String -> fromString(jsonValue, context)
                    is ByteArray -> {
                        var readIndex = 0
                        contextualResolver(context).getPropertyReferenceByBytes(jsonValue.size) {
                            jsonValue[readIndex++]
                        }
                    }
                    else -> {
                        throw ParseException("Property reference was not defined as byte array or string")
                    }
                }
            }
            else -> throw ParseException("Property reference should be a value")
        }
    }

    override fun calculateTransportByteLength(value: IsPropertyReference<*, *>, cacher: WriteCacheWriter, context: CX?) =
        value.calculateTransportByteLength(cacher)

    override fun writeTransportBytes(value: IsPropertyReference<*, *>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        value.writeTransportBytes(cacheGetter, writer)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?): IsPropertyReference<*, *> =
        contextualResolver(context).getPropertyReferenceByBytes(length, reader)
}

