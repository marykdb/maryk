package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Reference to a value property containing values of type [T]. The property is defined by Property Definition Wrapper
 * [D] and referred by PropertyReference of type [P].
 */
open class ValuePropertyReference<
        T: Any,
        TO: Any,
        out D : IsPropertyDefinitionWrapper<T, TO, *, *>,
        out P: AnyPropertyReference
> internal constructor(
        propertyDefinition: D,
        parentReference: P?
): PropertyReference<T, D, P>(propertyDefinition, parentReference) {
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

    /** Write transport bytes of property reference to [writer] anc get cache from [cacheGetter] */
    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        this.propertyDefinition.index.writeVarBytes(writer)
    }
}
