package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.definitions.wrapper.IsDataObjectProperty
import maryk.core.protobuf.ByteLengthContainer

/**
 * Reference to a property
 * @param <T> Type of reference
 * @param <D> Definition of property
 */
open class ValuePropertyReference<T: Any, out D : IsDataObjectProperty<T, *, *>, out P: IsPropertyReference<*, *>> (
        propertyDefinition: D,
        parentReference: P?
): PropertyReference<T, D, P>(propertyDefinition, parentReference) {
    open val name = this.propertyDefinition.name

    /** The name of property which is referenced */
    override val completeName: String? get() = this.parentReference?.let {
        "${it.completeName}.$name"
    } ?: name

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
