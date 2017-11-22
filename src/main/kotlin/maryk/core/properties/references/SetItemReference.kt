package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/** Reference to a Set Item by value
 * @param value           index of property reference
 * @param parentReference reference to parent
 * @param <V> value type
 */
class SetItemReference<T: Any, in CX: IsPropertyContext>(
        val value: T,
        parentReference: SetReference<T, CX>
) : CanHaveSimpleChildReference<T, IsPropertyDefinition<T>, SetReference<T, CX>>(
        parentReference.propertyDefinition.valueDefinition, parentReference
) {
    override val name: String? get() = this.parentReference?.name

    override val completeName: String get() = "${this.parentReference!!.completeName}.$$value"

    override fun calculateTransportByteLength(lengthCacher: (length: ByteLengthContainer) -> Unit): Int {
        val parentLength = this.parentReference!!.calculateTransportByteLength(lengthCacher)
        val valueLength = this.parentReference.propertyDefinition.valueDefinition.calculateTransportByteLength(value, lengthCacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(lengthCacheGetter, writer)
        ProtoBuf.writeKey(0, WireType.VAR_INT, writer)
        this.parentReference!!.propertyDefinition.valueDefinition.writeTransportBytes(value, lengthCacheGetter, writer)
    }
}