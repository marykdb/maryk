package maryk.core.models.migration

import maryk.core.base64.Base64Maryk

enum class MigrationPhase {
    Expand,
    Backfill,
    Verify,
    Contract,

    // Legacy phases kept for backward compatibility when resuming persisted state.
    Startup,
    Migrate,
}

fun MigrationPhase.normalizedRuntimePhase(): MigrationPhase = when (this) {
    MigrationPhase.Startup -> MigrationPhase.Expand
    MigrationPhase.Migrate -> MigrationPhase.Backfill
    else -> this
}

fun MigrationPhase.nextRuntimePhaseOrNull(): MigrationPhase? = when (this.normalizedRuntimePhase()) {
    MigrationPhase.Expand -> MigrationPhase.Backfill
    MigrationPhase.Backfill -> MigrationPhase.Verify
    MigrationPhase.Verify -> MigrationPhase.Contract
    MigrationPhase.Contract -> null
    else -> null
}

fun MigrationPhase.canTransitionTo(next: MigrationPhase): Boolean =
    this.nextRuntimePhaseOrNull() == next.normalizedRuntimePhase()

enum class MigrationStateStatus {
    Running,
    Partial,
    Retry,
    Failed,
}

/**
 * Persisted migration progress state.
 */
data class MigrationState(
    val migrationId: String,
    val phase: MigrationPhase,
    val status: MigrationStateStatus,
    val attempt: UInt,
    val fromVersion: String?,
    val toVersion: String,
    val cursor: ByteArray? = null,
    val message: String? = null,
) {
    fun toPersistedBytes(): ByteArray {
        val stateLines = buildList {
            add("v=1")
            add("migrationId=${migrationId}")
            add("phase=${phase.name}")
            add("status=${status.name}")
            add("attempt=${attempt}")
            add("from=${fromVersion.orEmpty()}")
            add("to=${toVersion}")
            add("cursor=${cursor?.let(Base64Maryk::encode) ?: ""}")
            add("message=${message?.encodeToByteArray()?.let(Base64Maryk::encode) ?: ""}")
        }
        return stateLines.joinToString("\n").encodeToByteArray()
    }

    companion object {
        fun fromPersistedBytes(bytes: ByteArray): MigrationState? {
            val entries = bytes.decodeToString()
                .lineSequence()
                .mapNotNull { line ->
                    val separatorIndex = line.indexOf('=')
                    if (separatorIndex <= 0) {
                        null
                    } else {
                        line.substring(0, separatorIndex) to line.substring(separatorIndex + 1)
                    }
                }
                .toMap()

            if (entries["v"] != "1") return null

            val migrationId = entries["migrationId"] ?: return null
            val phase = entries["phase"]?.let(MigrationPhase::valueOf) ?: return null
            val status = entries["status"]?.let(MigrationStateStatus::valueOf) ?: return null
            val attempt = entries["attempt"]?.toUIntOrNull() ?: return null
            val toVersion = entries["to"] ?: return null

            val fromVersion = entries["from"]?.ifBlank { null }
            val cursor = entries["cursor"]?.ifBlank { null }?.let(Base64Maryk::decode)
            val message = entries["message"]?.ifBlank { null }?.let {
                Base64Maryk.decode(it).decodeToString()
            }

            return MigrationState(
                migrationId = migrationId,
                phase = phase,
                status = status,
                attempt = attempt,
                fromVersion = fromVersion,
                toVersion = toVersion,
                cursor = cursor,
                message = message,
            )
        }
    }
}
