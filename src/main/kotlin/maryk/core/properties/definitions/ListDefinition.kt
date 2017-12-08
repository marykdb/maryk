package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference

class ListDefinition<T: Any, CX: IsPropertyContext>(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        override val minSize: Int? = null,
        override val maxSize: Int? = null,
        override val valueDefinition: IsValueDefinition<T, CX>
) : IsCollectionDefinition<T, List<T>, CX, IsValueDefinition<T, CX>> {
    init {
        assert(valueDefinition.required, { "Definition for value should have required=true on List" })
    }

    override fun newMutableCollection(context: CX?) = mutableListOf<T>()

    /** Get a reference to a specific list item by index
     * @param index to get list item reference for
     * @param parentList (optional) factory to create parent ref
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
