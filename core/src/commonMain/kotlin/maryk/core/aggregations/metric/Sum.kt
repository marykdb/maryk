package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationType.SumType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** Does a sum over all values encountered at [reference] */
data class Sum(
    val reference: IsPropertyReference<out Comparable<*>, *, *>
) : IsAggregationRequest {
    override val aggregationType = SumType

    companion object : SimpleQueryDataModel<Sum>(
        properties = object : ObjectPropertyDefinitions<Sum>() {
            init {
                DefinedByReference.addReference(this, Sum::reference, name = "of")
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Sum>) = Sum(
            reference = values(1u)
        )
    }
}
