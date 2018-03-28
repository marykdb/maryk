package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Reference to a List Item on [parentReference] with [T] by [index] */
class ListItemReference<T: Any, CX: IsPropertyContext>  internal constructor(
    val index: Int,
    listDefinition: ListDefinition<T, CX>,
    parentReference: ListReference<T, CX>?
) : CanHaveSimpleChildReference<T, IsValueDefinition<T, CX>, ListReference<T, CX>>(
    listDefinition.valueDefinition, parentReference
) {
    override val completeName: String get() = this.parentReference?.let {
        "${it.completeName}.@$index"
    } ?: "@$index"

    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = parentReference?.calculateTransportByteLength(cacher) ?: 0
        return parentLength + 1 + index.calculateVarByteLength()
    }

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        ProtoBuf.writeKey(0, WireType.VAR_INT, writer)
        index.writeVarBytes(writer)
    }
}
