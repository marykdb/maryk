package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.nextByteInSameLength

internal fun createIndexKeyPrefix(indexReference: ByteArray): ByteArray {
    val length = indexReference.size
    val prefix = ByteArray(length.calculateVarByteLength() + length)
    var writeIndex = 0
    length.writeVarBytes { prefix[writeIndex++] = it }
    indexReference.copyInto(prefix, writeIndex)
    return prefix
}

internal fun createIndexKey(indexReference: ByteArray, valueAndKey: ByteArray) =
    combineToByteArray(createIndexKeyPrefix(indexReference), valueAndKey)

internal fun createIndexKeyWithPrefix(indexKeyPrefix: ByteArray, valueAndKey: ByteArray) =
    combineToByteArray(indexKeyPrefix, valueAndKey)

internal fun createHistoricIndexKey(indexReference: ByteArray, valueAndKey: ByteArray, version: ByteArray) =
    combineToByteArray(createIndexKeyPrefix(indexReference), valueAndKey, version)

internal fun createIndexRangeEnd(indexReference: ByteArray) =
    createIndexKeyPrefix(indexReference).nextByteInSameLength()
