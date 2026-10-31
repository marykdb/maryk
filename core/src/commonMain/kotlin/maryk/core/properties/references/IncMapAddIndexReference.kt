package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Reference to a list index for add to Incremental map with key of [K] and value [V] contained in map referred by [parentReference] */
class IncMapAddIndexReference<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    val index: Int,
    mapDefinition: IsMapDefinition<K, V, CX>,
    parentReference: CanContainMapItemReference<*, *, *>?
) : CanHaveComplexChildReference<V, IsPropertyDefinition<V>, CanContainMapItemReference<*, *, *>, Map<K, V>>(
        mapDefinition.valueDefinition, parentReference
    ),
    CanContainMapItemReference<V, IsPropertyDefinition<V>, Map<K, V>>,
    CanContainListItemReference<V, IsPropertyDefinition<V>, Map<K, V>>,
    CanContainSetItemReference<V, IsPropertyDefinition<V>, Map<K, V>>,
    HasEmbeddedPropertyReference<V>,
    IsPropertyReferenceWithParent<V, IsPropertyDefinition<V>, CanContainMapItemReference<*, *, *>, Map<K, V>> {
    init {
        require(index >= 0) { "Map addition index cannot be negative" }
    }

    override val completeName by lazy {
        this.parentReference?.let {
            "${it.completeName}.^$index"
        } ?: "^$index"
    }

    override fun getEmbedded(name: String, context: IsPropertyContext?): AnyPropertyReference =
        when (propertyDefinition) {
            is IsEmbeddedDefinition<*> -> propertyDefinition.resolveReferenceByName(name, this)
            is IsMultiTypeDefinition<*, *, *> -> propertyDefinition.resolveReferenceByName(name, this)
            else -> readCollectionReference(name)
                ?: throw DefNotFoundException("Map addition cannot contain embedded name reference ($name)")
        }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): AnyPropertyReference =
        when (propertyDefinition) {
            is IsEmbeddedDefinition<*> -> propertyDefinition.resolveReference(reader, this)
            is IsMultiTypeDefinition<*, *, *> -> propertyDefinition.resolveReference(reader, this)
            else -> readCollectionReference(reader, context)
                ?: throw DefNotFoundException("Map addition cannot contain embedded transport references")
        }

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean,
    ): AnyPropertyReference = throw RequestException("Map addition references do not have storage references")

    override fun resolveFromAny(value: Any): Any {
        throw RequestException("Cannot be resolved on the map itself. Should be on the incrementing map change addition list")
    }

    override fun resolve(values: Map<K, V>): V? {
        throw RequestException("Cannot be resolved on the map itself. Should be on the incrementing map change addition list")
    }

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        val valueLength = index.calculateVarByteLength()
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(3u, VAR_INT, writer)
        index.writeVarBytes(writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        throw NotImplementedError("Inc Map Add Index reference is not supported to convert to storage bytes.")
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        throw NotImplementedError("Inc Map Add Index reference is not supported to convert to storage bytes.")
    }
}
