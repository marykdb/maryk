package maryk.core.properties.references.dsl

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.TypedValueReference

/** Specific extension to support fetching ref on Typed values by [type] */
fun <E : TypeEnum<I>, I: Any> IsMultiTypeDefinition<*, *, *>.refAtType(
    type: E
): (AnyOutPropertyReference?) -> TypedValueReference<E, I, IsPropertyContext> =
    {
        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        (this as IsMultiTypeDefinition<E, I, IsPropertyContext>).typedValueRef(type, parent as CanHaveComplexChildReference<*, *, *, *>)
    }
