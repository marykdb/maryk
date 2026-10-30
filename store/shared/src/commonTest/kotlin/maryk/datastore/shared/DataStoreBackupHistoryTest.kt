package maryk.datastore.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.string
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

private object BackupHistoryModel : RootDataModel<BackupHistoryModel>() {
    val dense by string(1u)
    val sparse by string(2u)
}

class DataStoreBackupHistoryTest {
    @Test
    fun creationReadMustMatchTheHistoryIdentityAndVersion() = runTest {
        val corruptions: List<(ValuesWithMetaData<BackupHistoryModel>) -> ValuesWithMetaData<BackupHistoryModel>> = listOf(
            { it.copy(key = BackupHistoryModel.key(ByteArray(16) { 1 })) },
            { it.copy(firstVersion = 2uL) },
            { it.copy(lastVersion = 2uL) },
        )
        for (corrupt in corruptions) {
            assertSourceRejected(QualifierPagedHistoryStore(creationRead = corrupt))
        }
    }

    @Test
    fun duplicateHistoryVersionsCannotDiscardAChangeBeforeValidation() = runTest {
        assertSourceRejected(QualifierPagedHistoryStore(historyPage = { changes, _ ->
            (changes + VersionedChanges(2003uL, listOf(Change(BackupHistoryModel.ref { dense } with "conflicting"))))
                .sortedBy { it.version }
        }))
    }

    @Test
    fun aHistoryVersionRepeatedAcrossPagesMustHaveTheSameChanges() = runTest {
        assertSourceRejected(QualifierPagedHistoryStore(historyPage = { changes, page ->
            if (page == 1) changes else changes.map { change ->
                if (change.version == 1004uL) VersionedChanges(1004uL,
                    listOf(Change(BackupHistoryModel.ref { dense } with "changed between pages"))) else change
            }
        }))
    }

    @Test
    fun olderPartialVersionsCanGainDenseFieldsOnALaterPage() = runTest {
        val records = mutableListOf<DataObjectVersionedChange<IsRootDataModel>>()
        QualifierPagedHistoryStore(sparseVersion = 3uL).backup(object : DataStoreBackupWriter {
            override suspend fun begin(manifest: DataStoreBackupManifest) = Unit
            override suspend fun write(chunk: DataStoreBackupChunk) { records += chunk.records }
            override suspend fun complete() = Unit
        }, snapshotVersion = 3000uL)
        assertEquals(listOf(1uL) + (3uL..2003uL).toList(), records.single().changes.map { it.version })
        assertEquals(setOf("dense-3", "sparse-3"), records.single().changes.first { it.version == 3uL }
            .changes.filterIsInstance<Change>().flatMap { it.referenceValuePairs }.map { it.value }.toSet())
    }

    private suspend fun assertSourceRejected(source: IsDataStore) {
        var completed = false
        var written = 0
        val writer = object : DataStoreBackupWriter {
            override suspend fun begin(manifest: DataStoreBackupManifest) = Unit
            override suspend fun write(chunk: DataStoreBackupChunk) { written++ }
            override suspend fun complete() { completed = true }
        }
        assertFailsWith<RequestException> { source.backup(writer, snapshotVersion = 3000uL) }
        assertEquals(0, written)
        assertFalse(completed)
    }

    @Test
    fun historyAtTheExclusiveSnapshotBoundaryCannotBePublished() = runTest {
        var completed = false
        val writer = object : DataStoreBackupWriter {
            override suspend fun begin(manifest: DataStoreBackupManifest) = Unit
            override suspend fun write(chunk: DataStoreBackupChunk) = Unit
            override suspend fun complete() { completed = true }
        }
        assertFailsWith<RequestException> {
            QualifierPagedHistoryStore().backup(writer, snapshotVersion = 2003uL)
        }
        assertFalse(completed)
    }

    @Test
    fun scannedRecordWithoutCreationHistoryCannotPublishAnIncompleteBackup() = runTest {
        var completed = false
        val writer = object : DataStoreBackupWriter {
            override suspend fun begin(manifest: DataStoreBackupManifest) = Unit
            override suspend fun write(chunk: DataStoreBackupChunk) = Unit
            override suspend fun complete() { completed = true }
        }
        assertFailsWith<RequestException> {
            QualifierPagedHistoryStore(omitHistory = true).backup(writer, snapshotVersion = 3000uL)
        }
        assertFalse(completed)
    }

