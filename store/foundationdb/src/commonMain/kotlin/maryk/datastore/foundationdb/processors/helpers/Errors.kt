package maryk.datastore.foundationdb.processors.helpers

private val wrapperNames = setOf(
    "CompletionException",
    "ExecutionException",
)

/**
 * Unwrap nested async/future wrappers to expose the underlying failure.
 * If a FoundationDB exception is present anywhere in the chain, return it;
 * otherwise return the first non-wrapper exception (or the original when no cause exists).
 */
internal fun Throwable.unwrapFdb(): Throwable {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable = this

    // Peel common future wrappers while a cause is available.
    while (current::class.simpleName in wrapperNames && current.cause != null && seen.add(current)) {
        current = current.cause!!
    }

    // Prefer the first FoundationDB exception found deeper in the chain.
    var scan: Throwable? = current
    while (scan != null && seen.add(scan)) {
        val simple = scan::class.simpleName ?: ""
        if (simple.contains("FDB") || simple.contains("FoundationDB")) {
            return scan
        }
        scan = scan.cause
    }

    // Fallback: return the first non-wrapper (or original if nothing else).
    return current
}
