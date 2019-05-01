package maryk.core.properties.references

import maryk.core.exceptions.RequestException
import maryk.core.processors.datastore.matchers.FuzzyDynamicLengthMatch
import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.processors.datastore.matchers.IsFuzzyMatcher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.pairs.ReferenceValuePair

/** Reference to map value [V] below any key of [K] contained in map referred by [parentReference] */
class MapAnyValueReference<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    val mapDefinition: IsMapDefinition<K, V, CX>,
    parentReference: MapReference<K, V, CX>?
) : IsFuzzyReference,
    IsPropertyReferenceWithParent<List<V>, IsListDefinition<V, CX>, MapReference<K, V, CX>, Map<K, V>>,
    CanHaveComplexChildReference<List<V>, IsListDefinition<V, CX>, MapReference<K, V, CX>, Map<K, V>>(
        ListDefinition(valueDefinition = mapDefinition.valueDefinition as IsValueDefinition<V, CX>),
        parentReference
    ) {
    override val completeName
        get() = this.parentReference?.let {
            "${it.completeName}.*"
        } ?: "*"

    /** Convenience infix method to create Reference [value] pairs */
    @Suppress("UNCHECKED_CAST")
    infix fun <T : Any> with(value: T) =
        ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)

    override fun fuzzyMatcher(): IsFuzzyMatcher {
        val keyDefinition = mapDefinition.keyDefinition
        return if (keyDefinition is IsFixedStorageBytesEncodable<*>) {
            FuzzyExactLengthMatch(keyDefinition.byteSize)
        } else {
            FuzzyDynamicLengthMatch
        }
    }

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(2u, VAR_INT, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        throw NotImplementedError("Map Any Value is not supported to convert to storage bytes. It uses fuzzy matchers instead")
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        throw NotImplementedError("Map Any Value is not supported to convert to storage bytes. It uses fuzzy matchers instead")
    }

    override fun resolve(values: Map<K, V>): List<V> = values.values.toMutableList()

    override fun resolveFromAny(value: Any): Any =
        if (value is Map<*, *>) {
            value.values.toMutableList()
        } else {
            throw RequestException("Expected a map into resolveFromAny instead of $value")
        }
}
