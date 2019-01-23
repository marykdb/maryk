package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextualIndexedEnumDefinition
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.AnyIndexedEnum
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Defines a pair of a [reference] and [range] of type [T] */
data class ReferenceTypePair<E: IndexedEnum<E>> internal constructor(
    override val reference: MultiTypePropertyReference<E, *, MultiTypeDefinitionWrapper<E, *, *, *>, *>,
    val type: E
) : DefinedByReference<TypedValue<E, Any>> {

    override fun toString() = "$reference: $type"

    object Properties: ObjectPropertyDefinitions<ReferenceTypePair<*>>() {
        val reference = DefinedByReference.addReference(
            this,
            ReferenceTypePair<*>::reference
        )
        @Suppress("UNCHECKED_CAST")
        val type = add(
            index = 2, name = "type",
            definition = ContextualIndexedEnumDefinition<RequestContext, RequestContext, AnyIndexedEnum, IsMultiTypeDefinition<AnyIndexedEnum, RequestContext>>(
                contextualResolver = {
                    (it?.reference as? MultiTypePropertyReference<AnyIndexedEnum, *, *, *>?)?.comparablePropertyDefinition?.definition as IsMultiTypeDefinition<AnyIndexedEnum, RequestContext>?
                        ?: throw ContextNotFoundException()
                }
            ),
            getter = ReferenceTypePair<*>::type as (ReferenceTypePair<*>) -> AnyIndexedEnum
        )
    }

    companion object: QueryDataModel<ReferenceTypePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ReferenceTypePair<*>, Properties>) = ReferenceTypePair<AnyIndexedEnum>(
            reference = values(1),
            type = values(2)
        )
    }
}

/** Convenience infix method to create Reference [type] pairs */
infix fun <E: IndexedEnum<E>> IsPropertyReference<TypedValue<E, *>, MultiTypeDefinitionWrapper<E, *, *, *>, *>.with(type: E) =
    ReferenceTypePair(this as MultiTypePropertyReference<E, *, MultiTypeDefinitionWrapper<E, *, *, *>, *>, type)
