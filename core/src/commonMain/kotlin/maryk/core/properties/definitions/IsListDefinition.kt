package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference

/** Defines a List definition */
interface IsListDefinition<T: Any, CX: IsPropertyContext>: IsCollectionDefinition<T, List<T>, CX, IsValueDefinition<T, CX>>,
    HasDefaultValueDefinition<List<T>> {
    /** Get a reference to a specific list item on [parentList] by [index]. */
    fun itemRef(index: UInt, parentList: ListReference<T, CX>?) =
        ListItemReference(index, this, parentList)
}
