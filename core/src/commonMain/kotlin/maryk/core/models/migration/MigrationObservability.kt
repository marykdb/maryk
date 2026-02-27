package maryk.core.models.migration

import maryk.core.base64.Base64Maryk

data class MigrationRetryPolicy(
    val maxAttempts: UInt? = null,
    val maxRetryOutcomes: UInt? = null,
)

enum class MigrationAuditEventType {
    LeaseAcquired,
    LeaseRejected,
    PhaseStarted,
    PhaseCompleted,
    Partial,
    RetryScheduled,
    Failed,
    Paused,
    Resumed,
    Canceled,
    Completed,
}

data class MigrationAuditEvent(
    val timestampMs: Long,
    val modelId: UInt,
    val migrationId: String,
    val type: MigrationAuditEventType,
    val phase: MigrationPhase? = null,
    val attempt: UInt? = null,
    val message: String? = null,
) {
    fun toPersistedLine(): String = buildString {
        append("v=1;")
        append("ts=").append(timestampMs).append(';')
        append("model=").append(modelId).append(';')
        append("migration=").append(Base64Maryk.encode(migrationId.encodeToByteArray())).append(';')
        append("type=").append(type.name).append(';')
        append("phase=").append(phase?.name.orEmpty()).append(';')
        append("attempt=").append(attempt?.toString().orEmpty()).append(';')
        append("message=").append(message?.encodeToByteArray()?.let(Base64Maryk::encode).orEmpty())
    }

    fun toLogLine(): String = buildString {
        append("migration")
        append(" modelId=").append(modelId)
        append(" migrationId=").append(migrationId)
        append(" type=").append(type.name)
        phase?.let { append(" phase=").append(it.name) }
        attempt?.let { append(" attempt=").append(it) }
        message?.let { append(" message=").append(it) }
        append(" timestampMs=").append(timestampMs)
    }

    companion object {
        fun fromPersistedLine(line: String): MigrationAuditEvent? {
            val entries = line.split(';')
                .mapNotNull { entry ->
                    val split = entry.indexOf('=')
                    if (split <= 0) null else entry.substring(0, split) to entry.substring(split + 1)
                }
                .toMap()
            if (entries["v"] != "1") return null
            val timestampMs = entries["ts"]?.toLongOrNull() ?: return null
            val modelId = entries["model"]?.toUIntOrNull() ?: return null
            val migrationId = entries["migration"]?.let(Base64Maryk::decode)?.decodeToString() ?: return null
            val type = entries["type"]?.let(MigrationAuditEventType::valueOf) ?: return null
            val phase = entries["phase"]?.ifBlank { null }?.let(MigrationPhase::valueOf)
            val attempt = entries["attempt"]?.ifBlank { null }?.toUIntOrNull()
            val message = entries["message"]?.ifBlank { null }?.let { Base64Maryk.decode(it).decodeToString() }
            return MigrationAuditEvent(
                timestampMs = timestampMs,
                modelId = modelId,
                migrationId = migrationId,
                type = type,
                phase = phase,
                attempt = attempt,
                message = message,
            )
        }
    }
}

data class MigrationMetrics(
    val started: UInt = 0u,
    val completed: UInt = 0u,
    val failed: UInt = 0u,
    val retries: UInt = 0u,
    val partials: UInt = 0u,
    val paused: UInt = 0u,
    val resumed: UInt = 0u,
    val canceled: UInt = 0u,
    val lastEventAtMs: Long? = null,
)

interface MigrationAuditLogStore {
    suspend fun append(modelId: UInt, event: MigrationAuditEvent)
    suspend fun read(modelId: UInt, limit: Int = 100): List<MigrationAuditEvent>
}

fun defaultMigrationAuditEventReporter(event: MigrationAuditEvent) {
    println(event.toLogLine())
}
