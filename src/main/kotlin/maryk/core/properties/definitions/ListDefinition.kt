package maryk.core.properties.definitions

import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.PropertyReference

class ListDefinition<T: Any>(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = true,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        minSize: Int? = null,
        maxSize: Int? = null,
        valueDefinition: AbstractValueDefinition<T>
) : AbstractCollectionDefinition<T, Array<T>>(
        name, index, indexed, searchable, required, final, minSize, maxSize, valueDefinition
), HasSizeDefinition {
    override fun getSize(newValue: Array<T>) = newValue.size

    override fun validateCollectionForExceptions(parentRefFactory: () -> PropertyReference<*, *>?,  newValue: Array<T>, validator: (item: T, parentRefFactory: () -> PropertyReference<*, *>?) -> Any) {
        newValue.forEachIndexed { index, item ->
            validator(item) {
                @Suppress("UNCHECKED_CAST")
                ListItemReference(
                        index,
                        getRef(parentRefFactory) as PropertyReference<Array<T>, ListDefinition<T>>
                )
            }
        }
    }
}
