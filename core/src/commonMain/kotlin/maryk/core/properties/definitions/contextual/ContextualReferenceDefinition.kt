package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException

/** Definition for a reference to another DataObject from a context resolved from [contextualResolver] */
class ContextualReferenceDefinition<in CX : IsPropertyContext>(
    override val required: Boolean = true,
    val contextualResolver: (context: CX?) -> IsRootDataModel
) : IsValueDefinition<Key<*>, CX>, IsContextualEncodable<Key<*>, CX> {
    override val final = true
    override val wireType = LENGTH_DELIMITED

    override fun fromString(string: String, context: CX?) =
        contextualResolver(context).key(string)

    override fun asString(value: Key<*>, context: CX?): String = value.toString()

    override fun writeJsonValue(value: Key<*>, writer: IsJsonLikeWriter, context: CX?) =
        writer.writeString(value.toString())

    override fun readJson(reader: IsJsonLikeReader, context: CX?) = reader.currentToken.let {
        when (it) {
            is Value<*> -> {
                when (val jsonValue = it.value) {
                    null -> throw ParseException("Reference cannot be null in JSON")
                    is String -> contextualResolver(context).key(jsonValue)
                    else -> throw ParseException("Reference has to be a String")
                }
            }
            else -> throw ParseException("Reference should be represented by a JSON value")
        }
    }

    override fun calculateTransportByteLength(value: Key<*>, cacher: WriteCacheWriter, context: CX?) =
        value.size

    override fun writeTransportBytes(
        value: Key<*>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) =
        value.writeBytes(writer)

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: Key<*>?
    ) =
        contextualResolver(context).key(reader)
}
