package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.properties.references.SetItemReference

/** Select a set item reference by [setItem]. */
infix fun <T : Any> IsSetDefinition<T, *>.item(
    setItem: T
): (AnyOutPropertyReference?) -> SetItemReference<T, *> =
    {
        this.itemRef(
            setItem,
            it as CanContainSetItemReference<*, *, *>
        )
    }

/** @deprecated Use [item]. */
@Deprecated("Use item(setItem)", ReplaceWith("item(setItem)", "maryk.core.properties.references.dsl.item"))
fun <T : Any> IsSetDefinition<T, *>.refAt(
    setItem: T
): (AnyOutPropertyReference?) -> SetItemReference<T, *> =
    this.item(setItem)
