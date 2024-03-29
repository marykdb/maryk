package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.models.QueryModel
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.subList
import maryk.core.properties.references.IncMapReference
import maryk.core.query.DefinedByReference
import maryk.core.query.RequestContext
import maryk.core.query.addReference
import maryk.core.values.ObjectValues

/**
 * Changes for an incrementing map property of [K] keys and [V] values referred by [reference] with [addValues]
 */
data class IncMapValueChanges<K : Comparable<K>, V : Any> internal constructor(
    override val reference: IncMapReference<K, V, *>,
    val addValues: List<V>? = null
) : DefinedByReference<Map<K, V>> {
    companion object : QueryModel<IncMapValueChanges<out Comparable<Any>, out Any>, Companion>() {
        val reference by addReference(IncMapValueChanges<*, *>::reference)

        val addValues by subList(
            index = 2u,
            name = "addValues",
            valueDefinition = ContextualSubDefinition(
                contextualResolver = { context: RequestContext? ->
                    @Suppress("UNCHECKED_CAST")
                    (context?.reference as IncMapReference<Comparable<Any>, Any, IsPropertyContext>?)?.propertyDefinition?.definition?.valueDefinition
                        ?: throw ContextNotFoundException()
                }
            ),
            getter = IncMapValueChanges<*, *>::addValues
        )

        override fun invoke(values: ObjectValues<IncMapValueChanges<out Comparable<Any>, out Any>, Companion>) = IncMapValueChanges<Comparable<Any>, Any>(
            reference = values(1u),
            addValues = values(2u)
        )
    }
}


/**
 * Convenience infix method to define a set value change
 * Set property of values [V] with [addValues] for changes
 * First values are deleted before adding new
 */
fun <K: Comparable<K>, V : Any> IncMapReference<K, V, *>.change(
    addValues: List<V>? = null
) =
    IncMapValueChanges(
        reference = this,
        addValues = addValues
    )
