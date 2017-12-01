package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference

data class DataObjectListProperty<T: Any, in CX: IsPropertyContext, in DM: Any>(
        override val index: Int,
        override val name: String,
        override val property: ListDefinition<T, CX>,
        override val getter: (DM) -> List<T>?
) :
        IsCollectionDefinition<List<T>, CX> by property,
        IsDataObjectProperty<List<T>, CX, DM>
{
    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?) =
            ListReference(this.property, parentRefFactory() as CanHaveComplexChildReference<*, *, *>?)

    /** Get a reference to a specific list item by index
     * @param index to get list item reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getItemRef(index: Int, parentRefFactory: () -> IsPropertyReference<*, *>? = { null })
            = ListItemReference(index, this.getRef(parentRefFactory))
}