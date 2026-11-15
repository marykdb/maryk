package maryk.datastore.shared

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.Aggregator
import maryk.core.aggregations.ValueByPropertyReference
import maryk.core.aggregations.metric.Average
import maryk.core.aggregations.metric.Max
import maryk.core.aggregations.metric.Min
import maryk.core.aggregations.metric.Stats
import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.ValueCount
import maryk.core.models.IsRootDataModel
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference

private val recordMetrics = setOf(Average::class, Max::class, Min::class, Stats::class, Sum::class, ValueCount::class)

/** Reuse repeated unselected scalar metric reads within one stable record, including missing values. */
fun <DM : IsRootDataModel> DM.createRecordAggregator(
    aggregations: Aggregations,
    select: RootPropRefGraph<DM>?,
): Aggregator {
    val requests = aggregations.namedAggregations.values
    if (select == null || requests.size !in 2..16 || requests.any { it::class !in recordMetrics }) {
        return Aggregator(aggregations)
    }
    if (requests.all { request -> select.properties.any { it === request.reference.propertyDefinition } }) {
        return Aggregator(aggregations)
    }
    var repeated: MutableList<AnyPropertyReference>? = null
    for (request in requests) {
        val reference = request.reference
        if (repeated?.contains(reference) == true) continue
        if (requests.count { it.reference == reference } < 2) continue
        val property = rootScalarReadProperty(reference) ?: continue
        if (select.properties.any { it.index == property.index }) continue
        val references = repeated ?: ArrayList<AnyPropertyReference>().also { repeated = it }
        references.add(reference)
    }
    return repeated?.let { RecordAggregator(aggregations, it) } ?: Aggregator(aggregations)
}

private class RecordAggregator(
    aggregations: Aggregations,
    private val references: List<AnyPropertyReference>,
) : Aggregator(aggregations) {
    override fun aggregate(valueFetcher: ValueByPropertyReference<*>) {
        val values = arrayOfNulls<Any>(references.size)
        val fetched = BooleanArray(references.size)
        super.aggregate { reference ->
            val index = references.indexOf(reference)
            if (index < 0) valueFetcher(reference) else {
                if (!fetched[index]) {
                    values[index] = valueFetcher(reference)
                    fetched[index] = true
                }
                values[index]
            }
        }
    }
}
