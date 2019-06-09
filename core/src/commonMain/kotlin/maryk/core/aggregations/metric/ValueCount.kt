package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationRequestType.ValueCountType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** Counts all values defined for [reference] */
data class ValueCount<T: Comparable<T>>(
    override val reference: IsPropertyReference<T, *, *>
) : IsAggregationRequest<T, IsPropertyReference<T, *, *>, ValueCountResponse<T>> {
    override val aggregationType = ValueCountType

    override fun createAggregator() =
        ValueCountAggregator(this)

    companion object : SimpleQueryDataModel<ValueCount<*>>(
        properties = object : ObjectPropertyDefinitions<ValueCount<*>>() {
            init {
                DefinedByReference.addReference(this, ValueCount<*>::reference, name = "of")
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ValueCount<*>>) = ValueCount<Comparable<Any>>(
            reference = values(1u)
        )
    }
}
