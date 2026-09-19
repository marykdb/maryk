package maryk.core.properties.references.dsl

import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import kotlin.jvm.JvmName

/** Specific extension to support fetching deeper references on Map with typed values at [key] */
@JvmName("atMulti")
fun <K : Any, V : TypedValue<E, I>, E: TypeEnum<I>, I: Any, T: Any, R : IsPropertyReference<T, *, *>> IsMapDefinition<K, V, *>.at(
    key: K,
    referenceGetter: IsMultiTypeDefinition<E, I, *>.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        referenceGetter(
            this.valueDefinition as IsMultiTypeDefinition<E, I, *>
        )(
            this.valueRef(key, parent as CanContainMapItemReference<*, *, *>?)
        )
    }
