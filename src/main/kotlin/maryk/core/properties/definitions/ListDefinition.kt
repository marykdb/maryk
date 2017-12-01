package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference

class ListDefinition<T: Any, in CX: IsPropertyContext>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        minSize: Int? = null,
        maxSize: Int? = null,
        valueDefinition: AbstractValueDefinition<T, CX>
) : AbstractCollectionDefinition<T, List<T>, CX, AbstractValueDefinition<T, CX>>(
        name, index, indexed, searchable, required, final, minSize, maxSize, valueDefinition
), HasSizeDefinition, IsCollectionDefinition<List<T>, CX> {
    override fun newMutableCollection(context: CX?) = mutableListOf<T>()

    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?) =
            ListReference(this, parentRefFactory() as CanHaveComplexChildReference<*, *, *>?)

    /** Get a reference to a specific list item by index
     * @param index to get list item reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getItemRef(index: Int, parentRefFactory: () -> IsPropertyReference<*, *>? = { null })
            = ListItemReference(index, this.getRef(parentRefFactory))

    override fun validateCollectionForExceptions(parentRefFactory: () -> IsPropertyReference<*, *>?,  newValue: List<T>, validator: (item: T, parentRefFactory: () -> IsPropertyReference<*, *>?) -> Any) {
        newValue.forEachIndexed { index, item ->
            validator(item) { this.getItemRef(index, parentRefFactory) }
        }
    }
}
