package maryk.datastore.remote

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.withTimeoutOrNull
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.backupState

import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.datastore.shared.DataStoreStateBackupChunk
import maryk.datastore.shared.DataStoreStateBackupManifest
import maryk.datastore.shared.DataStoreStateBackupWriter
import maryk.datastore.shared.StateBackupArchiveWriter
import maryk.datastore.shared.openStateBackupArchive
import maryk.datastore.shared.prepareStateBackupChunk

// One manifest (<= 2 MiB), one records frame (<= 2 MiB), completion and framing.
internal const val MAX_STATE_BATCH_ARCHIVE_BYTES = 5 * 1024 * 1024
private const val MAX_STATE_STREAM_FRAME_BYTES = 64 * 1024
private const val STATE_STREAM_HEARTBEAT_MILLIS = 1_000L

/** Reuse state-v2 framing rather than maintaining another record/counter wire representation. */
internal suspend fun encodeStateRestoreBatch(model: IsRootDataModel, chunk: DataStoreStateBackupChunk): ByteArray {
    val detached = prepareStateBackupChunk(model, chunk)
    require(detached.records.isNotEmpty()) { "A remote state batch cannot be empty" }
    val buffer = Buffer()
    var size = 0
    val writer = StateBackupArchiveWriter(mapOf(1u to model), { bytes ->
        if (bytes.size > MAX_STATE_BATCH_ARCHIVE_BYTES - size) throw RequestException("Remote state batch exceeds byte limit")
        size += bytes.size
        buffer.write(bytes)
    }, {})
    writer.begin(DataStoreStateBackupManifest(listOf(model.Meta.name), mapOf(model.Meta.name to model.Meta.version.major)))
    writer.write(detached)
    writer.complete()
    return buffer.readByteArray()
}

/** Finish integrity verification and EOF before allowing the server to mutate its target. */
internal suspend fun decodeStateRestoreBatch(models: Map<UInt, IsRootDataModel>, bytes: ByteArray): DataStoreStateBackupChunk {
    if (bytes.size > MAX_STATE_BATCH_ARCHIVE_BYTES) throw RequestException("Remote state batch exceeds byte limit")
    var position = 0
    val reader = openStateBackupArchive(models) { count ->
        val end = minOf(bytes.size, position + count)
        bytes.copyOfRange(position, end).also { position = end }
    }
    if (reader.manifest.modelNames.size != 1) throw RequestException("Remote state restore requires one model per batch")
    val name = reader.manifest.modelNames.single()
    var batch: DataStoreStateBackupChunk? = null
    reader.read { chunk ->
        if (batch != null || chunk.modelName != name || chunk.records.isEmpty()) throw RequestException("Remote state restore requires one nonempty batch")
        batch = chunk
    }
    val model = models.values.single { it.Meta.name == name }
    return prepareStateBackupChunk(model, batch ?: throw RequestException("Remote state restore is missing its batch"))
}

internal suspend fun receiveStateBackup(
    statement: HttpStatement,
    models: Map<UInt, IsRootDataModel>,
    writer: DataStoreStateBackupWriter,
): DataStoreStateBackupManifest = statement.execute { response ->
    requireSuccess(response, "state-backup")
    requireContentType(response, RemoteStoreProtocol.stateStreamContentType, "state-backup")
    val channel = response.bodyAsChannel()
    try {
        val lengthBytes = ByteArray(4)
        var payload = byteArrayOf()
        var offset = 0
        val reader = openStateBackupArchive(models) read@ { maximum ->
            while (offset == payload.size) {
                val frame = withTimeout(30_000) { readFrameLength(channel, lengthBytes) }
                if (frame == null) {
                    channel.closedCause?.let { throw it }
                    return@read byteArrayOf()
                }
                if (frame.length == 0) continue // A heartbeat is transport framing, not archive data.
                if (frame.length !in 1..MAX_STATE_STREAM_FRAME_BYTES) throw RequestException("Remote state stream frame exceeds byte limit")
                payload = ByteArray(frame.length)
                withTimeout(30_000) { readFramePayload(channel, payload) }
                offset = 0
            }
            val end = minOf(payload.size, offset + maximum)
            payload.copyOfRange(offset, end).also { offset = end }
        }
        writer.begin(reader.manifest)
        reader.read { writer.write(it) }
        writer.complete()
        reader.manifest
    } catch (error: Throwable) {
        channel.cancel(error)
        response.call.cancel()
        throw error
    }
}

/** Cancel state operations even when the application owns the supplied HTTP client. */
internal class RemoteStateBackupRequests {
    private val mutex = Mutex()
    private var closed = false
    private val jobs = mutableSetOf<Job>()

