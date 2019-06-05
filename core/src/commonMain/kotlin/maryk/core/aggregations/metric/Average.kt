package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationType.AverageType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** Finds the average value for [reference] */
data class Average(
    val reference: IsPropertyReference<out Comparable<*>, *, *>
) : IsAggregationRequest {
    override val aggregationType = AverageType

    companion object : SimpleQueryDataModel<Average>(
        properties = object : ObjectPropertyDefinitions<Average>() {
            init {
                DefinedByReference.addReference(this, Average::reference, name = "of")
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Average>) = Average(
            reference = values(1u)
        )
    }
}
