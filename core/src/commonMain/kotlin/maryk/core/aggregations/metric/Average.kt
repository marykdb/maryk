package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationRequestType.AverageType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** Finds the average value for [reference] */
data class Average<T: Comparable<T>>(
    override val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>
) : IsAggregationRequest<T, IsPropertyReference<out T, IsPropertyDefinition<T>, *>, AverageResponse<T>> {
    override val aggregationType = AverageType

    override fun createAggregator() =
        AverageAggregator(this)

    companion object : SimpleQueryModel<Average<*>>() {
        val of by addReference(Average<*>::reference)

        override fun invoke(values: SimpleObjectValues<Average<*>>) = Average<Comparable<Any>>(
            reference = values(of.index)
        )
    }
}
