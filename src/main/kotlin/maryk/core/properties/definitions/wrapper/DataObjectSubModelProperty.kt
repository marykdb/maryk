package maryk.core.properties.definitions.wrapper

import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSubModelDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SubModelPropertyRef

data class DataObjectSubModelProperty<DO: Any, D: DataModel<DO, CX>, CX: IsPropertyContext, in DM: Any>(
        override val index: Int,
        override val name: String,
        override val property: SubModelDefinition<DO, D, CX>,
        override val getter: (DM) -> DO?
) :
        IsSubModelDefinition<DO, CX> by property,
        IsDataObjectProperty<DO, CX, DM>
{
    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?) =
            SubModelPropertyRef(
                    this,
                    parentRefFactory()?.let {
                        it as CanHaveComplexChildReference<*, *, *>
                    }
            )
    override fun validate(previousValue: DO?, newValue: DO?, parentRefFactory: () -> IsPropertyReference<*, *>?) {
        this.property.validateWithRef(previousValue, newValue, { this.getRef(parentRefFactory) })
    }
}