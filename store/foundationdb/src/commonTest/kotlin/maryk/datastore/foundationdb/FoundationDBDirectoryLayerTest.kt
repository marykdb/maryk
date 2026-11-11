package maryk.datastore.foundationdb

import kotlinx.coroutines.runBlocking
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.model.detectStoredKeepAllVersions
import maryk.datastore.foundationdb.model.readStoredModelDefinitionsFromDirectory
import maryk.foundationdb.directory.DirectoryLayer
import maryk.foundationdb.directory.DirectoryTreeMigration
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.tuple.Tuple
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class FoundationDBDirectoryLayerTest {
    @Test
    fun opensAndDiscoversAVerifiedDirectoryMigrationDestination() = runBlocking<Unit> {
        val id = Uuid.random().toString()
        fun namespace(name: String) = Subspace(Tuple.from("maryk-layer-review", id, name))
        val sourceNodes = namespace("source-meta")
        val sourceContent = namespace("source-data")
        val targetNodes = namespace("target-meta")
        val targetContent = namespace("target-data")
        val source = DirectoryLayer(sourceNodes, sourceContent)
        val target = DirectoryLayer(targetNodes, targetContent)
        val path = listOf("store")
        val before = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster", directoryPath = path,
            dataModelsById = mapOf(1u to SimpleMarykModel), directoryLayer = source,
        )
        val key = try {
            assertIs<AddSuccess<SimpleMarykModel>>(before.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with "happy cutover" },
            )).statuses.single()).key
        } finally { before.close() }
        val migration = DirectoryTreeMigration(
            DirectoryTreeMigration.Configuration(sourceNodes, sourceContent),
            DirectoryTreeMigration.Configuration(targetNodes, targetContent),
        )
        val database = openFoundationDatabase("./fdb.cluster")
        try {
            val inventory = mutableListOf<DirectoryTreeMigration.Entry>()
            val pending = ArrayDeque<List<String>>()
            pending.addAll(database.readSuspend { migration.inspectRoots(it) })
            while (pending.isNotEmpty()) {
                val inspectPath = pending.removeFirst()
                val entry = database.readSuspend { migration.inspect(it, inspectPath) }
                inventory += entry
                pending.addAll(entry.children)
            }
            database.runSuspend { migration.prepare(it, inventory) }
            for (entry in inventory) {
                var suffix: ByteArray? = null
                do {
                    val result = database.runSuspend { migration.copyBatch(it, entry, suffix, keyLimit = 16) }
                    suffix = result.nextSuffix
                } while (!result.complete)
            }
            for (entry in inventory) {
                var suffix: ByteArray? = null
                do {
                    val result = database.readSuspend { migration.verifyBatch(it, entry, suffix, keyLimit = 16) }
                    suffix = result.nextSuffix
                } while (!result.complete)
            }
            database.readSuspend { migration.verifyRoots(it) }
        } finally { database.close() }
        assertEquals(SimpleMarykModel.Meta.name, readStoredModelDefinitionsFromDirectory(
            "./fdb.cluster", path, target,
        )[1u]?.Meta?.name)
        assertEquals(true, detectStoredKeepAllVersions("./fdb.cluster", path, listOf(SimpleMarykModel.Meta.name), target))
        val after = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster", directoryPath = path,
            dataModelsById = mapOf(1u to SimpleMarykModel), directoryLayer = target,
        )
        try {
            assertEquals("happy cutover", after.execute(SimpleMarykModel.get(key)).values.single().values { value })
        } finally { after.close() }
    }
}
