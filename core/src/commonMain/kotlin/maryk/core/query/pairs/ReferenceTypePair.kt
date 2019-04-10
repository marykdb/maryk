package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualIndexedEnumDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Defines a pair of a [reference] and [type] of type [E] */
data class ReferenceTypePair<E : IndexedEnum> internal constructor(
    override val reference: IsPropertyReference<TypedValue<E, Any>, IsPropertyDefinition<TypedValue<E, Any>>, Any>,
    val type: E
) : DefinedByReference<TypedValue<E, Any>> {

    override fun toString() = "$reference: $type"

    object Properties : ReferenceValuePairPropertyDefinitions<ReferenceTypePair<*>, IndexedEnum>() {
        override val reference = DefinedByReference.addReference(
            this,
            ReferenceTypePair<*>::reference
        )
        @Suppress("UNCHECKED_CAST")
        override val value = add(
            index = 2, name = "type",
            definition = ContextualIndexedEnumDefinition<RequestContext, RequestContext, IndexedEnum, IsMultiTypeDefinition<IndexedEnum, RequestContext>>(
                contextualResolver = {
                    (it?.reference as? MultiTypePropertyReference<IndexedEnum, *, *, *>?)?.comparablePropertyDefinition?.definition as IsMultiTypeDefinition<IndexedEnum, RequestContext>?
                        ?: throw ContextNotFoundException()
                }
            ),
            getter = ReferenceTypePair<*>::type as (ReferenceTypePair<*>) -> IndexedEnum
        ) as IsPropertyDefinitionWrapper<Any, IndexedEnum, RequestContext, ReferenceTypePair<*>>
    }

    companion object : QueryDataModel<ReferenceTypePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ReferenceTypePair<*>, Properties>) = ReferenceTypePair(
            reference = values(1),
            type = values(2)
        )
    }
}

/** Convenience infix method to create Reference [type] pairs */
@Suppress("UNCHECKED_CAST")
infix fun <E : IndexedEnum> IsPropertyReference<TypedValue<E, *>, IsPropertyDefinition<TypedValue<E, *>>, *>.withType(
    type: E
) =
    ReferenceTypePair(
        this as IsPropertyReference<TypedValue<E, Any>, IsPropertyDefinition<TypedValue<E, Any>>, Any>,
        type
    )
