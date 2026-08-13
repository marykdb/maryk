package maryk.datastore.foundationdb.model

import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.models.migration.MigrationAuditLogStore
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.foundationdb.Range
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.Transaction
import maryk.foundationdb.tuple.Tuple
import maryk.lib.bytes.combineToByteArray

private const val maxAuditChunkValueSize = 90_000
private const val maxAuditChunkedValueSize = 9_000_000
private const val auditChunkMarker = "migration-audit-v1"
private val auditManifestMagic = byteArrayOf(0, 0x4d, 0x41, 0x55, 0x01)

internal class FoundationDBMigrationAuditLogStore(
    private val tc: TransactionContext,
    private val modelPrefixesById: Map<UInt, ByteArray>,
    private val maxEntries: Int = 1000,
) : MigrationAuditLogStore {
    init {
        require(maxEntries > 0) { "maxEntries should be positive but was $maxEntries" }
    }

    override suspend fun append(modelId: UInt, event: MigrationAuditEvent) {
        append(modelId, event, null)
    }

    suspend fun append(modelId: UInt, event: MigrationAuditEvent, guard: ((Transaction) -> Unit)?) {
        val modelPrefix = modelPrefixesById[modelId] ?: return
        val key = packKey(modelPrefix, modelMigrationAuditLogKey)
        tc.run { tr ->
            guard?.invoke(tr)
            val current = readStoredBytes(tr, modelPrefix, tr.get(key).awaitResult())
                ?.decodeToString()
                ?.lineSequence()
                ?.filter { it.isNotBlank() }
                ?.toMutableList()
                ?: mutableListOf()
            if (current.size >= maxEntries) {
                current.subList(0, current.size - maxEntries + 1).clear()
            }
            current.add(event.toPersistedLine())
            writeStoredBytes(tr, modelPrefix, key, current.joinToString("\n").encodeToByteArray())
        }
    }

    override suspend fun read(modelId: UInt, limit: Int): List<MigrationAuditEvent> {
        val modelPrefix = modelPrefixesById[modelId] ?: return emptyList()
        val key = packKey(modelPrefix, modelMigrationAuditLogKey)
        return tc.run { tr ->
            readStoredBytes(tr, modelPrefix, tr.get(key).awaitResult())
        }?.decodeToString()
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.mapNotNull(MigrationAuditEvent::fromPersistedLine)
            ?.toList()
            ?.takeLast(limit.coerceAtLeast(0))
            ?: emptyList()
    }

    private fun writeStoredBytes(tr: Transaction, modelPrefix: ByteArray, key: ByteArray, bytes: ByteArray) {
        val chunkPrefix = auditChunkPrefix(modelPrefix)
        tr.clear(Range.startsWith(chunkPrefix))
        if (bytes.size <= maxAuditChunkValueSize) {
            tr.set(key, bytes)
            return
        }

        require(bytes.size <= maxAuditChunkedValueSize) {
            "Migration audit log exceeds the bounded chunk format: ${bytes.size} bytes"
        }
        val chunkCount = (bytes.size + maxAuditChunkValueSize - 1) / maxAuditChunkValueSize
        tr.set(key, encodeAuditManifest(bytes.size, chunkCount))
        for (index in 0 until chunkCount) {
            val start = index * maxAuditChunkValueSize
            val end = minOf(start + maxAuditChunkValueSize, bytes.size)
            tr.set(auditChunkKey(chunkPrefix, index), bytes.copyOfRange(start, end))
        }
    }

    private fun readStoredBytes(tr: Transaction, modelPrefix: ByteArray, value: ByteArray?): ByteArray? {
        value ?: return null
        if (!value.isAuditManifest()) return value
        val manifest = requireNotNull(decodeAuditManifest(value)) { "Invalid migration audit chunk manifest" }
        val bytes = ByteArray(manifest.totalSize)
        val chunkPrefix = auditChunkPrefix(modelPrefix)
        var offset = 0
        for (index in 0 until manifest.chunkCount) {
            val chunk = requireNotNull(tr.get(auditChunkKey(chunkPrefix, index)).awaitResult()) {
                "Missing migration audit chunk $index of ${manifest.chunkCount}"
            }
            val expectedSize = minOf(maxAuditChunkValueSize, manifest.totalSize - offset)
            require(chunk.size == expectedSize) {
                "Invalid migration audit chunk $index size: ${chunk.size}, expected $expectedSize"
            }
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }
        require(offset == manifest.totalSize) { "Incomplete migration audit chunk reconstruction" }
        return bytes
    }

    private fun auditChunkPrefix(modelPrefix: ByteArray): ByteArray = combineToByteArray(
        packKey(modelPrefix, modelMigrationAuditLogChunksKey),
        Tuple.from(auditChunkMarker).pack(),
    )

    private fun auditChunkKey(prefix: ByteArray, index: Int): ByteArray =
        combineToByteArray(prefix, Tuple.from(index).pack())
}

private data class AuditChunkManifest(val totalSize: Int, val chunkCount: Int)

private fun encodeAuditManifest(totalSize: Int, chunkCount: Int): ByteArray =
    ByteArray(auditManifestMagic.size + Int.SIZE_BYTES * 2).also { bytes ->
        auditManifestMagic.copyInto(bytes)
        bytes.writeIntBigEndian(auditManifestMagic.size, totalSize)
        bytes.writeIntBigEndian(auditManifestMagic.size + Int.SIZE_BYTES, chunkCount)
    }

private fun decodeAuditManifest(value: ByteArray): AuditChunkManifest? {
    if (!value.isAuditManifest()) return null
    val totalSize = value.readIntBigEndian(auditManifestMagic.size)
    val chunkCount = value.readIntBigEndian(auditManifestMagic.size + Int.SIZE_BYTES)
    if (totalSize !in (maxAuditChunkValueSize + 1)..maxAuditChunkedValueSize || chunkCount <= 1) return null
    if (((totalSize - 1) / maxAuditChunkValueSize) + 1 != chunkCount) return null
    return AuditChunkManifest(totalSize, chunkCount)
}

private fun ByteArray.isAuditManifest(): Boolean =
    size == auditManifestMagic.size + Int.SIZE_BYTES * 2 &&
        copyOfRange(0, auditManifestMagic.size).contentEquals(auditManifestMagic)

private fun ByteArray.readIntBigEndian(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 24) or
        ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or
        (this[offset + 3].toInt() and 0xFF)

private fun ByteArray.writeIntBigEndian(offset: Int, value: Int) {
    this[offset] = ((value ushr 24) and 0xFF).toByte()
    this[offset + 1] = ((value ushr 16) and 0xFF).toByte()
    this[offset + 2] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 3] = (value and 0xFF).toByte()
}
