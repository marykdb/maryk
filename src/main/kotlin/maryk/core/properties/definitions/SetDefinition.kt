package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference

class SetDefinition<T: Any, in CX: IsPropertyContext>(
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

    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?) =
            SetReference(this, parentRefFactory() as CanHaveComplexChildReference<*, *, *>?)

    /** Get a reference to a specific set item
     * @param key to get reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getItemRef(value: T, parentRefFactory: () -> IsPropertyReference<*, *>? = { null })
            = SetItemReference(value, this.getRef(parentRefFactory))

    override fun validateCollectionForExceptions(parentRefFactory: () -> IsPropertyReference<*, *>?, newValue: Set<T>, validator: (item: T, parentRefFactory: () -> IsPropertyReference<*, *>?) -> Any) {
        newValue.forEach {
            validator(it) {
                this.getItemRef(it, parentRefFactory)
            }
        }
    }
}