package maryk.datastore.rocksdb

import org.rocksdb.RocksIterator

open class DBIterator(
    internal val rocksIterator: RocksIterator
) : AutoCloseable {
    /**
     * Position at the first entry in the source whose key is that or
     * past target.
     *
     * The iterator is valid after this call if the source contains
     * a key that comes at or past target.
     *
     * @param target byte array describing a key or a
     * key prefix to seek for.
     */
    open fun seek(target: ByteArray) = rocksIterator.seek(target)

    /**
     * Position at the first entry in the source whose key is that or
     * before target.
     *
     * The iterator is valid after this call if the source contains
     * a key that comes at or before target.
     *
     * @param target byte array describing a key or a
     * key prefix to seek for.
     */
    open fun seekForPrev(target: ByteArray) = rocksIterator.seekForPrev(target)

    /**
     * Position at the last entry in the source.  The iterator is
     * valid after this call if the source is not empty.
     */
    open fun seekToLast() = rocksIterator.seekToLast()

    /**
     * An iterator is either positioned at an entry, or
     * not valid.  This method returns true if the iterator is valid.
     *
     * @return true if iterator is valid.
     */
    open fun isValid() = rocksIterator.isValid()

    /**
     * Moves to the next entry in the source.  After this call, Valid() is
     * true if the iterator was not positioned at the last entry in the source.
     *
     * REQUIRES: [.isValid]
     */
    open fun next() = rocksIterator.next()

    /**
     * Moves to the previous entry in the source.  After this call, Valid() is
     * true if the iterator was not positioned at the first entry in source.
     *
     * REQUIRES: [.isValid]
     */
    open fun prev() = rocksIterator.prev()

    /**
     * Return the key for the current entry.  The underlying storage for
     * the returned slice is valid only until the next modification of
     * the iterator.
     *
     * REQUIRES: [.isValid]
     *
     * @return key for the current entry.
     */
    open fun key(): ByteArray = rocksIterator.key()

    /**
     * Return the value for the current entry.  The underlying storage for
     * the returned slice is valid only until the next modification of
     * the iterator.
     *
     * REQUIRES: !AtEnd() &amp;&amp; !AtStart()
     * @return value for the current entry.
     */
    open fun value(): ByteArray = rocksIterator.value()

    override fun close() {
        rocksIterator.close()
    }
}
