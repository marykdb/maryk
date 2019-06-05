package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationType.MaxType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** Finds the maximum value for [reference] */
data class Max(
    val reference: IsPropertyReference<out Comparable<*>, *, *>
) : IsAggregationRequest {
    override val aggregationType = MaxType

    companion object : SimpleQueryDataModel<Max>(
        properties = object : ObjectPropertyDefinitions<Max>() {
            init {
                DefinedByReference.addReference(this, Max::reference, name = "of")
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Max>) = Max(
            reference = values(1u)
        )
    }
}
