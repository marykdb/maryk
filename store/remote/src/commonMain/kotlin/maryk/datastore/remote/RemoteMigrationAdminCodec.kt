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

internal object RemoteMigrationAdminCodec {
    private const val version = "1"

    fun encodeRequest(request: RemoteMigrationRequest): ByteArray = buildList {
        add("v=$version")
        add("op=${request.operation.name}")
        request.modelId?.let { add("model=$it") }
        request.reason?.let { add("reason=${encodeString(it)}") }
    }.joinToString("\n").encodeToByteArray()

    fun decodeRequest(bytes: ByteArray): RemoteMigrationRequest {
        val fields = parseFields(bytes)
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

    fun encodeResponse(response: RemoteMigrationResponse): ByteArray = buildList {
        add("v=$version")
        response.accepted?.let { add("accepted=$it") }
        response.statuses.entries.sortedBy { it.key }.forEach { (modelId, status) ->
            add(
                listOf(
                    "s",
                    modelId.toString(),
                    status.state.name,
                    status.phase?.name.orEmpty(),
                    status.attempt?.toString().orEmpty(),
                    status.hasCursor?.toString().orEmpty(),
                    status.etaMs?.toString().orEmpty(),
                    status.message?.let(::encodeString).orEmpty(),
                    status.lastError?.let(::encodeString).orEmpty(),
                ).joinToString("|")
            )
        }
        response.metrics.entries.sortedBy { it.key }.forEach { (modelId, metric) ->
            add(
                listOf(
                    "m",
                    modelId.toString(),
                    metric.started.toString(),
                    metric.completed.toString(),
                    metric.failed.toString(),
                    metric.retries.toString(),
                    metric.partials.toString(),
                    metric.paused.toString(),
                    metric.resumed.toString(),
                    metric.canceled.toString(),
                    metric.lastEventAtMs?.toString().orEmpty(),
                ).joinToString("|")
            )
        }
    }.joinToString("\n").encodeToByteArray()

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
                line.startsWith("accepted=") -> accepted = line.substringAfter('=').toBooleanStrictOrNull()
                    ?: throw IllegalArgumentException("Invalid migration administration acceptance value")
                line.startsWith("s|") -> decodeStatus(line).let { (id, status) -> statuses[id] = status }
                line.startsWith("m|") -> decodeMetrics(line).let { (id, metric) -> metrics[id] = metric }
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

    private fun parseFields(bytes: ByteArray): Map<String, String> =
        bytes.decodeToString().lineSequence().mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
        }.toMap()

    private fun encodeString(value: String): String = Base64Maryk.encode(value.encodeToByteArray())

    private fun decodeString(value: String): String = Base64Maryk.decode(value).decodeToString()

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        try {
            enumValueOf<T>(value)
        } catch (_: IllegalArgumentException) {
            null
        }
}
