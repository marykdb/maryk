package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import kotlin.jvm.JvmName

/** Specific extension to support fetching deeper references on Map with map values at [key] */
@JvmName("atMap")
fun <K : Any, V : Map<K2, V2>, K2: Any, V2: Any, T : Any, R : IsPropertyReference<T, *, *>> IsMapDefinition<K, V, *>.at(
    key: K,
    referenceGetter: IsMapDefinition<K2, V2, *>.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        referenceGetter(
            this.valueDefinition as IsMapDefinition<K2, V2, *>
        )(
            this.valueRef(key, parent as CanContainMapItemReference<*, *, *>?)
        )
    }
