package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference

class SetDefinition<T: Any, CX: IsPropertyContext>(
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = true,
        final: Boolean = false,
        minSize: Int? = null,
        maxSize: Int? = null,
        valueDefinition: AbstractValueDefinition<T, CX>
) : AbstractCollectionDefinition<T, Set<T>, CX, AbstractValueDefinition<T, CX>>(
        indexed, searchable, required, final, minSize, maxSize, valueDefinition
) {
    override fun newMutableCollection(context: CX?) = mutableSetOf<T>()

    /** Get a reference to a specific set item
     * @param key to get reference for
     * @param parentRefFactory (optional) factory to create parent ref
     */
    fun getItemRef(value: T, setReference: SetReference<T, CX>?)
            = SetItemReference(value, this, setReference)

    override fun validateCollectionForExceptions(refGetter: () -> IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>>?, newValue: Set<T>, validator: (item: T, parentRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) -> Any) {
        newValue.forEach {
            validator(it) {
                @Suppress("UNCHECKED_CAST")
                this.getItemRef(it, refGetter() as SetReference<T, CX>?)
            }
        }
    }
}