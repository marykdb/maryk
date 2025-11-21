package maryk.datastore.foundationdb.processors

import maryk.foundationdb.Range
import maryk.foundationdb.TransactionContext
import maryk.core.clock.HLC
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.setIndexValue
import maryk.datastore.foundationdb.processors.helpers.writeHistoricIndex
import maryk.datastore.shared.helpers.convertToValue
import maryk.lib.bytes.combineToByteArray

/**
 * Walk all current records and (re)build the specified indexes.
 * - Iterates all keys under `keysPrefix`
 * - For each key, computes index values using the latest stored values
 * - Writes non-historic index entries and corresponding historic entries at the latest version
 */
internal fun walkDataRecordsAndFillIndex(
    tc: TransactionContext,
    tableDirectories: IsTableDirectories,
    indexesToIndex: List<IsIndexable>
) {
    if (indexesToIndex.isEmpty()) return

    tc.run { tr ->
        fun processKey(keyBytes: ByteArray, latestVersion: ByteArray) {
            // Simple values getter to retrieve latest values for the key
            val getter = object : IsValuesGetter {
                override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
                    propertyReference: IsPropertyReference<T, D, C>
                ): T? {
                    val keyAndRef = combineToByteArray(keyBytes, propertyReference.toStorageByteArray())
                    return tr.getValue(tableDirectories, null, keyAndRef) { valueBytes, offset, length ->
                        valueBytes.convertToValue(propertyReference, offset, length) as T?
                    }
                }
            }

            for (indexable in indexesToIndex) {
                val indexRef = indexable.referenceStorageByteArray.bytes
                val valueAndKey = try {
                    indexable.toStorageByteArrayForIndex(getter, keyBytes)
                } catch (_: Throwable) { null }

                if (valueAndKey != null && latestVersion.size >= VERSION_BYTE_SIZE) {
                    // Write current index value and corresponding historic index entry at latest version
                    setIndexValue(tr, tableDirectories, indexRef, valueAndKey, latestVersion)
                }

                // If historic tables available, walk full history and backfill historic index
                if (tableDirectories is HistoricTableDirectories) {
                    val walker = HistoricStoreIndexValuesWalker(tableDirectories)
                    walker.walkHistoricalValuesForIndexKeys(keyBytes, tr, indexable) { historicValueAndKey, versionTs ->
                        val versionBytes = HLC.toStorageBytes(HLC(versionTs))
                        writeHistoricIndex(tr, tableDirectories, indexRef, historicValueAndKey, versionBytes, ByteArray(0))
                    }
                }
            }
        }

        val it = tr.getRange(Range.startsWith(tableDirectories.keysPrefix)).iterator()
        while (it.hasNext()) {
            val kv = it.nextBlocking()
            val fullKey = kv.key
            val keyBytes = fullKey.copyOfRange(tableDirectories.keysPrefix.size, fullKey.size)
            val latestVersion = tr.get(packKey(tableDirectories.tablePrefix, keyBytes)).awaitResult() ?: continue
            processKey(keyBytes, latestVersion)
        }
    }
}
