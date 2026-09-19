package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.ListItemReference

/** Select a list item reference by [listIndex]. */
infix fun <T : Any> IsListDefinition<T, *>.at(
    listIndex: UInt
): (AnyOutPropertyReference?) -> ListItemReference<T, *> =
    {
        this.itemRef(
            listIndex,
            it as CanContainListItemReference<*, *, *>
        )
    }

/** @deprecated Use [at]. */
@Deprecated("Use at(listIndex)", ReplaceWith("at(listIndex)", "maryk.core.properties.references.dsl.at"))
fun <T : Any> IsListDefinition<T, *>.refAt(
    listIndex: UInt
): (AnyOutPropertyReference?) -> ListItemReference<T, *> =
    this.at(listIndex)
