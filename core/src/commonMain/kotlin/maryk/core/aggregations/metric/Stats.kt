package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationRequestType.StatsType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/**
 * Creates a stats overview for [reference] which is a combination of Value Count,
 * SumType, MinType, Max and Average values
 */
data class Stats<T: Comparable<T>>(
    override val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>
) : IsAggregationRequest<T, IsPropertyReference<out T, IsPropertyDefinition<T>, *>, StatsResponse<T>> {
    override val aggregationType = StatsType

    override fun createAggregator() =
        StatsAggregator(this)

    companion object : SimpleQueryDataModel<Stats<*>>(
        properties = object : ObjectPropertyDefinitions<Stats<*>>() {
            val of by addReference(Stats<*>::reference)
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Stats<*>>) = Stats<Comparable<Any>>(
            reference = values(1u)
        )
    }
}
