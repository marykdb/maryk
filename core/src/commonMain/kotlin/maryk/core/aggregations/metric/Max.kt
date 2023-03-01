package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationRequestType.MaxType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** Finds the maximum value for [reference] */
data class Max<T: Comparable<T>>(
    override val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>
) : IsAggregationRequest<T, IsPropertyReference<out T, IsPropertyDefinition<T>, *>, MaxResponse<T>> {
    override val aggregationType = MaxType

    override fun createAggregator() =
        MaxAggregator(this)

    companion object : SimpleQueryModel<Max<*>>() {
        val of by addReference(Max<*>::reference)

        override fun invoke(values: SimpleObjectValues<Max<*>>) = Max<Comparable<Any>>(
            reference = values(1u)
        )
    }
}
