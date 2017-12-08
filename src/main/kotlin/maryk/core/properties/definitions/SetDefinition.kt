package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference

class SetDefinition<T: Any, CX: IsPropertyContext>(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        override val minSize: Int? = null,
        override val maxSize: Int? = null,
        override val valueDefinition: IsValueDefinition<T, CX>
) : IsCollectionDefinition<T, Set<T>, CX, IsValueDefinition<T, CX>> {
    init {
        assert(valueDefinition.required, { "Definition for value should have required=true on set" })
    }

    override fun newMutableCollection(context: CX?) = mutableSetOf<T>()

    /** Get a reference to a specific set item
     * @param value to get reference for
     * @param setReference (optional) factory to create parent ref
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