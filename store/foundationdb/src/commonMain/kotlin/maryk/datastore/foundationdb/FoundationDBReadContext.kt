package maryk.datastore.foundationdb

import maryk.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Bytes
import maryk.datastore.shared.DeferredReadSnapshot
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

internal class FoundationDBReadContext(
    readVersion: Long? = null,
    val transaction: Transaction? = null,
    private val startedAt: TimeMark = TimeSource.Monotonic.markNow(),
    val readBudget: FoundationDBReadBudget,
    private val readVersionSource: (() -> Long)? = null,
    val retainImplicitSnapshot: Boolean = true,
    val expectedStateBackupRevision: Bytes? = null,
) : DeferredReadSnapshot {
    var pinnedReadVersion: Long? = readVersion
        private set

    val readVersion: Long
        get() = pinnedReadVersion ?: requireNotNull(readVersionSource).invoke().also(::pinReadVersion)

    fun pinReadVersion(version: Long) {
        check(pinnedReadVersion == null || pinnedReadVersion == version) { "Read snapshot changed within one request" }
        pinnedReadVersion = version
    }

    override fun captureSnapshot() { readVersion }

    fun requireUsable() {
        if (startedAt.elapsedNow() >= maxSnapshotAge) {
            throw FoundationDBSnapshotExpiredException()
        }
    }

    private companion object {
        val maxSnapshotAge = 4.seconds
    }
}
