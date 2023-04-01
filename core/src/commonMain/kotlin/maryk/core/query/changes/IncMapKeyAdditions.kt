package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.QueryModel
import maryk.core.properties.definitions.SubListDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.subList
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
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
    companion object : QueryModel<IncMapKeyAdditions<out Comparable<Any>, out Any>, Companion>() {
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

        val addedValues =
            ListDefinitionWrapper(
                3u,
                "addedValues",
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
            ).also(::addSingle)

        override fun invoke(values: ObjectValues<IncMapKeyAdditions<out Comparable<Any>, out Any>, Companion>): IncMapKeyAdditions<out Comparable<Any>, out Any> =
            IncMapKeyAdditions(
                reference = values(1u),
                addedKeys = values(2u),
                addedValues = values(3u)
            )

        override val Serializer = object: ObjectDataModelSerializer<IncMapKeyAdditions<out Comparable<Any>, out Any>, Companion, RequestContext, RequestContext>(
            this
        ) {
            override fun readProtoBuf(
                length: Int,
                reader: () -> Byte,
                context: RequestContext?
            ) =
                super.readProtoBuf(length, reader, context).also {
                    addAddedValuesFromContext(it.values as MutableValueItems, context)
                }
        }

        override val Model = object : QueryDataModel<IncMapKeyAdditions<out Comparable<Any>, out Any>, Companion>(
            properties = Companion
        ) {
            override fun walkJsonToRead(reader: IsJsonLikeReader, values: MutableValueItems, context: RequestContext?) {
                super.walkJsonToRead(reader, values, context)

                addAddedValuesFromContext(values, context)
            }
        }

        private fun addAddedValuesFromContext(
            values: MutableValueItems,
            context: RequestContext?
        ) {
            if (values[addedValues.index] == null) {
                context?.getCollectedIncMapChanges()?.find { incMapChange ->
                    val foundValueChange = incMapChange.valueChanges.find {
                        it.reference == context.reference
                    }?.also {
                        values[addedValues.index] = it.addValues ?: emptyList<Any>()
                    }

                    foundValueChange != null
                }
            }
        }
    }
}
