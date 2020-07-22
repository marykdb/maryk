package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationRequestType.SumType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** Does a sum over all values encountered at [reference] */
data class Sum<T: Comparable<T>>(
    override val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>
) : IsAggregationRequest<T, IsPropertyReference<out T, IsPropertyDefinition<T>, *>, SumResponse<T>> {
    override val aggregationType = SumType

    override fun createAggregator() =
        SumAggregator(this)

    @Suppress("unused")
    companion object : SimpleQueryDataModel<Sum<*>>(
        properties = object : ObjectPropertyDefinitions<Sum<*>>() {
            val of by addReference(Sum<*>::reference)
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Sum<*>>) = Sum<Comparable<Any>>(
            reference = values(1u)
        )
    }
}
