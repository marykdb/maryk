package maryk.datastore.rocksdb

import maryk.rocksdb.OptimisticTransactionDB
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Snapshot

internal class RocksDBReadContext private constructor(
    private val db: OptimisticTransactionDB,
    private val snapshot: Snapshot,
    val defaultReadOptions: ReadOptions,
    val sequentialReadOptions: ReadOptions,
) : AutoCloseable {
    override fun close() {
        try {
            sequentialReadOptions.close()
        } finally {
            try {
                defaultReadOptions.close()
            } finally {
                db.releaseSnapshot(snapshot)
            }
        }
    }

    companion object {
        fun create(db: OptimisticTransactionDB): RocksDBReadContext {
            val snapshot = requireNotNull(db.getSnapshot()) {
                "RocksDB did not return a snapshot"
            }
            var defaultReadOptions: ReadOptions? = null
            var sequentialReadOptions: ReadOptions? = null
            try {
                defaultReadOptions = ReadOptions()
                defaultReadOptions.setPrefixSameAsStart(true)
                defaultReadOptions.setSnapshot(snapshot)
                sequentialReadOptions = ReadOptions()
                sequentialReadOptions.setSnapshot(snapshot)
                return RocksDBReadContext(
                    db,
                    snapshot,
                    defaultReadOptions,
                    sequentialReadOptions,
                )
            } catch (throwable: Throwable) {
                sequentialReadOptions?.close()
                defaultReadOptions?.close()
                db.releaseSnapshot(snapshot)
                throw throwable
            }
        }
    }
}
