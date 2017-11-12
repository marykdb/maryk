package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/** Reference to a List Item by index
 * @param index           index of property reference
 * @param parentReference reference to parent
 * @param <T> value type
 */
class ListItemReference<T: Any> (
        val index: Int,
        parentReference: ListReference<T>
) : CanHaveSimpleChildReference<T, AbstractValueDefinition<T, *>, ListReference<T>>(
        parentReference.propertyDefinition.valueDefinition, parentReference
), EmbeddedPropertyReference<T> {
    override val name = parentReference.name

    override val completeName: String get() = "${this.parentReference!!.completeName}.#$index"

    override fun calculateSubTransportByteLength(lengthCacher: (length: ByteLengthContainer) -> Unit): Int {
        val parentLength = parentReference?.calculateSubTransportByteLength(lengthCacher) ?: 0
        return parentLength + 1 + index.calculateVarByteLength()
    }

    override fun writeTransportBytes(lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(lengthCacheGetter, writer)
        ProtoBuf.writeKey(0, WireType.VAR_INT, writer)
        index.writeVarBytes(writer)
    }
}