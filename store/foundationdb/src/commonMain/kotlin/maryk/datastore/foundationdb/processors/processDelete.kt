package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsMapReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.values.IsValuesGetter
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.clusterlog.ClusterLogDeletion
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import maryk.datastore.foundationdb.processors.helpers.readMapByReference
import maryk.datastore.foundationdb.processors.helpers.readHLCTimestampIfExact
import maryk.datastore.foundationdb.processors.helpers.readSetByReference
import maryk.datastore.foundationdb.processors.helpers.requireVersionedValue
import maryk.datastore.foundationdb.processors.helpers.setLatestVersion
import maryk.datastore.foundationdb.processors.helpers.setValue
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.unwrapFdb
import maryk.datastore.foundationdb.processors.helpers.writeHistoricIndex
import maryk.datastore.foundationdb.processors.helpers.writeHistoricUnique
import maryk.datastore.shared.Cache
import maryk.datastore.shared.helpers.convertToValue
import maryk.datastore.shared.rethrowIfFatal
import maryk.datastore.shared.updates.Update
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.matchesRangePart
import maryk.foundationdb.Transaction
import maryk.foundationdb.Range as FDBRange

/** Process the deletion of the value at [key]/[version] from FoundationDB */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processDelete(
    tableDirs: IsTableDirectories,
    dataModel: DM,
    key: Key<DM>,
    version: HLC,
    dbIndex: UInt,
    hardDelete: Boolean,
    cache: Cache,
): IsDeleteResponseStatus<DM> = try {
    var updateToEmit: Update<DM>? = null
    runTransaction { tr ->
        val keyBytes = key.bytes
        val exists = tr.get(packKey(tableDirs.keysPrefix, keyBytes)).awaitResult()
            ?.readHLCTimestampIfExact() != null

        if (!exists) {
            return@runTransaction DoesNotExist(key)
        }

        val versionBytes = HLC.toStorageBytes(version)

        // Values getter to read current values by property reference for index computation
        val valuesGetter = object : IsValuesGetter {
            private val valueCache = HashMap<IsPropertyReference<*, *, *>, Any?>(8)

            override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
                propertyReference: IsPropertyReference<T, D, C>
            ): T? {
                if (valueCache.containsKey(propertyReference)) {
                    @Suppress("UNCHECKED_CAST")
                    return valueCache[propertyReference] as T?
                }

                val value = if (propertyReference is IsMapReference<*, *, *, *>) {
                    @Suppress("UNCHECKED_CAST")
                    tr.readMapByReference(
                        tableDirs.tablePrefix,
                        keyBytes,
                        propertyReference as IsMapReference<Any, Any, IsPropertyContext, *>,
                        this@processDelete::decryptValueIfNeeded
                    ) as T?
                } else if (propertyReference is SetReference<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    tr.readSetByReference(
                        tableDirs.tablePrefix,
                        keyBytes,
                        propertyReference as SetReference<Any, IsPropertyContext>
                    ) as T?
                } else {
                    @Suppress("UNCHECKED_CAST")
                    tr.getValue(
                        tableDirs,
                        null,
                        keyBytes,
                        propertyReference.toStorageByteArray(),
                        decryptValue = this@processDelete::decryptValueIfNeeded
                    ) { valueBytes, offset, length ->
                        valueBytes.convertToValue(propertyReference, offset, length) as T?
                    }
                }

                valueCache[propertyReference] = value
                return value
            }
        }

        // Remove unique index values: scan current stored values for this key and delete uniques
        run {
            val prefix = packKey(tableDirs.tablePrefix, key.bytes)
            val range = FDBRange.startsWith(prefix)
            val iterator = tr.getRange(range).iterator()
            while (iterator.hasNext()) {
                val kv = iterator.nextBlocking()
                val fullKey = kv.key
                // Skip meta latest-version entry
                if (fullKey.size == prefix.size) continue
                val referenceLength = fullKey.size - prefix.size

                // Skip soft delete indicator entries here; they are handled below
                if (referenceLength == 1 && fullKey[prefix.size] == SOFT_DELETE_INDICATOR) continue

                // Map reference bytes to property reference; if unique -> delete unique index
                var idx = 0
                val propRef = dataModel.getPropertyReferenceByStorageBytes(
                    referenceLength,
                    { fullKey[prefix.size + idx++] }
                )
                val def = propRef.comparablePropertyDefinition
                if (def is IsComparableDefinition<*, *> && def.unique) {
                    val reference = propRef.toStorageByteArray()
                    val value = kv.value
                    // Stored as (version || value)
                    requireVersionedValue(value)
                    val uniqueValue = withDecryptedValueIfNeeded(
                        value,
                        VERSION_BYTE_SIZE,
                        value.size - VERSION_BYTE_SIZE
                    ) { plainValue, offset, length ->
                        mapUniqueValueBytes(dbIndex, reference, plainValue, offset, length)
                    }
                    val uniqueRef = combineToByteArray(reference, uniqueValue)

                    // Delete current unique entry
                    tr.clear(packKey(tableDirs.uniquePrefix, uniqueRef))

                    if (tableDirs is HistoricTableDirectories) {
                        if (hardDelete) {
                            val historicPrefix = packKey(tableDirs.historicUniquePrefix, encodeZeroFreeUsing01(uniqueRef))
                            tr.clear(FDBRange.startsWith(historicPrefix))
                        } else {
                            // For soft delete, append a historic tombstone so history reflects the change
                            writeHistoricUnique(tr, tableDirs, key.bytes, uniqueRef, versionBytes)
                        }
                    }
                }
            }

            if (hardDelete) {
                for (reference in getUniqueIndices(dbIndex, tableDirs.uniquePrefix)) {
                    deleteCurrentUniqueIndexEntryForKey(
                        tr,
                        tableDirs,
                        reference,
                        key.bytes,
                        versionBytes
                    )
                }
            }
        }

        // Delete indexed values
        dataModel.Meta.indexes?.let { indexes ->
            val historicWalker = if (hardDelete && tableDirs is HistoricTableDirectories) {
                HistoricStoreIndexValuesWalker(tableDirs)
            } else null

            indexes.forEach { indexable ->
                val indexReference = indexable.referenceStorageByteArray.bytes
                indexable.toStorageByteArraysForIndex(valuesGetter, key.bytes).forEach { valueAndKey ->
                    // Delete current index entry
                    tr.clear(packKey(tableDirs.indexPrefix, indexReference, valueAndKey))

                    if (!hardDelete && tableDirs is HistoricTableDirectories) {
                        // Non-hard delete: write a deletion marker into historic index
                        writeHistoricIndex(tr, tableDirs, indexReference, valueAndKey, versionBytes, EMPTY_BYTEARRAY)
                    }
                }

                if (hardDelete && tableDirs is HistoricTableDirectories) {
                    historicWalker?.walkHistoricalValuesForIndexKeys(key.bytes, tr, indexable) { valueAndKey, historicVersion ->
                        val encodedQualifier = encodeZeroFreeUsing01(combineToByteArray(indexReference, valueAndKey))
                        tr.clear(
                            packVersionedKey(
                                tableDirs.historicIndexPrefix,
                                encodedQualifier,
                                version = HLC.toStorageBytes(HLC(historicVersion))
                            )
                        )
                    }
                }
            }
        }

        if (hardDelete) {
            cache.delete(dbIndex, key)

            // Delete key and all values under it (including historic if present)
            tr.clear(packKey(tableDirs.keysPrefix, key.bytes))
            tr.clear(FDBRange.startsWith(packKey(tableDirs.tablePrefix, key.bytes)))
            if (tableDirs is HistoricTableDirectories) {
                tr.clear(FDBRange.startsWith(packKey(tableDirs.historicTablePrefix, key.bytes)))
            }
        } else {
            // Soft delete: mark latest version and set soft-delete indicator
            setLatestVersion(tr, tableDirs, key.bytes, versionBytes)
            setValue(
                tr,
                tableDirs,
                key.bytes,
                byteArrayOf(SOFT_DELETE_INDICATOR),
                versionBytes,
                byteArrayOf(TRUE)
            )
        }

        tableDirs.updateHistoryPrefix?.let { prefix ->
            tr.set(packKey(prefix, version.timestamp.toReversedVersionBytes(), key.bytes), if (hardDelete) byteArrayOf(1) else EMPTY_BYTEARRAY)
        }

        updateToEmit = Update.Deletion(dataModel, key, version.timestamp, hardDelete)

        clusterUpdateLog?.append(
            tr = tr,
            modelId = dbIndex,
            update = ClusterLogDeletion(Bytes(key.bytes), version.timestamp, hardDelete),
        )

        DeleteSuccess(version.timestamp)
    }.also {
        emitUpdate(updateToEmit)
    }
} catch (e: Throwable) {
    e.rethrowIfFatal()
    val cause = e.unwrapFdb()
    ServerFail(cause.toString(), cause)
}

private fun deleteCurrentUniqueIndexEntryForKey(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    reference: ByteArray,
    key: ByteArray,
    versionBytes: ByteArray
) {
    val prefix = packKey(tableDirs.uniquePrefix, reference)
    val iterator = tr.getRange(FDBRange.startsWith(prefix)).iterator()

    while (iterator.hasNext()) {
        val kv = iterator.nextBlocking()
        if (
            kv.value.size == VERSION_BYTE_SIZE + key.size &&
            kv.value.matchesRangePart(VERSION_BYTE_SIZE, key)
        ) {
            tr.clear(kv.key)
            val uniqueRef = kv.key.copyOfRange(tableDirs.uniquePrefix.size, kv.key.size)
            writeHistoricUnique(tr, tableDirs, key, uniqueRef, versionBytes)
            break
        }
    }
}
