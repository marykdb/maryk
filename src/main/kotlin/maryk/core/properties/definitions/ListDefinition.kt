package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.PropertyReference

class ListDefinition<T: Any, CX: IsPropertyContext>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        minSize: Int? = null,
        maxSize: Int? = null,
        valueDefinition: AbstractValueDefinition<T, CX>
) : AbstractCollectionDefinition<T, List<T>, CX>(
        name, index, indexed, searchable, required, final, minSize, maxSize, valueDefinition
), HasSizeDefinition {
    override fun getSize(newValue: List<T>) = newValue.size

    override fun newMutableCollection() = mutableListOf<T>()

    override fun getRef(parentRefFactory: () -> PropertyReference<*, *>?) =
            PropertyReference(this, parentRefFactory())

    /** Get a reference to a specific list item by index
     * @param item to get list item reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getItemRef(index: Int, parentRefFactory: () -> PropertyReference<*, *>? = { null })
            = ListItemReference(index, this.getRef(parentRefFactory))

    override fun validateCollectionForExceptions(parentRefFactory: () -> PropertyReference<*, *>?,  newValue: List<T>, validator: (item: T, parentRefFactory: () -> PropertyReference<*, *>?) -> Any) {
        newValue.forEachIndexed { index, item ->
            validator(item) { this.getItemRef(index, parentRefFactory) }
        }
    }
}
