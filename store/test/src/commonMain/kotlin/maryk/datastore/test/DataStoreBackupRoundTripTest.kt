package maryk.datastore.test

import maryk.core.query.changes.Change
import maryk.core.query.changes.change
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

        val backup = InMemoryBackup()
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

private class InMemoryBackup : DataStoreBackupWriter, DataStoreBackupReader {
    private lateinit var storedManifest: DataStoreBackupManifest
    private val chunks = mutableListOf<DataStoreBackupChunk>()

    override val manifest: DataStoreBackupManifest
        get() = storedManifest

    override suspend fun begin(manifest: DataStoreBackupManifest) {
        storedManifest = manifest
    }

    override suspend fun write(chunk: DataStoreBackupChunk) {
        chunks += chunk
    }

    override suspend fun complete() = Unit

    override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) {
        chunks.forEach { consumer(it) }
    }
}
