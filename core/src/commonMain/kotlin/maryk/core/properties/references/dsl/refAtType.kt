package maryk.core.properties.references.dsl

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.TypedValue

/** Specific extension to support fetching ref on Typed values by [type] */
fun <E : TypeEnum<I>, I: Any> IsSubDefinition<TypedValue<E, I>, *>.refAtType(
    type: E
): (AnyOutPropertyReference?) -> TypedValueReference<E, I, IsPropertyContext> =
    {
        @Suppress("UNCHECKED_CAST")
        val multiTypeDef = this as IsMultiTypeDefinition<E, I, IsPropertyContext>

        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        multiTypeDef.typedValueRef(type, parent as CanHaveComplexChildReference<*, *, *, *>)
    }
