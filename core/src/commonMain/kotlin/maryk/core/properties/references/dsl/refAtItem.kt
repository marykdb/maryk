package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.properties.references.SetItemReference

/** Extension to support fetching set item refs by [setItem] */
@Suppress("UNCHECKED_CAST")
fun <T : Any> IsSubDefinition<Set<T>, *>.refAt(
    setItem: T
): (AnyOutPropertyReference?) -> SetItemReference<T, *> =
    {
        (this as SetDefinition<T, *>).itemRef(
            setItem,
            it as CanContainSetItemReference<*, *, *>
        )
    }
