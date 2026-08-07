package maryk.core.models.migration

import maryk.core.base64.Base64Maryk

/**
 * Internal migration phases.
 *
 * User-provided handlers can run during all phases:
 * `Expand` (`migrationExpandHandler`), `Backfill` (`migrationHandler`),
 * `Verify` (`migrationVerifyHandler`), and `Contract` (`migrationContractHandler`).
 */
enum class MigrationPhase {
    Expand,
    Backfill,
    Verify,
    Contract,
}

fun MigrationPhase.normalizedRuntimePhase(): MigrationPhase = this

fun MigrationPhase.nextRuntimePhaseOrNull(): MigrationPhase? = when (this) {
    MigrationPhase.Expand -> MigrationPhase.Backfill
    MigrationPhase.Backfill -> MigrationPhase.Verify
    MigrationPhase.Verify -> MigrationPhase.Contract
    MigrationPhase.Contract -> null
}

fun MigrationPhase.canTransitionTo(next: MigrationPhase): Boolean =
    this.nextRuntimePhaseOrNull() == next

fun MigrationPhase.remainingRuntimePhaseCount(): Int = when (this) {
    MigrationPhase.Expand -> 4
    MigrationPhase.Backfill -> 3
    MigrationPhase.Verify -> 2
    MigrationPhase.Contract -> 1
}

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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MigrationState) return false

        if (migrationId != other.migrationId) return false
        if (phase != other.phase) return false
        if (status != other.status) return false
        if (attempt != other.attempt) return false
        if (fromVersion != other.fromVersion) return false
        if (toVersion != other.toVersion) return false
        if (!cursor.contentEquals(other.cursor)) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = migrationId.hashCode()
        result = 31 * result + phase.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + attempt.hashCode()
        result = 31 * result + (fromVersion?.hashCode() ?: 0)
        result = 31 * result + toVersion.hashCode()
        result = 31 * result + (cursor?.contentHashCode() ?: 0)
        result = 31 * result + (message?.hashCode() ?: 0)
        return result
    }

    fun toPersistedBytes(): ByteArray {
        if (migrationId.isEmpty() || toVersion.isEmpty()) {
            throw MigrationException("Migration state requires a migration id and target version")
        }
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
        return encodePersistedState(stateLines)
    }

    /**
     * Ensures persisted progress belongs to the migration currently being run before resuming it.
     */
    fun requireMatchingMigration(
        migrationId: String,
        fromVersion: String,
        toVersion: String,
    ) {
        if (
            this.migrationId != migrationId ||
            this.fromVersion != fromVersion ||
            this.toVersion != toVersion
        ) {
            throw MigrationException(
                "Persisted migration state does not match the current migration: " +
                    "expected $migrationId ($fromVersion -> $toVersion), " +
                    "found ${this.migrationId} (${this.fromVersion} -> ${this.toVersion})"
            )
        }
    }

    companion object {
        /**
         * Decodes state that exists in a persistent store. A present state must never be ignored.
         */
        fun requireFromPersistedBytes(bytes: ByteArray): MigrationState {
            if (bytes.size > maximumPersistedStateSize) {
                throw malformedPersistedState()
            }
            val text = try {
                bytes.decodeToString(throwOnInvalidSequence = true)
            } catch (_: Exception) {
                throw malformedPersistedState()
            }
            val entries = mutableMapOf<String, String>()
            for (line in text.split('\n')) {
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) {
                    throw malformedPersistedState()
                }
                val key = line.substring(0, separatorIndex)
                val value = line.substring(separatorIndex + 1)
                if (
                    key !in persistedFieldNames ||
                    value.length > maximumPersistedFieldLength ||
                    entries.put(key, value) != null
                ) {
                    throw malformedPersistedState()
                }
            }
            if (entries.keys != persistedFieldNames) {
                throw malformedPersistedState()
            }
            return fromPersistedEntries(entries)
                ?.takeIf { it.migrationId.isNotEmpty() && it.toVersion.isNotEmpty() }
                ?: throw malformedPersistedState()
        }

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

            return fromPersistedEntries(entries)
        }

        private fun fromPersistedEntries(entries: Map<String, String>): MigrationState? {
            if (entries["v"] != "1") return null

            val migrationId = entries["migrationId"] ?: return null
            val phase = entries["phase"]?.let { enumValueOrNull<MigrationPhase>(it) } ?: return null
            val status = entries["status"]?.let { enumValueOrNull<MigrationStateStatus>(it) } ?: return null
            val attempt = entries["attempt"]?.toUIntOrNull() ?: return null
            val toVersion = entries["to"] ?: return null

            val fromVersion = entries["from"]?.ifBlank { null }
            val cursorEntry = entries["cursor"]?.ifBlank { null }
            val cursor = if (cursorEntry == null) {
                null
            } else {
                cursorEntry.decodeBase64OrNull() ?: return null
            }
            val messageEntry = entries["message"]?.ifBlank { null }
            val message = if (messageEntry == null) {
                null
            } else {
                messageEntry.decodeBase64OrNull()?.decodeUtf8OrNull() ?: return null
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

        private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
            try {
                enumValueOf<T>(name)
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun String.decodeBase64OrNull(): ByteArray? =
            try {
                Base64Maryk.decode(this)
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun ByteArray.decodeUtf8OrNull(): String? =
            try {
                decodeToString(throwOnInvalidSequence = true)
            } catch (_: Exception) {
                null
            }

        private fun encodePersistedState(lines: List<String>): ByteArray {
            if (lines.any { line -> line.substringAfter('=').length > maximumPersistedFieldLength }) {
                throw MigrationException("Migration state contains a field too large to persist")
            }
            return lines.joinToString("\n").encodeToByteArray().also { bytes ->
                if (bytes.size > maximumPersistedStateSize) {
                    throw MigrationException("Migration state is too large to persist")
                }
            }
        }

        private val persistedFieldNames = setOf(
            "v",
            "migrationId",
            "phase",
            "status",
            "attempt",
            "from",
            "to",
            "cursor",
            "message",
        )

        private const val maximumPersistedStateSize = 8 * 1024
        private const val maximumPersistedFieldLength = 4 * 1024

        private fun malformedPersistedState() =
            MigrationException("Persisted migration state is malformed or uses an unsupported format")
    }
}
