package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.invert
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.convertToValue
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.readReversedVersionBytes
import maryk.lib.extensions.compare.matchPart
import maryk.rocksdb.AutoCloseable
import maryk.rocksdb.ReadOptions

/**
 * Historical index values walker for a RocksDB store.
 * It allows you to get all versioned index values for a given data object by key.
 */
internal class HistoricStoreIndexValuesWalker(
    val columnFamilies: HistoricTableColumnFamilies,
    readOptions: ReadOptions
) {
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
        getter.moveToKey(key, dbAccessor)
        val indexableBytes = indexable.referenceStorageByteArray.bytes

        val keyAndVersionSize = key.size + VERSION_BYTE_SIZE
        do {
            var index = 0
            try {
                val historicIndexReference = ByteArray(
                    indexableBytes.size + indexable.calculateStorageByteLengthForIndex(
                        getter, keyAndVersionSize
                    )
                ).also { bytes ->
                    val writer = { it: Byte -> bytes[index++] = it }
                    indexableBytes.forEach(writer)
                    indexable.writeStorageBytesForIndex(getter, key, writer)
                    val versionIndex = index
                    getter.latestVersion?.writeBytes(writer)
                        ?: throw StorageException("Latest overall version was not set")
                    bytes.invert(versionIndex)
                }
                handleIndexReference(historicIndexReference)
            } catch (e: Throwable) {
                // skip failing index reference generation
            }
        } while (getter.gotoNextVersion())
    }
}

/**
 * A historical values getter which finds the first valid value until [latestVersion]
 * It stores iterators internally for each property so it can advance to the next version if all possible combinations
 * with that version have been captured.
 * It will internally check latestVersion and versionToSkip and if no progression could be made it will return false for
 * gotoNextVersion.
 */
private class HistoricStoreIndexValuesGetter(
    val columnFamilies: HistoricTableColumnFamilies,
    val readOptions: ReadOptions
) : IsValuesGetter, AutoCloseable {
    private val iterableReferenceMap = mutableMapOf<IsPropertyReference<*, *, *>, IterableReference>()

    // Latest version found in current walk
    internal var latestVersion: ULong? = null
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
        val reference = iterableReference.referenceAsBytes
        val keyAndReference = byteArrayOf(*key, *iterableReference.referenceAsBytes)

        // Only seek the first time
        if (iterableReference.lastVersion == null) {
            // Add empty version so iterator works correctly
            iterator.seek(keyAndReference.copyOf(keyAndReference.size + VERSION_BYTE_SIZE))
        }

        // Go to next version if it is the version to read past or not yet set
        if (!iterableReference.isPastBeginning && (iterableReference.lastVersion == null || versionToSkip == iterableReference.lastVersion)) {
            if (iterator.isValid()) {
                val qualifier = iterator.key()

                if (qualifier.matchPart(0, keyAndReference)) {
                    val valueBytes = iterator.value()
                    val historicReference =
                        ByteArray(reference.size + valueBytes.size + key.size + VERSION_BYTE_SIZE)
                    reference.copyInto(historicReference)
                    valueBytes.copyInto(historicReference, reference.size)
                    key.copyInto(historicReference, valueBytes.size + reference.size)
                    qualifier.copyInto(
                        historicReference,
                        valueBytes.size + reference.size + key.size,
                        qualifier.size - VERSION_BYTE_SIZE
                    )

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
                } else {
                    // Past beginning of the value history
                    iterableReference.isPastBeginning = true
                }
            } else {
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
            lastValue.convertToValue(propertyReference, 0, lastValue.size)
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
