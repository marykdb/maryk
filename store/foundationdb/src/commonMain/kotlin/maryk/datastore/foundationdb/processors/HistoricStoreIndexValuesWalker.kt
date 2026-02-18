package maryk.datastore.foundationdb.processors

import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapAnyKeyReference
import maryk.core.properties.references.SetAnyValueReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.shared.helpers.convertToValue

/**
 * Historical index values walker for the FoundationDB store.
 * It iterates per property over the historic table for a key and computes the
 * index values for each version, walking from newest to oldest.
 */
internal class HistoricStoreIndexValuesWalker(
    private val tableDirs: HistoricTableDirectories
) {
    private val getter = HistoricStoreIndexValuesGetter(tableDirs)

    /**
     * Walk historical values of [key] for [indexable] within [tr].
     * For each discovered version/buildable index state, invoke [handleIndex]
     * with a pair of (valueAndKeyBytes, versionTimestamp).
     */
    fun walkHistoricalValuesForIndexKeys(
        key: ByteArray,
        tr: Transaction,
        indexable: IsIndexable,
        handleIndex: (ByteArray, ULong) -> Unit
    ) {
        if (indexable is MapAnyKeyReference<*, *, *>) {
            walkMapAnyKeyHistory(key, tr, indexable, handleIndex)
            return
        }
        if (indexable is SetAnyValueReference<*, *>) {
            walkSetAnyValueHistory(key, tr, indexable, handleIndex)
            return
        }

        getter.moveToKey(key, tr)

        do {
            try {
                val valuesAndKeys = indexable.toStorageByteArraysForIndex(getter, key)
                val version = getter.latestVersion
                    ?: throw StorageException("Latest overall version was not set")

                valuesAndKeys.forEach { valueAndKeyBytes ->
                    handleIndex(valueAndKeyBytes, version)
                }
            } catch (_: Throwable) {
                // Skip failing index reference generation and keep walking
            }
        } while (getter.gotoNextVersion())
    }

    private fun walkMapAnyKeyHistory(
        key: ByteArray,
        tr: Transaction,
        indexable: MapAnyKeyReference<*, *, *>,
        handleIndex: (ByteArray, ULong) -> Unit
    ) {
        val parentReference = indexable.parentReference ?: return
        val encodedParent = encodeZeroFreeUsing01(parentReference.toStorageByteArray())
        val prefix = packKey(tableDirs.historicTablePrefix, key, encodedParent)
        val iterator = tr.getRange(Range.startsWith(prefix)).iterator()

        while (iterator.hasNext()) {
            val kv = iterator.nextBlocking()
            val historicKey = kv.key

            try {
                val versionOffset = historicKey.size - VERSION_BYTE_SIZE
                val sepIndex = versionOffset - 1
                if (sepIndex < prefix.size || historicKey[sepIndex] != 0.toByte()) continue

                val encodedMapKey = historicKey.copyOfRange(prefix.size, sepIndex)
                val mapKeyBytes = decodeZeroFreeUsing01(encodedMapKey)

                var readIndex = 0
                val mapKeyLength = initIntByVar { mapKeyBytes[readIndex++] }
                @Suppress("UNCHECKED_CAST")
                val mapKey = (indexable as MapAnyKeyReference<Any, Any, *>).readStorageBytes(mapKeyLength) { mapKeyBytes[readIndex++] }
                if (readIndex != mapKeyBytes.size) continue

                val valueAndKey = ByteArray(indexable.calculateStorageByteLength(mapKey) + key.size)
                var writeIndex = 0
                indexable.writeStorageBytes(mapKey) { valueAndKey[writeIndex++] = it }
                key.copyInto(valueAndKey, writeIndex)

                handleIndex(valueAndKey, historicKey.readReversedVersionBytes(versionOffset))
            } catch (_: Throwable) {
                // Skip malformed entries and keep walking
            }
        }
    }

    private fun walkSetAnyValueHistory(
        key: ByteArray,
        tr: Transaction,
        indexable: SetAnyValueReference<*, *>,
        handleIndex: (ByteArray, ULong) -> Unit
    ) {
        val parentReference = indexable.parentReference ?: return
        val encodedParent = encodeZeroFreeUsing01(parentReference.toStorageByteArray())
        val prefix = packKey(tableDirs.historicTablePrefix, key, encodedParent)
        val iterator = tr.getRange(Range.startsWith(prefix)).iterator()

        while (iterator.hasNext()) {
            val kv = iterator.nextBlocking()
            val historicKey = kv.key

            try {
                val versionOffset = historicKey.size - VERSION_BYTE_SIZE
                val sepIndex = versionOffset - 1
                if (sepIndex < prefix.size || historicKey[sepIndex] != 0.toByte()) continue

                val encodedSetItem = historicKey.copyOfRange(prefix.size, sepIndex)
                val setItemBytes = decodeZeroFreeUsing01(encodedSetItem)

                var readIndex = 0
                val setItemLength = initIntByVar { setItemBytes[readIndex++] }
                @Suppress("UNCHECKED_CAST")
                val setItem = (indexable as SetAnyValueReference<Any, *>).readStorageBytes(setItemLength) { setItemBytes[readIndex++] }
                if (readIndex != setItemBytes.size) continue

                val valueAndKey = ByteArray(indexable.calculateStorageByteLength(setItem) + key.size)
                var writeIndex = 0
                indexable.writeStorageBytes(setItem) { valueAndKey[writeIndex++] = it }
                key.copyInto(valueAndKey, writeIndex)

                handleIndex(valueAndKey, historicKey.readReversedVersionBytes(versionOffset))
            } catch (_: Throwable) {
                // Skip malformed entries and keep walking
            }
        }
    }
}

