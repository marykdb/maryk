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

/** Resource bounds for serialized input staging and replaying an atomic version. */
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
            throw RequestException("Backup exceeds restore staging limit; increase maxStagedBytes")
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
) : Comparable<RestoreEvent> {
    val version get() = record.changes.single().version
    override fun compareTo(other: RestoreEvent): Int =
        version.compareTo(other.version).takeIf { it != 0 } ?: ordinal.compareTo(other.ordinal)
}

/** Serialized staging bounds retained bytes and prevents a reader's mutable buffers being retained. */
internal class StagedBackupReader(
    private val codec: RestoreCodec,
    private val options: DataStoreRestoreOptions,
) {
    private val records = mutableListOf<Pair<String, ByteArray>>()
    private var bytes = 0

    fun add(modelName: String, record: DataObjectVersionedChange<IsRootDataModel>) {
        if (records.size >= options.maxStagedRecords) {
            throw RequestException("Backup exceeds restore staging record limit; increase maxStagedRecords")
        }
        val encoded = codec.encode(modelName, record, options.maxStagedBytes - bytes)
        records += modelName to encoded
        bytes += encoded.size
    }

    fun orderedEvents(): List<RestoreEvent> {
        val events = mutableListOf<RestoreEvent>()
        var ordinal = 0uL
        for ((modelName, bytes) in records) {
            val record = codec.decode(modelName, bytes)
            for (change in record.changes) {
                events += RestoreEvent(modelName, ordinal++, record.copy(changes = listOf(change)))
            }
        }
        return events.sorted()
    }
}
