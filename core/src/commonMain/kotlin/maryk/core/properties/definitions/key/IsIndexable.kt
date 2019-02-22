package maryk.core.properties.definitions.key

import maryk.core.values.Values

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

    /** Calculate byte length for reference to this indexable */
    fun calculateReferenceStorageByteLength(): Int

    /** Write storage bytes for reference to this indexable with [writer] */
    fun writeReferenceStorageBytes(writer: (Byte) -> Unit)

    /** Calculates the byte size of the storage bytes for [values] */
    fun calculateStorageByteLength(values: Values<*, *>): Int

    /**
     * Write bytes for storage of indexable for [values] to [writer]
     * Throws RequiredException if values are missing
     */
    fun writeStorageBytes(values: Values<*, *>, writer: (byte: Byte) -> Unit)
}
