package maryk.datastore.foundationdb

import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.filters.Equals
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.test.UniqueModel
import maryk.foundationdb.Range
import maryk.test.models.Log
import maryk.test.models.Severity
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Explicit operator drill, outside jvmTest: never connects to the configured application/test cluster. */
fun main() = runBlocking {
    val workspace = Files.createTempDirectory("maryk-fdb-recovery-").toRealPath().toFile()
    val children = mutableListOf<Process>()
    var commandNumber = 0
    fun tool(name: String): String = System.getenv("FDB_RECOVERY_BIN_DIR")
        ?.let { File(it, name).absolutePath } ?: name

    fun command(name: String, vararg arguments: String): String {
        val output = File(workspace, "command-${commandNumber++}.log")
        val process = ProcessBuilder(listOf(tool(name)) + arguments)
            .directory(workspace).redirectErrorStream(true).redirectOutput(output).start()
        children.add(process)
        check(process.waitFor(180, TimeUnit.SECONDS)) { "$name timed out; see $output" }
        check(process.exitValue() == 0) { "$name failed: ${output.readText().takeLast(4000)}" }
        return output.readText()
    }

    fun daemon(name: String, vararg arguments: String) {
        val output = File(workspace, "$name-${children.size}.log")
        children.add(ProcessBuilder(listOf(tool(name)) + arguments)
            .directory(workspace).redirectErrorStream(true).redirectOutput(output).start())
    }

    fun cluster(name: String): File {
        val directory = File(workspace, name).apply { mkdirs() }
        val port = ServerSocket(0).use { it.localPort }
        val clusterFile = File(directory, "fdb.cluster").apply {
            writeText("$name:$name@127.0.0.1:$port\n")
        }
        daemon("fdbserver", "-C", clusterFile.path, "-p", "127.0.0.1:$port",
            "-d", File(directory, "data").path, "-L", directory.path)
        command("fdbcli", "-C", clusterFile.path, "--timeout", "30", "--exec",
            "configure new single memory logs=1 commit_proxies=1 grv_proxies=1")
        command("fdbcli", "-C", clusterFile.path, "--timeout", "30", "--exec", "status minimal")
        daemon("backup_agent", "-C", clusterFile.path, "--logdir", directory.path)
        return clusterFile
    }

    suspend fun open(cluster: File, history: Boolean) = FoundationDBDataStore.open(
        fdbClusterFilePath = cluster.path,
        directoryPath = listOf("maryk", if (history) "history" else "current"),
        dataModelsById = mapOf(1u to UniqueModel, 2u to Log),
        keepAllVersions = history,
        keepUpdateHistoryIndex = history,
    )

    try {
        println("Recovery drill artifacts: $workspace")
        val versions = listOf("fdbserver", "fdbcli", "fdbbackup", "fdbrestore", "backup_agent")
            .associateWith { command(it, "--version").lineSequence().first() }
        val releasePattern = Regex("v\\d+\\.\\d+\\.\\d+")
        val releases = versions.values.map { releasePattern.find(it)?.value }
        check(releases.none { it == null } && releases.toSet().size == 1) {
            "Use matching FoundationDB tools: $versions"
        }
        println(versions.values.first())
        val source = cluster("source")
        val target = cluster("target")
        // Compare every user key, including DirectoryLayer metadata and allocator state, before Maryk reopens.
        fun userRows(clusterFile: File): List<Pair<List<Byte>, List<Byte>>> {
            val database = openFoundationDatabase(clusterFile.path)
            return try {
                database.run { transaction ->
                    transaction.getRange(Range(byteArrayOf(), byteArrayOf(0xff.toByte())))
                        .asList().awaitResult().map { it.key.toList() to it.value.toList() }
                }
            } finally {
                database.close()
            }
        }
        val checks = listOf(false, true).map { history ->
            val store = open(source, history)
            try {
                assertIs<AddSuccess<Log>>(store.execute(Log.add(Log("Recovery index fixture"))).statuses.single())
                val added = assertIs<AddSuccess<UniqueModel>>(store.execute(UniqueModel.add(
                    UniqueModel.create { email with "before@example.org" }
                )).statuses.single())
                assertIs<ChangeSuccess<UniqueModel>>(store.execute(UniqueModel.change(
                    added.key.change(Change(UniqueModel.email.ref() with "after@example.org"))
                )).statuses.single())
                val deleted = assertIs<AddSuccess<UniqueModel>>(store.execute(UniqueModel.add(
                    UniqueModel.create { email with "deleted@example.org" }
                )).statuses.single())
                assertIs<DeleteSuccess<UniqueModel>>(store.execute(UniqueModel.delete(deleted.key)).statuses.single())
                val purged = assertIs<AddSuccess<UniqueModel>>(store.execute(UniqueModel.add(
                    UniqueModel.create { email with "purged@example.org" }
                )).statuses.single())
                assertIs<DeleteSuccess<UniqueModel>>(store.execute(
                    UniqueModel.delete(purged.key, hardDelete = true)
                ).statuses.single())
                Triple(history, added, deleted.key)
            } finally {
                store.close()
            }
        }
        val expected = userRows(source)
        check(expected.isNotEmpty())
        val backupBase = File(workspace, "backups").apply { mkdirs() }.toPath().toUri().toString()
        command("fdbbackup", "start", "-C", source.path, "-d", backupBase, "-w")
        val container = File(workspace, "backups").listFiles()!!.single { it.isDirectory }.toPath().toUri().toString()
        val description = command("fdbbackup", "describe", "-d", container)
        File(workspace, "backup-description.txt").writeText(description)
        // Make the live source diverge after backup; recovery must retain the backed-up state.
        val live = open(source, false)
        try {
            assertIs<DeleteSuccess<UniqueModel>>(live.execute(
                UniqueModel.delete(checks.first().second.key, hardDelete = true)
            ).statuses.single())
        } finally {
            live.close()
        }
        check(userRows(source) != expected)
        command("fdbrestore", "start", "--dest-cluster-file", target.path, "-r", container, "-w")
        assertEquals(expected, userRows(target), "Restored user keyspace differs, including directory metadata")
        for ((history, added, deletedKey) in checks) {
            val restored = open(target, history)
            try {
                val current = restored.execute(UniqueModel.get(added.key)).values.single()
                assertEquals("after@example.org", current.values { email })
                assertEquals(added.version, current.firstVersion)
                assertEquals("Recovery index fixture", restored.execute(Log.scan(
                    where = Equals(Log.severity.ref() with Severity.INFO)
                )).values.single().values { message })
                assertEquals(0, restored.execute(UniqueModel.get(deletedKey)).values.size)
                assertEquals(1, restored.execute(UniqueModel.get(deletedKey, filterSoftDeleted = false)).values.size)
                if (history) {
                    assertEquals("before@example.org", restored.execute(UniqueModel.get(
                        added.key, toVersion = added.version
                    )).values.single().values { email })
                    assertEquals(2, restored.execute(UniqueModel.getChanges(added.key)).changes.single().changes.size)
                }
                assertIs<ValidationFail<UniqueModel>>(restored.execute(UniqueModel.add(
                    UniqueModel.create { email with "after@example.org" }
                )).statuses.single())
                val changed = assertIs<ChangeSuccess<UniqueModel>>(restored.execute(UniqueModel.change(
                    added.key.change(Change(UniqueModel.email.ref() with "recovered@example.org"))
                )).statuses.single())
                check(changed.version > current.lastVersion)
            } finally {
                restored.close()
            }
        }
        println("PASS: exact user-keyspace recovery; Maryk reopen, current/history reads, soft deletes, unique claims and new writes, with history enabled and disabled.")
    } finally {
        children.asReversed().forEach { process ->
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(5, TimeUnit.SECONDS)
                }
            }
        }
        println("Disposable processes stopped. Artifacts retained at $workspace")
    }
}
