@file:Suppress("UNUSED_PARAMETER")

package maryk.datastore.rocksdb

import maryk.rocksdb.AutoCloseable
import maryk.rocksdb.RocksIterator

open class DBIterator(
    internal val rocksIterator: RocksIterator
) : AutoCloseable {
    open fun seek(target: ByteArray) = rocksIterator.seek(target)

    open fun seekForPrev(target: ByteArray) = rocksIterator.seekForPrev(target)

    open fun seekToLast() = rocksIterator.seekToLast()

    open fun isValid() = rocksIterator.isValid()

    open fun next() = rocksIterator.next()

    open fun prev() = rocksIterator.prev()

    open fun key() = rocksIterator.key()

    open fun value() = rocksIterator.value()

    override fun close() {
        rocksIterator.close()
    }
}
