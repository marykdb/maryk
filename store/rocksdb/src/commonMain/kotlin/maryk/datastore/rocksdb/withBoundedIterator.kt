package maryk.datastore.rocksdb

import maryk.rocksdb.ColumnFamilyHandle
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Slice

/** Bounds use this column family's comparator order. Snapshot ownership stays with [source]. */
@Suppress("DEPRECATION") // Preserve caller settings for the legacy range-tombstone option.
internal inline fun <T> DBAccessor.withBoundedIterator(
    source: ReadOptions,
    family: ColumnFamilyHandle,
    lower: ByteArray?,
    upper: ByteArray?,
    block: (DBIterator) -> T,
): T = lower?.let(::Slice).use { lowerSlice ->
    upper?.let(::Slice).use { upperSlice ->
        ReadOptions().use { options ->
            options.setTotalOrderSeek(true)
            options.setFillCache(source.fillCache())
            options.setVerifyChecksums(source.verifyChecksums())
            options.setReadTier(source.readTier())
            options.setTailing(source.tailing())
            options.setReadaheadSize(source.readaheadSize())
            options.setAsyncIo(source.asyncIo())
            options.setDeadline(source.deadline())
            options.setIoTimeout(source.ioTimeout())
            options.setBackgroundPurgeOnIteratorCleanup(source.backgroundPurgeOnIteratorCleanup())
            options.setIgnoreRangeDeletions(source.ignoreRangeDeletions())
            options.setMaxSkippableInternalKeys(source.maxSkippableInternalKeys())
            options.setPinData(source.pinData())
            source.snapshot()?.let { options.setSnapshot(it) }
            lowerSlice?.let { options.setIterateLowerBound(it) }
            upperSlice?.let { options.setIterateUpperBound(it) }
            getIterator(options, family).use(block)
        }
    }
}

/** Unsigned bytewise prefix successor. All-FF and empty prefixes have no finite upper bound. */
internal fun bytewisePrefixLimit(prefix: ByteArray): ByteArray? {
    for (index in prefix.lastIndex downTo 0) {
        val value = prefix[index].toInt() and 0xff
        if (value != 0xff) return prefix.copyOf(index + 1).also { it[index] = (value + 1).toByte() }
    }
    return null
}
