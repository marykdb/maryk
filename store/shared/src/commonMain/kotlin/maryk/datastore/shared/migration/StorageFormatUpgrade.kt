package maryk.datastore.shared.migration

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initULong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationState
import maryk.core.models.migration.MigrationStateStatus
import maryk.core.models.migration.StorageFormat

/** Per-model opening repairs: byte 0 is the format, byte 1 has clock/history bits 1/2. */
val StorageMaintenanceMetadataKey = "maryk.storage/maintenance".encodeToByteArray()
val StorageFormatMetadataKey = "maryk.storage/format".encodeToByteArray()
val StorageFormatUpgradeStateKey = "maryk.storage/upgrade".encodeToByteArray()
private val StagePrefix = "maryk.storage/stage/".encodeToByteArray()
private val TargetPrefix = "maryk.storage/target/".encodeToByteArray()
private const val BatchSize = 64u

data class StorageFormatRow(val key: ByteArray, val value: ByteArray)
data class StorageFormatWrite(val namespace: Int, val key: ByteArray, val value: ByteArray?)

/** Backend 0 is metadata; other namespaces have a stable, persisted-model-derived order. */
interface StorageFormatUpgradeStore {
    val namespaceCount: Int
    val identity: String
    suspend fun get(namespace: Int, key: ByteArray): ByteArray?
    suspend fun scan(namespace: Int, after: ByteArray?, limit: UInt): List<StorageFormatRow>
    /** Data/checkpoint writes must commit atomically, including upgrade ownership checks. */
    suspend fun commit(writes: List<StorageFormatWrite>)
    suspend fun convert(namespace: Int, row: StorageFormatRow): StorageFormatRow
    /** Existing guarded clock/history repairs run before publishing the current stamp. */
    suspend fun preparePublication() = Unit
}

/** Read before decoding any format-sensitive model data. */
suspend fun StorageFormatUpgradeStore.readStorageFormat(): Int {
    return decodeStorageFormat(get(0, StorageFormatMetadataKey), get(0, StorageFormatUpgradeStateKey))
}

/** A completed publication atomically removes its checkpoint. Conflicting metadata is unsafe to open. */
fun decodeStorageFormat(bytes: ByteArray?, upgradeState: ByteArray?): Int = decodeStorageFormat(bytes).also { format ->
    if (format == StorageFormat.CURRENT && upgradeState != null) {
        throw MigrationException("Current storage format has an unfinished upgrade checkpoint; restore consistent upgrade metadata before reopening")
    }
}

fun decodeStorageFormat(bytes: ByteArray?): Int {
    bytes ?: return StorageFormat.LEGACY
    val format = bytes.decodeToString().toIntOrNull()
        ?: throw MigrationException("Malformed storage format metadata")
    if (format !in StorageFormat.LEGACY..StorageFormat.CURRENT) {
        throw MigrationException("Unsupported storage format $format; supported through ${StorageFormat.CURRENT}")
    }
    return format
}

/**
 * Stage only changed cells, then remove their old identities before installing new ones.
 * Staging leaves unrelated records/indexes untouched and makes key movement restart-safe.
 * A present checkpoint always takes precedence over the old applied-format stamp.
 */
