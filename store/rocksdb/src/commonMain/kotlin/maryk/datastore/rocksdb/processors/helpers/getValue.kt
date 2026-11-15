package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.RequestException
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.shared.isStoredPropertyDeleteMarker
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRangePart
import maryk.lib.recyclableByteArray
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.rocksDBNotFound

/**
 * Get a value for a [keyAndReference] from [columnFamilies] with [readOptions].
 * Depending on if [toVersion] is set it will be retrieved from the historic or current table.
 */
internal fun <T: Any> DBAccessor.getValue(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    toVersion: ULong?,
    keyAndReference: ByteArray,
    historicalTableReader: HistoricalTableReader? = null,
    decryptValue: Boolean = true,
    handleResult: (ByteArray, Int, Int) -> T?
): T? {
    return if (toVersion == null) {
        val valueLength = this.get(columnFamilies.table, readOptions, keyAndReference, recyclableByteArray)

        when {
            valueLength == rocksDBNotFound -> null
            valueLength > recyclableByteArray.size -> {
                val valueBytes = this.get(columnFamilies.table, readOptions, keyAndReference)!!
                requireVersionedValueSize(valueLength)
                if (!decryptValue) {
                    if (isStoredPropertyDeleteMarker(valueBytes, VERSION_BYTE_SIZE, valueLength - VERSION_BYTE_SIZE)) null
                    else handleResult(valueBytes, VERSION_BYTE_SIZE, valueLength - VERSION_BYTE_SIZE)
                } else this.dataStore.withDecryptedValueIfNeeded(columnFamilies.modelId,
                    { keyAndReference.copyOfRange(0, columnFamilies.keyByteSize) },
                    { keyAndReference.copyOfRange(columnFamilies.keyByteSize, keyAndReference.size) }, valueBytes, VERSION_BYTE_SIZE, valueLength - VERSION_BYTE_SIZE) { value, offset, length ->
                    if (isStoredPropertyDeleteMarker(value, offset, length)) null else handleResult(value, offset, length)
                }
            }
            else -> {
                requireVersionedValueSize(valueLength)
                if (!decryptValue) {
                    if (isStoredPropertyDeleteMarker(recyclableByteArray, VERSION_BYTE_SIZE, valueLength - VERSION_BYTE_SIZE)) null
                    else handleResult(recyclableByteArray, VERSION_BYTE_SIZE, valueLength - VERSION_BYTE_SIZE)
                } else this.dataStore.withDecryptedValueIfNeeded(
                    columnFamilies.modelId,
                    { keyAndReference.copyOfRange(0, columnFamilies.keyByteSize) },
                    { keyAndReference.copyOfRange(columnFamilies.keyByteSize, keyAndReference.size) },
                    recyclableByteArray,
                    VERSION_BYTE_SIZE,
                    valueLength - VERSION_BYTE_SIZE
                ) { value, offset, length ->
                    if (isStoredPropertyDeleteMarker(value, offset, length)) null else handleResult(value, offset, length)
                }
            }
        }
    } else {
        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("Cannot use toVersion on a non historic table")
        }

        fun read(reader: HistoricalTableReader): T? = reader.getValue(keyAndReference) { result, offset, length ->
            if (decryptValue) {
                dataStore.withDecryptedValueIfNeeded(columnFamilies.modelId,
                    { keyAndReference.copyOfRange(0, columnFamilies.keyByteSize) },
                    { keyAndReference.copyOfRange(columnFamilies.keyByteSize, keyAndReference.size) }, result, offset, length, handleResult)
            } else {
                handleResult(result, offset, length)
            }
        }
        if (historicalTableReader != null) {
            read(historicalTableReader)
        } else HistoricalTableReader(this, columnFamilies, readOptions, toVersion).use { reader ->
            read(reader)
        }
    }
}
