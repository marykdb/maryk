package maryk.core.properties.definitions.index

import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.types.Bytes
import maryk.core.values.IsValuesGetter

/**
 * Defines this item is usable to describe an Index Key
 */
interface IsIndexable {
    val indexKeyPartType: IndexKeyPartType<IsIndexable>
    val referenceStorageByteArray: Bytes

    /** Convert indexable to a ByteArray so it can be referenced */
    fun toStorageByteArrayForIndex(values: IsValuesGetter, key: ByteArray) = try {
        var index = 0
        ByteArray(this.calculateStorageByteLengthForIndex(values, key.size)).also { bytes ->
            this.writeStorageBytesForIndex(values, key) { bytes[index++] = it }
        }
    } catch (e: RequiredException) {
        null
    }

    /** Calculate byte length for reference to this indexable */
    fun calculateReferenceStorageByteLength(): Int

    /** Write storage bytes for reference to this indexable with [writer] */
    fun writeReferenceStorageBytes(writer: (Byte) -> Unit)

    /** Calculates the byte size of the storage bytes for index for [values] and [keySize] */
    fun calculateStorageByteLengthForIndex(values: IsValuesGetter, keySize: Int): Int

    /**
     * Write bytes for storage of indexable for [values] to [writer]
     * Adds lengths and [key] to the end
     * Throws RequiredException if values are missing
     */
    fun writeStorageBytesForIndex(values: IsValuesGetter, key: ByteArray, writer: (byte: Byte) -> Unit)

    /**
     * Write bytes for storage of indexable for [values] to [writer]
     * Throws RequiredException if values are missing
     */
    fun writeStorageBytes(values: IsValuesGetter, writer: (byte: Byte) -> Unit)
}

/** Convert indexable to a ByteArray so it can be referenced */
internal fun IsIndexable.toReferenceStorageByteArray(): ByteArray {
    var index = 0
    val referenceToCompareTo = ByteArray(this.calculateReferenceStorageByteLength())
    this.writeReferenceStorageBytes { referenceToCompareTo[index++] = it }
    return referenceToCompareTo
}
