package maryk.core.properties.references.dsl

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.types.TypedValue

/** Specific extension to support fetching sub refs on Map values by [key] and [type] */
@Suppress("UNCHECKED_CAST")
fun <E : TypeEnum<Any>, T : Any> IsSubDefinition<TypedValue<E, Any>, *>.refAtTypeAndIndexWeak(
    type: E,
    listIndex: UInt
): (AnyOutPropertyReference?) -> ListItemReference<T, *> =
    {
        val multiTypeDef = this as IsMultiTypeDefinition<E, IsPropertyContext>
        val typedValueRef = multiTypeDef.typedValueRef(type, it as CanHaveComplexChildReference<*, *, *, *>)
        (multiTypeDef.definitionMap[type] as ListDefinition<T, *>).itemRef(
            listIndex,
            typedValueRef
        )
    }

/** Specific extension to support fetching sub refs on multi type with [type] for lists */
@Suppress("UNCHECKED_CAST")
fun <E : TypeEnum<T>, T : Any> IsSubDefinition<TypedValue<E, Any>, *>.refAtTypeAndIndex(
    type: TypeEnum<List<T>>,
    listIndex: UInt
): (AnyOutPropertyReference?) -> ListItemReference<T, *> =
    {
        val multiTypeDef = this as IsMultiTypeDefinition<E, IsPropertyContext>
        val typedValueRef = multiTypeDef.typedValueRef(type as E, it as CanHaveComplexChildReference<*, *, *, *>)
        (multiTypeDef.definitionMap[type] as ListDefinition<T, *>).itemRef(
            listIndex,
            typedValueRef
        )
    }
