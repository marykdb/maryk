package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
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
) : HasEmbeddedPropertyReference<T>, CanHaveComplexChildReference<T, IsValueDefinition<T, CX>, ListReference<T, CX>, List<T>>(
    listDefinition.valueDefinition, parentReference
) {
    override fun getEmbedded(name: String) = if(this.propertyDefinition is IsEmbeddedObjectDefinition<*, *, *, *, *>) {
        this.propertyDefinition.dataModel.properties[name]?.getRef(this)
                ?: throw DefNotFoundException("Embedded Definition with $name not found")
    } else throw DefNotFoundException("ListItem can not contain embedded name references ($name)")

    override fun getEmbeddedRef(reader: () -> Byte): AnyPropertyReference {
        if(this.propertyDefinition is IsEmbeddedObjectDefinition<*, *, *, *, *>) {
            val index = initIntByVar(reader)
            return this.propertyDefinition.dataModel.properties[index]?.getRef(this)
                    ?: throw DefNotFoundException("Embedded Definition with $index not found")
        } else throw DefNotFoundException("ListItem can not contain embedded index references ($index)")
    }

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

    override fun resolve(values: List<T>): T? = values[index]
}
