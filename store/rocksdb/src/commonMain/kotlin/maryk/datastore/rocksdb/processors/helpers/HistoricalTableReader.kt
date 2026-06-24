package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.shared.TypeIndicator
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRange
import maryk.lib.extensions.compare.matchesRangePart
import maryk.rocksdb.ReadOptions

internal class HistoricalTableReader(
    dbAccessor: DBAccessor,
    columnFamilies: HistoricTableColumnFamilies,
    private val readOptions: ReadOptions,
    private val toVersion: ULong
) : AutoCloseable {
    private val iterator: DBIterator = dbAccessor.getIterator(readOptions, columnFamilies.historic.table)
    private val versionBytes = toVersion.toReversedVersionBytes()

    fun <T : Any> getValue(
        keyAndReference: ByteArray,
        handleResult: (ByteArray, Int, Int) -> T?
    ): T? {
        val toSeek = keyAndReference + versionBytes
        positionAtOrSeek(toSeek)
        while (iterator.isValid()) {
            val key = iterator.key()

            if (!key.matchesRangePart(0, keyAndReference)) {
                break
            }

            val versionOffset = key.size - versionBytes.size
            if (versionOffset != keyAndReference.size) {
                iterator.next()
                continue
            }

            if (versionBytes.compareToRange(key, versionOffset) <= 0) {
                val result = iterator.value()
                if (result.isHistoricDeleteMarker()) return null
                return handleResult(result, 0, result.size)
            }
            iterator.next()
        }
        return null
    }

    fun <R : Any> iterateValues(
        keyLength: Int,
        reference: ByteArray,
        handleValue: (ByteArray, Int, Int, ByteArray, Int, Int) -> R?
    ): R? {
        val toSeek = reference + versionBytes
        positionAtOrSeek(toSeek)
        var lastReferenceBytes: ByteArray? = null
        var lastReferenceLength = 0
        while (iterator.isValid()) {
            val referenceBytes = iterator.key()
            if (!referenceBytes.matchesRangePart(0, reference)) {
                break
            }
            val versionOffset = referenceBytes.size - versionBytes.size
            if (versionOffset < reference.size) {
                iterator.next()
                continue
            }
            if (versionBytes.compareToRange(referenceBytes, versionOffset) <= 0) {
                val previousReferenceBytes = lastReferenceBytes
                if (
                    previousReferenceBytes != null &&
                    lastReferenceLength == versionOffset &&
                    referenceBytes.matchesRange(0, previousReferenceBytes, versionOffset, 0, lastReferenceLength)
                ) {
                    iterator.next()
                    continue
                }
                lastReferenceBytes = referenceBytes.copyOf()
                lastReferenceLength = versionOffset

                val value = iterator.value()
                if (value.isHistoricDeleteMarker()) {
                    iterator.next()
                    continue
                }
                handleValue(referenceBytes, keyLength, versionOffset - keyLength, value, 0, value.size)?.let { return it }
            }
            iterator.next()
        }
        return null
    }

    override fun close() {
        iterator.close()
    }

    private fun positionAtOrSeek(target: ByteArray) {
        if (!iterator.isValid()) {
            iterator.seek(target)
            return
        }

        val currentKey = iterator.key()
        when {
            currentKey compareTo target == 0 -> return
            currentKey compareTo target > 0 -> iterator.seek(target)
            !advanceToAtLeast(target) -> iterator.seek(target)
        }
    }

    private fun advanceToAtLeast(target: ByteArray): Boolean {
        repeat(MAX_LINEAR_ADVANCE_STEPS) {
            iterator.next()
            if (!iterator.isValid()) {
                return false
            }
            if (iterator.key() compareTo target >= 0) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val MAX_LINEAR_ADVANCE_STEPS = 8
    }
}

private fun ByteArray.isHistoricDeleteMarker() =
    this.size == 1 && this[0] == TypeIndicator.DeletedIndicator.byte
