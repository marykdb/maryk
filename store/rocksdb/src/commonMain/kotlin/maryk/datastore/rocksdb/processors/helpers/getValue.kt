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
    val recordKey = keyAndReference.copyOfRange(0, columnFamilies.keyByteSize)
    val reference = keyAndReference.copyOfRange(columnFamilies.keyByteSize, keyAndReference.size)
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
                } else this.dataStore.withDecryptedValueIfNeeded(columnFamilies.modelId, recordKey, reference, valueBytes, VERSION_BYTE_SIZE, valueLength - VERSION_BYTE_SIZE) { value, offset, length ->
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
                    recordKey,
                    reference,
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

        historicalTableReader?.getValue(keyAndReference) { result, _, _ ->
            val decrypted = if (decryptValue) this.dataStore.decryptValueIfNeeded(columnFamilies.modelId, recordKey, reference, result) else result
            handleResult(decrypted, 0, decrypted.size)
        } ?: HistoricalTableReader(this, columnFamilies, readOptions, toVersion).use { reader ->
            reader.getValue(keyAndReference) { result, _, _ ->
                val decrypted = if (decryptValue) this.dataStore.decryptValueIfNeeded(columnFamilies.modelId, recordKey, reference, result) else result
                handleResult(decrypted, 0, decrypted.size)
            }
        }
    }
}
