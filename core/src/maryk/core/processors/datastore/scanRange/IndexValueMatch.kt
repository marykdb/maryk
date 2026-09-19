package maryk.core.processors.datastore.scanRange

/** Match another index entry for the same record by [toMatch]. */
data class IndexValueMatch(
    val toMatch: ByteArray,
    val partialMatch: Boolean
)
