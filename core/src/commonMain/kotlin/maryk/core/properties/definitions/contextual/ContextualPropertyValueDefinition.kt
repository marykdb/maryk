package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.protobuf.calculateKeyAndContentLength
import maryk.core.protobuf.writeKeyWithLength
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.lib.exceptions.ParseException

/** A complete contextual property value, including an explicitly empty collection. */
data class ContextualPropertyValueDefinition<CX : IsPropertyContext, CXI : IsPropertyContext, T : Any>(
    val contextualResolver: (CX?) -> IsSerializablePropertyDefinition<T, CXI>,
    val contextTransformer: (CX?) -> CXI? = {
        @Suppress("UNCHECKED_CAST")
        it as CXI?
    },
    override val required: Boolean = true,
) : IsContextualEncodable<T, CX>, IsValueDefinition<T, CX> {
    override val wireType = LENGTH_DELIMITED
    override val final = true
    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = null

    private fun IsSerializablePropertyDefinition<T, CXI>.needsEnvelope() =
        this is IsCollectionDefinition<*, *, *, *> || this is IsMapDefinition<*, *, *>

    override fun asString(value: T, context: CX?): String {
        @Suppress("UNCHECKED_CAST")
        val definition = contextualResolver(context) as? IsValueDefinition<T, CXI>
            ?: throw ParseException("A collection property cannot be encoded as a scalar string")
        return definition.asString(value, contextTransformer(context))
    }

    override fun fromString(string: String, context: CX?): T {
        @Suppress("UNCHECKED_CAST")
        val definition = contextualResolver(context) as? IsValueDefinition<T, CXI>
            ?: throw ParseException("A collection property cannot be decoded from a scalar string")
        return definition.fromString(string, contextTransformer(context))
    }

    override fun calculateTransportByteLength(value: T, cacher: WriteCacheWriter, context: CX?): Int {
        val definition = contextualResolver(context)
        return if (definition.needsEnvelope()) definition.calculateTransportByteLengthWithKey(1, value, cacher, contextTransformer(context))
        else {
            @Suppress("UNCHECKED_CAST")
            (definition as IsValueDefinition<T, CXI>).calculateTransportByteLength(value, cacher, contextTransformer(context))
        }
    }

    override fun writeTransportBytes(value: T, cacheGetter: WriteCacheReader, writer: (Byte) -> Unit, context: CX?) {
        val definition = contextualResolver(context)
        if (definition.needsEnvelope()) definition.writeTransportBytesWithKey(1, value, cacheGetter, writer, contextTransformer(context))
        else {
            @Suppress("UNCHECKED_CAST")
            (definition as IsValueDefinition<T, CXI>).writeTransportBytes(value, cacheGetter, writer, contextTransformer(context))
        }
    }

    override fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX?) =
        contextualResolver(context).writeJsonValue(value, writer, contextTransformer(context))

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        contextualResolver(context).readJson(reader, contextTransformer(context))

    override fun calculateTransportByteLengthWithKey(index: Int, value: T, cacher: WriteCacheWriter, context: CX?): Int {
        val definition = contextualResolver(context)
        val innerContext = contextTransformer(context)
        return if (definition.needsEnvelope()) {
            calculateKeyAndContentLength(LENGTH_DELIMITED, index.toUInt(), cacher) {
                definition.calculateTransportByteLengthWithKey(1, value, cacher, innerContext)
            }
        } else definition.calculateTransportByteLengthWithKey(index, value, cacher, innerContext)
    }

    override fun writeTransportBytesWithKey(index: Int, value: T, cacheGetter: WriteCacheReader, writer: (Byte) -> Unit, context: CX?) {
        val definition = contextualResolver(context)
        val innerContext = contextTransformer(context)
        if (definition.needsEnvelope()) {
            writeKeyWithLength(LENGTH_DELIMITED, index.toUInt(), writer, cacheGetter)
            definition.writeTransportBytesWithKey(1, value, cacheGetter, writer, innerContext)
        } else definition.writeTransportBytesWithKey(index, value, cacheGetter, writer, innerContext)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?, earlierValue: T?): T {
        val definition = contextualResolver(context)
        val innerContext = contextTransformer(context)
        if (!definition.needsEnvelope()) return definition.readTransportBytes(length, reader, innerContext, earlierValue)
        if (length < 0) throw ParseException("Collection replacement must be length-delimited")
        var consumed = 0
        val boundedReader = {
            if (consumed >= length) throw ParseException("Collection replacement exceeds declared length")
            consumed++
            reader()
        }
        @Suppress("UNCHECKED_CAST")
        var value: T = when (definition) {
            is IsCollectionDefinition<*, *, *, *> -> (definition as IsCollectionDefinition<Any, Collection<Any>, CXI, IsSubDefinition<Any, CXI>>).newMutableCollection(innerContext) as T
            is IsMapDefinition<*, *, *> -> mutableMapOf<Any, Any>() as T
            else -> throw ParseException("Unsupported collection replacement definition")
        }
        while (consumed < length) {
            val key = ProtoBuf.readKey(boundedReader)
            if (key.tag == 1u) {
                value = definition.readTransportBytes(ProtoBuf.getLength(key.wireType, boundedReader), boundedReader, innerContext, value)
            } else ProtoBuf.skipField(key, boundedReader)
        }
        return value
    }
}
