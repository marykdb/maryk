package maryk.datastore.remote

import maryk.core.base64.Base64Maryk
import maryk.core.models.migration.MigrationMetrics
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationRuntimeState
import maryk.core.models.migration.MigrationRuntimeStatus

internal enum class RemoteMigrationOperation {
    Status,
    Pause,
    Resume,
    Cancel,
}

internal data class RemoteMigrationRequest(
    val operation: RemoteMigrationOperation,
    val modelId: UInt? = null,
    val reason: String? = null,
)

internal data class RemoteMigrationResponse(
    val statuses: Map<UInt, MigrationRuntimeStatus> = emptyMap(),
    val metrics: Map<UInt, MigrationMetrics> = emptyMap(),
    val accepted: Boolean? = null,
)

internal class RemoteMigrationResponseTooLargeException(
    maxBytes: Int,
) : IllegalArgumentException(
    "Remote migration administration response exceeds max size: limit is $maxBytes bytes"
)

internal object RemoteMigrationAdminCodec {
    private const val version = "1"

    fun encodeRequest(request: RemoteMigrationRequest): ByteArray = buildList {
        add("v=$version")
        add("op=${request.operation.name}")
        request.modelId?.let { add("model=$it") }
        request.reason?.let { add("reason=${encodeString(it)}") }
    }.joinToString("\n").encodeToByteArray()

    fun decodeRequest(bytes: ByteArray): RemoteMigrationRequest {
        val fields = parseFields(
            bytes,
            setOf("v", "op", "model", "reason"),
        )
        require(fields["v"] == version) { "Unsupported migration administration request format" }
        val operation = fields["op"]?.let { enumValueOrNull<RemoteMigrationOperation>(it) }
            ?: throw IllegalArgumentException("Missing migration administration operation")
        val modelId = fields["model"]?.toUIntOrNull()
        require(fields["model"] == null || modelId != null) { "Invalid migration model id" }
        val reason = fields["reason"]?.let(::decodeString)
        when (operation) {
            RemoteMigrationOperation.Status -> require(modelId == null && reason == null) {
                "Migration status does not accept a model id or reason"
            }
            RemoteMigrationOperation.Pause,
            RemoteMigrationOperation.Resume -> require(modelId != null && reason == null) {
                "Migration $operation requires a model id and does not accept a reason"
            }
            RemoteMigrationOperation.Cancel -> require(modelId != null) {
                "Migration cancel requires a model id"
            }
        }
        return RemoteMigrationRequest(
            operation = operation,
            modelId = modelId,
            reason = reason,
        )
    }

    fun encodeResponse(
        response: RemoteMigrationResponse,
        maxBytes: Int = MAX_MIGRATION_ADMIN_RESPONSE_BYTES,
    ): ByteArray {
        val writer = BoundedResponseWriter(maxBytes)
        writer.append("v=$version")
        response.accepted?.let {
            writer.append("\naccepted=$it")
        }
        response.statuses.entries.sortedBy { it.key }.forEach { (modelId, status) ->
            writer.append("\ns|$modelId|${status.state.name}|")
            writer.append(status.phase?.name.orEmpty())
            writer.append("|")
            writer.append(status.attempt?.toString().orEmpty())
            writer.append("|")
            writer.append(status.hasCursor?.toString().orEmpty())
            writer.append("|")
            writer.append(status.etaMs?.toString().orEmpty())
            writer.append("|")
            status.message?.let(writer::appendEncodedString)
            writer.append("|")
            status.lastError?.let(writer::appendEncodedString)
        }
        response.metrics.entries.sortedBy { it.key }.forEach { (modelId, metric) ->
            writer.append(
                "\nm|$modelId|${metric.started}|${metric.completed}|${metric.failed}|${metric.retries}" +
                    "|${metric.partials}|${metric.paused}|${metric.resumed}|${metric.canceled}" +
                    "|${metric.lastEventAtMs?.toString().orEmpty()}"
            )
        }
        return writer.toByteArray()
    }

    fun decodeResponse(bytes: ByteArray): RemoteMigrationResponse {
        val lines = bytes.decodeToString().lineSequence().toList()
        require(lines.firstOrNull() == "v=$version") {
            "Unsupported migration administration response format"
        }
        val statuses = mutableMapOf<UInt, MigrationRuntimeStatus>()
        val metrics = mutableMapOf<UInt, MigrationMetrics>()
        var accepted: Boolean? = null
        lines.drop(1).forEach { line ->
            when {
                line.startsWith("accepted=") -> {
                    require(accepted == null) { "Duplicate migration administration acceptance value" }
                    accepted = line.substringAfter('=').toBooleanStrictOrNull()
                        ?: throw IllegalArgumentException("Invalid migration administration acceptance value")
                }
                line.startsWith("s|") -> decodeStatus(line).let { (id, status) ->
                    require(statuses.put(id, status) == null) {
                        "Duplicate migration administration status for model id: $id"
                    }
                }
                line.startsWith("m|") -> decodeMetrics(line).let { (id, metric) ->
                    require(metrics.put(id, metric) == null) {
                        "Duplicate migration administration metrics for model id: $id"
                    }
                }
                line.isNotEmpty() -> throw IllegalArgumentException("Invalid migration administration response line")
            }
        }
        return RemoteMigrationResponse(statuses, metrics, accepted)
    }

