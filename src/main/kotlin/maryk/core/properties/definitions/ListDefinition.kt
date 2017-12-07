package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference

class ListDefinition<T: Any, CX: IsPropertyContext>(
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = true,
        final: Boolean = false,
        minSize: Int? = null,
        maxSize: Int? = null,
        valueDefinition: AbstractValueDefinition<T, CX>
) : AbstractCollectionDefinition<T, List<T>, CX, AbstractValueDefinition<T, CX>>(
        indexed, searchable, required, final, minSize, maxSize, valueDefinition
) {
    override fun newMutableCollection(context: CX?) = mutableListOf<T>()

    /** Get a reference to a specific list item by index
     * @param index to get list item reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getItemRef(index: Int, parentList: ListReference<T, CX>?)
            = ListItemReference(index, this, parentList)

    override fun validateCollectionForExceptions(refGetter: () -> IsPropertyReference<List<T>, IsPropertyDefinition<List<T>>>?, newValue: List<T>, validator: (item: T, parentRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) -> Any) {
        newValue.forEachIndexed { index, item ->
            validator(item) {
                @Suppress("UNCHECKED_CAST")
                this.getItemRef(index, refGetter() as ListReference<T, CX>?)
            }
        }
    }
}
