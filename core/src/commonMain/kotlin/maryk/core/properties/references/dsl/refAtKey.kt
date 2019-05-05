package maryk.core.properties.references.dsl

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.MapValueReference

/** Specific extension to support fetching references on map definition at [key] */
fun <K : Any, V : Any> IsSubDefinition<Map<K, V>, *>.refAtKey(
    key: K
): (AnyOutPropertyReference?) -> MapValueReference<K, V, out IsPropertyContext> =
    {
        val mapDefinition = this as IsMapDefinition<K, V, *>

        val parent = if (this is IsPropertyDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        mapDefinition.valueRef(key, parent as CanContainMapItemReference<*, *, *>?)
    }
