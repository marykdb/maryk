package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationRequestType.MinType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** Finds the minimum value for [reference] */
data class Min<T: Comparable<T>>(
    override val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>
) : IsAggregationRequest<T, IsPropertyReference<out T, IsPropertyDefinition<T>, *>, MinResponse<T>> {
    override val aggregationType = MinType

    override fun createAggregator() =
        MinAggregator(this)

    companion object : SimpleQueryModel<Min<*>>() {
        val of by addReference(Min<*>::reference)

        override fun invoke(values: SimpleObjectValues<Min<*>>) = Min<Comparable<Any>>(
            reference = values(1u)
        )
    }
}
