package maryk.datastore.foundationdb

import maryk.core.exceptions.RequestException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A request kept its FoundationDB snapshot longer than the supported MVCC window.
 * Retry the request with a smaller page; scan cursors can resume completed pages.
 */
class FoundationDBSnapshotExpiredException : RequestException(
    "FoundationDB read snapshot expired; retry with a smaller page using the scan cursor",
)

internal data class FoundationDBReadContext(
    val readVersion: Long,
    private val startedAt: TimeMark = TimeSource.Monotonic.markNow(),
) {
    fun requireUsable() {
        if (startedAt.elapsedNow() >= maxSnapshotAge) {
            throw FoundationDBSnapshotExpiredException()
        }
    }

    private companion object {
        val maxSnapshotAge = 4.seconds
    }
}
