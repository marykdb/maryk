package maryk.datastore.indexeddb

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.types.Bytes
import maryk.core.exceptions.StorageException
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IndexDelete
import maryk.core.query.changes.IndexUpdate
import maryk.core.query.changes.IsChange
import maryk.datastore.indexeddb.processors.decodeVersionedChange
import maryk.datastore.indexeddb.processors.decodeCurrentSnapshotRecord
import maryk.datastore.indexeddb.processors.encodeVersionedChange
import maryk.datastore.indexeddb.processors.isUnserializableChangeLogMarker
import maryk.datastore.indexeddb.processors.toBigEndianBytes
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.shared.updates.Update

internal const val CommitJournalStoreName = "__maryk_commit_journal"
internal const val CommitConsumerStoreName = "__maryk_commit_consumers"
internal val CommitClockMetadataKey = "commit-clock".encodeToByteArray()
internal val CommitJournalFloorMetadataKey = "commit-journal-floor".encodeToByteArray()
internal const val JournalPollIntervalMillis = 250L
internal const val JournalConsumerTimeoutMillis = 120_000uL
internal var indexedDbMaxRetainedJournalEntries = 4096
internal var indexedDbJournalPollingPausedForTests = false
internal val CommitJournalStartCursor = ByteArray(ULong.SIZE_BYTES + UInt.SIZE_BYTES)

internal data class JournalConsumer(
    val heartbeatMillis: ULong,
    val cursor: ByteArray?,
)

internal fun encodeJournalConsumer(heartbeatMillis: ULong, cursor: ByteArray?): ByteArray =
    heartbeatMillis.toBigEndianBytes() + (cursor ?: byteArrayOf())

internal fun decodeJournalConsumer(bytes: ByteArray): JournalConsumer {
    require(bytes.size >= ULong.SIZE_BYTES) { "Invalid IndexedDB journal consumer" }
    return JournalConsumer(
        heartbeatMillis = bytes.readBigEndianULong(),
        cursor = bytes.copyOfRange(ULong.SIZE_BYTES, bytes.size).takeUnless { it.isEmpty() },
    )
}

internal suspend fun encodeChangeJournalPayload(
    dataModel: IsRootDataModel,
    modelId: UInt,
    sensitiveFields: IndexedDbSensitiveFieldSupport,
    key: ByteArray,
    version: ULong,
    changePayload: ByteArray?,
    changes: List<IsChange>,
): ByteArray {
    val indexChanges = changes.filterIsInstance<IndexChange>().flatMap(IndexChange::changes)
    val parts = mutableListOf<ByteArray>()
    val ordinaryPayload = when {
        changePayload == null -> sensitiveFields.encryptChangeLogPayload(
            modelId,
            key,
            version,
            encodeVersionedChange(
                dataModel,
                DataObjectVersionedChange(
                    key = dataModel.key(key),
                    changes = listOf(VersionedChanges(version, emptyList())),
                ),
            ),
        )
        changePayload.isUnserializableChangeLogMarker() -> null
        else -> changePayload
    }
    parts += byteArrayOf(if (ordinaryPayload == null) UnavailableChangePayload else ReplayableChangePayload)
    if (ordinaryPayload == null) return parts.single()
    parts += ordinaryPayload.size.toUInt().toBigEndianBytes()
    parts += ordinaryPayload
    parts += indexChanges.size.toUInt().toBigEndianBytes()
    for (change in indexChanges) {
        parts += byteArrayOf(if (change is IndexUpdate) 1 else 2)
        parts += encodeSizedBytes(change.index.bytes)
        when (change) {
            is IndexUpdate -> {
                parts += encodeSizedBytes(change.indexKey.bytes)
                parts += change.previousIndexKey?.bytes?.let(::encodeSizedBytes)
                    ?: UInt.MAX_VALUE.toBigEndianBytes()
            }
            is IndexDelete -> parts += encodeSizedBytes(change.indexKey.bytes)
        }
    }
    return parts.fold(byteArrayOf()) { result, part -> result + part }
}

