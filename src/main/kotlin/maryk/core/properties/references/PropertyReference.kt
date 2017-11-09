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
open class PropertyReference<T: Any, out D : IsPropertyDefinition<T>> (
        val propertyDefinition: D,
        val parentReference: PropertyReference<*, *>?
) {
    open val name = propertyDefinition.name

    /** The name of property which is referenced */
    open val completeName: String? get() = this.parentReference?.let {
        if(name != null) {
            "${it.completeName}.$name"
        } else {
            it.completeName
        }
    } ?: name

    override fun equals(other: Any?) = when {
        this === other -> true
        other == null || other !is PropertyReference<*, *> -> false
        else -> other.completeName!!.contentEquals(this.completeName!!)
    }

    override fun hashCode() = this.completeName?.hashCode() ?: 0

    /** Calculate the transport length of encoding this reference
     * @param lengthCacher to cache length with
     */
    fun calculateTransportByteLength(lengthCacher: (length: ByteLengthContainer) -> Unit): Int {
        val container = ByteLengthContainer()
        lengthCacher(container)

        container.length = this.calculateTransportByteLength()
        return container.length
    }

    /** Calculate the transport length of encoding this reference
     * For cascading use
     * @return size of this reference part
     */
    open protected fun calculateTransportByteLength(): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength() ?: 0
        return this.propertyDefinition.index.calculateVarByteLength() + parentLength
    }

    /** Write transport bytes of property reference
     * @param writer: To write bytes to
     */
    open fun writeTransportBytes(writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(writer)
        this.propertyDefinition.index.writeVarBytes(writer)
    }
}
