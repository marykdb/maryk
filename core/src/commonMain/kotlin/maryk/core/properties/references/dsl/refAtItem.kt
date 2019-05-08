package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.properties.references.SetItemReference

/** Extension to support fetching set item refs by [setItem] */
fun <T : Any> IsSetDefinition<T, *>.refAt(
    setItem: T
): (AnyOutPropertyReference?) -> SetItemReference<T, *> =
    {
        this.itemRef(
            setItem,
            it as CanContainSetItemReference<*, *, *>
        )
    }
