package maryk.datastore.indexeddb.processors

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.decodeStorageIndex
import maryk.lib.exceptions.ParseException
import maryk.core.query.ValuesWithMetaData
import maryk.datastore.indexeddb.IndexedDbByteStore
import maryk.datastore.indexeddb.IndexedDbRecordMeta
import maryk.datastore.indexeddb.decodeRecordMeta
import maryk.datastore.indexeddb.decryptSelectedStorageRows
import maryk.datastore.indexeddb.decodeStorageRowsToValues
import maryk.datastore.indexeddb.encodeRecordMeta
import maryk.datastore.shared.simpleScalarReadProperty

private const val MAX_PREPARED_SNAPSHOT_ROWS = 256



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
    decryptValue: suspend (ByteArray, ByteArray) -> ByteArray = { _, value -> value },
): ValuesWithMetaData<DM>? {
    val snapshot = get(keyStoreName, keyBytes) ?: return null
    if (snapshot.size == 17) return null

    val decoded = decodeSelectedSnapshot(dataModel, snapshot, select)
    val (meta, rows) = decoded
    // Released non-history soft deletes could retain table cells but omit this
    // cached payload. Let the caller recover the authoritative table rows.
    if (meta.isDeleted && decoded.storedRowCount == 0) return null
    val values = decodeStorageRowsToValues(
        dataModel,
        decryptSelectedStorageRows(dataModel, rows, null, decryptValue),
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
    allowEmptyDeletedSnapshot: Boolean = false,
    decryptValue: suspend (ByteArray, ByteArray) -> ByteArray = { _, value -> value },
): ValuesWithMetaData<DM>? {
    if (snapshot.size == 17) return null

    val decoded = decodeSelectedSnapshot(dataModel, snapshot, select)
    val (meta, rows) = decoded
    // A journal snapshot is self-contained, and a deleted record can validly
    // have no optional fields. Table reads instead recover retained legacy cells.
    if (!allowEmptyDeletedSnapshot && meta.isDeleted && decoded.storedRowCount == 0) return null
    val values = decodeStorageRowsToValues(
        dataModel,
        decryptSelectedStorageRows(dataModel, rows, null, decryptValue),
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

internal fun ByteArray.isHardDeleteTombstone() =
    size == 1 && this[0] == 1.toByte()

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
    val writer: (Byte) -> Unit = { bytes[index++] = it }
    rows.size.writeVarBytes(writer)
    for ((qualifier, value) in rows) {
        qualifier.size.writeVarBytes(writer)
        qualifier.copyInto(bytes, destinationOffset = index)
        index += qualifier.size
        value.size.writeVarBytes(writer)
        value.copyInto(bytes, destinationOffset = index)
        index += value.size
    }
    return bytes
}

internal fun decodeHistoricSnapshot(bytes: ByteArray): Pair<IndexedDbRecordMeta, List<Pair<ByteArray, ByteArray>>> {
    return decodeRowsSnapshot(bytes)
}

internal fun decodeRowsSnapshot(bytes: ByteArray): Pair<IndexedDbRecordMeta, List<Pair<ByteArray, ByteArray>>> =
    decodeSnapshotRows(bytes).let { it.meta to it.rows }

internal data class DecodedIndexedDbSnapshot(
    val meta: IndexedDbRecordMeta,
    val rows: List<Pair<ByteArray, ByteArray>>,
    val storedRowCount: Int,
)

/** Inspect qualifiers before allocating payload arrays, retaining selected structural parents. */
internal fun decodeSelectedSnapshot(
    dataModel: IsRootDataModel,
    bytes: ByteArray,
    select: RootPropRefGraph<*>?,
): DecodedIndexedDbSnapshot = when {
    select == null -> decodeSnapshotRows(bytes)
    select.properties.isEmpty() -> decodeSnapshotRows(bytes, skipAll = true)
    else -> decodeSnapshotRows(bytes) { offset, length -> snapshotRowIsSelected(dataModel, bytes, select, offset, length) }
}

internal fun isNarrowSnapshotSelection(dataModel: IsRootDataModel, select: RootPropRefGraph<*>?): Boolean =
    select != null && select.properties.size <= 64 &&
        (select.properties.size < dataModel.count() || select.properties.any { it !is IsDefinitionWrapper<*, *, *, *> })

/** Inspect spans first so a wide selected payload never creates a second blob copy. */
internal fun prepareNarrowSnapshot(
    dataModel: IsRootDataModel,
    bytes: ByteArray,
    select: RootPropRefGraph<*>,
): DecodedIndexedDbSnapshot? {
    if (!isNarrowSnapshotSelection(dataModel, select)) return null
    val meta = decodeRecordMeta(bytes)
    lateinit var spans: IntArray
    var selectedCount = 0
    var selectedBytes = 0L
    val rowLimit = if (select.properties.all { simpleScalarReadProperty(it) != null }) {
        select.properties.size + 1
    } else MAX_PREPARED_SNAPSHOT_ROWS
    val count = visitSnapshotRows(bytes, { rows -> spans = IntArray(minOf(rows, rowLimit) * 4) }) { qualifierOffset, qualifierSize, valueOffset, valueSize ->
        if (select.properties.isNotEmpty() && snapshotRowIsSelected(dataModel, bytes, select, qualifierOffset, qualifierSize)) {
            selectedBytes += qualifierSize.toLong() + valueSize
            if (selectedBytes > bytes.size / 2 || selectedCount >= spans.size / 4) return@visitSnapshotRows false
            val base = selectedCount++ * 4
            spans[base] = qualifierOffset; spans[base + 1] = qualifierSize
            spans[base + 2] = valueOffset; spans[base + 3] = valueSize
        }
        true
    }
    if (count < 0) return null
    val rows = ArrayList<Pair<ByteArray, ByteArray>>(selectedCount)
    repeat(selectedCount) { index ->
        val base = index * 4
        rows += bytes.copyOfRange(spans[base], spans[base] + spans[base + 1]) to
            bytes.copyOfRange(spans[base + 2], spans[base + 2] + spans[base + 3])
    }
    return DecodedIndexedDbSnapshot(meta, rows, count)
}

private fun snapshotRowIsSelected(
    dataModel: IsRootDataModel, bytes: ByteArray, select: RootPropRefGraph<*>, offset: Int, length: Int,
): Boolean {
    var index = offset
    val rootIndex = decodeStorageIndex({
        if (index >= offset + length) throw ParseException("Property reference exceeds declared length")
        bytes[index++]
    }) { propertyIndex, _ -> propertyIndex }
    // Unselected roots need no nested map/key decoding; unknown roots remain validated.
    if (dataModel[rootIndex] != null && select.properties.none { it.index == rootIndex }) return false
    index = offset
    return select.contains(dataModel.getPropertyReferenceByStorageBytes(length, reader = { bytes[index++] }))
}

private fun decodeSnapshotRows(
    bytes: ByteArray,
    skipAll: Boolean = false,
    includeRow: ((offset: Int, length: Int) -> Boolean)? = null,
): DecodedIndexedDbSnapshot {
    val meta = decodeRecordMeta(bytes)
    lateinit var rows: ArrayList<Pair<ByteArray, ByteArray>>
    val count = visitSnapshotRows(bytes, { count ->
        rows = ArrayList(if (skipAll) 0 else if (includeRow == null) count else minOf(count, 16))
    }) { qualifierOffset, qualifierSize, valueOffset, valueSize ->
        if (!skipAll && (includeRow == null || includeRow(qualifierOffset, qualifierSize))) {
            rows += bytes.copyOfRange(qualifierOffset, qualifierOffset + qualifierSize) to
                bytes.copyOfRange(valueOffset, valueOffset + valueSize)
        }
        true
    }
    return DecodedIndexedDbSnapshot(meta, rows, count)
}

/** A negative result means the visitor declined preparation; malformed rows still throw. */
private inline fun visitSnapshotRows(
    bytes: ByteArray,
    beforeRows: (Int) -> Unit,
    row: (qualifierOffset: Int, qualifierSize: Int, valueOffset: Int, valueSize: Int) -> Boolean,
): Int {
    var index = 17
    val reader = { bytes[index++] }
    val count = initIntByVar(reader)
    require(count >= 0 && count <= (bytes.size - index) / 2) { "Invalid IndexedDB snapshot row count" }
    beforeRows(count)
    repeat(count) {
        val qualifierSize = initIntByVar(reader)
        require(qualifierSize >= 0 && qualifierSize <= bytes.size - index) { "Invalid IndexedDB snapshot qualifier size" }
        val qualifierOffset = index
        index += qualifierSize
        val valueSize = initIntByVar(reader)
        require(valueSize >= 0 && valueSize <= bytes.size - index) { "Invalid IndexedDB snapshot value size" }
        if (!row(qualifierOffset, qualifierSize, index, valueSize)) return -1
        index += valueSize
    }
    return count
}

internal fun ULong.toBigEndianBytes(): ByteArray {
    val bytes = ByteArray(ULong.SIZE_BYTES)
    for (index in bytes.indices) {
        bytes[index] = (this shr ((ULong.SIZE_BYTES - 1 - index) * Byte.SIZE_BITS)).toByte()
    }
    return bytes
}