    suspend fun <T> run(block: suspend () -> T): T = coroutineScope {
        val job = requireNotNull(currentCoroutineContext()[Job])
        mutex.withLock {
            check(!closed) { "Remote store is closed" }
            jobs += job
        }
        try { block() } finally { withContext(NonCancellable) { mutex.withLock { jobs -= job } } }
    }

    suspend fun close() {
        val active = mutex.withLock { closed = true; jobs.toList().also { jobs.clear() } }
        active.forEach { it.cancel() }
    }
}

/** Rendezvous delivery bounds buffering; heartbeats detect abandoned captures during backend waits. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun ByteWriteChannel.writeStateBackupStream(
    store: IsDataStore,
    options: DataStoreStateBackupOptions,
    idleTimeoutMillis: Long,
    authorize: suspend (String?) -> Unit,
) = coroutineScope {
    val names = store.dataModelsById.values.map { it.Meta.name }
    suspend fun authorizeAll() {
        if (names.isEmpty()) authorize(null) else names.forEach { authorize(it) }
    }
    val stream = produce<ByteArray>(capacity = 0) {
        val archive = StateBackupArchiveWriter(store.dataModelsById, { bytes ->
            // Transport chunks stay small and do not depend on the archive's internal frame layout.
            for (offset in bytes.indices step MAX_STATE_STREAM_FRAME_BYTES) {
                send(bytes.copyOfRange(offset, minOf(bytes.size, offset + MAX_STATE_STREAM_FRAME_BYTES)))
            }
        }, {})
        store.backupState(object : DataStoreStateBackupWriter by archive {
            override suspend fun write(chunk: DataStoreStateBackupChunk) {
                authorize(chunk.modelName)
                archive.write(chunk)
            }
            override suspend fun complete() {
                authorizeAll()
                archive.complete()
            }
        }, options)
    }
    try {
        while (true) {
            val result = withTimeoutOrNull(STATE_STREAM_HEARTBEAT_MILLIS) { stream.receiveCatching() }
            val bytes = if (result == null) {
                authorizeAll()
                byteArrayOf()
            } else {
                result.exceptionOrNull()?.let { throw it }
                result.getOrNull() ?: break
            }
            withTimeout(idleTimeoutMillis) {
                writeFully(RemoteStoreCodec.lengthPrefix(bytes.size))
                writeFully(bytes)
                flush()
            }
        }
    } finally { stream.cancel() }
}

/**
 * Active jobs are bounded by normal call admission. Short, bounded cancellation tombstones cover
 * a cancel arriving before its GET; captures cannot start after the startup deadline has elapsed.
 */
internal class RemoteStateCaptureRegistry(private val capacity: Int) {
    private data class Capture(val owner: String, val job: Job)
    private val mutex = Mutex()
    private val captures = mutableMapOf<String, Capture>()
    private val cancelled = mutableMapOf<Pair<String, String>, TimeMark>()
    private val completed = mutableMapOf<Pair<String, String>, TimeMark>()

    private fun prune() {
        cancelled.entries.removeAll { it.value.elapsedNow() >= 60.seconds }
        completed.entries.removeAll { it.value.elapsedNow() >= 60.seconds }
    }

    suspend fun <T> capture(id: String, owner: String, requestedAt: TimeMark, block: suspend () -> T): T = coroutineScope {
        val job = requireNotNull(currentCoroutineContext()[Job])
        val key = id to owner
        mutex.withLock {
            prune()
            if (requestedAt.elapsedNow() >= 30.seconds) throw RequestException("State capture startup timed out")
            if (key in cancelled) throw RequestException("State capture was cancelled")
            if (id in captures || key in completed) throw RequestException("State capture identity was already used")
            // Fail closed if pending cancellations saturate their bounded registration window.
            if (cancelled.size >= capacity) throw RequestException("State capture cancellation capacity is exhausted; retry later")
            captures[id] = Capture(owner, job)
        }
        try { block() } finally {
            withContext(NonCancellable) { mutex.withLock {
                captures.remove(id)
                prune()
                if (completed.size >= capacity) completed.remove(completed.keys.first())
                completed[key] = TimeSource.Monotonic.markNow()
            } }
        }
    }

    suspend fun cancel(id: String, owner: String, allowPending: Boolean) {
        val job = mutex.withLock {
            prune()
            val active = captures[id]
            if (active != null) return@withLock active.takeIf { it.owner == owner }?.job
            val key = id to owner
            if (allowPending && key !in completed && cancelled.size < capacity) cancelled[key] = TimeSource.Monotonic.markNow()
            null
        }
        job?.cancel()
    }
}
