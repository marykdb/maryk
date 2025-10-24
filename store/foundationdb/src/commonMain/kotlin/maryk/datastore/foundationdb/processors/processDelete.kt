package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.values.IsValuesGetter
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.setLatestVersion
import maryk.datastore.foundationdb.processors.helpers.setValue
import maryk.datastore.foundationdb.processors.helpers.writeHistoricIndex
import maryk.datastore.foundationdb.processors.helpers.writeHistoricUnique
import maryk.datastore.shared.Cache
import maryk.datastore.shared.helpers.convertToValue
import maryk.datastore.shared.updates.Update
import maryk.lib.bytes.combineToByteArray
import com.apple.foundationdb.Range as FDBRange

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
        val exists = tr.get(packKey(tableDirs.keysPrefix, key.bytes)).awaitResult() != null

        if (!exists) {
            return@runTransaction DoesNotExist(key)
        }

        val versionBytes = HLC.toStorageBytes(version)

        // Values getter to read current values by property reference for index computation
        val valuesGetter = object : IsValuesGetter {
            override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
                propertyReference: IsPropertyReference<T, D, C>
            ): T? {
                @Suppress("UNCHECKED_CAST")
                return tr.getValue(tableDirs, null, combineToByteArray(key.bytes, propertyReference.toStorageByteArray())) { valueBytes, offset, length ->
                    valueBytes.convertToValue(propertyReference, offset, length) as T?
                }
            }
        }

        // Remove unique index values: scan current stored values for this key and delete uniques
        run {
            val prefix = packKey(tableDirs.tablePrefix, key.bytes)
            val range = FDBRange.startsWith(prefix)
            val kvs = tr.getRange(range).asList().awaitResult()
            for (kv in kvs) {
                val fullKey = kv.key
                // Skip meta latest-version entry
                if (fullKey.size == prefix.size) continue

                val reference = fullKey.copyOfRange(prefix.size, fullKey.size)

                // Skip soft delete indicator entries here; they are handled below
                if (reference.size == 1 && reference[0] == SOFT_DELETE_INDICATOR) continue

                // Map reference bytes to property reference; if unique -> delete unique index
                var idx = 0
                val propRef = dataModel.getPropertyReferenceByStorageBytes(reference.size, { reference[idx++] })
                val def = propRef.comparablePropertyDefinition
                if (def is IsComparableDefinition<*, *> && def.unique) {
                    val value = kv.value
                    // Stored as (version || value)
                    val valueBytes = value.copyOfRange(VERSION_BYTE_SIZE, value.size)
                    val uniqueRef = combineToByteArray(reference, valueBytes)

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
        }

        // Delete indexed values
        dataModel.Meta.indexes?.let { indexes ->
            indexes.forEach { indexable ->
                val indexReference = indexable.referenceStorageByteArray.bytes
                val valueAndKey = indexable.toStorageByteArrayForIndex(valuesGetter, key.bytes)
                    ?: return@forEach // skip if no complete values are found

                // Delete current index entry
                tr.clear(packKey(tableDirs.indexPrefix, indexReference, valueAndKey))

                if (hardDelete && tableDirs is HistoricTableDirectories) {
                    // Hard delete: clean all historic index entries with this prefix
                    val histPrefix = packKey(tableDirs.historicIndexPrefix, indexReference, valueAndKey)
                    tr.clear(FDBRange.startsWith(histPrefix))
                } else if (tableDirs is HistoricTableDirectories) {
                    // Non-hard delete: write a deletion marker into historic index
                    writeHistoricIndex(tr, tableDirs, indexReference, valueAndKey, versionBytes, EMPTY_BYTEARRAY)
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

        updateToEmit = Update.Deletion(dataModel, key, version.timestamp, hardDelete)

        DeleteSuccess(version.timestamp)
    }.also {
        emitUpdate(updateToEmit)
    }
} catch (e: Throwable) {
    ServerFail(e.toString(), e)
}
