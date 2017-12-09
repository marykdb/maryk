package maryk.core.properties.references

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.SubModelPropertyDefinitionWrapper
import maryk.core.protobuf.ByteLengthContainer

class SubModelPropertyRef<DO : Any, P: PropertyDefinitions<DO>, DM : DataModel<DO, P, CX>, CX: IsPropertyContext>(
        propertyDefinition: SubModelPropertyDefinitionWrapper<DO, P, DM, CX, *>,
        parentReference: CanHaveComplexChildReference<*, *, *>?
): CanHaveComplexChildReference<DO, SubModelPropertyDefinitionWrapper<DO, P, DM, CX, *>, CanHaveComplexChildReference<*, *, *>>(
        propertyDefinition, parentReference
), HasEmbeddedPropertyReference<DO> {
    val name = this.propertyDefinition.name

    /** The name of property which is referenced */
    override val completeName: String? get() = this.parentReference?.let {
        "${it.completeName}.$name"
    } ?: name

    override fun getEmbedded(name: String)
            = this.propertyDefinition.definition.dataModel.getDefinition(name)!!.getRef(this)

    override fun getEmbeddedRef(reader: () -> Byte): IsPropertyReference<*, *> {
        val index = initIntByVar(reader)
        return this.propertyDefinition.definition.dataModel.getDefinition(index)!!.getRef(this)
    }

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