/**
 * A historical values getter which finds the latest valid property values for a key,
 * walking backwards in time. It manages per-reference iterators over the historic table.
 */
private class HistoricStoreIndexValuesGetter(
    private val tableDirs: HistoricTableDirectories
) : IsValuesGetter {
    private data class IterableReference(
        val referenceAsBytes: ByteArray,
        val iterator: maryk.foundationdb.async.AsyncIterator<maryk.foundationdb.KeyValue>,
        var lastVersion: ULong? = null,
        var lastValue: ByteArray? = null,
        var isPastBeginning: Boolean = false
    )

    private val iterableReferenceMap = mutableMapOf<IsPropertyReference<*, *, *>, IterableReference>()

    // Latest version found across all properties in the current step
    var latestVersion: ULong? = null
        private set

    // Version to skip during the next iteration step
    private var versionToSkip: ULong? = null

    private lateinit var tr: Transaction
    private lateinit var key: ByteArray

    /** Prepare to walk historical values for [key] in transaction [tr]. */
    fun moveToKey(key: ByteArray, tr: Transaction) {
        this.key = key
        this.tr = tr
        reset()
    }

    /** Go to next step by marking the current [latestVersion] to skip on next read. */
    fun gotoNextVersion(): Boolean = when (this.versionToSkip) {
        this.latestVersion -> {
            reset()
            false
        }
        else -> {
            this.versionToSkip = this.latestVersion
            this.latestVersion = null
            true
        }
    }

    private fun reset() {
        iterableReferenceMap.clear()
        latestVersion = null
        versionToSkip = null
    }

    override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
        propertyReference: IsPropertyReference<T, D, C>
    ): T? {
        val iterableReference = iterableReferenceMap.getOrPut(propertyReference) {
            val refBytes = propertyReference.toStorageByteArray()
            val encRef = encodeZeroFreeUsing01(refBytes)
            val prefix = packKey(tableDirs.historicTablePrefix, key, encRef)
            val it = tr.getRange(Range.startsWith(prefix)).iterator()
            IterableReference(refBytes, it)
        }

        val iterator = iterableReference.iterator

        // Only advance to next version if needed
        if (!iterableReference.isPastBeginning && (iterableReference.lastVersion == null || versionToSkip == iterableReference.lastVersion)) {
            if (iterator.hasNext()) {
                val kv = iterator.nextBlocking()
                val keyBytes = kv.key

                // Historic key layout: historicTablePrefix + key + encodedRef + 0x00 + reversedVersion
                val versionOffset = keyBytes.size - VERSION_BYTE_SIZE
                val sepIndex = versionOffset - 1
                if (sepIndex < 0 || keyBytes[sepIndex] != 0.toByte()) {
                    iterableReference.isPastBeginning = true
                    return null
                }

                // Decode stored value and reversed version
                val valueBytes = kv.value
                iterableReference.lastValue = valueBytes
                val lastVersion = keyBytes.readReversedVersionBytes(versionOffset)
                iterableReference.lastVersion = lastVersion

                // Track latest across properties
                latestVersion = latestVersion?.let { maxOf(lastVersion, it) } ?: lastVersion
            } else {
                iterableReference.isPastBeginning = true
                return null
            }
        } else {
            // Update latest across properties based on cached version
            latestVersion = latestVersion?.let { latest ->
                iterableReference.lastVersion?.let { last -> maxOf(last, latest) } ?: latest
            } ?: iterableReference.lastVersion
        }

        if (iterableReference.isPastBeginning) return null

        val lastValue = iterableReference.lastValue ?: return null
        return lastValue.convertToValue(propertyReference, 0, lastValue.size)
    }
}
