package maryk.core.properties.definitions.key

/**
 * Defines this item is usable to describe an Index Key
 */
interface IsIndexable {
    /** The size it contributes to the key */
    val byteSize: Int

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
}
