package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationAuditEvent
import maryk.core.models.migration.MigrationAuditEventType
import maryk.core.properties.definitions.string
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.clusterlog.ClusterLogAddition
import maryk.datastore.foundationdb.model.FoundationDBMigrationAuditLogStore
import maryk.datastore.foundationdb.model.modelMigrationAuditLogChunksKey
import maryk.datastore.foundationdb.model.modelMigrationAuditLogKey
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.foundationdb.Range
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private object LargeClusterLogModel : RootDataModel<LargeClusterLogModel>() {
    val first by string(index = 1u)
    val second by string(index = 2u)
    val third by string(index = 3u)
}

class FoundationDBBoundedStorageTest {
    @Test
    fun logicalUpdateAboveFoundationDbValueLimitRoundTripsThroughClusterLog() = runTest(timeout = 3.minutes) {
        val root = listOf("maryk", "test", "bounded-cluster-log", Uuid.random().toString())
        val writer = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = mapOf(1u to LargeClusterLogModel),
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "writer-${Uuid.random()}",
            ),
        )
        val reader = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = root,
            dataModelsById = mapOf(1u to LargeClusterLogModel),
            clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                enableClusterUpdateLog = true,
                clusterUpdateLogConsumerId = "reader-${Uuid.random()}",
            ),
        )

        try {
            val first = "a".repeat(45_000)
            val second = "b".repeat(45_000)
            val third = "c".repeat(45_000)
            assertIs<AddSuccess<LargeClusterLogModel>>(
                writer.execute(
                    LargeClusterLogModel.add(
                        LargeClusterLogModel.create {
                            LargeClusterLogModel.first with first
                            LargeClusterLogModel.second with second
                            LargeClusterLogModel.third with third
                        }
                    )
                ).statuses.single()
            )

            val log = assertNotNull(reader.clusterUpdateLog)
            val decoded = (0 until 64).firstNotNullOfOrNull { shard ->
                val activation = reader.runTransaction { transaction ->
                    log.tailOnce(transaction, shard, 1u, cursorKey = null, limit = 1)
                }
                val cursor = activation.lastKey ?: return@firstNotNullOfOrNull activation.decoded.singleOrNull()
                reader.runTransaction { transaction ->
                    log.tailOnce(transaction, shard, 1u, cursorKey = cursor, limit = 1)
                }.decoded.singleOrNull()
            }
            val addition = assertIs<ClusterLogAddition>(assertNotNull(decoded).update)
            assertEquals(first, addition.values[LargeClusterLogModel.first.ref()])
            assertEquals(second, addition.values[LargeClusterLogModel.second.ref()])
            assertEquals(third, addition.values[LargeClusterLogModel.third.ref()])

            val populatedShard = (0 until 64).single { shard ->
                writer.runTransaction { transaction ->
                    transaction.getRange(log.chunkRange(shard, 1u)).iterator().hasNext()
                }
            }
            val chunkEntries = writer.runTransaction { transaction ->
                val iterator = transaction.getRange(log.chunkRange(populatedShard, 1u)).iterator()
                buildList {
                    while (iterator.hasNext()) add(iterator.nextBlocking())
                }
            }
            assertTrue(chunkEntries.size > 1)
            assertTrue(chunkEntries.all { it.value.size < 100_000 })
            writer.runTransaction { transaction -> transaction.clear(chunkEntries.first().key) }
            val activation = reader.runTransaction { transaction ->
                log.tailOnce(transaction, populatedShard, 1u, cursorKey = null, limit = 1)
            }
            val clusterFailure = assertFails {
                reader.runTransaction { transaction ->
                    log.tailOnce(transaction, populatedShard, 1u, assertNotNull(activation.lastKey), limit = 1)
                }
            }
            assertTrue(clusterFailure.message.orEmpty().contains("Missing cluster-log chunk"))
            writer.runTransaction { transaction ->
                transaction.set(chunkEntries.first().key, chunkEntries.first().value)
            }
            writer.runTransaction { transaction ->
                log.clearBefore(transaction, populatedShard, 1u, cutoff = ULong.MAX_VALUE)
            }
            assertFalse(writer.runTransaction { transaction ->
                transaction.getRange(Range.startsWith(log.shardModelPrefix(populatedShard, 1u))).iterator().hasNext()
            })
            assertFalse(writer.runTransaction { transaction ->
                transaction.getRange(log.chunkRange(populatedShard, 1u)).iterator().hasNext()
            })
        } finally {
            reader.close()
            writer.close()
        }
    }

    @Test
    fun migrationAuditChunksLargeEventsAndRetainsOnlyNewestEntries() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "bounded-migration-audit", Uuid.random().toString()),
            dataModelsById = mapOf(1u to SimpleMarykModel),
        )

        try {
            val modelPrefix = store.getTableDirs(1u).modelPrefix
            val legacyEvents = listOf(auditEvent("legacy-1"), auditEvent("legacy-2"))
            store.runTransaction { transaction ->
                transaction.set(
                    packKey(modelPrefix, modelMigrationAuditLogKey),
                    legacyEvents.joinToString("\n", transform = MigrationAuditEvent::toPersistedLine).encodeToByteArray(),
                )
            }

            val auditStore = FoundationDBMigrationAuditLogStore(
                tc = store.tc,
                modelPrefixesById = mapOf(1u to modelPrefix),
                maxEntries = 3,
            )
            val largeOne = auditEvent("1".repeat(120_000))
            val largeTwo = auditEvent("2".repeat(120_000))
            auditStore.append(1u, largeOne)
            auditStore.append(1u, largeTwo)

            assertEquals(listOf(legacyEvents[1], largeOne, largeTwo), auditStore.read(1u, limit = 10))
            val chunkValues = store.runTransaction { transaction ->
                val iterator = transaction.getRange(
                    Range.startsWith(packKey(modelPrefix, modelMigrationAuditLogChunksKey))
                ).iterator()
                buildList {
                    while (iterator.hasNext()) add(iterator.nextBlocking().value)
                }
            }
            assertTrue(chunkValues.isNotEmpty())
            assertTrue(chunkValues.all { it.size < 100_000 })

            store.runTransaction { transaction ->
                val iterator = transaction.getRange(
                    Range.startsWith(packKey(modelPrefix, modelMigrationAuditLogChunksKey))
                ).iterator()
                transaction.clear(iterator.nextBlocking().key)
            }
            val readFailure = assertFails { auditStore.read(1u, limit = 10) }
            assertTrue(readFailure.message.orEmpty().contains("Missing migration audit chunk"))
            val appendFailure = assertFails { auditStore.append(1u, auditEvent("must-not-overwrite")) }
            assertTrue(appendFailure.message.orEmpty().contains("Missing migration audit chunk"))

            store.runTransaction { transaction ->
                transaction.clear(Range.startsWith(packKey(modelPrefix, modelMigrationAuditLogChunksKey)))
                transaction.set(
                    packKey(modelPrefix, modelMigrationAuditLogKey),
                    legacyEvents.joinToString("\n", transform = MigrationAuditEvent::toPersistedLine)
                        .encodeToByteArray(),
                )
            }

            val newestEvents = listOf(auditEvent("new-1"), auditEvent("new-2"), auditEvent("new-3"))
            newestEvents.forEach { auditStore.append(1u, it) }
            assertEquals(newestEvents, auditStore.read(1u, limit = 10))
            val chunksAfterRetention = store.runTransaction { transaction ->
                transaction.getRange(
                    Range.startsWith(packKey(modelPrefix, modelMigrationAuditLogChunksKey))
                ).iterator().hasNext()
            }
            assertFalse(chunksAfterRetention)
        } finally {
            store.close()
        }
    }

    private fun auditEvent(message: String) = MigrationAuditEvent(
        timestampMs = message.length.toLong(),
        modelId = 1u,
        migrationId = "migration",
        type = MigrationAuditEventType.Partial,
        message = message,
    )
}
