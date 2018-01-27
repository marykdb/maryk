package maryk.core.properties.definitions.contextual

import maryk.core.json.IsJsonLikeReader
import maryk.core.json.IsJsonLikeWriter
import maryk.core.objects.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Key
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Definition for a reference to another DataObject from a context resolved from [contextualResolver] */
internal class ContextualReferenceDefinition<in CX: IsPropertyContext>(
    val contextualResolver: (context: CX?) -> RootDataModel<*, *>.KeyDefinition
): IsValueDefinition<Key<*>, CX>, IsSerializableFlexBytesEncodable<Key<*>, CX> {
    override val indexed = false
    override val searchable = false
    override val required = true
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun fromString(string: String, context: CX?) =
        contextualResolver(context).get(string)

    override fun asString(value: Key<*>, context: CX?): String = value.toString()

    override fun writeJsonValue(value: Key<*>, writer: IsJsonLikeWriter, context: CX?) =
        writer.writeString(value.toString())

    override fun readJson(reader: IsJsonLikeReader, context: CX?) = reader.lastValue?.let {
        contextualResolver(context).get(it)
    } ?: throw ParseException("Reference cannot be null in JSON")

    override fun calculateTransportByteLength(value: Key<*>, cacher: WriteCacheWriter, context: CX?) =
        value.size

    override fun writeTransportBytes(value: Key<*>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) =
        value.writeBytes(writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?) =
        contextualResolver(context).get(reader)
}