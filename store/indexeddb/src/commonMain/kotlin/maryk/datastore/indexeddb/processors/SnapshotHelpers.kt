package maryk.datastore.indexeddb.processors

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.ValuesWithMetaData
import maryk.datastore.indexeddb.IndexedDbByteStore
import maryk.datastore.indexeddb.IndexedDbRecordMeta
import maryk.datastore.indexeddb.decodeRecordMeta
import maryk.datastore.indexeddb.decodeStorageRowsToValues
import maryk.datastore.indexeddb.encodeRecordMeta



internal fun encodeCurrentSnapshot(
    meta: IndexedDbRecordMeta,
    rows: List<Pair<ByteArray, ByteArray>>,
): ByteArray = encodeRowsSnapshot(meta, rows)

internal fun decodeCurrentSnapshot(bytes: ByteArray): Pair<IndexedDbRecordMeta, List<Pair<ByteArray, ByteArray>>> =
    decodeRowsSnapshot(bytes)

internal suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readCurrentSnapshot(
    dataModel: DM,
    keyStoreName: String,
    keyBytes: ByteArray,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray) -> ByteArray = { it },
): ValuesWithMetaData<DM>? {
    val snapshot = get(keyStoreName, keyBytes) ?: return null
    if (snapshot.size == 17) return null

    val (meta, rows) = decodeCurrentSnapshot(snapshot)
    val values = decodeStorageRowsToValues(
        dataModel,
        rows.map { (qualifier, value) -> qualifier to decryptValue(value) },
        select,
    ) ?: return null

    return ValuesWithMetaData(
        key = dataModel.key(keyBytes),
        values = values,
        firstVersion = meta.firstVersion,
        lastVersion = meta.lastVersion,
        isDeleted = meta.isDeleted,
    )
}

internal suspend fun <DM : IsRootDataModel> decodeCurrentSnapshotRecord(
    dataModel: DM,
    keyBytes: ByteArray,
    snapshot: ByteArray,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray) -> ByteArray = { it },
): ValuesWithMetaData<DM>? {
    if (snapshot.size == 17) return null

    val (meta, rows) = decodeCurrentSnapshot(snapshot)
    val values = decodeStorageRowsToValues(
        dataModel,
        rows.map { (qualifier, value) -> qualifier to decryptValue(value) },
        select,
    ) ?: return null

    return ValuesWithMetaData(
        key = dataModel.key(keyBytes),
        values = values,
        firstVersion = meta.firstVersion,
        lastVersion = meta.lastVersion,
        isDeleted = meta.isDeleted,
    )
}

internal fun ByteArray.readInvertedVersion(): ULong {
    var inverted = 0uL
    for (index in 0 until ULong.SIZE_BYTES) {
        inverted = (inverted shl Byte.SIZE_BITS) or this[index].toUByte().toULong()
    }
    return ULong.MAX_VALUE - inverted
}

internal fun ByteArray.readTrailingInvertedVersion(): ULong =
    copyOfRange(size - ULong.SIZE_BYTES, size).readInvertedVersion()

internal fun ByteArray.readTrailingVersion(): ULong {
    var version = 0uL
    for (index in size - ULong.SIZE_BYTES until size) {
        version = (version shl Byte.SIZE_BITS) or this[index].toUByte().toULong()
    }
    return version
}

internal val unserializableChangeLogMarker = byteArrayOf(0)

internal fun ByteArray.isUnserializableChangeLogMarker() =
    size == unserializableChangeLogMarker.size && contentEquals(unserializableChangeLogMarker)

internal fun encodeHistoricSnapshot(
    meta: IndexedDbRecordMeta,
    rows: List<Pair<ByteArray, ByteArray>>,
): ByteArray = encodeRowsSnapshot(meta, rows)

internal fun encodeRowsSnapshot(
    meta: IndexedDbRecordMeta,
    rows: List<Pair<ByteArray, ByteArray>>,
): ByteArray {
    val size = 17 + rows.size.calculateVarByteLength() + rows.sumOf { (qualifier, value) ->
        qualifier.size.calculateVarByteLength() + qualifier.size +
            value.size.calculateVarByteLength() + value.size
    }
    val bytes = ByteArray(size)
    var index = 0
    encodeRecordMeta(meta).copyInto(bytes, destinationOffset = index)
    index += 17
    rows.size.writeVarBytes { bytes[index++] = it }
    for ((qualifier, value) in rows) {
        qualifier.size.writeVarBytes { bytes[index++] = it }
        qualifier.copyInto(bytes, destinationOffset = index)
        index += qualifier.size
        value.size.writeVarBytes { bytes[index++] = it }
        value.copyInto(bytes, destinationOffset = index)
        index += value.size
    }
    return bytes
}

internal fun decodeHistoricSnapshot(bytes: ByteArray): Pair<IndexedDbRecordMeta, List<Pair<ByteArray, ByteArray>>> {
    return decodeRowsSnapshot(bytes)
}

internal fun decodeRowsSnapshot(bytes: ByteArray): Pair<IndexedDbRecordMeta, List<Pair<ByteArray, ByteArray>>> {
    var index = 0
    val meta = decodeRecordMeta(bytes.copyOfRange(0, 17))
    index = 17
    val count = initIntByVar { bytes[index++] }
    val rows = ArrayList<Pair<ByteArray, ByteArray>>(count)
    repeat(count) {
        val qualifierSize = initIntByVar { bytes[index++] }
        val qualifier = bytes.copyOfRange(index, index + qualifierSize)
        index += qualifierSize
        val valueSize = initIntByVar { bytes[index++] }
        val value = bytes.copyOfRange(index, index + valueSize)
        index += valueSize
        rows += qualifier to value
    }
    return meta to rows
}

internal fun ULong.toBigEndianBytes(): ByteArray {
    val bytes = ByteArray(ULong.SIZE_BYTES)
    for (index in bytes.indices) {
        bytes[index] = (this shr ((ULong.SIZE_BYTES - 1 - index) * Byte.SIZE_BITS)).toByte()
    }
    return bytes
}

