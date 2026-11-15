package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WriteCache
import maryk.core.protobuf.LengthOnlyWriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.InitialChangesUpdate

/** Resource bounds for serialized input staging and replaying one complete source-version group. */
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
    private val modelReferences = models.mapValues { DataModelReference(it.value) }
    private val replayEnvelopeBounds = mutableMapOf<String, Long>()

    private fun context(modelName: String) = RequestContext(
        DefinitionsContext(modelReferences.toMutableMap()),
        models.getValue(modelName),
    ).also { it.rejectUnknownProtoBufFields = true }

    fun encode(modelName: String, record: DataObjectVersionedChange<IsRootDataModel>, remainingBytes: Int): ByteArray {
        val context = context(modelName)
        val cache = WriteCache()
        val size = DataObjectVersionedChange.Serializer.calculateObjectProtoBufLength(record, cache, context)
        if (size < 0 || size > remainingBytes) {
            throw RequestException("Serialized restore input exceeds the configured byte limit")
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

    /** A version subset cannot exceed its staged record; only the replay envelope is added. */
    fun replayByteUpperBound(modelName: String, events: List<RestoreEvent>): Long {
        require(events.all { it.serializedRecordBytes >= 0 })
        return replayEnvelopeBounds.getOrPut(modelName) {
            // Maximum version width plus room for growth of the nested length prefixes.
            replaySize(modelName, ULong.MAX_VALUE, emptyList()).toLong() + 64L
        } + events.sumOf { it.serializedRecordBytes.toLong() + 10L } // At most five bytes each for a field key and length.
    }

    fun replaySize(modelName: String, snapshotVersion: ULong, changes: List<DataObjectVersionedChange<IsRootDataModel>>): Int =
        UpdateResponse.Serializer.calculateObjectProtoBufLength(
            UpdateResponse(models.getValue(modelName), InitialChangesUpdate(snapshotVersion, changes)),
            LengthOnlyWriteCache,
            context(modelName),
        )
}

internal data class RestoreEvent(
    val modelName: String,
    val ordinal: ULong,
    val record: DataObjectVersionedChange<IsRootDataModel>,
    val serializedRecordBytes: Int,
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
    private val identities = mutableSetOf<Pair<String, Bytes>>()

    fun add(modelName: String, record: DataObjectVersionedChange<IsRootDataModel>) {
        if (records.size >= options.maxStagedRecords) {
            throw RequestException("Backup exceeds restore staging record limit; increase maxStagedRecords")
        }
        if (!identities.add(modelName to Bytes(record.key.bytes.copyOf()))) {
            throw RequestException("Backup contains duplicate record identities")
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
            val serializedRecordBytes = if (record.hasStableStringPayload()) bytes.size else -1
            for (change in record.changes) {
                events += RestoreEvent(modelName, ordinal++, record.copy(changes = listOf(change)), serializedRecordBytes)
            }
        }
        return events.sorted()
    }
}

/** These concrete payloads round-trip without filling context-derived values or object defaults. */
private fun DataObjectVersionedChange<*>.hasStableStringPayload(): Boolean = changes.all { version ->
    version.changes.all { change ->
        when (change) {
            ObjectCreate, is ObjectSoftDeleteChange -> true
            is Change -> change.referenceValuePairs.all { pair ->
                val reference = pair.reference
                if (reference !is IsPropertyReferenceForValues<*, *, *, *> || reference.parentReference != null) false
                else when (val definition = reference.propertyDefinition.definition) {
                    is StringDefinition -> pair.value == null || pair.value is String
                    is ListDefinition<*, *> -> definition.valueDefinition is StringDefinition &&
                        (pair.value == null || (pair.value as? List<*>)?.all { it is String } == true)
                    else -> false
                }
            }
            else -> false
        }
    }
}
