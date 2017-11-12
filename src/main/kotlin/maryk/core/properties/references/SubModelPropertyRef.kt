package maryk.core.properties.references

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.SubModelDefinition

class SubModelPropertyRef<DO : Any, D : DataModel<DO, CX>, CX: IsPropertyContext>(
        propertyDefinition: SubModelDefinition<DO, D, CX>,
        parentReference: CanHaveComplexChildReference<*, *, *>?
): CanHaveSimpleChildReference<DO, SubModelDefinition<DO, D, CX>, CanHaveComplexChildReference<*, *, *>>(
        propertyDefinition, parentReference
), HasEmbeddedPropertyReference<DO> {
    override fun getEmbeddedRef(reader: () -> Byte): IsPropertyReference<*, *> {
        val index = initIntByVar(reader)
        return this.propertyDefinition.dataModel.getDefinition(index)!!.getRef({ this })
    }
}