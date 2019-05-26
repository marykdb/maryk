package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference

/** Defines a set definition */
interface IsSetDefinition<T : Any, CX : IsPropertyContext> :
    IsCollectionDefinition<T, Set<T>, CX, IsSubDefinition<T, CX>>,
    HasDefaultValueDefinition<Set<T>> {
    /** Get a reference by [value] to a specific set item of set of [setReference] */
    fun itemRef(value: T, setReference: CanContainSetItemReference<*, *, *>?) =
        SetItemReference(value, this, setReference)

    override fun newMutableCollection(context: CX?) = mutableSetOf<T>()

    override fun getItemPropertyRefCreator(
        index: UInt,
        item: T
    ) = { parentRef: AnyPropertyReference? ->
        @Suppress("UNCHECKED_CAST")
        this.itemRef(item, parentRef as SetReference<T, CX>?) as IsPropertyReference<Any, *, *>
    }

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>, *>?,
        newValue: Set<T>,
        validator: (item: T, parentRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>, *>?) -> Any
    ) {
        for (it in newValue) {
            validator(it) {
                @Suppress("UNCHECKED_CAST")
                this.itemRef(it, refGetter() as SetReference<T, CX>?)
            }
        }
    }
}
