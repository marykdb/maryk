package maryk.datastore.rocksdb

import maryk.rocksdb.OptimisticTransactionDB
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Snapshot

internal class RocksDBReadContext private constructor(
    private val db: OptimisticTransactionDB,
    private val snapshot: Snapshot,
    val defaultReadOptions: ReadOptions,
    private val accessor: DBAccessor?,
) : AutoCloseable {
    // Point reads ignore iterator seek flags. All request iterators already bound
    // their ranges, so one immutable snapshot-bound option serves both paths.
    val sequentialReadOptions: ReadOptions get() = defaultReadOptions

    inline fun <T> withAccessor(store: RocksDBDataStore, block: (DBAccessor) -> T): T =
        block(accessor ?: store.readAccessor)

    override fun close() {
        try {
            defaultReadOptions.close()
        } finally {
            db.releaseSnapshot(snapshot)
        }
    }

    companion object {
        fun create(db: OptimisticTransactionDB, accessor: DBAccessor? = null): RocksDBReadContext {
            val snapshot = requireNotNull(db.getSnapshot()) {
                "RocksDB did not return a snapshot"
            }
            var defaultReadOptions: ReadOptions? = null
            try {
                defaultReadOptions = ReadOptions()
                defaultReadOptions.setTotalOrderSeek(true)
                defaultReadOptions.setSnapshot(snapshot)
                return RocksDBReadContext(
                    db,
                    snapshot,
                    defaultReadOptions,
                    accessor,
                )
            } catch (throwable: Throwable) {
                defaultReadOptions?.close()
                db.releaseSnapshot(snapshot)
                throw throwable
            }
        }
    }
}