suspend fun StorageFormatUpgradeStore.upgradeStorageFormat() {
    if (readStorageFormat() == StorageFormat.CURRENT) return
    val migrationId = "storage:${StorageFormat.LEGACY}->${StorageFormat.CURRENT}:$identity"
    val persistedState = get(0, StorageFormatUpgradeStateKey)
    var state = persistedState?.let(MigrationState::requireFromPersistedBytes)
        ?: MigrationState(migrationId, MigrationPhase.Expand, MigrationStateStatus.Running, 1u, StorageFormat.LEGACY.toString(), StorageFormat.CURRENT.toString())
    state.requireMatchingMigration(migrationId, StorageFormat.LEGACY.toString(), StorageFormat.CURRENT.toString())
    if (persistedState != null) validateResumeState(state)
    suspend fun checkpoint(next: MigrationState, writes: List<StorageFormatWrite> = emptyList()) {
        commit(writes + StorageFormatWrite(0, StorageFormatUpgradeStateKey, next.toPersistedBytes()))
        state = next
    }

    if (state.phase == MigrationPhase.Expand) {
        var cursor = decodeCursor(state.cursor)
        while (cursor.namespace <= namespaceCount) {
            val rows = scan(cursor.namespace, cursor.key, BatchSize)
            if (rows.isEmpty()) {
                cursor = Cursor(cursor.namespace + 1, null, cursor.sequence)
                checkpoint(state.copy(cursor = cursor.encode()))
                continue
            }
            val writes = mutableListOf<StorageFormatWrite>()
            val targets = mutableMapOf<String, ByteArray>()
            for (row in rows) {
                val converted = convert(cursor.namespace, row)
                if (converted.key.contentEquals(row.key) && converted.value.contentEquals(row.value)) continue
                val target = targetKey(cursor.namespace, converted.key)
                val previousOwner = targets[target.toHex()] ?: get(0, target)
                if (previousOwner != null && !previousOwner.contentEquals(row.key)) {
                    throw MigrationException("Storage conversion collides in namespace ${cursor.namespace}")
                }
                if (!converted.key.contentEquals(row.key)) {
                    get(cursor.namespace, converted.key)?.let { existing ->
                        val existingTarget = convert(cursor.namespace, StorageFormatRow(converted.key, existing))
                        if (existingTarget.key.contentEquals(converted.key)) {
                            throw MigrationException("Storage conversion collides with an unchanged row in namespace ${cursor.namespace}")
                        }
                    }
                }
                require(cursor.sequence != ULong.MAX_VALUE) { "Storage upgrade entry counter exhausted" }
                val stageKey = StagePrefix + (++cursor.sequence).bytes()
                writes += StorageFormatWrite(0, stageKey, encodeEntry(cursor.namespace, row.key, converted))
                writes += StorageFormatWrite(0, target, row.key)
                targets[target.toHex()] = row.key
            }
            cursor = cursor.copy(key = rows.last().key)
            checkpoint(state.copy(cursor = cursor.encode()), writes)
        }
        checkpoint(state.copy(phase = MigrationPhase.Backfill, cursor = null))
    }

    if (state.phase == MigrationPhase.Backfill || state.phase == MigrationPhase.Verify) {
        for (phase in listOf(MigrationPhase.Backfill, MigrationPhase.Verify)) {
            if (state.phase != phase) continue
            while (true) {
                val rows = stagedRows(state.cursor)
                if (rows.isEmpty()) break
                val writes = rows.map { staged ->
                    val entry = decodeEntry(staged.value)
                    if (phase == MigrationPhase.Backfill) StorageFormatWrite(entry.namespace, entry.oldKey, null)
                    else StorageFormatWrite(entry.namespace, entry.row.key, entry.row.value)
                }
                checkpoint(state.copy(cursor = rows.last().key), writes)
            }
            checkpoint(state.copy(phase = if (phase == MigrationPhase.Backfill) MigrationPhase.Verify else MigrationPhase.Contract, cursor = null))
        }
    }

    if (state.phase == MigrationPhase.Contract && state.message != "cleanup") {
        while (true) {
            val rows = stagedRows(state.cursor)
            if (rows.isEmpty()) break
            for (staged in rows) {
                val entry = decodeEntry(staged.value)
                if (get(entry.namespace, entry.row.key)?.contentEquals(entry.row.value) != true) {
                    throw MigrationException("Storage upgrade verification failed in namespace ${entry.namespace}")
                }
            }
            checkpoint(state.copy(cursor = rows.last().key))
        }
        preparePublication()
        checkpoint(state.copy(cursor = null, message = "cleanup"))
    }
    for (prefix in listOf(StagePrefix, TargetPrefix, StorageFormatTokenPrefix)) {
        while (true) {
            val rows = scan(0, prefix, BatchSize).takeWhile { it.key.startsWith(prefix) }
            if (rows.isEmpty()) break
            commit(rows.map { StorageFormatWrite(0, it.key, null) })
        }
    }
    commit(listOf(StorageFormatWrite(0, StorageFormatMetadataKey, StorageFormat.CURRENT.toString().encodeToByteArray()), StorageFormatWrite(0, StorageFormatUpgradeStateKey, null)))
}

private suspend fun StorageFormatUpgradeStore.stagedRows(after: ByteArray?) =
    scan(0, after ?: StagePrefix, BatchSize).takeWhile { it.key.startsWith(StagePrefix) }

