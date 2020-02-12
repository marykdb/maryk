package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.subList
import maryk.core.properties.references.IncMapReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.addReference
import maryk.core.values.ObjectValues

/** Contains [addedKeys] for incrementing map property of [K] keys and [V] values referred by [reference] */
data class IncMapKeyAdditions<K : Comparable<K>, V : Any>(
    override val reference: IncMapReference<K, V, *>,
    val addedKeys: List<K>? = null
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
    }

    companion object : QueryDataModel<IncMapKeyAdditions<out Comparable<Any>, out Any>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<IncMapKeyAdditions<out Comparable<Any>, out Any>, Properties>) = IncMapKeyAdditions<Comparable<Any>, Any>(
            reference = values(1u),
            addedKeys = values(2u)
        )
    }
}
