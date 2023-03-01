package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationRequestType.ValueCountType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** Counts all values defined for [reference] */
data class ValueCount<T: Comparable<T>>(
    override val reference: IsPropertyReference<T, IsPropertyDefinition<T>, *>
) : IsAggregationRequest<T, IsPropertyReference<T, IsPropertyDefinition<T>, *>, ValueCountResponse<T>> {
    override val aggregationType = ValueCountType

    override fun createAggregator() =
        ValueCountAggregator(this)

    companion object : SimpleQueryModel<ValueCount<*>>() {
        val of by addReference(ValueCount<*>::reference)

        override fun invoke(values: SimpleObjectValues<ValueCount<*>>) = ValueCount<Comparable<Any>>(
            reference = values(1u)
        )
    }
}
