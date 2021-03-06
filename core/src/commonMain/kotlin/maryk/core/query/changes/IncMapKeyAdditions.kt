package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.SubListDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.subList
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.references.IncMapReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.addReference
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader

/** Contains [addedKeys] for incrementing map property of [K] keys and [V] values referred by [reference] */
data class IncMapKeyAdditions<K : Comparable<K>, V : Any>(
    override val reference: IncMapReference<K, V, *>,
    val addedKeys: List<K>? = null,
    val addedValues: List<V>? = null
) : DefinedByReference<Map<K, V>> {
    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<IncMapKeyAdditions<out Comparable<Any>, out Any>>() {
        val reference by addReference(IncMapKeyAdditions<*, *>::reference)

        val addedKeys by subList(
            index = 2u,
            valueDefinition = ContextualSubDefinition(
                contextualResolver = { context: RequestContext? ->
                    @Suppress("UNCHECKED_CAST")
                    (context?.reference as IncMapReference<Comparable<Any>, Any, IsPropertyContext>?)?.propertyDefinition?.definition?.keyDefinition
                        ?: throw ContextNotFoundException()
                }
            ),
            getter = IncMapKeyAdditions<*, *>::addedKeys
        )

        val addedValues by ObjectDefinitionWrapperDelegateLoader(this) { propName ->
            ListDefinitionWrapper(
                3u,
                propName,
                SubListDefinition(
                    ContextualSubDefinition(
                        contextualResolver = { context: RequestContext? ->
                            @Suppress("UNCHECKED_CAST")
                            (context?.reference as IncMapReference<Comparable<Any>, Any, IsPropertyContext>?)?.propertyDefinition?.definition?.valueDefinition
                                ?: throw ContextNotFoundException()
                        }
                    )
                ),
                getter = IncMapKeyAdditions<*, *>::addedValues,
                toSerializable = { _, _ ->
                    null // Reset value to null, so it does not get serialized/send.
                }
            )
        }
    }

    companion object : QueryDataModel<IncMapKeyAdditions<out Comparable<Any>, out Any>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<IncMapKeyAdditions<out Comparable<Any>, out Any>, Properties>) = IncMapKeyAdditions<Comparable<Any>, Any>(
            reference = values(1u),
            addedKeys = values(2u),
            addedValues = values(3u)
        )

        override fun walkJsonToRead(reader: IsJsonLikeReader, values: MutableValueItems, context: RequestContext?) {
            super.walkJsonToRead(reader, values, context)

            addAddedValuesFromContext(values, context)
        }

        override fun readProtoBuf(
            length: Int,
            reader: () -> Byte,
            context: RequestContext?
        ) =
            super.readProtoBuf(length, reader, context).also {
                addAddedValuesFromContext(it.values as MutableValueItems, context)
            }

        private fun addAddedValuesFromContext(
            values: MutableValueItems,
            context: RequestContext?
        ) {
            if (values[Properties.addedValues.index] == null) {
                context?.getCollectedIncMapChanges()?.find { incMapChange ->
                    val foundValueChange = incMapChange.valueChanges.find {
                        it.reference == context.reference
                    }?.also {
                        values[Properties.addedValues.index] = it.addValues ?: emptyList<Any>()
                    }

                    foundValueChange != null
                }
            }
        }
    }
}
