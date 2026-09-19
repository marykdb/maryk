package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import kotlin.jvm.JvmName

/** Specific extension to support fetching deeper references on Map with set at [key] */
@JvmName("atSet")
fun <K : Any, V : Set<I>, I: Any, T: Any, R : IsPropertyReference<T, *, *>> IsMapDefinition<K, V, *>.at(
    key: K,
    referenceGetter: IsSetDefinition<I, *>.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        referenceGetter(
            this.valueDefinition as IsSetDefinition<I, *>
        )(
            this.valueRef(key, parent as CanContainMapItemReference<*, *, *>?)
        )
    }
