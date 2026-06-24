package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.StorageException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.references.SimpleTypedValueReference
import maryk.core.properties.types.TypedValue
import maryk.core.values.IsValuesGetter
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfExact
import maryk.datastore.foundationdb.processors.helpers.readMapByReference
import maryk.datastore.foundationdb.processors.helpers.readSetByReference
import maryk.datastore.foundationdb.processors.helpers.requireVersionedValue
import maryk.datastore.foundationdb.processors.helpers.setIndexValue
import maryk.datastore.foundationdb.processors.helpers.writeHistoricIndex
import maryk.datastore.shared.readValue
import maryk.datastore.shared.rethrowIfFatal
import maryk.foundationdb.Range
import maryk.foundationdb.TransactionContext

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
                    if (propertyReference is IsMapReference<*, *, *, *>) {
                        @Suppress("UNCHECKED_CAST")
                        return tr.readMapByReference(
                            tableDirectories.tablePrefix,
                            keyBytes,
                            propertyReference as IsMapReference<Any, Any, IsPropertyContext, *>
                        ) as T?
                    } else if (propertyReference is SetReference<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        return tr.readSetByReference(
                            tableDirectories.tablePrefix,
                            keyBytes,
                            propertyReference as SetReference<Any, IsPropertyContext>
                        ) as T?
                    }

                    val valueBytes = tr.get(packKey(tableDirectories.tablePrefix, keyBytes, propertyReference.toStorageByteArray())).awaitResult()
                        ?: return null
                    requireVersionedValue(valueBytes)
                    return valueBytes.convertToValueIfExact(propertyReference, VERSION_BYTE_SIZE, valueBytes.size - VERSION_BYTE_SIZE)
                }
            }

            for (indexable in indexesToIndex) {
                val indexRef = indexable.referenceStorageByteArray.bytes
                val valuesAndKeys = try {
                    indexable.toStorageByteArraysForIndex(getter, keyBytes)
                } catch (error: Throwable) {
                    error.rethrowIfFatal()
                    throw StorageException(
                        "Failed to build index for key ${keyBytes.contentToString()}: ${error.message ?: error::class.simpleName}",
                        error
                    )
                }

                if (latestVersion.readHLCTimestampIfExact() != null) {
                    valuesAndKeys.forEach { valueAndKey ->
                        // Write current index value and corresponding historic index entry at latest version
                        setIndexValue(tr, tableDirectories, indexRef, valueAndKey, latestVersion)
                    }
                }

                // If historic tables available, walk full history and backfill historic index
                if (tableDirectories is HistoricTableDirectories) {
                    val walker = HistoricStoreIndexValuesWalker(tableDirectories)
                    var newerVersionBytes: ByteArray? = null
                    walker.walkHistoricalValuesForIndexKeys(keyBytes, tr, indexable) { historicValueAndKey, versionTs ->
                        newerVersionBytes?.let { versionBytes ->
                            writeHistoricIndex(
                                tr,
                                tableDirectories,
                                indexRef,
                                historicValueAndKey,
                                versionBytes,
                                HISTORIC_REMOVAL_MARKER
                            )
                        }

                        val versionBytes = HLC.toStorageBytes(HLC(versionTs))
                        writeHistoricIndex(tr, tableDirectories, indexRef, historicValueAndKey, versionBytes, EMPTY_BYTEARRAY)
                        newerVersionBytes = versionBytes
                    }
                }
            }
        }

        val it = tr.getRange(Range.startsWith(tableDirectories.keysPrefix)).iterator()
        while (it.hasNext()) {
            val kv = it.nextBlocking()
            val fullKey = kv.key
            if (kv.value.readHLCTimestampIfExact() == null) continue
            val keyPrefixSize = tableDirectories.keysPrefix.size
            val keyBytes = ByteArray(fullKey.size - keyPrefixSize).also { keyBytes ->
                fullKey.copyInto(keyBytes, 0, keyPrefixSize)
            }
            val latestVersion = tr.get(packKey(tableDirectories.tablePrefix, keyBytes)).awaitResult() ?: continue
            processKey(keyBytes, latestVersion)
        }
    }
}

private fun <T : Any, D : IsPropertyDefinition<T>, C : Any> ByteArray.convertToValueIfExact(
    reference: IsPropertyReference<T, D, C>,
    offset: Int = 0,
    length: Int = this.size - offset
): T? {
    var readIndex = offset
    val reader = {
        this[readIndex++]
    }

    return try {
        if (reference is SimpleTypedValueReference<*, *, *>) {
            val typedValue = readValue(reference.parentReference!!.propertyDefinition, reader) {
                length - readIndex + offset
            } as TypedValue<*, *>

            if (readIndex != offset + length || typedValue.type != reference.type) {
                return null
            }

            @Suppress("UNCHECKED_CAST")
            typedValue.value as T?
        } else {
            val value = readValue(reference.comparablePropertyDefinition, reader) {
                length - readIndex + offset
            }

            if (readIndex != offset + length || value == Unit) {
                return null
            }

            @Suppress("UNCHECKED_CAST")
            value as T?
        }
    } catch (error: Throwable) {
        error.rethrowIfFatal()
        null
    }
}
