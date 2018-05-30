package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.objects.AbstractDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.SubModelPropertyDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Reference to a SubModel property containing type [DO] DataObjects, [P] PropertyDefinitions. Which is defined by
 * DataModel of type [DM] and expects context of type [CX] which is transformed into context [CXI] for properties.
 */
class SubModelPropertyRef<
    DO : Any,
    out P: PropertyDefinitions<DO>,
    out DM : AbstractDataModel<DO, P, CXI, CX>,
    CXI: IsPropertyContext,
    CX: IsPropertyContext
> internal constructor(
    propertyDefinition: SubModelPropertyDefinitionWrapper<DO, P, DM, CXI, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *>?
): CanHaveComplexChildReference<DO, SubModelPropertyDefinitionWrapper<DO, P, DM, CXI, CX, *>, CanHaveComplexChildReference<*, *, *>>(
    propertyDefinition, parentReference
), HasEmbeddedPropertyReference<DO> {
    val name = this.propertyDefinition.name

    /** The name of property which is referenced */
    override val completeName: String get() = this.parentReference?.let {
        "${it.completeName}.$name"
    } ?: name

    override fun getEmbedded(name: String) =
        this.propertyDefinition.definition.dataModel.properties.getDefinition(name)?.getRef(this)
            ?: throw DefNotFoundException("Embedded Definition with $name not found")

    override fun getEmbeddedRef(reader: () -> Byte): IsPropertyReference<*, *> {
        val index = initIntByVar(reader)
        return this.propertyDefinition.definition.dataModel.properties.getDefinition(index)?.getRef(this)
                ?: throw DefNotFoundException("Embedded Definition with $name not found")
    }

    /** Calculate the transport length of encoding this reference and cache length with [cacher] */
    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        return this.propertyDefinition.index.calculateVarByteLength() + parentLength
    }

    /** Write transport bytes of property reference to [writer] */
    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        this.propertyDefinition.index.writeVarBytes(writer)
    }
}
