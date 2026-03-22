@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.FetchByUpdateHistoryIndex
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.models.key
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class UpdateHistoryBackfillTest {
    @Test
    fun enablingUpdateHistoryIndexBackfillsLatestStateWithoutHistory() = runTest {
        val directoryPath = listOf("maryk", "test", "update-history-backfill-latest", Uuid.random().toString())
        val values = Log("latest-state")
        val key = Log.key(values)

        FoundationDBDataStore.open(
            directoryPath = directoryPath,
            keepAllVersions = false,
            keepUpdateHistoryIndex = false,
            dataModelsById = dataModelsForTests,
        ).let { dataStore ->
            try {
            assertIs<AddSuccess<*>>(dataStore.execute(Log.add(key to values)).statuses.first())
            } finally {
                dataStore.close()
            }
        }

        FoundationDBDataStore.open(
            directoryPath = directoryPath,
            keepAllVersions = false,
            keepUpdateHistoryIndex = true,
            dataModelsById = dataModelsForTests,
        ).let { dataStore ->
            try {
            val scanResponse = dataStore.execute(Log.scanUpdates(limit = 1u))

            assertIs<FetchByUpdateHistoryIndex>(scanResponse.dataFetchType)
            assertEquals(listOf(key), assertIs<OrderedKeysUpdate<Log>>(scanResponse.updates.first()).keys)
            assertEquals(key, assertIs<AdditionUpdate<Log>>(scanResponse.updates[1]).key)
            } finally {
                dataStore.close()
            }
        }
    }

    @Test
    fun enablingUpdateHistoryIndexReplaysHistoricChanges() = runTest {
        val directoryPath = listOf("maryk", "test", "update-history-backfill-history", Uuid.random().toString())
        val initial = Log("historic-state")
        val key = Log.key(initial)

        val addVersion = FoundationDBDataStore.open(
            directoryPath = directoryPath,
            keepAllVersions = true,
            keepUpdateHistoryIndex = false,
            dataModelsById = dataModelsForTests,
        ).let { dataStore ->
            try {
            val addStatus = assertIs<AddSuccess<*>>(dataStore.execute(Log.add(key to initial)).statuses.first())
            val changeStatus = assertIs<ChangeSuccess<*>>(
                dataStore.execute(
                    Log.change(
                        key.change(Change(Log { message::ref } with "historic-state-updated"))
                    )
                ).statuses.first()
            )
            assertTrue(changeStatus.version > addStatus.version)
            addStatus.version
            } finally {
                dataStore.close()
            }
        }

        FoundationDBDataStore.open(
            directoryPath = directoryPath,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = dataModelsForTests,
        ).let { dataStore ->
            try {
            val scanResponse = dataStore.execute(
                Log.scanUpdates(
                    fromVersion = addVersion + 1uL,
                    limit = 1u
                )
            )

            assertIs<FetchByUpdateHistoryIndex>(scanResponse.dataFetchType)
            assertEquals(listOf(key), assertIs<OrderedKeysUpdate<Log>>(scanResponse.updates.first()).keys)
            assertEquals(key, assertIs<ChangeUpdate<Log>>(scanResponse.updates[1]).key)
            } finally {
                dataStore.close()
            }
        }
    }

    @Test
    fun enablingUpdateHistoryIndexReplaysLargeHistoryInChunks() = runTest {
        val directoryPath = listOf("maryk", "test", "update-history-backfill-large-history", Uuid.random().toString())
        val initial = Log("historic-state")
        val key = Log.key(initial)

        val firstChangeVersion = FoundationDBDataStore.open(
            directoryPath = directoryPath,
            keepAllVersions = true,
            keepUpdateHistoryIndex = false,
            dataModelsById = dataModelsForTests,
        ).let { dataStore ->
            try {
                assertIs<AddSuccess<*>>(dataStore.execute(Log.add(key to initial)).statuses.first())

                var firstVersion: ULong? = null
                repeat(300) { index ->
                    val status = assertIs<ChangeSuccess<*>>(
                        dataStore.execute(
                            Log.change(
                                key.change(Change(Log { message::ref } with "historic-state-$index"))
                            )
                        ).statuses.first()
                    )
                    if (firstVersion == null) {
                        firstVersion = status.version
                    }
                }

                firstVersion ?: error("Expected at least one change version")
            } finally {
                dataStore.close()
            }
        }

        FoundationDBDataStore.open(
            directoryPath = directoryPath,
            keepAllVersions = true,
            keepUpdateHistoryIndex = true,
            dataModelsById = dataModelsForTests,
        ).let { dataStore ->
            try {
                val scanResponse = dataStore.execute(
                    Log.scanUpdates(
                        fromVersion = firstChangeVersion,
                        limit = 1u,
                        maxVersions = 500u
                    )
                )

                assertIs<FetchByUpdateHistoryIndex>(scanResponse.dataFetchType)
                assertEquals(listOf(key), assertIs<OrderedKeysUpdate<Log>>(scanResponse.updates.first()).keys)
                assertTrue(scanResponse.updates.drop(1).filterIsInstance<ChangeUpdate<Log>>().isNotEmpty())
            } finally {
                dataStore.close()
            }
        }
    }
}
