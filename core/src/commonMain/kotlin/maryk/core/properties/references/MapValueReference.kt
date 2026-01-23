package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.pairs.ReferenceNullPair
import maryk.core.query.pairs.ReferenceValuePair
import kotlin.js.JsName

/** Reference to map value [V] below [key] of [K] contained in map referred by [parentReference] */
class MapValueReference<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    val key: K,
    val mapDefinition: IsMapDefinition<K, V, CX>,
    parentReference: CanContainMapItemReference<*, *, *>?
) : CanHaveComplexChildReference<V, IsSubDefinition<V, CX>, CanContainMapItemReference<*, *, *>, Map<K, V>>(
        mapDefinition.valueDefinition, parentReference
    ),
    CanContainMapItemReference<V, IsSubDefinition<V, CX>, Map<K, V>>,
    CanContainListItemReference<V, IsSubDefinition<V, CX>, Map<K, V>>,
    CanContainSetItemReference<V, IsSubDefinition<V, CX>, Map<K, V>>,
    IsPropertyReferenceWithParent<V, IsSubDefinition<V, CX>, CanContainMapItemReference<*, *, *>, Map<K, V>>,
    HasEmbeddedPropertyReference<V> {
    override val completeName by lazy {
        this.parentReference?.let {
            "${it.completeName}.@$key"
        } ?: "@$key"
    }

    /** Convenience infix method to create Reference [value] pairs */
    @Suppress("UNCHECKED_CAST")
    infix fun <T : Any> with(value: T) =
        ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)

    @JsName("withValueOrNull")
    infix fun <T : Any> with(value: T?) =
        @Suppress("UNCHECKED_CAST")
        if (value == null) {
            ReferenceNullPair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>)
        } else {
            ReferenceValuePair(this as IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>, value)
        }

    override fun resolveFromAny(value: Any): Any {
        @Suppress("UNCHECKED_CAST")
        val map = (value as? Map<K, V>) ?: throw UnexpectedValueException("Expected Map to get value by reference")
        return map[this.key] as Any?
            ?: throw UnexpectedValueException("Expected Map to contain key to get value by reference")
    }

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        val valueLength = mapDefinition.keyDefinition.calculateTransportByteLength(key, cacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0u, VAR_INT, writer)
        mapDefinition.keyDefinition.writeTransportBytes(key, cacheGetter, writer)
    }

    override fun calculateSelfStorageByteLength(): Int {
        val keyLength = this.mapDefinition.keyDefinition.calculateStorageByteLength(this.key)

        // Add key byte length
        return keyLength.calculateVarByteLength() +
            // add bytes for map key
            keyLength
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        // Write key length
        this.mapDefinition.keyDefinition.calculateStorageByteLength(this.key).writeVarBytes(writer)
        // Write value bytes
        this.mapDefinition.keyDefinition.writeStorageBytes(key, writer)
    }

    override fun resolve(values: Map<K, V>): V? {
        return values[key]
    }

    override fun getEmbedded(name: String, context: IsPropertyContext?): AnyPropertyReference =
        when (this.propertyDefinition) {
            is IsEmbeddedDefinition<*> -> this.propertyDefinition.resolveReferenceByName(name, this)
            is IsMultiTypeDefinition<*, *, *> -> this.propertyDefinition.resolveReferenceByName(name, this)
            else -> throw DefNotFoundException("Map value can not contain embedded name references ($name)")
        }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): AnyPropertyReference =
        when (this.propertyDefinition) {
            is IsEmbeddedDefinition<*> -> this.propertyDefinition.resolveReference(reader, this)
            is IsMultiTypeDefinition<*, *, *> -> this.propertyDefinition.resolveReference(reader, this)
            else -> throw DefNotFoundException("Map value can not contain embedded index references ($key)")
        }

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference =
        when (this.propertyDefinition) {
            is IsEmbeddedDefinition<*> -> this.propertyDefinition.resolveReferenceFromStorage(reader, this, context, isDoneReading)
            is IsMultiTypeDefinition<*, *, *> -> this.propertyDefinition.resolveReferenceFromStorage(reader, this)
            else -> throw DefNotFoundException("Map value can not contain embedded index references ($key)")
        }
}
