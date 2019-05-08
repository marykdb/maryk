package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import kotlin.jvm.JvmName

/** Specific extension to support fetching deeper references on Map with list at [key] */
@JvmName("atList")
fun <K : Any, V : List<I>, I: Any, T: Any, R : IsPropertyReference<T, *, *>> IsSubDefinition<Map<K, V>, *>.at(
    key: K,
    referenceGetter: IsListDefinition<I, *>.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        @Suppress("UNCHECKED_CAST")
        val mapDefinition = this as IsMapDefinition<K, V, *>

        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        referenceGetter(
            mapDefinition.valueDefinition as IsListDefinition<I, *>
        )(
            mapDefinition.valueRef(key, parent as CanContainMapItemReference<*, *, *>?)
        )
    }
