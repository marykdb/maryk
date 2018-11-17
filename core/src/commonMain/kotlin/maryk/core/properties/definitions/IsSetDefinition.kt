package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference

/** Defines a set definition */
interface IsSetDefinition<T: Any, CX: IsPropertyContext>: IsCollectionDefinition<T, Set<T>, CX, IsValueDefinition<T, CX>>,
    HasDefaultValueDefinition<Set<T>> {
    /** Get a reference by [value] to a specific set item of set of [setReference] */
    fun getItemRef(value: T, setReference: SetReference<T, CX>?) =
        SetItemReference(value, this, setReference)
}
