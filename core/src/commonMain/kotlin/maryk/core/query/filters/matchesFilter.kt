package maryk.core.query.filters

import maryk.core.properties.references.MapAnyKeyReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.SetAnyValueReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.ValueRange

/**
 * Test if values should be filtered based on given [filter]
 */
fun matchesFilter(
    filter: IsFilter?,
    valueMatcher: (AnyPropertyReference, (Any?) -> Boolean) -> Boolean,
    normalizer: (AnyPropertyReference, Any?) -> Any? = { _, value -> value },
    searchMatcher: (String, String) -> Boolean = { _, _ -> false }
): Boolean {
    if (filter == null) {
        return true
    }

    when (filter.filterType) {
        FilterType.And -> {
            val and = filter as And
            for (aFilter in and.filters) {
                if (!matchesFilter(aFilter, valueMatcher, normalizer, searchMatcher)) return false
            }
            return true
        }
        FilterType.Or -> {
            val or = filter as Or
            for (aFilter in or.filters) {
                if (matchesFilter(aFilter, valueMatcher, normalizer, searchMatcher)) return true
            }
            return false
        }
        FilterType.Not -> {
            val notFilter = (filter as Not)
            for (aFilter in notFilter.filters) {
                // If internal filter succeeds, then fail
                if (matchesFilter(aFilter, valueMatcher, normalizer, searchMatcher)) return false
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
                val isAnyCollectionReference =
                    propRef is MapAnyValueReference<*, *, *> ||
                        propRef is MapAnyKeyReference<*, *, *> ||
                        propRef is SetAnyValueReference<*, *>

                if (!valueMatcher(propRef) { actualValue ->
                    val normalizedValue = normalizer(propRef, value)
                    if (isAnyCollectionReference && actualValue is Collection<*>) {
                        actualValue.any { transformedEquals(normalizer(propRef, it), normalizedValue) }
                    } else {
                        transformedEquals(normalizer(propRef, actualValue), normalizedValue)
                    }
                }) return false
            }
            return true
        }
        FilterType.LessThan -> {
            val lessThan = filter as LessThan
            for ((propRef, value) in lessThan.referenceValuePairs) {
                @Suppress("UNCHECKED_CAST")
                if (!valueMatcher(propRef) {
                        val normalizedActual = normalizer(propRef, it)
                        val normalizedValue = normalizer(propRef, value)
                        normalizedActual != null && (normalizedValue as Comparable<Any>) > normalizedActual
                    }) return false
            }
            return true
        }
        FilterType.LessThanEquals -> {
            val lessThanEquals = filter as LessThanEquals
            for ((propRef, value) in lessThanEquals.referenceValuePairs) {
                @Suppress("UNCHECKED_CAST")
                if (!valueMatcher(propRef) {
                        val normalizedActual = normalizer(propRef, it)
                        val normalizedValue = normalizer(propRef, value)
                        normalizedActual != null && (normalizedValue as Comparable<Any>) >= normalizedActual
                    }) return false
            }
            return true
        }
        FilterType.GreaterThan -> {
            val greaterThan = filter as GreaterThan
            for ((propRef, value) in greaterThan.referenceValuePairs) {
                @Suppress("UNCHECKED_CAST")
                if (!valueMatcher(propRef) {
                        val normalizedActual = normalizer(propRef, it)
                        val normalizedValue = normalizer(propRef, value)
                        normalizedActual != null && (normalizedValue as Comparable<Any>) < normalizedActual
                    }) return false
            }
            return true
        }
        FilterType.GreaterThanEquals -> {
            val greaterThanEquals = filter as GreaterThanEquals
            for ((propRef, value) in greaterThanEquals.referenceValuePairs) {
                @Suppress("UNCHECKED_CAST")
                if (!valueMatcher(propRef) {
                        val normalizedActual = normalizer(propRef, it)
                        val normalizedValue = normalizer(propRef, value)
                        normalizedActual != null && (normalizedValue as Comparable<Any>) <= normalizedActual
                    }) return false
            }
            return true
        }
        FilterType.Prefix -> {
            val prefixFilter = filter as Prefix
            for ((propRef, prefix) in prefixFilter.referenceValuePairs) {
                if (!valueMatcher(propRef) {
                        val normalizedActual = normalizer(propRef, it)
                        val normalizedPrefix = normalizer(propRef, prefix)
                        when {
                            normalizedActual is Collection<*> && normalizedPrefix is Collection<*> ->
                                normalizedPrefix.all { prefixPart ->
                                    prefixPart is String && normalizedActual.any { it is String && it.startsWith(prefixPart) }
                                }
                            normalizedActual is Collection<*> && normalizedPrefix is String ->
                                normalizedActual.any { it is String && it.startsWith(normalizedPrefix) }
                            normalizedActual is String && normalizedPrefix is Collection<*> ->
                                normalizedPrefix.any { it is String && normalizedActual.startsWith(it) }
                            normalizedActual is String && normalizedPrefix is String ->
                                normalizedActual.startsWith(normalizedPrefix)
                            else -> false
                        }
                    }) return false
            }
            return true
        }
        FilterType.Range -> {
            val rangeFilter = filter as Range
            for ((propRef, range) in rangeFilter.referenceValuePairs) {
                @Suppress("UNCHECKED_CAST")
                if (!valueMatcher(propRef) {
                        val normalizedActual = normalizer(propRef, it) as? Comparable<Any>
                        normalizedActual != null && normalizedActual in range.normalized(propRef, normalizer)
                    }) return false
            }
            return true
        }
        FilterType.RegEx -> {
            val regExFilter = filter as RegEx
            for ((propRef, regEx) in regExFilter.referenceValuePairs) {
                if (!valueMatcher(propRef) {
                        when (val normalizedActual = normalizer(propRef, it)) {
                            is Collection<*> -> normalizedActual.any { it is String && regEx.matches(it) }
                            is String -> regEx.matches(normalizedActual)
                            else -> false
                        }
                    }) return false
            }
            return true
        }
        FilterType.ValueIn -> {
            val valueInFilter = filter as ValueIn
            for ((propRef, values) in valueInFilter.referenceValuePairs) {
                if (!valueMatcher(propRef) {
                        val normalizedActual = normalizer(propRef, it)
                        normalizedActual != null && values.any { value -> transformedEquals(normalizedActual, normalizer(propRef, value)) }
                    }) return false
            }
            return true
        }
        FilterType.Matches -> {
            val matches = filter as Matches
            for ((name, value) in matches.nameValuePairs) {
                if (!searchMatcher(name, value)) return false
            }
            return true
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun ValueRange<*>.normalized(
    propertyReference: AnyPropertyReference,
    normalizer: (AnyPropertyReference, Any?) -> Any?
) = ValueRange(
    from = normalizer(propertyReference, this.from) as Comparable<Any>,
    to = normalizer(propertyReference, this.to) as Comparable<Any>,
    inclusiveFrom = this.inclusiveFrom,
    inclusiveTo = this.inclusiveTo
)

private fun transformedEquals(actual: Any?, expected: Any?): Boolean = when {
    actual is Collection<*> && expected is Collection<*> -> expected.all { expectedValue ->
        actual.any { it == expectedValue }
    }
    actual is Collection<*> -> actual.any { it == expected }
    expected is Collection<*> -> expected.any { it == actual }
    else -> actual == expected
}
