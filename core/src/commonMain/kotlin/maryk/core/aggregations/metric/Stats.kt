package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationType.StatsType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/**
 * Creates a stats overview for [reference] which is a combination of Value Count,
 * SumType, MinType, Max and Average values
 */
data class Stats(
    val reference: IsPropertyReference<out Comparable<*>, *, *>
) : IsAggregationRequest {
    override val aggregationType = StatsType

    companion object : SimpleQueryDataModel<Stats>(
        properties = object : ObjectPropertyDefinitions<Stats>() {
            init {
                DefinedByReference.addReference(this, Stats::reference, name = "of")
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Stats>) = Stats(
            reference = values(1u)
        )
    }
}
