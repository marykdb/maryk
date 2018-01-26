package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Reference to a Set Item by [value] of [T] and context [CX] on set referred to [parentReference] and
 * defined by [setDefinition]
 */
class SetItemReference<T: Any, CX: IsPropertyContext> internal constructor(
    val value: T,
    setDefinition: SetDefinition<T, CX>,
    parentReference: SetReference<T, CX>?
) : CanHaveSimpleChildReference<T, IsPropertyDefinition<T>, SetReference<T, CX>>(
    setDefinition.valueDefinition, parentReference
) {
    override val completeName: String get() = this.parentReference?.let {
        "${it.completeName}.$$value"
    } ?: "$$value"

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference!!.calculateTransportByteLength(cacher)
        val valueLength = this.parentReference.propertyDefinition.definition.valueDefinition.calculateTransportByteLength(value, cacher)
        return parentLength + 1 + valueLength
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0, WireType.VAR_INT, writer)
        this.parentReference!!.propertyDefinition.definition.valueDefinition.writeTransportBytes(value, cacheGetter, writer)
    }
}