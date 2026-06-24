package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.StorageException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapAnyKeyReference
import maryk.core.properties.references.SetAnyValueReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.createHistoricIndexKey
import maryk.datastore.rocksdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.helpers.convertToValueOrNull
import maryk.lib.extensions.compare.matchesRangePart
import maryk.lib.exceptions.ParseException
import maryk.rocksdb.ReadOptions

/**
 * Historical index values walker for a RocksDB store.
 * It allows you to get all versioned index values for a given data object by key.
 */
internal class HistoricStoreIndexValuesWalker(
    val columnFamilies: HistoricTableColumnFamilies,
    private val readOptions: ReadOptions
) : AutoCloseable {
    private val getter = HistoricStoreIndexValuesGetter(columnFamilies, readOptions)

    /**
     * Walk historical values of [key] for [indexable] from [dbAccessor]
     * Allows you to find all historical index keys for data object at [key]
     * Result is passed to [handleIndexReference] with the index reference and the version
     * It will walk all index values from new to old.
     */
    fun walkHistoricalValuesForIndexKeys(
        key: ByteArray,
        dbAccessor: DBAccessor,
        indexable: IsIndexable,
        handleIndexReference: (ByteArray) -> Unit
    ) {
        if (indexable is MapAnyKeyReference<*, *, *>) {
            walkMapAnyKeyHistory(key, dbAccessor, indexable, handleIndexReference)
            return
        }
        if (indexable is SetAnyValueReference<*, *>) {
            walkSetAnyValueHistory(key, dbAccessor, indexable, handleIndexReference)
            return
        }

        getter.moveToKey(key, dbAccessor)
        do {
            try {
                val valuesAndKeys = indexable.toStorageByteArraysForIndex(getter, key)
                val version = getter.latestVersion
                    ?: throw StorageException("Latest overall version was not set")
                val reversedVersion = version.toReversedVersionBytes()

                valuesAndKeys.forEach { valueAndKeyBytes ->
                    handleIndexReference(createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, valueAndKeyBytes, reversedVersion))
                }
            } catch (_: ValidationException) {
                // skip historical values no longer valid for the current index
            } catch (_: ParseException) {
                // skip malformed historical values
            } catch (_: StorageException) {
                // skip obsolete historical values which cannot be reconstructed anymore
            } catch (e: DefNotFoundException) {
                throw e
            } catch (_: IndexOutOfBoundsException) {
                // skip malformed historical values
            }
        } while (getter.gotoNextVersion())
    }

    private fun walkMapAnyKeyHistory(
        key: ByteArray,
        dbAccessor: DBAccessor,
        indexable: MapAnyKeyReference<*, *, *>,
        handleIndexReference: (ByteArray) -> Unit
    ) {
        val parentReference = indexable.parentReference ?: return
        val keyAndReference = key + parentReference.toStorageByteArray()
        @Suppress("UNCHECKED_CAST")
        val typedIndexable = indexable as MapAnyKeyReference<Any, Any, *>

        dbAccessor.getIterator(readOptions, columnFamilies.historic.table).use { iterator ->
            iterator.seek(keyAndReference.copyOf(keyAndReference.size + VERSION_BYTE_SIZE))
            val pendingEvents = mutableListOf<Pair<ByteArray, Boolean>>()
            var currentMapKey: Any? = null

            fun flushPendingEvents(mapKey: Any?) {
                if (mapKey == null || pendingEvents.isEmpty()) return

                val indexValueLength = try {
                    typedIndexable.calculateStorageByteLength(mapKey)
                } catch (_: ValidationException) {
                    pendingEvents.clear()
                    return
                } catch (_: ParseException) {
                    pendingEvents.clear()
                    return
                } catch (_: StorageException) {
                    pendingEvents.clear()
                    return
                } catch (e: DefNotFoundException) {
                    throw e
                }
                val indexValueLengthSize = indexValueLength.calculateVarByteLength()
                val valueAndKey = ByteArray(indexValueLength + indexValueLengthSize + key.size).also { bytes ->
                    var writeIndex = 0
                    typedIndexable.writeStorageBytes(mapKey) { bytes[writeIndex++] = it }
                    indexValueLength.writeVarBytes { bytes[writeIndex++] = it }
                    key.copyInto(bytes, writeIndex)
                }

                var present = false
                for (i in pendingEvents.lastIndex downTo 0) {
                    val (reversedVersion, isDelete) = pendingEvents[i]
                    if (isDelete) {
                        present = false
                    } else if (!present) {
                        handleIndexReference(createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, valueAndKey, reversedVersion))
                        present = true
                    }
                }
                pendingEvents.clear()
            }

            while (iterator.isValid()) {
                val qualifier = iterator.key()
                if (!qualifier.matchesRangePart(0, keyAndReference)) {
                    break
                }
                if (qualifier.size < keyAndReference.size + VERSION_BYTE_SIZE) {
                    iterator.next()
                    continue
                }

                val valueBytes = iterator.value()
                val isDelete = valueBytes.size == 1 && valueBytes[0] == TypeIndicator.DeletedIndicator.byte

                try {
                    var readIndex = keyAndReference.size
                    val mapKeyLength = initIntByVar { qualifier[readIndex++] }
                    val mapKey = typedIndexable.readStorageBytes(mapKeyLength) { qualifier[readIndex++] }
                    if (readIndex != qualifier.size - VERSION_BYTE_SIZE) {
                        iterator.next()
                        continue
                    }

                    if (currentMapKey != null && currentMapKey != mapKey) {
                        flushPendingEvents(currentMapKey)
                    }
                    currentMapKey = mapKey

                    val reversedVersion = qualifier.readReversedVersionBytes(
                        offset = qualifier.size - VERSION_BYTE_SIZE
                    ).toReversedVersionBytes()
                    pendingEvents += reversedVersion to isDelete
                } catch (_: ValidationException) {
                    // skip historical values no longer valid for the current index
                } catch (_: ParseException) {
                    // skip malformed historical values
                } catch (_: StorageException) {
                    // skip obsolete historical values which cannot be reconstructed anymore
                } catch (e: DefNotFoundException) {
                    throw e
                } catch (_: IndexOutOfBoundsException) {
                    // skip malformed references
                }
                iterator.next()
            }

            flushPendingEvents(currentMapKey)
        }
    }

    private fun walkSetAnyValueHistory(
        key: ByteArray,
        dbAccessor: DBAccessor,
        indexable: SetAnyValueReference<*, *>,
        handleIndexReference: (ByteArray) -> Unit
    ) {
        val parentReference = indexable.parentReference ?: return
        val keyAndReference = key + parentReference.toStorageByteArray()

        dbAccessor.getIterator(readOptions, columnFamilies.historic.table).use { iterator ->
            iterator.seek(keyAndReference.copyOf(keyAndReference.size + VERSION_BYTE_SIZE))
            while (iterator.isValid()) {
                val qualifier = iterator.key()
                if (!qualifier.matchesRangePart(0, keyAndReference)) {
                    break
                }
                if (qualifier.size < keyAndReference.size + VERSION_BYTE_SIZE) {
                    iterator.next()
                    continue
                }

                val valueBytes = iterator.value()
                if (valueBytes.size == 1 && valueBytes[0] == TypeIndicator.DeletedIndicator.byte) {
                    iterator.next()
                    continue
                }

                try {
                    var readIndex = keyAndReference.size
                    val setItemLength = initIntByVar { qualifier[readIndex++] }
                    @Suppress("UNCHECKED_CAST")
                    val setItem = (indexable as SetAnyValueReference<Any, *>).readStorageBytes(setItemLength) { qualifier[readIndex++] }
                    if (readIndex != qualifier.size - VERSION_BYTE_SIZE) {
                        iterator.next()
                        continue
                    }

                    val reversedVersion = qualifier.readReversedVersionBytes(
                        offset = qualifier.size - VERSION_BYTE_SIZE
                    ).toReversedVersionBytes()
                    val indexValueLength = indexable.calculateStorageByteLength(setItem)
                    val indexValueLengthSize = indexValueLength.calculateVarByteLength()
                    val valueAndKey = ByteArray(indexValueLength + indexValueLengthSize + key.size).also { valueBytes ->
                        var writeIndex = 0
                        indexable.writeStorageBytes(setItem) { valueBytes[writeIndex++] = it }
                        indexValueLength.writeVarBytes { valueBytes[writeIndex++] = it }
                        key.copyInto(valueBytes, writeIndex)
                    }

                    handleIndexReference(createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, valueAndKey, reversedVersion))
                } catch (_: ValidationException) {
                    // skip historical values no longer valid for the current index
                } catch (_: ParseException) {
                    // skip malformed historical values
                } catch (_: StorageException) {
                    // skip obsolete historical values which cannot be reconstructed anymore
                } catch (e: DefNotFoundException) {
                    throw e
                } catch (_: IndexOutOfBoundsException) {
                    // skip malformed references
                }
                iterator.next()
            }
        }
    }

    override fun close() {
        getter.close()
    }
}

