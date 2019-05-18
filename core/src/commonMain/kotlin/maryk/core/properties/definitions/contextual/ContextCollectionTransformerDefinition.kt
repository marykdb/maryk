package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/**
 * Definition wrapper to transform the context with [contextTransformer] for collection Definition of [T] defined by [definition]
 */
internal data class ContextCollectionTransformerDefinition<T : Any, C : Collection<T>, in CX : IsPropertyContext, CXI : IsPropertyContext>(
    val definition: IsSerializablePropertyDefinition<C, CXI>,
    private val contextTransformer: (CX?) -> CXI?
) : IsSerializablePropertyDefinition<C, CX>, IsContextualEncodable<C, CX> {
    override val required = definition.required
    override val final = definition.final

    override fun getEmbeddedByName(name: String) = this.definition.getEmbeddedByName(name)
    override fun getEmbeddedByIndex(index: UInt) = this.definition.getEmbeddedByIndex(index)

    override fun writeJsonValue(value: C, writer: IsJsonLikeWriter, context: CX?) {
        this.definition.writeJsonValue(value, writer, contextTransformer(context))
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        this.definition.readJson(reader, contextTransformer(context))

    override fun calculateTransportByteLengthWithKey(
        index: UInt,
        value: C,
        cacher: WriteCacheWriter,
        context: CX?
    ) = this.definition.calculateTransportByteLengthWithKey(index, value, cacher, contextTransformer(context))

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: C?
    ) = this.definition.readTransportBytes(length, reader, contextTransformer(context), earlierValue)

    override fun writeTransportBytesWithKey(
        index: UInt,
        value: C,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        this.definition.writeTransportBytesWithKey(index, value, cacheGetter, writer, contextTransformer(context))
    }
}
