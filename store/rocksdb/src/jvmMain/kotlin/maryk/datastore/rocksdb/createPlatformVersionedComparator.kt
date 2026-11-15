package maryk.datastore.rocksdb

import maryk.datastore.rocksdb.processors.VersionedComparator
import maryk.rocksdb.AbstractComparator
import maryk.rocksdb.ComparatorOptions
import org.rocksdb.NativeComparatorWrapper

internal actual fun createPlatformVersionedComparator(options: ComparatorOptions, keySize: Int): AbstractComparator =
    if (keySize >= 8 && RocksDBNativeComparatorLibrary.available) NativeVersionedComparator.create(keySize)
    else VersionedComparator(options, keySize)

internal class NativeVersionedComparator private constructor(keySize: Int) : NativeComparatorWrapper() {
    init { setKeySize(nativeHandle_, keySize) }
    override fun initializeNative(vararg handles: Long): Long = createNative()
    private external fun createNative(): Long
    private external fun setKeySize(handle: Long, keySize: Int)
    private external fun compareNative(handle: Long, a: ByteArray, b: ByteArray): Int

    internal fun compareBytes(a: ByteArray, b: ByteArray): Int {
        check(isOwningHandle()) { "Comparator is closed" }
        return compareNative(nativeHandle_, a, b)
    }

    companion object {
        fun create(keySize: Int): NativeVersionedComparator {
            require(keySize >= 8)
            check(RocksDBNativeComparatorLibrary.available)
            return NativeVersionedComparator(keySize)
        }
    }
}
