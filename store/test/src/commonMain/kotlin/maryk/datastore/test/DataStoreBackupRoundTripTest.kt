package maryk.datastore.test

import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.change
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupReader
import maryk.datastore.shared.DataStoreBackupWriter
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.backup
import maryk.datastore.shared.restore
import maryk.test.models.SimpleMarykModel
import kotlin.test.assertEquals

/** Portable backup contract exercised by persistent datastore implementations. */
class DataStoreBackupRoundTripTest(
    private val source: IsDataStore,
    private val target: IsDataStore,
    private val useAutomaticSnapshot: Boolean = true,
) {
    suspend fun restoresCurrentValueAndCompleteHistory() {
        val add = source.execute(
            SimpleMarykModel.add(SimpleMarykModel.create { value with "ha before backup" })
        )
        val key = assertStatusIs<AddSuccess<SimpleMarykModel>>(add.statuses.single()).key
        val change = source.execute(
            SimpleMarykModel.change(
                key.change(Change(SimpleMarykModel { value::ref } with "ha after backup"))
            )
        )
        val latestVersion = assertStatusIs<ChangeSuccess<SimpleMarykModel>>(change.statuses.single()).version

        val backup = SerializedBackup(source)
        source.backup(
            backup,
            snapshotVersion = if (useAutomaticSnapshot) null else latestVersion + 1uL,
            batchSize = 1u,
        )

        assertEquals(1uL, target.restore(backup).records)
        assertEquals(
            "ha after backup",
            target.execute(SimpleMarykModel.get(key)).values.single().values { value },
        )
        assertEquals(
            2,
            target.execute(SimpleMarykModel.getChanges(key)).changes.single().changes.size,
        )
    }
}

/**
 * Test transport which forces backup records through the same protobuf model encoding used by
 * portable consumers. This prevents the contract test from accidentally restoring object refs.
 */
private class SerializedBackup(
    private val source: IsDataStore,
) : DataStoreBackupWriter, DataStoreBackupReader {
    private lateinit var storedManifest: DataStoreBackupManifest
    private val chunks = mutableListOf<SerializedBackupChunk>()

    override val manifest: DataStoreBackupManifest
        get() = storedManifest

    override suspend fun begin(manifest: DataStoreBackupManifest) {
        storedManifest = manifest.copy(
            modelNames = manifest.modelNames.toList(),
            modelMajorVersions = manifest.modelMajorVersions.toMap(),
        )
    }

    override suspend fun write(chunk: DataStoreBackupChunk) {
        chunks += SerializedBackupChunk(
            modelName = chunk.modelName,
            records = chunk.records.map { encode(it, chunk.modelName) },
        )
    }

    override suspend fun complete() = Unit

    override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) {
        chunks.forEach { chunk ->
            consumer(
                DataStoreBackupChunk(
                    modelName = chunk.modelName,
                    records = chunk.records.map { decode(it, chunk.modelName) },
                )
            )
        }
    }

    private fun context(modelName: String): RequestContext {
        val dataModels = source.dataModelsById.values.associateBy(
            keySelector = { it.Meta.name },
            valueTransform = ::DataModelReference,
        )
        val model = dataModels[modelName]?.get?.invoke()
            ?: error("Backup model `$modelName` is not registered")
        return RequestContext(DefinitionsContext(dataModels.toMutableMap()), model)
    }

    private fun encode(
        record: DataObjectVersionedChange<IsRootDataModel>,
        modelName: String,
    ): ByteArray {
        val context = context(modelName)
        val cache = WriteCache()
        val size = DataObjectVersionedChange.Serializer.calculateObjectProtoBufLength(record, cache, context)
        var index = 0
        return ByteArray(size).also { bytes ->
            DataObjectVersionedChange.Serializer.writeObjectProtoBuf(record, cache, { bytes[index++] = it }, context)
        }
    }

    private fun decode(bytes: ByteArray, modelName: String): DataObjectVersionedChange<IsRootDataModel> {
        var index = 0
        return DataObjectVersionedChange.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, context(modelName))
            .toDataObject()
    }
}

private data class SerializedBackupChunk(
    val modelName: String,
    val records: List<ByteArray>,
)
