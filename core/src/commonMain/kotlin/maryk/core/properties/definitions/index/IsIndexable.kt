package maryk.core.properties.definitions.index

import maryk.core.properties.exceptions.RequiredException
import maryk.core.values.IsValuesGetter

/**
 * Defines this item is usable to describe an Index Key
 */
interface IsIndexable {
    val indexKeyPartType: IndexKeyPartType

    /** Convert indexable to a ByteArray so it can be referenced */
    fun toReferenceStorageByteArray(): ByteArray {
        var index = 0
        val referenceToCompareTo = ByteArray(this.calculateReferenceStorageByteLength())
        this.writeReferenceStorageBytes { referenceToCompareTo[index++] = it }
        return referenceToCompareTo
    }

    /** Convert indexable to a ByteArray so it can be referenced */
    fun toStorageByteArray(values: IsValuesGetter) = try {
        var index = 0
        ByteArray(this.calculateStorageByteLength(values)).also { bytes ->
            this.writeStorageBytes(values) { bytes[index++] = it }
        }
    } catch (e: RequiredException) {
        null
    }

    /** Calculate byte length for reference to this indexable */
    fun calculateReferenceStorageByteLength(): Int

    /** Write storage bytes for reference to this indexable with [writer] */
    fun writeReferenceStorageBytes(writer: (Byte) -> Unit)

    /** Calculates the byte size of the storage bytes for [values] */
    fun calculateStorageByteLength(values: IsValuesGetter): Int

    /**
     * Write bytes for storage of indexable for [values] to [writer]
     * Throws RequiredException if values are missing
     */
    fun writeStorageBytes(values: IsValuesGetter, writer: (byte: Byte) -> Unit)

    /**
     * Write bytes for storage of indexable for [values] to [writer]
     * Throws RequiredException if values are missing
     */
    fun writeStorageBytesForKey(values: IsValuesGetter, writer: (byte: Byte) -> Unit)
}
