package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.datastore.shared.RestoreVersionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object CreationReadRecord : RootDataModel<CreationReadRecord>(indexes = { listOf(CreationReadRecord.title.ref()) }) {
    val title by string(1u, final = true)
    val body by string(2u)
    val entries by list(3u, valueDefinition = StringDefinition())
}

class CreationSnapshotReadTest {
    @Test
    fun fullCreationSnapshotsOwnResultsAndKeepOriginalValuesAfterLaterChanges() = runTest {
        val model = CreationReadRecord
        for (historyIndex in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to model), keepAllVersions = true,
                keepUpdateHistoryIndex = historyIndex)
            try {
                val entries = List(64) { "initial-$it" }
                val initial = model.create { title with "record"; body with "initial"; this.entries with entries }
                val added = assertIs<AddSuccess<CreationReadRecord>>(store.execute(model.add(initial)).statuses.single())
                val snapshot = store.execute(model.scanUpdates(order = model.title.ref().ascending(), maxVersions = 10u))
                    .updates.filterIsInstance<AdditionUpdate<CreationReadRecord>>().single()
                assertEquals(initial, snapshot.values)
                assertEquals(added.version, snapshot.version)
                @Suppress("UNCHECKED_CAST")
                val mutableEntries = snapshot.values[model.entries.ref()] as MutableList<String>
                mutableEntries[0] = "caller mutation"
                assertEquals(entries, store.execute(model.get(added.key)).values.single().values { this.entries })
                val changed = assertIs<ChangeSuccess<CreationReadRecord>>(store.execute(model.change(added.key.change(Change(
                    model.body.ref() with "later", model.entries.ref() with List(64) { "later-$it" },
                )))).statuses.single())
                val replay = store.execute(model.scanUpdates(order = model.title.ref().ascending(), maxVersions = 10u)).updates
                assertEquals(initial, replay.filterIsInstance<AdditionUpdate<CreationReadRecord>>().single().values)
                assertTrue(replay.filterIsInstance<ChangeUpdate<CreationReadRecord>>().any { it.version == changed.version })
                val historic = store.execute(model.scanUpdates(order = model.title.ref().ascending(), maxVersions = 10u,
                    toVersion = added.version)).updates.filterIsInstance<AdditionUpdate<CreationReadRecord>>().single()
                assertEquals(initial, historic.values)
            } finally { store.close() }
        }
    }

    @Test
    fun creationSnapshotsPreserveAnInitiallySoftDeletedRecord() = runTest {
        val model = CreationReadRecord
        for (historyIndex in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to model), keepAllVersions = true,
                keepUpdateHistoryIndex = historyIndex)
            try {
                val initial = model.create { title with "deleted"; body with "initial"; entries with List(64) { "entry-$it" } }
                val key = Key<CreationReadRecord>(ByteArray(model.Meta.keyByteSize))
                val record = DataObjectVersionedChange(key, changes = listOf(VersionedChanges(100uL,
                    listOf(ObjectCreate) + initial.toChanges().toList() + ObjectSoftDeleteChange(true))))
                @Suppress("UNCHECKED_CAST")
                val update = UpdateResponse(model, InitialChangesUpdate(101uL, listOf(record))) as UpdateResponse<IsRootDataModel>
                store.restoreVersion(RestoreVersionRequest(listOf(update)))
                val snapshot = store.execute(model.scanUpdates(filterSoftDeleted = false)).updates
                    .filterIsInstance<AdditionUpdate<CreationReadRecord>>().single()
                assertEquals(initial, snapshot.values)
                assertEquals(100uL, snapshot.firstVersion)
                assertEquals(100uL, snapshot.version)
                assertTrue(snapshot.isDeleted)
            } finally { store.close() }
        }
    }
}
