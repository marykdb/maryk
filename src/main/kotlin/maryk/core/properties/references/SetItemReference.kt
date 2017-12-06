package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/** Reference to a Set Item by value
 * @param value           index of property reference
 * @param parentReference reference to parent
 * @param <V> value type
 */
class SetItemReference<T: Any, CX: IsPropertyContext>(
        val value: T,
        setDefinition: SetDefinition<T, CX>,
        parentReference: SetReference<T, CX>?
) : CanHaveSimpleChildReference<T, IsPropertyDefinition<T>, SetReference<T, CX>>(
        setDefinition.valueDefinition, parentReference
) {
    override val completeName: String get() = this.parentReference?.let {
        "${it.completeName}.$$value"
    } ?: "$$value"

    override fun calculateTransportByteLength(lengthCacher: (length: ByteLengthContainer) -> Unit): Int {
        val parentLength = this.parentReference!!.calculateTransportByteLength(lengthCacher)
        val valueLength = this.parentReference.propertyDefinition.property.valueDefinition.calculateTransportByteLength(value, lengthCacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(lengthCacheGetter, writer)
        ProtoBuf.writeKey(0, WireType.VAR_INT, writer)
        this.parentReference!!.propertyDefinition.property.valueDefinition.writeTransportBytes(value, lengthCacheGetter, writer)
    }
}