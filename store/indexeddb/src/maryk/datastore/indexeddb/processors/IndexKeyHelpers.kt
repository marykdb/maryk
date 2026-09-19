package maryk.datastore.indexeddb.processors

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.nextByteInSameLength



internal fun createUniqueRowKey(reference: ByteArray, valueBytes: ByteArray): ByteArray {
    val combined = ByteArray(reference.size + valueBytes.size)
    reference.copyInto(combined, endIndex = reference.size)
    valueBytes.copyInto(combined, destinationOffset = reference.size)
    return combined
}

internal fun createIndexKeyPrefix(indexReference: ByteArray): ByteArray {
    val length = indexReference.size
    val prefix = ByteArray(length.calculateVarByteLength() + length)
    var writeIndex = 0
    length.writeVarBytes { prefix[writeIndex++] = it }
    indexReference.copyInto(prefix, writeIndex)
    return prefix
}

internal fun createIndexRowKey(indexReference: ByteArray, valueAndKey: ByteArray) =
    combineToByteArray(createIndexKeyPrefix(indexReference), valueAndKey)

internal fun createIndexKeyWithPrefix(indexKeyPrefix: ByteArray, valueAndKey: ByteArray) =
    combineToByteArray(indexKeyPrefix, valueAndKey)

internal fun createIndexRangeEnd(indexReference: ByteArray) =
    createIndexKeyPrefix(indexReference).nextByteInSameLength()
