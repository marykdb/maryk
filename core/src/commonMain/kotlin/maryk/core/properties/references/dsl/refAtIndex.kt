package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.ListItemReference

/** Specific extension to support fetching list item refs by [listIndex] */
@Suppress("UNCHECKED_CAST")
fun <T : Any> IsSubDefinition<List<T>, *>.refAt(
    listIndex: UInt
): (AnyOutPropertyReference?) -> ListItemReference<T, *> =
    {
        (this as ListDefinition<T, *>).itemRef(
            listIndex,
            it as CanContainListItemReference<*, *, *>
        )
    }