private suspend fun decodeChangeJournalPayload(
    dataModel: IsRootDataModel,
    modelId: UInt,
    sensitiveFields: IndexedDbSensitiveFieldSupport,
    key: ByteArray,
    version: ULong,
    payload: ByteArray,
): List<IsChange> {
    var offset = 0
    fun readSize(): UInt = payload.readBigEndianUInt(offset).also { offset += UInt.SIZE_BYTES }
    fun readBytes(): ByteArray {
        val size = readSize().toInt()
        return payload.copyOfRange(offset, offset + size).also { offset += size }
    }
    if (payload[offset++] == UnavailableChangePayload) {
        throw StorageException(
            "IndexedDB committed changes could not be serialized for cross-context replay; " +
                "resubscribe to obtain a fresh snapshot"
        )
    }
    val ordinary = readBytes()
    val changes = decodeVersionedChange(
        dataModel,
        sensitiveFields.decryptChangeLogPayloadIfNeeded(modelId, key, version, ordinary),
    ).changes.single().changes.toMutableList()
    val indexUpdates = buildList {
        repeat(readSize().toInt()) {
            val type = payload[offset++]
            val index = Bytes(readBytes())
            when (type.toInt()) {
                1 -> {
                    val indexKey = Bytes(readBytes())
                    val previousSize = readSize()
                    val previous = if (previousSize == UInt.MAX_VALUE) null else {
                        Bytes(payload.copyOfRange(offset, offset + previousSize.toInt())).also { offset += previousSize.toInt() }
                    }
                    add(IndexUpdate(index, indexKey, previous))
                }
                2 -> add(IndexDelete(index, Bytes(readBytes())))
                else -> error("Unknown IndexedDB journal index update type $type")
            }
        }
    }
    if (indexUpdates.isNotEmpty()) changes += IndexChange(indexUpdates)
    return changes
}

private fun encodeSizedBytes(bytes: ByteArray) = bytes.size.toUInt().toBigEndianBytes() + bytes

private const val AdditionJournalEntry: Byte = 1
private const val ChangeJournalEntry: Byte = 2
private const val SoftDeleteJournalEntry: Byte = 3
private const val HardDeleteJournalEntry: Byte = 4
private const val UnavailableChangePayload: Byte = 0
private const val ReplayableChangePayload: Byte = 1

internal fun createCommitJournalKey(version: ULong, sequence: UInt): ByteArray =
    version.toBigEndianBytes() + sequence.toBigEndianBytes()

internal fun encodeCommitJournalEntry(
    modelId: UInt,
    update: Update<out IsRootDataModel>,
    payload: ByteArray?,
): ByteArray {
    val type: Byte
    when (update) {
        is Update.Addition -> {
            type = AdditionJournalEntry
        }
        is Update.Change -> {
            type = ChangeJournalEntry
        }
        is Update.Deletion -> {
            type = if (update.isHardDelete) HardDeleteJournalEntry else SoftDeleteJournalEntry
        }
    }
    return byteArrayOf(type) + modelId.toBigEndianBytes() + update.version.toBigEndianBytes() +
        update.key.bytes + (payload ?: byteArrayOf())
}

