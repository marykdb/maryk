package maryk.core.properties.references.dsl

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.MapValueReference

/** Specific extension to support fetching references on map definition at [key] */
fun <K : Any, V : Any> IsMapDefinition<K, V, *>.refAt(
    key: K
): (AnyOutPropertyReference?) -> MapValueReference<K, V, out IsPropertyContext> =
    {
        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        this.valueRef(key, parent as CanContainMapItemReference<*, *, *>?)
    }
