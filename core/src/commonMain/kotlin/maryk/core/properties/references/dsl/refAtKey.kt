package maryk.core.properties.references.dsl

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.MapValueReference

/** Select a map value reference at [key]. */
infix fun <K : Any, V : Any> IsMapDefinition<K, V, *>.at(
    key: K
): (AnyOutPropertyReference?) -> MapValueReference<K, V, out IsPropertyContext> =
    {
        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        this.valueRef(key, parent as CanContainMapItemReference<*, *, *>?)
    }

/** @deprecated Use [at]. */
@Deprecated("Use at(key)", ReplaceWith("at(key)", "maryk.core.properties.references.dsl.at"))
fun <K : Any, V : Any> IsMapDefinition<K, V, *>.refAt(
    key: K
): (AnyOutPropertyReference?) -> MapValueReference<K, V, out IsPropertyContext> =
    this.at(key)
