package maryk.core.properties.references.dsl

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.values.Values

/** Specific extension to support fetching deeper references on multi types by [type] and explicit [properties] */
fun <E : TypeEnum<I>, I: Any, P : PropertyDefinitions, T : Any, R : IsPropertyReference<T, IsPropertyDefinitionWrapper<T, *, *, *>, *>> IsSubDefinition<TypedValue<E, *>, *>.atType(
    type: E,
    @Suppress("UNUSED_PARAMETER") properties: P, // So it is not needed to pass in types
    referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    @Suppress("UNCHECKED_CAST")
    {
        val multiTypeDef = this as IsMultiTypeDefinition<E, I, IsPropertyContext>

        val parent = if (this is IsPropertyDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        val typedValueRef = multiTypeDef.typedValueRef(type, parent as CanHaveComplexChildReference<*, *, *, *>)
        (multiTypeDef.definitionMap[type] as EmbeddedValuesDefinition<IsValuesDataModel<P>, P>).dataModel(
            typedValueRef,
            referenceGetter
        )
    }

/** Specific extension to support fetching deeper references on multi types by [type] */
fun <E: TypeEnum<I>, I: Any, P : PropertyDefinitions, T : Any, R : IsPropertyReference<T, IsPropertyDefinitionWrapper<T, *, *, *>, *>> IsSubDefinition<TypedValue<E, Any>, *>.atType(
    type: TypeEnum<Values<*, P>>,
    referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    @Suppress("UNCHECKED_CAST")
    {
        val multiTypeDef = this as IsMultiTypeDefinition<E, I, IsPropertyContext>

        val parent = if (this is IsPropertyDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        val typedValueRef = multiTypeDef.typedValueRef(type as E, parent as CanHaveComplexChildReference<*, *, *, *>)
        (multiTypeDef.definitionMap[type] as EmbeddedValuesDefinition<IsValuesDataModel<P>, P>).dataModel(
            typedValueRef,
            referenceGetter
        )
    }
