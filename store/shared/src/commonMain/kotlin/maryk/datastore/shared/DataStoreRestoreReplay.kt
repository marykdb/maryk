package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.InitialChangesUpdate

/**
 * A reader that can reopen the same immutable backup on every [read] call.
 * Each call must emit identical chunks, records and versions in the same order.
 * File/object-storage readers can implement this without retaining decoded history.
 */
interface RepeatableDataStoreBackupReader : DataStoreBackupReader

/** Resource bounds for staging one-pass input and replaying an atomic version. */
data class DataStoreRestoreOptions(
    val maxStagedBytes: Int = 64 * 1024 * 1024,
    val maxStagedRecords: Int = 100_000,
    val maxReplayBytes: Int = 8 * 1024 * 1024,
) {
    init {
        require(maxStagedBytes > 0 && maxStagedRecords > 0 && maxReplayBytes > 0) {
            "Restore resource bounds must be positive"
        }
    }
}

internal class RestoreCodec(private val models: Map<String, IsRootDataModel>) {
    private fun context(modelName: String) = RequestContext(
        DefinitionsContext(models.mapValues { DataModelReference(it.value) }.toMutableMap()),
        models.getValue(modelName),
    )

    fun encode(modelName: String, record: DataObjectVersionedChange<IsRootDataModel>, remainingBytes: Int): ByteArray {
        val context = context(modelName)
        val cache = WriteCache()
        val size = DataObjectVersionedChange.Serializer.calculateObjectProtoBufLength(record, cache, context)
        if (size < 0 || size > remainingBytes) {
            throw RequestException("Backup exceeds restore staging limit; use a RepeatableDataStoreBackupReader or increase maxStagedBytes")
        }
        var index = 0
        return ByteArray(size).also { bytes ->
            DataObjectVersionedChange.Serializer.writeObjectProtoBuf(record, cache, { bytes[index++] = it }, context)
        }
    }

    fun decode(modelName: String, bytes: ByteArray): DataObjectVersionedChange<IsRootDataModel> {
        var index = 0
        return DataObjectVersionedChange.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, context(modelName)).toDataObject()
    }

    fun replaySize(modelName: String, snapshotVersion: ULong, changes: List<DataObjectVersionedChange<IsRootDataModel>>): Int =
        UpdateResponse.Serializer.calculateObjectProtoBufLength(
            UpdateResponse(models.getValue(modelName), InitialChangesUpdate(snapshotVersion, changes)),
            WriteCache(),
            context(modelName),
        )
}

internal data class RestoreEvent(
    val modelName: String,
    val ordinal: ULong,
    val record: DataObjectVersionedChange<IsRootDataModel>,
    val encodedSize: Int,
) : Comparable<RestoreEvent> {
    val version get() = record.changes.single().version
    override fun compareTo(other: RestoreEvent): Int =
        version.compareTo(other.version).takeIf { it != 0 } ?: ordinal.compareTo(other.ordinal)
}

/** Serialized staging bounds retained bytes and prevents a reader's mutable buffers being retained. */
internal class StagedBackupReader(
    override val manifest: DataStoreBackupManifest,
    private val codec: RestoreCodec,
    private val options: DataStoreRestoreOptions,
) : RepeatableDataStoreBackupReader {
    private val records = mutableListOf<Pair<String, ByteArray>>()
    private var bytes = 0

    fun add(modelName: String, record: DataObjectVersionedChange<IsRootDataModel>) {
        if (records.size >= options.maxStagedRecords) {
            throw RequestException("Backup exceeds restore staging record limit; use a RepeatableDataStoreBackupReader or increase maxStagedRecords")
        }
        val encoded = codec.encode(modelName, record, options.maxStagedBytes - bytes)
        records += modelName to encoded
        bytes += encoded.size
    }

    override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) {
        for ((modelName, bytes) in records) {
            consumer(DataStoreBackupChunk(modelName, listOf(codec.decode(modelName, bytes))))
        }
    }
}

/**
 * Select one globally earliest atomic version without retaining the whole input or assuming
 * record-key order is version order. One request per version keeps the replay order exact even
 * when models are interleaved, and keeps each RemoteDataStore request bounded.
 */
internal suspend fun nextRestoreBatch(
    reader: RepeatableDataStoreBackupReader,
    after: RestoreEvent?,
    codec: RestoreCodec,
    options: DataStoreRestoreOptions,
): List<RestoreEvent> {
    var selected: RestoreEvent? = null
    var ordinal = 0uL
    reader.read { chunk ->
        for (record in chunk.records) {
            for (change in record.changes) {
                val position = ordinal++
                if (after != null && (change.version < after.version || change.version == after.version && position <= after.ordinal)) continue
                val event = RestoreEvent(chunk.modelName, position, record.copy(changes = listOf(change)), 0)
                if (selected?.let { event >= it } == true) continue
                val size = codec.replaySize(chunk.modelName, reader.manifest.snapshotVersion, listOf(event.record))
                if (size < 0 || size > options.maxReplayBytes) {
                    throw RequestException("One backup version exceeds maxReplayBytes; a single atomic version cannot be split")
                }
                selected = event.copy(encodedSize = size)
            }
        }
    }
    return selected?.let(::listOf).orEmpty()
}