/**
 * A historical values getter which finds the first valid value until [latestVersion]
 * It stores iterators internally for each property, so it can advance to the next version if all possible combinations
 * with that version has been captured.
 * It will internally check latestVersion and versionToSkip and if no progression could be made it will return false for
 * gotoNextVersion.
 */
private class HistoricStoreIndexValuesGetter(
    val columnFamilies: HistoricTableColumnFamilies,
    val readOptions: ReadOptions
) : IsValuesGetter, AutoCloseable {
    private val iterableReferenceMap = mutableMapOf<IsPropertyReference<*, *, *>, IterableReference>()

    // Latest version found in current walk
    var latestVersion: ULong? = null
    // Version to skip during this walk
    private var versionToSkip: ULong? = null

    lateinit var dbAccessor: DBAccessor
    lateinit var key: ByteArray

    /** Set the getter to get the values for [key] */
    fun moveToKey(key: ByteArray, dbAccessor: DBAccessor) {
        this.key = key
        this.dbAccessor = dbAccessor

        reset()
    }

    /**
     *  Go to next version in the walker by setting current version to skip.
     *  Don't go to next version if there was no version progression and return false
     *  so looping can stop
     */
    fun gotoNextVersion() = when (this.versionToSkip) {
        this.latestVersion -> { // Reached the end if both versions match
            reset()
            false
        }
        else -> {
            this.versionToSkip = this.latestVersion
            this.latestVersion = null
            true
        }
    }

    /** Reset versions and close iterators for a clean state for a next walk */
    private fun reset() {
        for ((_, value) in iterableReferenceMap) {
            value.iterator.close()
        }
        iterableReferenceMap.clear()
        latestVersion = null
        versionToSkip = null
    }

    /** Get latest property for [propertyReference] before versionToSkip */
    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        val iterableReference = iterableReferenceMap.getOrPut(
            propertyReference
        ) {
            IterableReference(
                propertyReference.toStorageByteArray(),
                dbAccessor.getIterator(readOptions, columnFamilies.historic.table)
            )
        }
        val iterator = iterableReference.iterator
        val keyAndReference = key + iterableReference.referenceAsBytes

        // Only seek the first time
        if (iterableReference.lastVersion == null) {
            // Add empty version so iterator works correctly
            iterator.seek(keyAndReference.copyOf(keyAndReference.size + VERSION_BYTE_SIZE))
        }

        // Go to next version if it is the version to read past or not yet set
        if (!iterableReference.isPastBeginning && (iterableReference.lastVersion == null || versionToSkip == iterableReference.lastVersion)) {
            var foundNextValue = false
            while (iterator.isValid()) {
                val qualifier = iterator.key()

                if (!qualifier.matchesRangePart(0, keyAndReference)) {
                    iterableReference.isPastBeginning = true
                    break
                }

                if (qualifier.size < keyAndReference.size + VERSION_BYTE_SIZE) {
                    iterator.next()
                    continue
                }

                val valueBytes = iterator.value().copyOf()
                iterableReference.lastValue = valueBytes

                val lastVersion = qualifier.readReversedVersionBytes(
                    offset = qualifier.size - VERSION_BYTE_SIZE // start at version
                )

                iterableReference.lastVersion = lastVersion
                latestVersion = latestVersion?.let {
                    maxOf(lastVersion, it)
                } ?: lastVersion

                // already go to next value for next read
                iterator.next()
                foundNextValue = true
                break
            }

            if (!foundNextValue && !iterableReference.isPastBeginning) {
                iterableReference.isPastBeginning = true
            }
        } else {
            // Update latest overall version to reflect cached change
            latestVersion = latestVersion?.let { latest ->
                iterableReference.lastVersion?.let { last ->
                    maxOf(last, latest)
                } ?: latest
            } ?: iterableReference.lastVersion
        }

        if (iterableReference.isPastBeginning) {
            return null
        }

        return iterableReference.lastValue?.let { lastValue ->
            lastValue.convertToValueOrNull(propertyReference, 0, lastValue.size)
        }
    }

    override fun close() {
        reset()
    }
}

class IterableReference(
    val referenceAsBytes: ByteArray,
    val iterator: DBIterator,
    var lastVersion: ULong? = null,
    var lastValue: ByteArray? = null,
    var isPastBeginning: Boolean = false
)
