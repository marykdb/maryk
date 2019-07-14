package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationRequestType.MinType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** Finds the minimum value for [reference] */
data class Min<T: Comparable<T>>(
    override val reference: IsPropertyReference<out T, *, *>
) : IsAggregationRequest<T, IsPropertyReference<out T, *, *>, MinResponse<T>> {
    override val aggregationType = MinType

    override fun createAggregator() =
        MinAggregator(this)

    companion object : SimpleQueryDataModel<Min<*>>(
        properties = object : ObjectPropertyDefinitions<Min<*>>() {
            init {
                DefinedByReference.addReference(this, Min<*>::reference, name = "of")
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Min<*>>) = Min<Comparable<Any>>(
            reference = values(1u)
        )
    }
}