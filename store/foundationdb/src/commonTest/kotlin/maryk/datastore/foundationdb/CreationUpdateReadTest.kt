package maryk.datastore.foundationdb

import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private object CreationReadModel : RootDataModel<CreationReadModel>(
    indexes = { listOf(CreationReadModel.label.ref()) }, minimumKeyScanByteRange = 0u,
) {
    val label by string(1u, final = true)
    val body by string(2u)
    val entries by map(3u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

class CreationUpdateReadTest {
    @Test
    fun updatesReuseCompleteCreationsButKeepHistoricalValuesForChangedRecords() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "creation-reuse", Uuid.random().toString()),
            dataModelsById = mapOf(1u to CreationReadModel), keepAllVersions = true, keepUpdateHistoryIndex = true,
        )
        val unchanged = CreationReadModel.create {
            label with "unchanged"; body with "before".repeat(12_000); entries with mapOf("key" to "original")
        }
        val changed = CreationReadModel.create {
            label with "changed"; body with "old"; entries with mapOf("key" to "original")
        }
        try {
            val added = store.execute(CreationReadModel.add(unchanged, changed)).statuses
            val key = assertIs<AddSuccess<CreationReadModel>>(added[1]).key
            assertIs<ChangeSuccess<CreationReadModel>>(store.execute(CreationReadModel.change(key.change(Change(
                CreationReadModel.body.ref() with "new", CreationReadModel.entries.ref() with mapOf("key" to "new"),
            )))).statuses.single())
            for (indexed in listOf(true, false)) {
                val response = store.execute(CreationReadModel.scanUpdates(order = if (indexed) CreationReadModel.label.ref().ascending() else null))
                val creations = response.updates.filterIsInstance<AdditionUpdate<CreationReadModel>>()
                assertEquals(setOf(unchanged, changed), creations.map { it.values }.toSet())
                val projected = store.execute(CreationReadModel.scanUpdates(
                    order = if (indexed) CreationReadModel.label.ref().ascending() else null,
                    select = CreationReadModel.graph { listOf(label) },
                )).updates.filterIsInstance<AdditionUpdate<CreationReadModel>>()
                assertEquals(setOf("unchanged", "changed"), projected.map { it.values { label } }.toSet())
                projected.forEach { assertNull(it.values { body }); assertNull(it.values { entries }) }
            }
        } finally { store.close() }
    }

    @Test
    fun projectedCurrentAndHistoricChangesDoNotReadUnselectedPayloads() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "projected-change-budget", Uuid.random().toString()),
            dataModelsById = mapOf(1u to CreationReadModel), keepAllVersions = true,
            transactionConfiguration = FoundationDBTransactionConfiguration(),
            executionConfiguration = FoundationDBExecutionConfiguration(maxRecordReadBytes = 4_096, maxRequestReadBytes = 8_192),
        )
        try {
            val added = assertIs<AddSuccess<CreationReadModel>>(store.execute(CreationReadModel.add(CreationReadModel.create {
                label with "record"; body with "x".repeat(65_536); entries with mapOf("key" to "value")
            })).statuses.single())
            for (version in listOf(null, added.version)) {
                val result = store.execute(CreationReadModel.scanChanges(
                    order = CreationReadModel.label.ref().ascending(), select = CreationReadModel.graph { listOf(label) }, toVersion = version,
                ))
                val pairs = result.changes.single().changes.flatMap { it.changes }.filterIsInstance<Change>().flatMap { it.referenceValuePairs }
                assertEquals(listOf("label"), pairs.map { it.reference.completeName })
                assertEquals(listOf("record"), pairs.map { it.value })
            }
        } finally { store.close() }
    }
}
