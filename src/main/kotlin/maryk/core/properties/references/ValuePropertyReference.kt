package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Reference to a property
 * @param <T> Type of reference
 * @param <D> Definition of property
 */
open class ValuePropertyReference<T: Any, out D : IsPropertyDefinitionWrapper<T, *, *>, out P: IsPropertyReference<*, *>> (
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
    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        return this.propertyDefinition.index.calculateVarByteLength() + parentLength
    }

    /** Write transport bytes of property reference
     * @param writer: To write bytes to
     */
    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        this.propertyDefinition.index.writeVarBytes(writer)
    }
}