    fun decodeResponse(
        bytes: ByteArray,
        operation: RemoteMigrationOperation,
    ): RemoteMigrationResponse = decodeResponse(bytes).also { response ->
        when (operation) {
            RemoteMigrationOperation.Status -> require(response.accepted == null) {
                "Migration status response cannot contain an acceptance value"
            }
            RemoteMigrationOperation.Pause,
            RemoteMigrationOperation.Resume,
            RemoteMigrationOperation.Cancel -> require(response.accepted != null) {
                "Migration control response is missing an acceptance value"
            }
        }
    }

    private fun decodeStatus(line: String): Pair<UInt, MigrationRuntimeStatus> {
        val parts = line.split('|')
        require(parts.size == 9) { "Invalid migration status response" }
        val id = parts[1].toUIntOrNull() ?: throw IllegalArgumentException("Invalid migration status model id")
        val state = enumValueOrNull<MigrationRuntimeState>(parts[2])
            ?: throw IllegalArgumentException("Invalid migration runtime state")
        val phase = parts[3].takeIf(String::isNotEmpty)?.let { enumValueOrNull<MigrationPhase>(it) }
        require(parts[3].isEmpty() || phase != null) { "Invalid migration phase" }
        val attempt = parts[4].takeIf(String::isNotEmpty)?.toUIntOrNull()
        require(parts[4].isEmpty() || attempt != null) { "Invalid migration attempt" }
        val hasCursor = parts[5].takeIf(String::isNotEmpty)?.toBooleanStrictOrNull()
        require(parts[5].isEmpty() || hasCursor != null) { "Invalid migration cursor flag" }
        val etaMs = parts[6].takeIf(String::isNotEmpty)?.toLongOrNull()
        require(parts[6].isEmpty() || etaMs != null) { "Invalid migration estimate" }
        return id to MigrationRuntimeStatus(
            state = state,
            phase = phase,
            attempt = attempt,
            hasCursor = hasCursor,
            etaMs = etaMs,
            message = parts[7].takeIf(String::isNotEmpty)?.let(::decodeString),
            lastError = parts[8].takeIf(String::isNotEmpty)?.let(::decodeString),
        )
    }

    private fun decodeMetrics(line: String): Pair<UInt, MigrationMetrics> {
        val parts = line.split('|')
        require(parts.size == 11) { "Invalid migration metrics response" }
        val values = parts.drop(1).take(9).map { it.toUIntOrNull() }
        require(values.none { it == null }) { "Invalid migration metrics value" }
        val lastEventAtMs = parts[10].takeIf(String::isNotEmpty)?.toLongOrNull()
        require(parts[10].isEmpty() || lastEventAtMs != null) { "Invalid migration event time" }
        return values[0]!! to MigrationMetrics(
            started = values[1]!!,
            completed = values[2]!!,
            failed = values[3]!!,
            retries = values[4]!!,
            partials = values[5]!!,
            paused = values[6]!!,
            resumed = values[7]!!,
            canceled = values[8]!!,
            lastEventAtMs = lastEventAtMs,
        )
    }

    private fun parseFields(
        bytes: ByteArray,
        allowedNames: Set<String>,
    ): Map<String, String> = buildMap {
        bytes.decodeToString().lineSequence().forEach { line ->
            val separator = line.indexOf('=')
            require(separator > 0) { "Invalid migration administration request field" }
            val name = line.substring(0, separator)
            require(name in allowedNames) { "Unknown migration administration request field: $name" }
            require(put(name, line.substring(separator + 1)) == null) {
                "Duplicate migration administration request field: $name"
            }
        }
    }

    private fun encodeString(value: String): String = Base64Maryk.encode(value.encodeToByteArray())

    private fun decodeString(value: String): String = Base64Maryk.decode(value).decodeToString()

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        try {
            enumValueOf<T>(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    private class BoundedResponseWriter(
        private val maxBytes: Int,
    ) {
        private val response = StringBuilder()

        init {
            require(maxBytes > 0) { "Migration administration response max size must be positive" }
        }

        fun append(value: String) {
            if (value.length > maxBytes - response.length) {
                throw RemoteMigrationResponseTooLargeException(maxBytes)
            }
            response.append(value)
        }

        fun appendEncodedString(value: String) {
            if (base64EncodedLength(value) > maxBytes - response.length) {
                throw RemoteMigrationResponseTooLargeException(maxBytes)
            }
            response.append(encodeString(value))
        }

        fun toByteArray(): ByteArray = response.toString().encodeToByteArray()

        private fun base64EncodedLength(value: String): Long {
            var inputBytes = 0L
            var index = 0
            while (index < value.length) {
                val character = value[index]
                inputBytes += when {
                    character.code < 0x80 -> 1
                    character.code < 0x800 -> 2
                    character.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate() -> {
                        index++
                        4
                    }
                    character.isSurrogate() -> 3
                    else -> 3
                }
                index++
            }
            return inputBytes / 3 * 4 + when ((inputBytes % 3).toInt()) {
                0 -> 0
                1 -> 2
                else -> 3
            }
        }
    }
}

internal const val MAX_MIGRATION_ADMIN_RESPONSE_BYTES = 16 * 1024 * 1024
