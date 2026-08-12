package maryk.core.query.filters

import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext

/** Maximum number of nested And, Or, and Not filters accepted in one filter path. */
const val MAX_FILTER_NESTING_DEPTH = 64

/** Raised when an untrusted filter exceeds [MAX_FILTER_NESTING_DEPTH]. */
class FilterNestingDepthException : IllegalArgumentException(
    "Filter nesting depth exceeds $MAX_FILTER_NESTING_DEPTH"
)

internal inline fun <T> withFilterNesting(
    context: RequestContext?,
    block: (RequestContext) -> T
): T {
    val filterContext = context ?: RequestContext(DefinitionsContext())
    if (filterContext.filterNestingDepth >= MAX_FILTER_NESTING_DEPTH) {
        throw FilterNestingDepthException()
    }

    filterContext.filterNestingDepth++
    try {
        return block(filterContext)
    } finally {
        filterContext.filterNestingDepth--
    }
}

internal fun checkFilterNesting(filter: IsFilter) {
    val pending = mutableListOf(filter to 0)

    while (pending.isNotEmpty()) {
        val (current, parentDepth) = pending.removeLast()
        if (current !is IsFilterList) continue

        val depth = parentDepth + 1
        if (depth > MAX_FILTER_NESTING_DEPTH) {
            throw FilterNestingDepthException()
        }
        current.filters.forEach { pending += it to depth }
    }
}
