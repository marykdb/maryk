package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationRequestType.ValueCountType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** Counts all values defined for [reference] */
data class ValueCount<T: Comparable<T>>(
    override val reference: IsPropertyReference<T, *, *>
) : IsAggregationRequest<T, IsPropertyReference<T, *, *>, ValueCountResponse<T>> {
    override val aggregationType = ValueCountType

    override fun createAggregator() =
        ValueCountAggregator(this)

    @Suppress("unused")
    companion object : SimpleQueryDataModel<ValueCount<*>>(
        properties = object : ObjectPropertyDefinitions<ValueCount<*>>() {
            val of by addReference(ValueCount<*>::reference)
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ValueCount<*>>) = ValueCount<Comparable<Any>>(
            reference = values(1u)
        )
    }
}
