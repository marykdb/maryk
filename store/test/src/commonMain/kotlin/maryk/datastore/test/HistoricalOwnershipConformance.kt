package maryk.datastore.test

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.FetchByUniqueKey
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertEquals
import kotlin.test.assertIs

object HistoricalOwner : RootDataModel<HistoricalOwner>() {
    val code by string(1u, unique = true)
    val body by string(2u)
}

val historicalOwnershipModels = mapOf(1u to HistoricalOwner)

/** Valid source histories; conflicting overlapping claims remain an engine-specific policy. */
class HistoricalOwnershipConformance(
    private val retainsErasedValues: Boolean = false,
    private val createStore: suspend (String) -> IsDataStore,
) {
    private fun key(value: Int) = Key<HistoricalOwner>(ByteArray(16).also { it[15] = value.toByte() })

    suspend fun reversedRecordHistoriesPreserveUniqueOwnership() = withStore("ownership-reversed") { store ->
        val first = key(1)
        val second = key(2)
        store.add(second, 5uL, "second")
        store.deleted(second, 8uL, true)
        store.add(first, 1uL, "first")
        store.deleted(first, 4uL, true)
        for ((version, owner) in listOf(0uL to null, 2uL to first, 4uL to null, 5uL to second, 7uL to second, 8uL to null)) {
            store.assertOwner(version, owner)
        }
        store.assertOwner(null, null)
    }

    suspend fun equalVersionTransfersRetainAppliedOrder() = withStore("ownership-ties") { store ->
        val first = key(1)
        val second = key(2)
        store.add(first, 1uL, "first")
        store.deleted(first, 4uL, true)
        store.add(second, 4uL, "second")
        store.deleted(second, 6uL, true)
        store.deleted(first, 6uL, false)
        store.assertOwner(3uL, first)
        store.assertOwner(4uL, second)
        store.assertOwner(5uL, second)
        store.assertOwner(6uL, first)
        store.assertOwner(null, first)
    }

    suspend fun hardDeletedKeyReusePreservesHistoricalIncarnations() = withStore("ownership-incarnations") { store ->
        val reused = key(1)
        store.add(reused, 1uL, "old")
        store.processUpdate(UpdateResponse(HistoricalOwner, RemovalUpdate(reused, 4uL, HardDelete)))
        store.add(reused, 5uL, "new")
        store.assertOwner(2uL, reused.takeIf { retainsErasedValues })
        store.assertOwner(4uL, null)
        store.assertOwner(6uL, reused)
        val old = store.execute(HistoricalOwner.get(reused, toVersion = 2uL)).values
        if (retainsErasedValues) assertEquals("old", old.single().values { body })
        else assertEquals(emptyList(), old)
        assertEquals("new", store.execute(HistoricalOwner.get(reused, toVersion = 6uL)).values.single().values { body })
    }

    private suspend fun IsDataStore.add(key: Key<HistoricalOwner>, version: ULong, body: String) {
        val result = processUpdate(UpdateResponse(HistoricalOwner, AdditionUpdate(key, version, version, 0, false,
            HistoricalOwner.create { code with "shared"; HistoricalOwner.body with body })))
        assertIs<AddSuccess<HistoricalOwner>>(assertIs<AddResponse<HistoricalOwner>>(result.result).statuses.single())
    }

    private suspend fun IsDataStore.deleted(key: Key<HistoricalOwner>, version: ULong, deleted: Boolean) {
        val result = processUpdate(UpdateResponse(HistoricalOwner,
            ChangeUpdate(key, version, 0, listOf(ObjectSoftDeleteChange(deleted)))))
        assertIs<ChangeSuccess<HistoricalOwner>>(assertIs<ChangeResponse<HistoricalOwner>>(result.result).statuses.single())
    }

    private suspend fun IsDataStore.assertOwner(version: ULong?, expected: Key<HistoricalOwner>?) {
        val response = execute(HistoricalOwner.scan(
            where = Equals(HistoricalOwner.code.ref() with "shared"), toVersion = version, limit = 2u,
        ))
        assertIs<FetchByUniqueKey>(response.dataFetchType)
        assertEquals(listOfNotNull(expected), response.values.map { it.key }, "unique ownership at $version")
        if (expected != null) assertEquals(expected, execute(HistoricalOwner.get(expected, toVersion = version)).values.single().key)
    }

    private suspend fun withStore(name: String, block: suspend (IsDataStore) -> Unit) {
        val store = createStore(name)
        try { block(store) } finally { store.close() }
    }
}