internal suspend fun migrateLegacyCommitJournalEntry(
    dataModelsById: Map<UInt, IsRootDataModel>,
    sensitiveFields: IndexedDbSensitiveFieldSupport,
    targetModelId: UInt,
    value: ByteArray,
): ByteArray? {
    require(value.size >= JournalHeaderSize) { "Invalid IndexedDB commit journal entry" }
    if (value[0] != ChangeJournalEntry) return null

    val modelId = value.readBigEndianUInt(1)
    if (modelId != targetModelId) return null
    val version = value.readBigEndianULong(1 + UInt.SIZE_BYTES)
    val dataModel = dataModelsById.getValue(modelId)
    val keyStart = JournalHeaderSize
    val keyEnd = keyStart + dataModel.Meta.keyByteSize
    require(value.size > keyEnd) { "Invalid IndexedDB change journal entry" }
    val key = value.copyOfRange(keyStart, keyEnd)
    val payloadOffset = keyEnd
    if (value[payloadOffset] == UnavailableChangePayload) return null
    require(value[payloadOffset] == ReplayableChangePayload && value.size >= payloadOffset + 1 + UInt.SIZE_BYTES) {
        "Invalid IndexedDB change journal payload"
    }

    val ordinarySizeOffset = payloadOffset + 1
    val ordinarySize = value.readBigEndianUInt(ordinarySizeOffset).toInt()
    val ordinaryOffset = ordinarySizeOffset + UInt.SIZE_BYTES
    val ordinaryEnd = ordinaryOffset + ordinarySize
    require(ordinaryEnd <= value.size) { "Invalid IndexedDB change journal payload size" }
    val ordinary = value.copyOfRange(ordinaryOffset, ordinaryEnd)
    if (sensitiveFields.isEncryptedChangeLogPayload(ordinary)) return null

    val encrypted = sensitiveFields.encryptChangeLogPayload(modelId, key, version, ordinary)
    return value.copyOfRange(0, ordinarySizeOffset) +
        encrypted.size.toUInt().toBigEndianBytes() +
        encrypted +
        value.copyOfRange(ordinaryEnd, value.size)
}

internal suspend fun decodeCommitJournalEntry(
    dataModelsById: Map<UInt, IsRootDataModel>,
    sensitiveFields: IndexedDbSensitiveFieldSupport,
    key: ByteArray,
    value: ByteArray,
): Update<out IsRootDataModel> {
    require(key.size >= ULong.SIZE_BYTES && value.size >= JournalHeaderSize) {
        "Invalid IndexedDB commit journal entry"
    }
    val version = value.readBigEndianULong(1 + UInt.SIZE_BYTES)
    val type = value[0]
    val modelId = value.readBigEndianUInt(1)
    val dataModel = dataModelsById.getValue(modelId)
    val keyEnd = JournalHeaderSize + dataModel.Meta.keyByteSize
    val objectKey = dataModel.key(value.copyOfRange(JournalHeaderSize, keyEnd))
    val payload = value.copyOfRange(keyEnd, value.size)
    return when (type) {
        AdditionJournalEntry -> Update.Addition(
            dataModel,
            objectKey,
            version,
            requireNotNull(
                decodeCurrentSnapshotRecord(
                    dataModel,
                    objectKey.bytes,
                    payload,
                    select = null,
                    decryptValue = { qualifier, encoded ->
                        sensitiveFields.decryptValueIfNeeded(modelId, objectKey.bytes, qualifier, encoded)
                    },
                )
            ) { "Invalid IndexedDB addition journal snapshot" }.values,
        )
        ChangeJournalEntry -> Update.Change(
            dataModel,
            objectKey,
            version,
            decodeChangeJournalPayload(dataModel, modelId, sensitiveFields, objectKey.bytes, version, payload),
        )
        SoftDeleteJournalEntry, HardDeleteJournalEntry -> Update.Deletion(
            dataModel,
            objectKey,
            version,
            type == HardDeleteJournalEntry,
        )
        else -> error("Unknown IndexedDB commit journal entry type $type")
    }
}

internal fun ByteArray.readBigEndianULong(offset: Int = 0): ULong {
    var value = 0uL
    repeat(ULong.SIZE_BYTES) { index ->
        value = (value shl Byte.SIZE_BITS) or this[offset + index].toUByte().toULong()
    }
    return value
}

private fun UInt.toBigEndianBytes(): ByteArray = ByteArray(UInt.SIZE_BYTES) { index ->
    (this shr ((UInt.SIZE_BYTES - 1 - index) * Byte.SIZE_BITS)).toByte()
}

private fun ByteArray.readBigEndianUInt(offset: Int): UInt {
    var value = 0u
    repeat(UInt.SIZE_BYTES) { index ->
        value = (value shl Byte.SIZE_BITS) or this[offset + index].toUByte().toUInt()
    }
    return value
}

private const val JournalHeaderSize = 1 + UInt.SIZE_BYTES + ULong.SIZE_BYTES
