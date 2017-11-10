package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.PropertyReference
import maryk.core.properties.references.SetItemReference

class SetDefinition<T: Any, CX: IsPropertyContext>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        minSize: Int? = null,
        maxSize: Int? = null,
        valueDefinition: AbstractSimpleValueDefinition<T, CX>
) : AbstractCollectionDefinition<T, Set<T>, CX>(
        name, index, indexed, searchable, required, final, minSize, maxSize, valueDefinition
), HasSizeDefinition {
    override fun getSize(newValue: Set<T>) = newValue.size

    override fun newMutableCollection() = mutableSetOf<T>()

    override fun getRef(parentRefFactory: () -> PropertyReference<*, *>?) =
            PropertyReference(this, parentRefFactory())

    /** Get a reference to a specific set item
     * @param key to get reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getItemRef(value: T, parentRefFactory: () -> PropertyReference<*, *>? = { null })
            = SetItemReference(value, this.getRef(parentRefFactory))

    override fun validateCollectionForExceptions(parentRefFactory: () -> PropertyReference<*, *>?, newValue: Set<T>, validator: (item: T, parentRefFactory: () -> PropertyReference<*, *>?) -> Any) {
        newValue.forEach {
            validator(it) {
                this.getItemRef(it, parentRefFactory)
            }
        }
    }
}