package maryk.core.properties.definitions.contextual

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeWriter

/**
 * Definition wrapper to inject a collection on write from context with [valueInjector] for valueDefinition of [T] defined by [definition]
 */
data class ContextInjectCollectionOnWriteDefinition<T : Any, C : Collection<T>, in CX : IsPropertyContext>(
    val definition: IsCollectionDefinition<T, C, CX, IsValueDefinition<T, CX>>,
    private val valueInjector: (CX?) -> C
) : IsByteTransportableCollection<T, C, CX> by definition, IsContextualEncodable<C, CX> {
    override val required = definition.required
    override val final = definition.final

    override fun writeJsonValue(value: C, writer: IsJsonLikeWriter, context: CX?) {
        this.definition.writeJsonValue(this.valueInjector(context), writer, context)
    }

    override fun calculateTransportByteLengthWithKey(
        index: Int,
        value: C,
        cacher: WriteCacheWriter,
        context: CX?
    ) = this.definition.calculateTransportByteLengthWithKey(index, this.valueInjector(context), cacher, context)

    override fun writeTransportBytesWithKey(
        index: Int,
        value: C,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        this.definition.writeTransportBytesWithKey(index, this.valueInjector(context), cacheGetter, writer, context)
    }
}
