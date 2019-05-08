package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualIndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Defines a pair of a [reference] and [type] of type [E] */
data class ReferenceTypePair<E : TypeEnum<Any>> internal constructor(
    override val reference: TypedPropertyReference<out TypedValue<E, Any>>,
    val type: E
) : DefinedByReference<TypedValue<E, Any>> {

    override fun toString() = "$reference: $type"

    object Properties : ReferenceValuePairPropertyDefinitions<ReferenceTypePair<*>, IndexedEnum, IndexedEnum>() {
        override val reference = DefinedByReference.addReference(
            this,
            ReferenceTypePair<*>::reference
        )

        override val value = add(
            index = 2u, name = "type",
            definition = ContextualIndexedEnumDefinition<RequestContext, RequestContext, IndexedEnum, IsMultiTypeDefinition<TypeEnum<Any>, Any, RequestContext>>(
                contextualResolver = {
                    @Suppress("UNCHECKED_CAST")
                    (it?.reference as? MultiTypePropertyReference<TypeEnum<Any>, *, *, *, *>?)?.comparablePropertyDefinition?.definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, RequestContext>?
                        ?: throw ContextNotFoundException()
                }
            ),
            getter = ReferenceTypePair<*>::type as (ReferenceTypePair<*>) -> IndexedEnum
        )
    }

    companion object : QueryDataModel<ReferenceTypePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ReferenceTypePair<*>, Properties>) = ReferenceTypePair(
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
