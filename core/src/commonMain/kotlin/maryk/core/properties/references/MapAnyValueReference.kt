package maryk.core.properties.references

import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.processors.datastore.matchers.FuzzyDynamicLengthMatch
import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.processors.datastore.matchers.IsFuzzyMatcher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.pairs.ReferenceValuePair

/** Reference to map value [V] below any key of [K] contained in map referred by [parentReference] */
class MapAnyValueReference<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    val mapDefinition: IsMapDefinition<K, V, CX>,
    parentReference: MapReference<K, V, CX>?
) : IsFuzzyReference,
    IsPropertyReferenceWithIndirectStorageParent<V, IsSubDefinition<V, CX>, MapReference<K, V, CX>, Map<K, V>>,
    CanHaveComplexChildReference<V, IsSubDefinition<V, CX>, MapReference<K, V, CX>, Map<K, V>>(
        mapDefinition.valueDefinition, parentReference
    ) {
    override val completeName
        get() = this.parentReference?.let {
            "${it.completeName}.*"
        } ?: "*"

    /** Convenience infix method to create Reference [value] pairs */
    @Suppress("UNCHECKED_CAST")
    infix fun <T : Any> with(value: T) =
        ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)

    override fun resolveFromAny(value: Any): Any {
        throw RequestException("Cannot get a specific value with any value reference")
    }

    override fun fuzzyMatcher(): IsFuzzyMatcher {
        val keyDefinition = mapDefinition.keyDefinition
        return if (keyDefinition is IsFixedBytesEncodable<*>) {
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
        ProtoBuf.writeKey(2u, WireType.VAR_INT, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        return 1 + // The type byte
            // The map index
            (this.parentReference?.propertyDefinition?.index?.calculateVarByteLength() ?: 0) +
            1
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        writer(CompleteReferenceType.MAP_ANY_VALUE.value)
        this.parentReference?.propertyDefinition?.index?.writeVarBytes(writer)
        writer(0)
    }

    override fun resolve(values: Map<K, V>): V? {
        throw RequestException("Cannot get a specific value with any value reference")
    }
}
