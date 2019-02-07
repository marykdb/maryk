package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/**
 * Definition wrapper to transform the context with [contextTransformer] for collection Definition of [T] defined by [definition]
 */
internal data class ContextCollectionTransformerDefinition<T: Any, C: Collection<T>, in CX: IsPropertyContext, CXI: IsPropertyContext>(
    val definition: IsByteTransportableCollection<T, C, CXI>,
    private val contextTransformer: (CX?) -> CXI?
) : IsByteTransportableCollection<T, C, CX> {
    override val required = definition.required
    override val final = definition.final

    override fun readCollectionTransportBytes(length: Int, reader: () -> Byte, context: CX?) =
        this.definition.readCollectionTransportBytes(length, reader, contextTransformer(context))

    override fun readPackedCollectionTransportBytes(length: Int, reader: () -> Byte, context: CX?) =
        this.definition.readPackedCollectionTransportBytes(length, reader, contextTransformer(context))

    override fun newMutableCollection(context: CX?) =
        this.definition.newMutableCollection(contextTransformer(context))

    override fun isPacked(context: CX?, encodedWireType: WireType) =
        this.definition.isPacked(contextTransformer(context), encodedWireType)

    override fun writeJsonValue(value: C, writer: IsJsonLikeWriter, context: CX?) {
        this.definition.writeJsonValue(value, writer, contextTransformer(context))
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        this.definition.readJson(reader, contextTransformer(context))

    override fun calculateTransportByteLengthWithKey(
        index: Int,
        value: C,
        cacher: WriteCacheWriter,
        context: CX?
    ) = this.definition.calculateTransportByteLengthWithKey(index, value, cacher, contextTransformer(context))

    override fun writeTransportBytesWithKey(
        index: Int,
        value: C,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        this.definition.writeTransportBytesWithKey(index, value, cacheGetter, writer, contextTransformer(context))
    }

    override fun getEmbeddedByName(name: String) = this.definition.getEmbeddedByName(name)

    override fun getEmbeddedByIndex(index: Int) = this.definition.getEmbeddedByIndex(index)
}
