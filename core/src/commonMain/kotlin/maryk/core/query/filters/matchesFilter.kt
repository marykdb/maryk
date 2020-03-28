package maryk.core.query.filters

import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.ValueRange

/**
 * Test if values should be filtered based on given [filter]
 */
fun matchesFilter(
    filter: IsFilter?,
    valueMatcher: (AnyPropertyReference, (Any?) -> Boolean) -> Boolean
): Boolean {
    if (filter == null) {
        return true
    }

    when (filter.filterType) {
        FilterType.And -> {
            val and = filter as And
            for (aFilter in and.filters) {
                if (!matchesFilter(aFilter, valueMatcher)) return false
            }
            return true
        }
        FilterType.Or -> {
            val or = filter as Or
            for (aFilter in or.filters) {
                if (matchesFilter(aFilter, valueMatcher)) return true
            }
            return false
        }
        FilterType.Not -> {
            val notFilter = (filter as Not)
            for (aFilter in notFilter.filters) {
                // If internal filter succeeds, then fail
                if (matchesFilter(aFilter, valueMatcher)) return false
            }
            return true
        }
        FilterType.Exists -> {
            val exists = filter as Exists
            for (propRef in exists.references) {
                if (!valueMatcher(propRef) { it != null }) return false
            }
            return true
        }
        FilterType.Equals -> {
            val equals = filter as Equals
            for ((propRef, value) in equals.referenceValuePairs) {
                if (!valueMatcher(propRef) { it == value }) return false
            }
            return true
        }
        FilterType.LessThan -> {
            val lessThan = filter as LessThan
            for ((propRef, value) in lessThan.referenceValuePairs) {
                @Suppress("UNCHECKED_CAST")
                if (!valueMatcher(propRef) { it != null && (value as Comparable<Any>) > it }) return false
            }
            return true
        }
        FilterType.LessThanEquals -> {
            val lessThanEquals = filter as LessThanEquals
            for ((propRef, value) in lessThanEquals.referenceValuePairs) {
                @Suppress("UNCHECKED_CAST")
                if (!valueMatcher(propRef) { it != null && (value as Comparable<Any>) >= it }) return false
            }
            return true
        }
        FilterType.GreaterThan -> {
            val greaterThan = filter as GreaterThan
            for ((propRef, value) in greaterThan.referenceValuePairs) {
                @Suppress("UNCHECKED_CAST")
                if (!valueMatcher(propRef) { it != null && (value as Comparable<Any>) < it }) return false
            }
            return true
        }
        FilterType.GreaterThanEquals -> {
            val greaterThanEquals = filter as GreaterThanEquals
            for ((propRef, value) in greaterThanEquals.referenceValuePairs) {
                @Suppress("UNCHECKED_CAST")
                if (!valueMatcher(propRef) { it != null && (value as Comparable<Any>) <= it }) return false
            }
            return true
        }
        FilterType.Prefix -> {
            val prefixFilter = filter as Prefix
            for ((propRef, prefix) in prefixFilter.referenceValuePairs) {
                if (!valueMatcher(propRef) { it != null && (it as String).startsWith(prefix) }) return false
            }
            return true
        }
        FilterType.Range -> {
            val rangeFilter = filter as Range
            for ((propRef, range) in rangeFilter.referenceValuePairs) {
                @Suppress("UNCHECKED_CAST")
                if (!valueMatcher(propRef) { it != null && (it as Comparable<Any>) in range as ValueRange<Comparable<Any>> }) return false
            }
            return true
        }
        FilterType.RegEx -> {
            val regExFilter = filter as RegEx
            for ((propRef, regEx) in regExFilter.referenceValuePairs) {
                if (!valueMatcher(propRef) { it != null && regEx.matches(it as String) }) return false
            }
            return true
        }
        FilterType.ValueIn -> {
            val valueInFilter = filter as ValueIn
            for ((propRef, values) in valueInFilter.referenceValuePairs) {
                if (!valueMatcher(propRef) { it != null && values.contains(it) }) return false
            }
            return true
        }
    }
}
