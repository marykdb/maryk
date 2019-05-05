package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import kotlin.jvm.JvmName

/** Specific extension to support fetching deeper references on Map with typed values at [key] */
@JvmName("atMulti")
fun <K : Any, V : TypedValue<E, T>, E: IndexedEnum, T: Any, R : IsPropertyReference<T, *, *>> IsSubDefinition<Map<K, V>, *>.at(
    key: K,
    referenceGetter: IsMultiTypeDefinition<E, *>.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        val mapDefinition = this as IsMapDefinition<K, V, *>

        val parent = if (this is IsPropertyDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        referenceGetter(
            mapDefinition.valueDefinition as IsMultiTypeDefinition<E, *>
        )(
            mapDefinition.valueRef(key, parent as CanContainMapItemReference<*, *, *>?)
        )
    }
