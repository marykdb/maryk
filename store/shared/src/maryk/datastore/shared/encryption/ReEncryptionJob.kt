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

/**
 * One persisted sensitive field selected by a backend rotation adapter.
 *
 * [payload] includes its MKE1/MKE2 store envelope but excludes backend version
 * bytes. [modelId], [recordKey], and [reference] reproduce the authenticated
 * identity used by normal datastore reads and writes. [id] is opaque to this
 * helper and is returned unchanged to the backend write callback.
 */
data class EncryptedFieldRecord(
    val id: ByteArray,
    val modelId: UInt,
    val recordKey: ByteArray,
    val reference: ByteArray,
    val payload: ByteArray,
)

data class ReEncryptionBatch(
    val records: List<EncryptedFieldRecord>,
    val nextCursor: ByteArray?,
)

/**
 * Backend-neutral resumable persisted-field rotation loop. The storage integration
 * supplies bounded reads, idempotent writes, and durable state persistence. MKE1
 * payloads are upgraded and MKE2 payloads are decrypted/re-encrypted with their
 * authenticated field identity. Writes contain a complete MKE2 field payload.
 *
 * This helper does not make those operations atomic; a crash before [persistState]
 * may replay the current batch.
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
        val envelope = requireNotNull(FieldEncryptionEnvelope.fromSensitiveValue(record.payload)) {
            "Re-encryption record does not contain a persisted sensitive-field envelope"
        }
        val payloadOffset = envelope.magic.size
        val payloadLength = record.payload.size - payloadOffset
        if (
            envelope == FieldEncryptionEnvelope.Legacy ||
            provider.needsReEncryption(record.payload, payloadOffset, payloadLength)
        ) {
            val context = FieldEncryptionContext(record.modelId, record.recordKey, record.reference)
            val plain = when (envelope) {
                FieldEncryptionEnvelope.Legacy -> provider.decrypt(record.payload, payloadOffset, payloadLength)
                FieldEncryptionEnvelope.Contextual -> provider.decrypt(
                    context,
                    record.payload,
                    payloadOffset,
                    payloadLength,
                )
            }
            write(
                record.id,
                FieldEncryptionEnvelope.Contextual.magic + provider.encrypt(context, plain),
            )
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
