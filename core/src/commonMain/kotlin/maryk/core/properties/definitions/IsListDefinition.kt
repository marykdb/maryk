package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference

/** Defines a List definition */
interface IsListDefinition<T : Any, CX : IsPropertyContext> :
    IsCollectionDefinition<T, List<T>, CX, IsValueDefinition<T, CX>>,
    HasDefaultValueDefinition<List<T>> {
    /** Get a reference to a specific list item on [parentList] by [index]. */
    fun itemRef(index: UInt, parentList: CanContainListItemReference<*, *, *>?) =
        ListItemReference(index, this, parentList)

    /** Get a reference to any list item on [parentList]. */
    fun anyItemRef(parentList: IsPropertyReference<*, *, *>?) =
        ListAnyItemReference(this, parentList)
}
