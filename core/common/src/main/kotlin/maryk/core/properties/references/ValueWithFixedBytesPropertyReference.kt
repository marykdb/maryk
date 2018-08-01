package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.objects.AbstractValues
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Reference to a value property containing values of type [T] which are of fixed byte length. This can be used inside
 * keys. The property is defined by Property Definition Wrapper [propertyDefinition] of type [D]
 * and referred by PropertyReference of type [P].
 */
open class ValueWithFixedBytesPropertyReference<
    T: Any,
    out D : FixedBytesPropertyDefinitionWrapper<T, *, *, *, *>,
    out P: AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
): PropertyReference<T, D, P, AbstractValues<*, *, *>>(propertyDefinition, parentReference) {
    open val name = this.propertyDefinition.name

    /** The name of property which is referenced */
    override val completeName: String get() = this.parentReference?.let {
        "${it.completeName}.$name"
    } ?: name

    /** Calculate the transport length of encoding this reference and cache length with [cacher] */
    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        return this.propertyDefinition.index.calculateVarByteLength() + parentLength
    }

    /** Write transport bytes of property reference to [writer] */
    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        this.propertyDefinition.index.writeVarBytes(writer)
    }

    override fun resolve(values: AbstractValues<*, *, *>): T? {
        @Suppress("UNCHECKED_CAST")
        return values.original(propertyDefinition.index) as T?
    }
}