    @Test
    fun sparseOldChangesCannotSkipOlderPagesOfAnotherProperty() = runTest {
        val source = QualifierPagedHistoryStore()
        val records = mutableListOf<DataObjectVersionedChange<IsRootDataModel>>()
        val writer = object : DataStoreBackupWriter {
            override suspend fun begin(manifest: DataStoreBackupManifest) = Unit
            override suspend fun write(chunk: DataStoreBackupChunk) { records += chunk.records }
            override suspend fun complete() = Unit
        }
        source.backup(writer, snapshotVersion = 3000uL)
        assertEquals((1uL..2003uL).toList(), records.single().changes.map { it.version })
        assertEquals("dense-3", records.single().changes.first { it.version == 3uL }
            .changes.filterIsInstance<Change>().single().referenceValuePairs.single().value)
    }
}

/** Models the persistent engines' independent maxVersions cap for each qualifier. */
private class QualifierPagedHistoryStore(
    private val omitHistory: Boolean = false,
    sparseVersion: ULong = 2uL,
    private val creationRead: (ValuesWithMetaData<BackupHistoryModel>) -> ValuesWithMetaData<BackupHistoryModel> = { it },
    private val historyPage: (List<VersionedChanges>, Int) -> List<VersionedChanges> = { changes, _ -> changes },
) : IsDataStore {
    override val dataModelsById = mapOf(1u to BackupHistoryModel)
    override val dataModelIdsByString = mapOf(BackupHistoryModel.Meta.name to 1u)
    override val keepAllVersions = true
    override val keepUpdateHistoryIndex = false
    override val supportsFuzzyQualifierFiltering = false
    override val supportsSubReferenceFiltering = false
    private val key = BackupHistoryModel.key(ByteArray(16))
    private val creationValues = BackupHistoryModel.create { dense with "dense-1"; sparse with "sparse-1" }
    private val creation = VersionedChanges(1uL, listOf(ObjectCreate, Change(
        BackupHistoryModel.ref { dense } with "dense-1", BackupHistoryModel.ref { sparse } with "sparse-1",
    )))
    private val denseChanges = (3uL..2003uL).map { version ->
        VersionedChanges(version, listOf(Change(BackupHistoryModel.ref { dense } with "dense-$version")))
    }
    private val sparseChanges = listOf(VersionedChanges(sparseVersion,
        listOf(Change(BackupHistoryModel.ref { sparse } with "sparse-$sparseVersion"))))
    private var page = 0

    @Suppress("UNCHECKED_CAST")
    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP =
        when (request) {
            is ScanRequest<*> -> ValuesResponse(BackupHistoryModel,
                listOf(ValuesWithMetaData(key, creationValues, 1uL, 2003uL, false)))
            is GetRequest<*> -> ValuesResponse(BackupHistoryModel,
                listOf(creationRead(ValuesWithMetaData(key, creationValues, 1uL, 1uL, false))))
            is GetChangesRequest<*> -> {
                val changes = listOf(creation) + listOf(denseChanges, sparseChanges).flatMap { history ->
                    history.filter { it.version <= (request.toVersion ?: ULong.MAX_VALUE) }.takeLast(request.maxVersions.toInt())
                }
                val merged = changes.groupBy { it.version }.map { (version, versions) ->
                    VersionedChanges(version, versions.flatMap { it.changes })
                }.sortedBy { it.version }
                ChangesResponse(BackupHistoryModel, if (omitHistory) emptyList() else listOf(DataObjectVersionedChange(key,
                    changes = historyPage(merged, ++page))))
            }
            else -> error("Unexpected backup request")
        } as RP

    override suspend fun <DM : IsRootDataModel, RQ : IsFlowRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(request: RQ): Flow<IsUpdateResponse<DM>> = error("Unexpected flow")
    override suspend fun <DM : IsRootDataModel> processUpdate(updateResponse: UpdateResponse<DM>): ProcessResponse<DM> = error("Unexpected update")
    override suspend fun close() = Unit
    override suspend fun closeAllListeners() = Unit
}
