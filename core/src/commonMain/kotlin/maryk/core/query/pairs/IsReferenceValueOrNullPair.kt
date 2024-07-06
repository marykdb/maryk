package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsValuesDataModel
import maryk.core.models.ReferenceValuePairDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsValueDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.addReference
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import kotlin.js.JsName

interface IsReferenceValueOrNullPair<T : Any> : DefinedByReference<T> {
    val value: T?

    companion object : ReferenceValuePairDataModel<IsReferenceValueOrNullPair<Any>, Companion, Any, Any, ContextualDefinitionWrapper<Any, Any, RequestContext, ContextualValueDefinition<RequestContext, IsPropertyContext, Any, IsValueDefinition<Any, IsPropertyContext>>, IsReferenceValueOrNullPair<Any>>>() {
        override val reference by addReference(
            IsReferenceValueOrNullPair<*>::reference
        )
        override val value by contextual(
            index = 2u,
            getter = IsReferenceValueOrNullPair<*>::value,
            definition = ContextualValueDefinition(
                contextualResolver = { context: RequestContext? ->
                    context?.reference?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.comparablePropertyDefinition as IsValueDefinition<Any, IsPropertyContext>
                    } ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<IsReferenceValueOrNullPair<Any>, Companion>): IsReferenceValueOrNullPair<Any> {
            val value: Any? = values(2u)
            return if (value == null) {
                ReferenceNullPair(
                    reference = values(1u)
                )
            } else {
                ReferenceValuePair(
                    reference = values(1u),
                    value = value
                )
            }
        }
    }
}



/** Convenience infix method to create Reference [value] pairs */
@JsName("withValueOrNull")
infix fun <T : Any> IsPropertyReference<T, IsValueDefinitionWrapper<T, *, IsPropertyContext, *>, *>.with(value: T?) =
    when (value) {
        null -> ReferenceNullPair(this)
        else -> ReferenceValuePair(this, value)
    }

/** Convenience infix method to create Reference [list] pairs */
@Suppress("UNCHECKED_CAST")
infix fun <T : Any, D : IsListDefinition<T, *>> IsPropertyReference<List<T>, D, *>.with(list: List<T>?): IsReferenceValueOrNullPair<List<T>> =
    when (list) {
        null -> ReferenceNullPair(
            this as IsPropertyReference<List<T>, IsChangeableValueDefinition<List<T>, IsPropertyContext>, *>
        )
        else -> ReferenceValuePair(
            this as IsPropertyReference<List<T>, IsChangeableValueDefinition<List<T>, IsPropertyContext>, *>,
            list.toList()
        )
    }

/** Convenience infix method to create Reference [set] pairs */
@Suppress("UNCHECKED_CAST")
infix fun <T : Any, D : IsSetDefinition<T, *>> IsPropertyReference<Set<T>, D, *>.with(set: Set<T>?): IsReferenceValueOrNullPair<Set<T>> =
    when(set) {
        null -> ReferenceNullPair(
            this as IsPropertyReference<Set<T>, IsChangeableValueDefinition<Set<T>, IsPropertyContext>, *>,
        )
        else -> ReferenceValuePair(
            this as IsPropertyReference<Set<T>, IsChangeableValueDefinition<Set<T>, IsPropertyContext>, *>,
            set.toSet()
        )
    }

/** Convenience infix method to create Reference [map] pairs */
@Suppress("UNCHECKED_CAST")
infix fun <K : Any, V : Any, D : IsMapDefinition<K, V, *>> IsPropertyReference<Map<K, V>, D, *>.with(map: Map<K, V>?): IsReferenceValueOrNullPair<Map<K, V>> =
    when(map) {
        null -> ReferenceNullPair(
            this as IsPropertyReference<Map<K, V>, IsChangeableValueDefinition<Map<K, V>, IsPropertyContext>, *>,
        )
        else -> ReferenceValuePair(
            this as IsPropertyReference<Map<K, V>, IsChangeableValueDefinition<Map<K, V>, IsPropertyContext>, *>,
            map.toMap()
        )
    }

/** Convenience infix method to create Reference [typedValue] pairs */
@Suppress("UNCHECKED_CAST")
infix fun <E : TypeEnum<T>, T: Any, D : IsMultiTypeDefinition<E, T, *>> IsPropertyReference<TypedValue<E, T>, D, *>.with(
    typedValue: TypedValue<E, *>?
): IsReferenceValueOrNullPair<TypedValue<E, *>> =
    when (typedValue) {
        null -> ReferenceNullPair(
            this as IsPropertyReference<TypedValue<E, *>, IsChangeableValueDefinition<TypedValue<E, *>, IsPropertyContext>, *>
        )
        else -> ReferenceValuePair(
            this as IsPropertyReference<TypedValue<E, *>, IsChangeableValueDefinition<TypedValue<E, *>, IsPropertyContext>, *>,
            typedValue
        )
    }

/** Convenience infix method to create Reference [typedValue] pairs */
@Suppress("UNCHECKED_CAST")
@JsName("withTypeReferenceOrNull")
infix fun <E : TypeEnum<T>, T: Any> TypeReference<E, T, *>.with(
    typedValue: E?,
): IsReferenceValueOrNullPair<E> =
    when (typedValue) {
        null -> ReferenceNullPair(
            this as IsPropertyReference<E, IsChangeableValueDefinition<E, IsPropertyContext>, *>
        )
        else -> ReferenceValuePair(
            this as IsPropertyReference<E, IsChangeableValueDefinition<E, IsPropertyContext>, *>,
            typedValue
        )
    }

/** Convenience infix method to create TypeReference [values] pairs */
@Suppress("UNCHECKED_CAST")
infix fun <DM : IsValuesDataModel, D : IsEmbeddedValuesDefinition<DM, *>> IsPropertyReference<Values<DM>, D, *>.with(
    values: Values<DM>?
): IsReferenceValueOrNullPair<Values<DM>> =
    when (values) {
        null -> ReferenceNullPair(
            this as IsPropertyReference<Values<DM>, IsChangeableValueDefinition<Values<DM>, IsPropertyContext>, *>
        )
        else -> ReferenceValuePair(
            this as IsPropertyReference<Values<DM>, IsChangeableValueDefinition<Values<DM>, IsPropertyContext>, *>,
            values
        )
    }
