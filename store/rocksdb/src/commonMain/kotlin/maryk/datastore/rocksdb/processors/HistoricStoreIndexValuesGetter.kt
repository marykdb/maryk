package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initULong
import maryk.core.extensions.bytes.invert
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.values.IsValuesGetter
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.convertToValue
import maryk.lib.extensions.compare.matchPart
import maryk.rocksdb.AutoCloseable
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksIterator
import maryk.rocksdb.Transaction
import kotlin.experimental.xor

/** Reads historical index values from the RocksDB store. */
class HistoricStoreIndexValuesWalker(
    val columnFamilies: HistoricTableColumnFamilies,
    private val readOptions: ReadOptions
) {
    fun walkIndexHistory(
        key: Key<*>,
        transaction: Transaction,
        indexable: IsIndexable,
        indexableBytes: ByteArray,
        handleIndexReference: (ByteArray) -> Unit
    ) {
        val getter = HistoricStoreIndexValuesGetter(
            columnFamilies, transaction, readOptions, key
        )

        var lastVersion: ULong?
        val keyAndVersionSize = key.bytes.size + ULong.SIZE_BYTES
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
                    indexable.writeStorageBytesForIndex(getter, key.bytes, writer)
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

private class HistoricStoreIndexValuesGetter(
    val columnFamilies: HistoricTableColumnFamilies,
    var transaction: Transaction,
    val readOptions: ReadOptions,
    var key: Key<*>
) : IsValuesGetter, AutoCloseable {
    val iterableReferenceMap = mutableMapOf<IsPropertyReference<*, *, *>, IterableReference>()
    var latestOverallVersion: ULong? = null
    var versionToSkip: ULong? = null

    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        val iterableReference = iterableReferenceMap.getOrPut(
            propertyReference
        ) {
            IterableReference(
                propertyReference.toStorageByteArray(),
                transaction.getIterator(readOptions, columnFamilies.historic.table)
            )
        }
        val iterator = iterableReference.iterator
        val reference = iterableReference.referenceAsBytes
        val keyAndReference = byteArrayOf(*key.bytes, *iterableReference.referenceAsBytes)

        if (latestOverallVersion == iterableReference.lastVersion) {
            // Only seek the first time
            if (iterableReference.lastVersion == null) {
                iterator.seek(keyAndReference)
            }

            // Go to next version if it is the version to read past or not yet set
            if (!iterableReference.isPastBeginning && (iterableReference.lastVersion == null || versionToSkip == iterableReference.lastVersion)) {
                val qualifier = iterator.key()

                if (qualifier.matchPart(0, keyAndReference)) {
                    val valueBytes = iterator.value()
                    val historicReference =
                        ByteArray(reference.size + valueBytes.size + key.bytes.size + ULong.SIZE_BYTES)
                    reference.copyInto(historicReference)
                    valueBytes.copyInto(historicReference, reference.size)
                    key.bytes.copyInto(historicReference, valueBytes.size + reference.size)
                    qualifier.copyInto(
                        historicReference,
                        valueBytes.size + reference.size + key.bytes.size,
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
    val iterator: RocksIterator,
    var lastVersion: ULong? = null,
    var lastValue: ByteArray? = null,
    var isPastBeginning: Boolean = false
)
