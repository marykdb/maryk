package maryk.core.properties.definitions.index

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.types.Bytes
import maryk.core.values.IsValuesGetter
import maryk.lib.bytes.combineToByteArray

/**
 * Defines this item is usable to describe an Index Key
 */
interface IsIndexable {
    val indexKeyPartType: IndexKeyPartType<IsIndexable>
    val referenceStorageByteArray: Bytes

    /** Convert indexable to a ByteArray so it can be referenced */
    fun toStorageByteArrayForIndex(values: IsValuesGetter, key: ByteArray? = null): ByteArray? =
        toStorageByteArraysForIndex(values, key).firstOrNull()

    /** Convert indexable to one or more ByteArrays for index entries */
    fun toStorageByteArraysForIndex(values: IsValuesGetter, key: ByteArray? = null): List<ByteArray> = try {
        toStorageByteArrays(values).map { valueBytes ->
            val length = valueBytes.size
            combineToByteArray(
                valueBytes,
                ByteArray(length.calculateVarByteLength()).also { lengthBytes ->
                    var index = 0
                    length.writeVarBytes { lengthBytes[index++] = it }
                },
                key ?: ByteArray(0)
            )
        }
    } catch (_: RequiredException) {
        emptyList()
    }

    /** Convert indexable value bytes without index key lengths */
    fun toStorageByteArrays(values: IsValuesGetter): List<ByteArray> = try {
        val bytes = mutableListOf<Byte>()
        this.writeStorageBytes(values) { bytes += it }
        listOf(bytes.toByteArray())
    } catch (_: RequiredException) {
        emptyList()
    }

    /** Calculate byte length for reference to this indexable */
    fun calculateReferenceStorageByteLength(): Int

    /** Write storage bytes for reference to this indexable with [writer] */
    fun writeReferenceStorageBytes(writer: (Byte) -> Unit)

    /** Calculates the byte size of the storage bytes for index for [values] and [keySize] */
    fun calculateStorageByteLengthForIndex(values: IsValuesGetter, keySize: Int? = null): Int

    /**
     * Write bytes for storage of indexable for [values] to [writer]
     * Adds lengths and [key] to the end
     * Throws RequiredException if values are missing
     */
    fun writeStorageBytesForIndex(values: IsValuesGetter, key: ByteArray?, writer: (byte: Byte) -> Unit)

    /**
     * Write bytes for storage of indexable for [values] to [writer]
     * Throws RequiredException if values are missing
     */
    fun writeStorageBytes(values: IsValuesGetter, writer: (byte: Byte) -> Unit)

    /** Checks if [dataModel] is compatible with this indexable */
    fun isCompatibleWithModel(dataModel: IsRootDataModel): Boolean
}

/** Convert indexable to a ByteArray so it can be referenced */
internal fun IsIndexable.toReferenceStorageByteArray(): ByteArray {
    var index = 0
    val referenceToCompareTo = ByteArray(this.calculateReferenceStorageByteLength())
    this.writeReferenceStorageBytes { referenceToCompareTo[index++] = it }
    return referenceToCompareTo
}
