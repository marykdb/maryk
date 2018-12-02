package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.ValueRange
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn
import maryk.core.query.requests.IsFetchRequest
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.DeleteState.Deleted

/**
 * Test if [dataRecord] should be filtered based on given FetchRequest
 * Filters on soft deleted state and given filters.
 * Return true if [dataRecord] should be filtered away.
 */
internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> IsFetchRequest<DM, *>.filterData(
    dataRecord: DataRecord<DM, P>
) = when {
    this.filterSoftDeleted && dataRecord.isDeleted is Deleted -> true
    this.filter != null -> doFilter(this.filter as IsFilter, dataRecord)
    else -> false
}

/** Test if [dataRecord] is passing given [filter]. True if filter matches */
internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> doFilter(filter: IsFilter, dataRecord: DataRecord<DM, P>): Boolean {
    when(filter.filterType) {
        FilterType.And -> {
            val and = filter as And
            for (f in and.filters) {
                if(!doFilter(f, dataRecord)) return false
            }
            return true
        }
        FilterType.Or -> {
            val or = filter as Or
            for (f in or.filters) {
                if(doFilter(f, dataRecord)) return true
            }
            return false
        }
        FilterType.Not -> {
            val notFilter = (filter as Not)
            for (aFilter in notFilter.filters) {
                // If internal filter succeeds, then fail
                if (doFilter(aFilter, dataRecord)) return false
            }
            return true
        }
        FilterType.Exists -> {
            val exists = filter as Exists
            for (propRef in exists.references) {
                if (dataRecord[propRef] == null) return false
            }
            return true
        }
        FilterType.Equals -> {
            val equals = filter as Equals
            for ((propRef, value) in equals.referenceValuePairs) {
                if (dataRecord[propRef] != value) return false
            }
            return true
        }
        FilterType.LessThan -> {
            val lessThan = filter as LessThan
            for ((propRef, value) in lessThan.referenceValuePairs) {
                dataRecord[propRef]?.let {
                    @Suppress("UNCHECKED_CAST")
                    if ((value as Comparable<Any>) <= it) return false
                }
            }
            return true
        }
        FilterType.LessThanEquals -> {
            val lessThanEquals = filter as LessThanEquals
            for ((propRef, value) in lessThanEquals.referenceValuePairs) {
                dataRecord[propRef]?.let {
                    @Suppress("UNCHECKED_CAST")
                    if ((value as Comparable<Any>) < it) return false
                }
            }
            return true
        }
        FilterType.GreaterThan -> {
            val greaterThan = filter as GreaterThan
            for ((propRef, value) in greaterThan.referenceValuePairs) {
                dataRecord[propRef]?.let {
                    @Suppress("UNCHECKED_CAST")
                    if ((value as Comparable<Any>) >= it) return false
                }
            }
            return true
        }
        FilterType.GreaterThanEquals -> {
            val greaterThanEquals = filter as GreaterThanEquals
            for ((propRef, value) in greaterThanEquals.referenceValuePairs) {
                dataRecord[propRef]?.let {
                    @Suppress("UNCHECKED_CAST")
                    if ((value as Comparable<Any>) > it) return false
                }
            }
            return true
        }
        FilterType.Prefix -> {
            val prefixFilter = filter as Prefix
            for ((propRef, prefix) in prefixFilter.referenceValuePairs) {
                dataRecord[propRef]?.let {
                    if(!(it).startsWith(prefix)) return false
                }
            }
            return true
        }
        FilterType.Range -> {
            val rangeFilter = filter as Range
            for ((propRef, range) in rangeFilter.referenceRangePairs) {
                dataRecord[propRef]?.let {
                    @Suppress("UNCHECKED_CAST")
                    if ((it as Comparable<Any>) !in range as ValueRange<Comparable<Any>>) return false
                }
            }
            return true
        }
        FilterType.RegEx -> {
            val regExFilter = filter as RegEx
            for ((propRef, regEx) in regExFilter.referenceValuePairs) {
                dataRecord[propRef]?.let {
                    if(!regEx.matches(it)) return false
                }
            }
            return true
        }
        FilterType.ValueIn -> {
            val valueInFilter = filter as ValueIn
            for ((propRef, values) in valueInFilter.referenceValuePairs) {
                dataRecord[propRef]?.let {
                    if(!values.contains(it)) return false
                }
            }
            return true
        }
    }
}
