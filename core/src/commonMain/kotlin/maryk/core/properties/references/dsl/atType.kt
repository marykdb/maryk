package maryk.core.properties.references.dsl

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.values.Values
import kotlin.jvm.JvmName

/** Specific extension to support fetching deeper references on multi types by [type] */
@JvmName("atEmbedType")
fun <P : PropertyDefinitions, T : Any, R : IsPropertyReference<T, IsDefinitionWrapper<T, *, *, *>, *>> IsMultiTypeDefinition<*, *, *>.atType(
    type: TypeEnum<Values<*, P>>,
    referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    @Suppress("UNCHECKED_CAST")
    {
        val multiTypeDef = this as IsMultiTypeDefinition<TypeEnum<*>, *, *>

        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        val typedValueRef = multiTypeDef.typedValueRef(type, parent as CanHaveComplexChildReference<*, *, *, *>)
        (multiTypeDef.definition(type) as EmbeddedValuesDefinition<IsValuesDataModel<P>, P>).dataModel(
            typedValueRef,
            referenceGetter
        )
    }

/** Specific extension to support fetching deeper references on type definition with [type] */
@JvmName("atListType")
fun <E: TypeEnum<I>, I: List<T>, T: Any, R : IsPropertyReference<*, *, *>> IsMultiTypeDefinition<*, *, *>.atType(
    type: E,
    referenceGetter: IsListDefinition<T, *>.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        @Suppress("UNCHECKED_CAST")
        val multiDefinition = this as IsMultiTypeDefinition<E, I, *>

        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        referenceGetter(
            multiDefinition.definition(type) as? IsListDefinition<T, *>
                ?: throw DefNotFoundException("No definition found for $type in $multiDefinition")
        )(
            multiDefinition.typedValueRef(type, parent as CanHaveComplexChildReference<*, *, *, *>?)
        )
    }


/** Specific extension to support fetching deeper references on type definition with [type] */
@JvmName("atSetType")
fun <E: TypeEnum<I>, I: Set<T>, T: Any, R : IsPropertyReference<*, *, *>> IsMultiTypeDefinition<*, *, *>.atType(
    type: E,
    referenceGetter: IsSetDefinition<T, *>.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        @Suppress("UNCHECKED_CAST")
        val multiDefinition = this as IsMultiTypeDefinition<E, I, *>

        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        referenceGetter(
            multiDefinition.definition(type) as? IsSetDefinition<T, *>
                ?: throw DefNotFoundException("No definition found for $type in $multiDefinition")
        )(
            multiDefinition.typedValueRef(type, parent as CanHaveComplexChildReference<*, *, *, *>?)
        )
    }


/** Specific extension to support fetching deeper references on type definition with [type] */
@JvmName("atMapType")
fun <E: TypeEnum<I>, I: Map<K, V>, K: Any, V: Any, R : IsPropertyReference<*, *, *>> IsMultiTypeDefinition<*, *, *>.atType(
    type: E,
    referenceGetter: IsMapDefinition<K, V, *>.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        @Suppress("UNCHECKED_CAST")
        val multiDefinition = this as IsMultiTypeDefinition<E, I, *>

        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        referenceGetter(
            multiDefinition.definition(type) as? IsMapDefinition<K, V, *>
                ?: throw DefNotFoundException("No definition found for $type in $multiDefinition")
        )(
            multiDefinition.typedValueRef(type, parent as CanHaveComplexChildReference<*, *, *, *>?)
        )
    }

/** Specific extension to support fetching deeper references on type definition with [type] */
@JvmName("atMultiType")
fun <E: TypeEnum<I>, I: TypedValue<E2, *>, E2: TypeEnum<*>, R : IsPropertyReference<*, *, *>> IsMultiTypeDefinition<*, *, *>.atType(
    type: E,
    referenceGetter: IsMultiTypeDefinition<E2, *, *>.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        @Suppress("UNCHECKED_CAST")
        val multiDefinition = this as IsMultiTypeDefinition<E, I, *>

        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        referenceGetter(
            multiDefinition.definition(type) as? IsMultiTypeDefinition<E2, *, *>
                ?: throw DefNotFoundException("No definition found for $type in $multiDefinition")
        )(
            multiDefinition.typedValueRef(type, parent as CanHaveComplexChildReference<*, *, *, *>?)
        )
    }
