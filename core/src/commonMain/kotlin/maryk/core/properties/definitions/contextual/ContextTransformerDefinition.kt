package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/**
 * Definition wrapper to transform the context with [contextTransformer] for valueDefinition of [T] defined by [definition]
 */
data class ContextTransformerDefinition<T : Any, in CX : IsPropertyContext, CXI : IsPropertyContext>(
    val definition: IsValueDefinition<T, CXI>,
    private val contextTransformer: (CX?) -> CXI?
) : IsValueDefinition<T, CX>, IsContextualEncodable<T, CX> {
    override val wireType = definition.wireType
    override val required = definition.required
    override val final = definition.final

    override fun fromString(string: String, context: CX?) =
        this.definition.fromString(string, contextTransformer(context))

    override fun asString(value: T, context: CX?) =
        this.definition.asString(value, contextTransformer(context))

    override fun calculateTransportByteLengthWithKey(index: UInt, value: T, cacher: WriteCacheWriter, context: CX?) =
        this.definition.calculateTransportByteLengthWithKey(index, value, cacher, contextTransformer(context))

    override fun calculateTransportByteLength(value: T, cacher: WriteCacheWriter, context: CX?) =
        this.definition.calculateTransportByteLength(value, cacher, contextTransformer(context))

    override fun writeTransportBytes(
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) =
        this.definition.writeTransportBytes(value, cacheGetter, writer, contextTransformer(context))

    override fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX?) =
        this.definition.writeJsonValue(value, writer, contextTransformer(context))

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        this.definition.readJson(reader, contextTransformer(context))

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?) =
        this.definition.readTransportBytes(length, reader, contextTransformer(context))
}
