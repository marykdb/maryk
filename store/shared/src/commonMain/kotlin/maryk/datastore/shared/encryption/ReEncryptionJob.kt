package maryk.datastore.shared.encryption

enum class ReEncryptionStatus {
    Running,
    Completed,
}

/**
 * Persist this versioned state after every batch to make re-encryption resumable.
 * A replay is possible when writes succeed but this state has not been persisted.
 */
data class ReEncryptionState(
    val formatVersion: UInt = 1u,
    val targetKeyId: String,
    val cursor: ByteArray? = null,
    val processed: ULong = 0uL,
    val status: ReEncryptionStatus = ReEncryptionStatus.Running,
)

data class EncryptedFieldRecord(
    val id: ByteArray,
    val payload: ByteArray,
)

data class ReEncryptionBatch(
    val records: List<EncryptedFieldRecord>,
    val nextCursor: ByteArray?,
)

/**
 * Backend-neutral resumable rotation loop. The storage integration supplies bounded
 * reads, idempotent writes, and durable state persistence. This helper does not make
 * those operations atomic; a crash before [persistState] may replay the current batch.
 */
suspend fun runReEncryptionBatch(
    provider: KeyringFieldEncryptionProvider,
    state: ReEncryptionState,
    read: suspend (cursor: ByteArray?) -> ReEncryptionBatch,
    write: suspend (id: ByteArray, payload: ByteArray) -> Unit,
    persistState: suspend (ReEncryptionState) -> Unit,
): ReEncryptionState {
    require(state.formatVersion == 1u) { "Unsupported re-encryption state format ${state.formatVersion}" }
    require(state.targetKeyId == provider.activeKeyId) {
        "Re-encryption target `${state.targetKeyId}` is not the active key `${provider.activeKeyId}`"
    }
    if (state.status == ReEncryptionStatus.Completed) return state

    val batch = read(state.cursor)
    var processed = state.processed
    batch.records.forEach { record ->
        if (provider.needsReEncryption(record.payload)) {
            val plain = provider.decrypt(record.payload)
            write(record.id, provider.encrypt(plain))
        }
        processed++
    }
    val next = state.copy(
        cursor = batch.nextCursor,
        processed = processed,
        status = if (batch.nextCursor == null) ReEncryptionStatus.Completed else ReEncryptionStatus.Running,
    )
    persistState(next)
    return next
}
