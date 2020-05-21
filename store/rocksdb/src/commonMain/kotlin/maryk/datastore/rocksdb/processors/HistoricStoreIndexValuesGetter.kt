package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initULong
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
import maryk.lib.extensions.compare.matchPart
import maryk.rocksdb.AutoCloseable
import maryk.rocksdb.ReadOptions
import kotlin.experimental.xor

/**
 * Historical index values walker for a RocksDB store.
 * It allows you to get all versioned index values for a given data object by key.
 */
internal class HistoricStoreIndexValuesWalker(
    val columnFamilies: HistoricTableColumnFamilies,
    private val readOptions: ReadOptions
) {
    private val getter = HistoricStoreIndexValuesGetter(columnFamilies, readOptions)

    /**
     * Walk historical values of [key] for [indexable]
     * Allows you to find all historical index keys for data object at [key]
     * Result is passed to [handleIndexReference] with the index reference and the version
     */
    fun walkHistoricalValuesForIndexKeys(
        key: ByteArray,
        dbAccessor: DBAccessor,
        indexable: IsIndexable,
        handleIndexReference: (ByteArray) -> Unit
    ) {
        getter.moveToKey(key, dbAccessor)
        val indexableBytes = indexable.referenceStorageByteArray.bytes

        var lastVersion: ULong?
        val keyAndVersionSize = key.size + ULong.SIZE_BYTES
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
                    getter.latestOverallVersion?.writeBytes(writer)
                        ?: throw StorageException("Latest overall version not set")
                    bytes.invert(versionIndex)
                }
                handleIndexReference(historicIndexReference)
            } catch (e: Throwable) {
                // skip failing index reference generation
            }

            lastVersion = getter.versionToSkip
            getter.versionToSkip = getter.latestOverallVersion
        } while (getter.versionToSkip != lastVersion)
    }
}

/**
 * A historical values getter which finds the first valid value until [latestOverallVersion]
 * It stores iterators internally for each property so it can advance to the next version if all possible combinations
 * have been captured.
 */
private class HistoricStoreIndexValuesGetter(
    val columnFamilies: HistoricTableColumnFamilies,
    val readOptions: ReadOptions
) : IsValuesGetter, AutoCloseable {
    val iterableReferenceMap = mutableMapOf<IsPropertyReference<*, *, *>, IterableReference>()
    var latestOverallVersion: ULong? = null
    var versionToSkip: ULong? = null

    lateinit var dbAccessor: DBAccessor
    lateinit var key: ByteArray

    /** Set the getter to get the values for [key] */
    fun moveToKey(key: ByteArray, dbAccessor: DBAccessor) {
        this.key = key
        this.dbAccessor = dbAccessor

        iterableReferenceMap.clear()
        latestOverallVersion = null
        versionToSkip = null
    }

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

        if (latestOverallVersion == iterableReference.lastVersion) {
            // Only seek the first time
            if (iterableReference.lastVersion == null) {
                // Add empty version so iterator works correctly
                iterator.seek(keyAndReference.copyOf(keyAndReference.size + 8))
            }

            // Go to next version if it is the version to read past or not yet set
            if (!iterableReference.isPastBeginning && (iterableReference.lastVersion == null || versionToSkip == iterableReference.lastVersion) && iterator.isValid()) {
                val qualifier = iterator.key()

                if (qualifier.matchPart(0, keyAndReference)) {
                    val valueBytes = iterator.value()
                    val historicReference =
                        ByteArray(reference.size + valueBytes.size + key.size + ULong.SIZE_BYTES)
                    reference.copyInto(historicReference)
                    valueBytes.copyInto(historicReference, reference.size)
                    key.copyInto(historicReference, valueBytes.size + reference.size)
                    qualifier.copyInto(
                        historicReference,
                        valueBytes.size + reference.size + key.size,
                        qualifier.size - ULong.SIZE_BYTES
                    )

                    iterableReference.lastValue = valueBytes
                    var readIndex = qualifier.size - ULong.SIZE_BYTES // start at version
                    val lastVersion = initULong({
                        // invert value before reading because is stored inverted
                        qualifier[readIndex++] xor -1
                    })
                    iterableReference.lastVersion = lastVersion
                    latestOverallVersion = latestOverallVersion?.let {
                        maxOf(lastVersion, it)
                    } ?: lastVersion

                    // already go to next value for next read
                    iterator.next()
                } else {
                    // Past beginning of the value history
                    iterableReference.isPastBeginning = true
                }
            }
        }

        if (iterableReference.isPastBeginning) {
            return null
        }

        return iterableReference.lastValue?.let { lastValue ->
            lastValue.convertToValue(propertyReference, 0, lastValue.size)
        }
    }

    override fun close() {
        for ((_, value) in iterableReferenceMap) {
            value.iterator.close()
        }
    }
}

class IterableReference(
    val referenceAsBytes: ByteArray,
    val iterator: DBIterator,
    var lastVersion: ULong? = null,
    var lastValue: ByteArray? = null,
    var isPastBeginning: Boolean = false
)
