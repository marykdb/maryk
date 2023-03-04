package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationRequestType.DateHistogramType
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.enum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.DateUnit
import maryk.core.query.DefinedByReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** Bucket all together that are on same date/time for [reference] */
data class DateHistogram<T: Comparable<*>>(
    override val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>,
    val dateUnit: DateUnit,
    val aggregations: Aggregations? = null
) : IsAggregationRequest<T, IsPropertyReference<out T, IsPropertyDefinition<T>, *>, DateHistogramResponse<T>>,
    DefinedByReference<Comparable<*>> {
    override val aggregationType = DateHistogramType

    override fun createAggregator() = DateHistogramAggregator(this)

    @Suppress("unused")
    companion object : SimpleQueryModel<DateHistogram<*>>() {
        val of by addReference(DateHistogram<*>::reference)
        val dateUnit by enum(3u, DateHistogram<*>::dateUnit, enum = DateUnit)
        val aggregations by embedObject(
            index = 2u,
            getter = DateHistogram<*>::aggregations,
            dataModel = { Aggregations.Model },
            alternativeNames = setOf("aggs")
        )

        override fun invoke(values: SimpleObjectValues<DateHistogram<*>>) = DateHistogram<Comparable<Any>>(
            reference = values(1u),
            aggregations = values(2u),
            dateUnit = values(3u)
        )
    }
}
