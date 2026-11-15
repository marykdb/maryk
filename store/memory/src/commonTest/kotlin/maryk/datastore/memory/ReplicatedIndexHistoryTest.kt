package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByUniqueKey
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object ReplicatedIndexOwner : RootDataModel<ReplicatedIndexOwner>() {
    val email by string(1u, unique = true)
    val name by string(2u)
}

class ReplicatedIndexHistoryTest {
    private fun key(value: Int) = Key<ReplicatedIndexOwner>(ByteArray(16).also { it[15] = value.toByte() })

    @Test
    fun backdatedReplacementSurvivesThePreviousOwnersRelease() = runTest {
        val store = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = mapOf(1u to ReplicatedIndexOwner))
        val first = key(1)
        val second = key(2)
        suspend fun add(key: Key<ReplicatedIndexOwner>, version: ULong, name: String) {
            store.processUpdate(UpdateResponse(ReplicatedIndexOwner, AdditionUpdate(key, version, version, 0, false,
                ReplicatedIndexOwner.create { email with "shared"; this.name with name })))
        }
        suspend fun owners(version: ULong?): List<Key<ReplicatedIndexOwner>> {
            val result = store.execute(ReplicatedIndexOwner.scan(
                where = Equals(ReplicatedIndexOwner.email.ref() with "shared"), toVersion = version, limit = 1u,
            ))
            assertIs<FetchByUniqueKey>(result.dataFetchType)
            return result.values.map { it.key }
        }
        try {
            add(first, 1uL, "first")
            store.processUpdate(UpdateResponse(ReplicatedIndexOwner,
                ChangeUpdate(first, 4uL, 0, listOf(ObjectSoftDeleteChange(true)))))
            add(second, 3uL, "second")
            assertEquals(listOf(first), owners(2uL))
            assertEquals(listOf(second), owners(3uL))
            assertEquals(listOf(second), owners(4uL))
            assertEquals(listOf(second), owners(null))
            store.processUpdate(UpdateResponse(ReplicatedIndexOwner,
                ChangeUpdate(second, 6uL, 0, listOf(ObjectSoftDeleteChange(true)))))
            assertEquals(listOf(second), owners(5uL))
            assertEquals(emptyList(), owners(6uL))
            assertEquals(emptyList(), owners(null))
            assertEquals(second, store.execute(ReplicatedIndexOwner.get(second, toVersion = 5uL)).values.single().key)
        } finally { store.close() }
    }

    @Test
    fun historicalUniqueLookupAgreesWithTheVisibleRecordAtOverlappingVersions() = runTest {
        val store = InMemoryDataStore.open(keepAllVersions = true, dataModelsById = mapOf(1u to ReplicatedIndexOwner))
        val newer = key(1)
        val older = key(2)
        try {
            store.processUpdate(UpdateResponse(ReplicatedIndexOwner, AdditionUpdate(newer, 10uL, 10uL, 0, false,
                ReplicatedIndexOwner.create { email with "shared"; name with "newer" })))
            store.processUpdate(UpdateResponse(ReplicatedIndexOwner,
                ChangeUpdate(newer, 20uL, 0, listOf(ObjectSoftDeleteChange(true)))))
            store.processUpdate(UpdateResponse(ReplicatedIndexOwner, AdditionUpdate(older, 3uL, 3uL, 0, false,
                ReplicatedIndexOwner.create { email with "shared"; name with "older" })))

            val result = store.execute(ReplicatedIndexOwner.scan(
                where = Equals(ReplicatedIndexOwner.email.ref() with "shared"), toVersion = 15uL, limit = 1u,
            ))
            assertIs<FetchByUniqueKey>(result.dataFetchType)
            assertEquals(listOf(newer), result.values.map { it.key })
            // Current admission continues to follow the successfully applied replacement.
            assertEquals(listOf(older), store.execute(ReplicatedIndexOwner.scan(
                where = Equals(ReplicatedIndexOwner.email.ref() with "shared"), limit = 1u,
            )).values.map { it.key })
        } finally { store.close() }
    }
}
