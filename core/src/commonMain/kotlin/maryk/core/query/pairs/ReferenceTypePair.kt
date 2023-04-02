package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ReferenceValuePairDataModel
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualIndexedEnumDefinition
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.addReference
import maryk.core.values.ObjectValues

/** Defines a pair of a [reference] and [type] of type [E] */
data class ReferenceTypePair<E : TypeEnum<Any>> internal constructor(
    override val reference: TypedPropertyReference<out TypedValue<E, Any>>,
    val type: E
) : DefinedByReference<TypedValue<E, Any>> {

    override fun toString() = "$reference: $type"

    companion object : ReferenceValuePairDataModel<ReferenceTypePair<*>, Companion, IndexedEnum, IndexedEnum, ContextualDefinitionWrapper<IndexedEnum, IndexedEnum, RequestContext, ContextualIndexedEnumDefinition<RequestContext, RequestContext, IndexedEnum, IsMultiTypeDefinition<TypeEnum<Any>, Any, RequestContext>>, ReferenceTypePair<*>>>() {
        override val reference by addReference(
            ReferenceTypePair<*>::reference
        )

        override val value by contextual(
            index = 2u,
            getter = ReferenceTypePair<*>::type as (ReferenceTypePair<*>) -> IndexedEnum,
            definition = ContextualIndexedEnumDefinition<RequestContext, RequestContext, IndexedEnum, IsMultiTypeDefinition<TypeEnum<Any>, Any, RequestContext>>(
                contextualResolver = {
                    @Suppress("UNCHECKED_CAST")
                    (it?.reference as? MultiTypePropertyReference<TypeEnum<Any>, *, *, *, *>?)?.comparablePropertyDefinition?.definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, RequestContext>?
                        ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<ReferenceTypePair<*>, Companion>) = ReferenceTypePair(
            reference = values(1u),
            type = values(2u)
        )
    }
}

/** Convenience infix method to create Reference [type] pairs */
@Suppress("UNCHECKED_CAST")
infix fun <E : TypeEnum<Any>> IsPropertyReference<TypedValue<E, *>, IsPropertyDefinition<TypedValue<E, *>>, *>.withType(
    type: E
) =
    ReferenceTypePair(
        this as IsPropertyReference<TypedValue<E, Any>, IsPropertyDefinition<TypedValue<E, Any>>, Any>,
        type
    )
