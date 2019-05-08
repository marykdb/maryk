package maryk.core.properties.references.dsl

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.MapValueReference

/** Specific extension to support fetching sub refs on multi type with [type] for lists */
@Suppress("UNCHECKED_CAST")
fun <E : TypeEnum<Map<K, V>>, K: Any, V : Any> IsMultiTypeDefinition<*, *, *>.refAtTypeAndKey(
    type: TypeEnum<Map<K, V>>,
    key: K
): (AnyOutPropertyReference?) -> MapValueReference<*, V, *> =
    {
        val multiTypeDef = this as IsMultiTypeDefinition<TypeEnum<Map<K, V>>, Map<K, V>, IsPropertyContext>
        val typedValueRef = multiTypeDef.typedValueRef(type, it as CanHaveComplexChildReference<*, *, *, *>)
        (multiTypeDef.definitionMap[type] as MapDefinition<K, V, *>).valueRef(
            key,
            typedValueRef
        )
    }
