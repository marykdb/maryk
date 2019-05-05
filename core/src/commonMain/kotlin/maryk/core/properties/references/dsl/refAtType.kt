package maryk.core.properties.references.dsl

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.TypedValue

/** Specific extension to support fetching ref on Typed values by [type] */
fun <E : IndexedEnum> IsSubDefinition<TypedValue<E, *>, *>.refAtType(
    type: E
): (AnyOutPropertyReference?) -> TypedValueReference<E, IsPropertyContext> =
    {
        @Suppress("UNCHECKED_CAST")
        val multiTypeDef = this as IsMultiTypeDefinition<E, IsPropertyContext>

        val parent = if (this is IsPropertyDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        multiTypeDef.typedValueRef(type, parent as CanHaveComplexChildReference<*, *, *, *>)
    }