/** Validate the durable work inventory before a resumed migration can delete any more source rows. */
private suspend fun StorageFormatUpgradeStore.validateResumeState(state: MigrationState) {
    val stateCursor = state.cursor
    val cleanup = state.phase == MigrationPhase.Contract && state.message == "cleanup"
    if (state.message == "cleanup" && !cleanup || cleanup && state.cursor != null) {
        throw malformedUpgrade("invalid cleanup checkpoint")
    }
    val expand = if (state.phase == MigrationPhase.Expand) try {
        decodeCursor(state.cursor)
    } catch (_: IllegalArgumentException) {
        throw malformedUpgrade("invalid expansion cursor")
    } else null
    if (expand != null && (expand.namespace.toLong() > namespaceCount.toLong() + 1 ||
            expand.namespace == namespaceCount + 1 && expand.key != null)) {
        throw malformedUpgrade("expansion cursor is outside the namespace inventory")
    }
    if (expand == null && stateCursor != null) stageSequence(stateCursor)
    if (get(0, StagePrefix) != null) throw malformedUpgrade("invalid staging key")

    var after: ByteArray = StagePrefix
    var sequence = 0uL
    var foundCursor = state.cursor == null || expand != null
    while (true) {
        val rows = stagedRows(after)
        if (rows.isEmpty()) break
        for (row in rows) {
            val next = stageSequence(row.key)
            if ((!cleanup || sequence != 0uL) && next != sequence + 1uL) {
                throw malformedUpgrade("missing or reordered staged entry")
            }
            sequence = next
            val entry = try {
                decodeEntry(row.value)
            } catch (_: IllegalArgumentException) {
                throw malformedUpgrade("invalid staged entry")
            }
            if (entry.namespace !in 1..namespaceCount) throw malformedUpgrade("invalid staged namespace")
            if (!cleanup && get(0, targetKey(entry.namespace, entry.row.key))?.contentEquals(entry.oldKey) != true) {
                throw malformedUpgrade("missing or inconsistent staged target ownership")
            }
            if (row.key.contentEquals(state.cursor)) foundCursor = true
        }
        after = rows.last().key
    }
    if (expand != null && expand.sequence != sequence) throw malformedUpgrade("staged entry count differs from its checkpoint")
    if (!foundCursor) throw malformedUpgrade("checkpoint refers to a missing staged entry")
    if (!cleanup) {
        var targetCount = 0uL
        var targetAfter = TargetPrefix
        while (true) {
            val rows = scan(0, targetAfter, BatchSize).takeWhile { it.key.startsWith(TargetPrefix) }
            if (rows.isEmpty()) break
            targetCount += rows.size.toULong()
            targetAfter = rows.last().key
        }
        if (targetCount != sequence || get(0, TargetPrefix) != null) {
            throw malformedUpgrade("staging inventory differs from its target ownership inventory")
        }
    }
}

private fun stageSequence(key: ByteArray): ULong {
    if (key.size != StagePrefix.size + 8 || !key.startsWith(StagePrefix)) throw malformedUpgrade("invalid staging cursor or key")
    var index = StagePrefix.size
    return initULong(reader = { key[index++] }).also { if (it == 0uL) throw malformedUpgrade("invalid staging sequence") }
}

private fun malformedUpgrade(reason: String) = MigrationException(
    "Malformed storage upgrade state: $reason; no further migration writes were applied. Restore authoritative checkpoint and staging data before reopening"
)

private data class Cursor(val namespace: Int, val key: ByteArray?, var sequence: ULong) {
    fun encode(): ByteArray = namespace.toUInt().bytes() + sequence.bytes() + (key ?: byteArrayOf())
}
private fun decodeCursor(bytes: ByteArray?): Cursor {
    bytes ?: return Cursor(1, null, 0u)
    require(bytes.size >= 12) { "Invalid storage upgrade cursor" }
    var index = 0
    val namespace = initUInt(reader = { bytes[index++] }).toInt()
    val sequence = initULong(reader = { bytes[index++] })
    require(namespace > 0) { "Invalid storage upgrade namespace" }
    return Cursor(namespace, bytes.copyOfRange(index, bytes.size).takeIf { it.isNotEmpty() }, sequence)
}

private data class Entry(val namespace: Int, val oldKey: ByteArray, val row: StorageFormatRow)
private fun encodeEntry(namespace: Int, oldKey: ByteArray, row: StorageFormatRow) =
    namespace.toUInt().bytes() + oldKey.size.toUInt().bytes() + row.key.size.toUInt().bytes() + oldKey + row.key + row.value
private fun decodeEntry(bytes: ByteArray): Entry {
    require(bytes.size >= 12) { "Invalid staged storage row" }
    var index = 0
    val namespace = initUInt(reader = { bytes[index++] }).toInt()
    val oldLength = initUInt(reader = { bytes[index++] }).toInt()
    val newLength = initUInt(reader = { bytes[index++] }).toInt()
    require(oldLength >= 0 && newLength >= 0 && oldLength.toLong() + newLength <= bytes.size - index) { "Invalid staged storage row lengths" }
    val oldKey = bytes.copyOfRange(index, index + oldLength).also { index += oldLength }
    val newKey = bytes.copyOfRange(index, index + newLength).also { index += newLength }
    return Entry(namespace, oldKey, StorageFormatRow(newKey, bytes.copyOfRange(index, bytes.size)))
}
private suspend fun targetKey(namespace: Int, key: ByteArray) =
    TargetPrefix + CryptographyProvider.Default.get(SHA256).hasher().hash(namespace.toUInt().bytes() + key)
private fun UInt.bytes() = ByteArray(4).also { bytes -> var index = 0; writeBytes({ bytes[index++] = it }) }
private fun ULong.bytes() = ByteArray(8).also { bytes -> var index = 0; writeBytes({ bytes[index++] = it }) }
private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
private fun ByteArray.toHex() = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
