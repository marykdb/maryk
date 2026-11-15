package maryk.datastore.shared

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.metric.Average
import maryk.core.aggregations.metric.Max
import maryk.core.aggregations.metric.Min
import maryk.core.aggregations.metric.Stats
import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.ValueCount
import maryk.core.models.IsRootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.IsFilterList
import maryk.core.query.filters.IsReferenceAnyPairsFilter
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn

private val scalarFilters = setOf(Equals::class, GreaterThan::class, GreaterThanEquals::class,
    LessThan::class, LessThanEquals::class, Prefix::class, Range::class, RegEx::class, ValueIn::class)
private val scalarAggregations = setOf(Average::class, Max::class, Min::class, Stats::class, Sum::class, ValueCount::class)

/** Null retains full decoding whenever a dependency cannot be proven to be a built-in root scalar. */
fun <DM : IsRootDataModel> DM.requiredReadSelection(
    select: RootPropRefGraph<DM>?,
    filter: IsFilter?,
    aggregations: Aggregations?,
    index: IsIndexable? = null,
): RootPropRefGraph<DM>? {
    if (select == null) return null
    if (filter == null && aggregations == null && index == null) return select
    val properties = LinkedHashMap<UInt, IsDefinitionWrapper<*, *, *, *>>()
    fun add(reference: AnyPropertyReference): Boolean {
        val property = rootScalarReadProperty(reference) ?: return false
        properties[property.index] = property
        return properties.size <= 64
    }
    for (node in select.properties) {
        val property = node as? IsDefinitionWrapper<*, *, *, *> ?: return null
        if (!add(property.ref())) return null
    }
    fun addFilter(current: IsFilter?, depth: Int): Boolean = when {
        current == null -> true
        depth > 32 -> false
        current::class == And::class || current::class == Or::class || current::class == Not::class ->
            (current as IsFilterList).filters.all { addFilter(it, depth + 1) }
        current::class == Exists::class -> (current as Exists).references.all(::add)
        current::class in scalarFilters -> (current as IsReferenceAnyPairsFilter<*>).referenceValuePairs.all { add(it.reference) }
        else -> false
    }
    fun addIndex(current: IsIndexable): Boolean = when (current) {
        is Multiple -> current.references.all(::addIndex)
        is AnyPropertyReference -> add(current)
        else -> false
    }
    if (!addFilter(filter, 0)) return null
    for (aggregation in aggregations?.namedAggregations?.values.orEmpty()) {
        if (aggregation::class !in scalarAggregations || !add(aggregation.reference)) return null
    }
    if (index != null && !addIndex(index)) return null
    return graph { properties.values.toList() }
}

/** Anchor positioning needs only the declared canonical index inputs. */
fun <DM : IsRootDataModel> DM.requiredIndexReadSelection(index: IsIndexable): RootPropRefGraph<DM>? =
    requiredReadSelection(graph { emptyList() }, null, null, index)
