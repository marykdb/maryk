package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.ByteLengthContainer

/**
 * Reference to a property
 * @param <T> Type of reference
 * @param <D> Definition of property
 */
open class PropertyReference<T: Any, out D : IsPropertyDefinition<T>, out P: IsPropertyReference<*, *>> (
        override final val propertyDefinition: D,
        val parentReference: P?
): IsPropertyReference<T, D> {

    open val name = this.propertyDefinition.name

    /** The name of property which is referenced */
    override val completeName: String? get() = this.parentReference?.let {
        if(name != null) {
            "${it.completeName}.$name"
        } else {
            it.completeName
        }
    } ?: name

    override fun toString() = this.completeName ?: "null"

    override fun equals(other: Any?) = when {
        this === other -> true
        other == null || other !is IsPropertyReference<*, *> -> false
        else -> other.completeName!!.contentEquals(this.completeName!!)
    }

    override fun hashCode() = this.completeName?.hashCode() ?: 0

    /** Calculate the transport length of encoding this reference
     * @param lengthCacher to cache length with
     */
    override fun calculateTransportByteLength(lengthCacher: (length: ByteLengthContainer) -> Unit): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(lengthCacher) ?: 0
        return this.propertyDefinition.index.calculateVarByteLength() + parentLength
    }

    /** Write transport bytes of property reference
     * @param writer: To write bytes to
     */
    override fun writeTransportBytes(lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(lengthCacheGetter, writer)
        this.propertyDefinition.index.writeVarBytes(writer)
    }
}
