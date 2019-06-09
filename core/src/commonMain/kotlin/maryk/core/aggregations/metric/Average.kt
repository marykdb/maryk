package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationRequestType.AverageType
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** Finds the average value for [reference] */
data class Average<T: Comparable<T>>(
    override val reference: IsPropertyReference<out T, *, *>
) : IsAggregationRequest<T, IsPropertyReference<out T, *, *>, AverageResponse<T>> {
    override val aggregationType = AverageType

    override fun createAggregator() =
        AverageAggregator(this)

    companion object : SimpleQueryDataModel<Average<*>>(
        properties = object : ObjectPropertyDefinitions<Average<*>>() {
            init {
                DefinedByReference.addReference(this, Average<*>::reference, name = "of")
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Average<*>>) = Average<Comparable<Any>>(
            reference = values(1u)
        )
    }
}
