package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.ListItemReference

/** Specific extension to support fetching list item refs by [listIndex] */
fun <T : Any> IsListDefinition<T, *>.refAt(
    listIndex: UInt
): (AnyOutPropertyReference?) -> ListItemReference<T, *> =
    {
        this.itemRef(
            listIndex,
            it as CanContainListItemReference<*, *, *>
        )
    }
