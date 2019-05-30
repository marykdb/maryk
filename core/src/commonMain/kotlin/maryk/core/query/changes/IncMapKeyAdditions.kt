package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.SubListDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.references.IncMapReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Contains [addedKeys] for incrementing map property of [K] keys and [V] values referred by [reference] */
data class IncMapKeyAdditions<K : Comparable<K>, V : Any>(
    override val reference: IncMapReference<K, V, *>,
    val addedKeys: List<K>? = null
) : DefinedByReference<Map<K, V>> {
    object Properties : ObjectPropertyDefinitions<IncMapKeyAdditions<out Comparable<Any>, out Any>>() {
        val reference = DefinedByReference.addReference(this, IncMapKeyAdditions<*, *>::reference)

        @Suppress("UNCHECKED_CAST", "unused")
        val addedKeys = add(
            2u, "addedKeys",
            SubListDefinition(
                valueDefinition = ContextualSubDefinition(
                    contextualResolver = { context: RequestContext? ->
                        (context?.reference as IncMapReference<Comparable<Any>, Any, IsPropertyContext>?)?.propertyDefinition?.definition?.keyDefinition
                            ?: throw ContextNotFoundException()
                    }
                )
            ),
            getter = IncMapKeyAdditions<*, *>::addedKeys as (IncMapKeyAdditions<*, *>) -> List<Comparable<Any>>?
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
