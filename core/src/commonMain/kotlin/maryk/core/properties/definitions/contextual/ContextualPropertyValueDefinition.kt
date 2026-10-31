package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.models.migration.readsLegacyTransportStorage
import maryk.core.models.migration.ReleasedTransportFingerprintException
import maryk.core.models.migration.writesReleasedTransportForFingerprint
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.acceptsProtoBufWireType
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
import maryk.core.properties.definitions.emptyTransportCollection
import maryk.core.properties.definitions.isTransportCollection

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
    internal var rejectAmbiguousLegacyCollectionReads = false
    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = null

    private fun rejectReleasedAppend(definition: IsSerializablePropertyDefinition<T, CXI>, context: CX?, innerContext: CXI?) {
        if (rejectAmbiguousLegacyCollectionReads && definition.isTransportCollection(innerContext) && context.writesReleasedTransportForFingerprint()) {
            throw ReleasedTransportFingerprintException("Released collection-valued incrementing-map appends do not retain element boundaries")
        }
    }

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
        val innerContext = contextTransformer(context)
        rejectReleasedAppend(definition, context, innerContext)
        return if (definition.isTransportCollection(innerContext)) definition.calculateTransportByteLengthWithKey(1, value, cacher, innerContext)
        else {
            @Suppress("UNCHECKED_CAST")
            (definition as IsValueDefinition<T, CXI>).calculateTransportByteLength(value, cacher, innerContext)
        }
    }

    override fun writeTransportBytes(value: T, cacheGetter: WriteCacheReader, writer: (Byte) -> Unit, context: CX?) {
        val definition = contextualResolver(context)
        val innerContext = contextTransformer(context)
        rejectReleasedAppend(definition, context, innerContext)
        if (definition.isTransportCollection(innerContext)) definition.writeTransportBytesWithKey(1, value, cacheGetter, writer, innerContext)
        else {
            @Suppress("UNCHECKED_CAST")
            (definition as IsValueDefinition<T, CXI>).writeTransportBytes(value, cacheGetter, writer, innerContext)
        }
    }

    override fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX?) =
        contextualResolver(context).writeJsonValue(value, writer, contextTransformer(context))

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        contextualResolver(context).readJson(reader, contextTransformer(context))

    override fun calculateTransportByteLengthWithKey(index: Int, value: T, cacher: WriteCacheWriter, context: CX?): Int {
        val definition = contextualResolver(context)
        val innerContext = contextTransformer(context)
        rejectReleasedAppend(definition, context, innerContext)
        return if (definition.isTransportCollection(innerContext)) {
            calculateKeyAndContentLength(LENGTH_DELIMITED, index.toUInt(), cacher) {
                definition.calculateTransportByteLengthWithKey(1, value, cacher, innerContext)
            }
        } else definition.calculateTransportByteLengthWithKey(index, value, cacher, innerContext)
    }

    override fun writeTransportBytesWithKey(index: Int, value: T, cacheGetter: WriteCacheReader, writer: (Byte) -> Unit, context: CX?) {
        val definition = contextualResolver(context)
        val innerContext = contextTransformer(context)
        rejectReleasedAppend(definition, context, innerContext)
        if (definition.isTransportCollection(innerContext)) {
            writeKeyWithLength(LENGTH_DELIMITED, index.toUInt(), writer, cacheGetter)
            definition.writeTransportBytesWithKey(1, value, cacheGetter, writer, innerContext)
        } else definition.writeTransportBytesWithKey(index, value, cacheGetter, writer, innerContext)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?, earlierValue: T?): T {
        val definition = contextualResolver(context)
        val innerContext = contextTransformer(context)
        if (!definition.isTransportCollection(innerContext)) return definition.readTransportBytes(length, reader, innerContext, earlierValue)
        if (rejectAmbiguousLegacyCollectionReads && context.readsLegacyTransportStorage()) {
            throw ParseException("Legacy collection-valued incrementing-map appends have ambiguous element boundaries; reconstruct the log from a known source or restore a fresh snapshot")
        }
        if (length < 0) throw ParseException("Collection replacement must be length-delimited")
        var consumed = 0
        val boundedReader = {
            if (consumed >= length) throw ParseException("Collection replacement exceeds declared length")
            consumed++
            reader()
        }
        @Suppress("UNCHECKED_CAST")
        var value = definition.emptyTransportCollection(innerContext) as? T
            ?: throw ParseException("Unsupported collection replacement definition")
        while (consumed < length) {
            val key = ProtoBuf.readKey(boundedReader)
            if (key.tag == 1u) {
                if (!definition.acceptsProtoBufWireType(key.wireType, innerContext)) {
                    throw ParseException("Collection replacement wire type does not match its definition")
                }
                val valueLength = ProtoBuf.getLength(key.wireType, boundedReader)
                if (valueLength > length - consumed) throw ParseException("Collection replacement exceeds declared length")
                value = definition.readTransportBytes(valueLength, boundedReader, innerContext, value)
            } else ProtoBuf.skipField(key, boundedReader)
        }
        return value
    }
}
