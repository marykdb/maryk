package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationType.ValueCountType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** Counts all values defined for [reference] */
data class ValueCount(
    val reference: IsPropertyReference<*, *, *>
) : IsAggregationRequest {
    override val aggregationType = ValueCountType

    companion object : SimpleQueryDataModel<ValueCount>(
        properties = object : ObjectPropertyDefinitions<ValueCount>() {
            init {
                DefinedByReference.addReference(this, ValueCount::reference, name = "of")
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ValueCount>) = ValueCount(
            reference = values(1u)
        )
    }
}
